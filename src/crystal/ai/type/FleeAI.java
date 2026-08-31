package crystal.ai.type;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class FleeAI extends AIController {

    // ============ 配置参数 ============

    /** 敌人检测半径 */
    public float detectRange = 300f;
    /** 安全距离 — 超过此距离停止逃跑 */
    public float safeRange = 400f;
    /** 恐慌距离 — 低于此距离触发 boost */
    public float panicRange = 120f;
    /** 逃离目标距离 */
    public float fleeDst = 200f;
    /** 重新计算逃离目标的间隔 */
    public float retargetInterval = 20f;
    /** 是否允许反击 */
    public boolean fightBack = true;
    /** 反击射程比例 */
    public float fightBackRatio = 0.5f;
    /** 方向采样数量 */
    public int directionSamples = 24;
    /** 卡死检测阈值 */
    public float stuckThreshold = 60f;
    /** 卡死时偏移距离 */
    public float stuckJitter = 100f;

    // ============ 内部状态 ============

    protected final Vec2 fleePos = new Vec2();
    protected float retargetTimer;
    protected float stuckTimer, stuckX = -999f, stuckY = -999f;
    protected @Nullable Unit nearestEnemy;
    protected float nearestDst = Float.MAX_VALUE;

    public FleeAI() {
    }

    public FleeAI(float detectRange, float safeRange) {
        this.detectRange = detectRange;
        this.safeRange = safeRange;
    }

    // ============ 核心更新 ============

    @Override
    public void updateMovement() {
        if (unit == null || !unit.isValid())
            return;

        // 1. 找最近敌人
        findNearestEnemy();

        // 2. 安全判定
        if (nearestEnemy == null || nearestDst > safeRange) {
            onSafe();
            return;
        }

        // 3. 重新计算逃离点
        if (retargetTimer <= 0) {
            recalculateFlee();
            retargetTimer = retargetInterval;
        }
        retargetTimer -= Time.delta;

        // 4. 恐慌时 boost
        if (nearestDst < panicRange && unit.type.canBoost) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 1f, 0.08f);
        }

        // 5. 执行移动 — 参考 DefenderAI: moveTo(target, distance, smooth)
        // circleLength = 10f: 到达目标点后保持 10f 距离
        // smooth = 50f: 平滑度
        moveTo(fleePos, 10f, 50f);

        // 6. 面向逃离方向
        unit.lookAt(fleePos);

        // 7. 卡死检测
        checkStuck();
    }

    @Override
    public void updateTargeting() {
        if (!fightBack || !unit.hasWeapons())
            return;

        float fbRange = unit.type.range * fightBackRatio;

        if (retarget()) {
            target = Units.closestEnemy(unit.team, unit.x, unit.y, fbRange,
                    u -> u.isValid() && u.targetable(unit.team));
        }

        if (target != null && shouldShoot()) {
            unit.lookAt(target);
            for (var mount : unit.mounts) {
                if (mount.weapon.controllable) {
                    mount.target = target;
                }
            }
        }
    }

    // ============ 子方法 ============

    /** 找到最近的敌人 */
    protected void findNearestEnemy() {
        nearestEnemy = Units.closestEnemy(unit.team, unit.x, unit.y, detectRange,
                u -> u.isValid() && u.targetable(unit.team));
        nearestDst = nearestEnemy != null ? unit.dst(nearestEnemy) : Float.MAX_VALUE;
    }

    /** 安全状态 */
    protected void onSafe() {
        // 停止移动
        unit.moveAt(Vec2.ZERO);
        stuckTimer = 0f;
        nearestEnemy = null;
    }

    /** 重新计算逃离方向 — 方向采样，避开障碍 */
    protected void recalculateFlee() {
        if (nearestEnemy == null)
            return;

        boolean isFlying = unit.type.flying;
        float bestScore = -Float.MAX_VALUE;
        float bestAngle = unit.rotation;

        // 在周围均匀采样多个方向
        for (int i = 0; i < directionSamples; i++) {
            float angle = (360f / directionSamples) * i;
            float testX = unit.x + Mathf.cosDeg(angle) * fleeDst;
            float testY = unit.y + Mathf.sinDeg(angle) * fleeDst;

            // 边界限制
            testX = Mathf.clamp(testX, 0, world.unitWidth());
            testY = Mathf.clamp(testY, 0, world.unitHeight());

            // === 地形检查：地面单位避开墙和深水 ===
            if (!isFlying) {
                Tile testTile = world.tileWorld(testX, testY);
                if (testTile != null) {
                    if (testTile.solid())
                        continue; // 墙 → 跳过
                    if (testTile.floor() != null && testTile.floor().isDeep())
                        continue; // 深水 → 跳过
                }
            }

            float score = evaluatePosition(testX, testY);

            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }

        // 设置逃离目标
        fleePos.set(
                unit.x + Mathf.cosDeg(bestAngle) * fleeDst,
                unit.y + Mathf.sinDeg(bestAngle) * fleeDst);
        fleePos.x = Mathf.clamp(fleePos.x, 0, world.unitWidth());
        fleePos.y = Mathf.clamp(fleePos.y, 0, world.unitHeight());

        // 地面单位：确保目标瓦片可通行，否则搜索附近
        if (!isFlying) {
            Tile safe = findSafeTile(fleePos);
            if (safe != null) {
                fleePos.set(safe.worldx(), safe.worldy());
            }
        }
    }

    /** 评估某个位置的安全性 — 分数越高越安全 */
    protected float evaluatePosition(float px, float py) {
        float score = 0f;

        // 远离最近敌人
        if (nearestEnemy != null) {
            score += Mathf.dst(px, py, nearestEnemy.x, nearestEnemy.y) * 2f;
        }

        // 远离世界边缘（避免被逼到墙角）
        float margin = 80f;
        if (px < margin)
            score -= (margin - px) * 3f;
        if (px > world.unitWidth() - margin)
            score -= (px - (world.unitWidth() - margin)) * 3f;
        if (py < margin)
            score -= (margin - py) * 3f;
        if (py > world.unitHeight() - margin)
            score -= (py - (world.unitHeight() - margin)) * 3f;

        return score;
    }

    /** 为地面单位寻找可通行瓦片 */
    protected @Nullable Tile findSafeTile(Vec2 desiredPos) {
        Tile target = world.tileWorld(desiredPos.x, desiredPos.y);
        if (target == null)
            return null;
        // 目标本身可通行且不是深水 → 直接返回
        if (!target.solid() && !(target.floor() != null && target.floor().isDeep()))
            return target;

        // 在周围螺旋搜索可通行瓦片
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r)
                        continue; // 只搜索外圈
                    Tile t = world.tile(target.x + dx, target.y + dy);
                    if (t == null)
                        continue;
                    if (t.solid())
                        continue;
                    if (t.floor() != null && t.floor().isDeep())
                        continue;

                    float d = desiredPos.dst(t.worldx(), t.worldy());
                    if (d < bestDst) {
                        bestDst = d;
                        best = t;
                    }
                }
            }
            if (best != null)
                break; // 找到最近的可通行瓦片就返回
        }
        return best;
    }

    /** 卡死检测 */
    protected void checkStuck() {
        if (unit.vel.len() > unit.type.speed * 0.2f) {
            stuckTimer = 0f;
            stuckX = unit.x;
            stuckY = unit.y;
            return;
        }

        if (Mathf.within(unit.x, unit.y, stuckX, stuckY, tilesize * 2f)) {
            stuckTimer += Time.delta;
            if (stuckTimer > stuckThreshold) {
                fleePos.add(Mathf.range(stuckJitter), Mathf.range(stuckJitter));
                fleePos.x = Mathf.clamp(fleePos.x, 0, world.unitWidth());
                fleePos.y = Mathf.clamp(fleePos.y, 0, world.unitHeight());
                stuckTimer = 0f;
                retargetTimer = 0f;
            }
        } else {
            stuckX = unit.x;
            stuckY = unit.y;
            stuckTimer = 0f;
        }
    }

    // ============ 预设 ============

    public static FleeAI coward() {
        FleeAI ai = new FleeAI();
        ai.detectRange = 450f;
        ai.safeRange = 550f;
        ai.panicRange = 200f;
        ai.fleeDst = 300f;
        ai.fightBack = false;
        ai.directionSamples = 32;
        return ai;
    }

    public static FleeAI guerrilla() {
        FleeAI ai = new FleeAI();
        ai.detectRange = 260f;
        ai.safeRange = 320f;
        ai.panicRange = 90f;
        ai.fleeDst = 180f;
        ai.fightBack = true;
        ai.fightBackRatio = 1.0f;
        ai.directionSamples = 16;
        return ai;
    }
}
