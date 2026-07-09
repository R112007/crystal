package crystal.entities;

import arc.graphics.*;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static mindustry.Vars.*;

/**
 * 定向闪电 - 在原有闪电基础上增加目标点约束，使闪电最终趋向指定目的地。
 * 
 */
public class DirectedLightning {
    private static final Rand random = new Rand();
    private static final Rect rect = new Rect();
    private static final Seq<Unit> entities = new Seq<>();
    private static final IntSet hit = new IntSet();
    private static final int maxChain = 8;
    private static final float hitRange = 30f;
    private static boolean bhit = false;
    private static int lastSeed = 0;

    public static Effect lightning = new Effect(10f, 500f, e -> {
        if (!(e.data instanceof Seq))
            return;
        Seq<Vec2> lines = e.data();

        // 原2.2 → 1.4，线条整体变细
        stroke(1.4f);
        // 颜色代码完全原样保留，不改动
        color(e.color, Color.white, e.fin());

        for (int i = 0; i < lines.size - 1; i++) {
            Vec2 cur = lines.get(i);
            Vec2 next = lines.get(i + 1);
            Lines.line(cur.x, cur.y, next.x, next.y);
        }

        // 原1f → 0.4f，顶点圆点大幅缩小，消除明显粗节点
        for (Vec2 p : lines) {
            Fill.circle(p.x, p.y, 0.4f * e.fout());
        }
    });

    /**
     * 创建定向闪电，从起点发射，最终趋向目标点。
     * 
     * @param hitter              发射子弹（用于伤害计算和队伍判断）
     * @param color               闪电颜色
     * @param damage              闪电伤害
     * @param x                   起点X
     * @param y                   起点Y
     * @param targetX             目标点X（aimX）
     * @param targetY             目标点Y（aimY）
     * @param baseAngle           基础发射角度（武器朝向）
     * @param length              闪电段数
     * @param maxAngleOffset      最大角度偏移（相对于目标方向）
     * @param targetPullStrength  向目标点拉拢强度 0~1
     * @param stepLengthMin       每步最小长度
     * @param stepLengthMax       每步最大长度
     * @param maxBends            最大额外转折次数
     * @param forceReachThreshold 最后几段强制指向目标的阈值
     * @param aimJitter           目标点随机偏移
     */
    public static void create(Bullet hitter, Color color, float damage,
            float x, float y, float targetX, float targetY,
            float baseAngle, int length,
            float maxAngleOffset, float targetPullStrength,
            float stepLengthMin, float stepLengthMax,
            int maxBends, int forceReachThreshold, float aimJitter) {
        createDirectedInternal(hitter,
                hitter == null || hitter.type.lightningType == null ? Bullets.damageLightning
                        : hitter.type.lightningType,
                lastSeed++, hitter.team, color, damage, x, y, targetX, targetY, baseAngle, length,
                maxAngleOffset, targetPullStrength, stepLengthMin, stepLengthMax, maxBends, forceReachThreshold,
                aimJitter);
    }

    /**
     * 简化版本 - 自动计算目标角度和长度，使用默认参数
     * 
     * @param hitter  发射子弹
     * @param color   闪电颜色
     * @param damage  伤害
     * @param x       起点X
     * @param y       起点Y
     * @param targetX 目标X（aimX）
     * @param targetY 目标Y（aimY）
     * @param length  闪电长度
     */
    public static void create(Bullet hitter, Color color, float damage,
            float x, float y, float targetX, float targetY, int length) {
        float targetAngle = Angles.angle(x, y, targetX, targetY);
        // 默认参数
        float maxAngleOffset = 45f;
        float targetPullStrength = 0.35f;
        float stepLengthMin = 12f, stepLengthMax = 20f;
        int maxBends = length / 3;
        int forceReachThreshold = 3;
        float aimJitter = 4f;

        create(hitter, color, damage, x, y, targetX, targetY, targetAngle, length,
                maxAngleOffset, targetPullStrength, stepLengthMin, stepLengthMax,
                maxBends, forceReachThreshold, aimJitter);
    }

    /**
     * 最简版本 - 用于 ContinuousLightningWeapon 集成
     * 自动从起点指向目标点，使用默认参数
     */
    public static void createToTarget(Bullet hitter, Color color, float damage,
            float x, float y, float targetX, float targetY) {
        float dst = Mathf.dst(x, y, targetX, targetY);
        // 根据距离计算长度：每段约15单位，至少8段，最多25段
        int length = Mathf.clamp((int) (dst / 15f), 8, 25);
        float targetAngle = Angles.angle(x, y, targetX, targetY);
        // 默认参数
        float maxAngleOffset = 45f;
        float targetPullStrength = 0.35f;
        float stepLengthMin = 12f, stepLengthMax = 20f;
        int maxBends = length / 3;
        int forceReachThreshold = 3;
        float aimJitter = 4f;

        create(hitter, color, damage, x, y, targetX, targetY, targetAngle, length,
                maxAngleOffset, targetPullStrength, stepLengthMin, stepLengthMax,
                maxBends, forceReachThreshold, aimJitter);
    }

    private static void createDirectedInternal(@Nullable Bullet hitter, BulletType hitCreate, int seed,
            Team team, Color color, float damage,
            float startX, float startY, float targetX, float targetY,
            float baseAngle, int length,
            float maxAngleOffset, float targetPullStrength,
            float stepLengthMin, float stepLengthMax,
            int maxBends, int forceReachThreshold, float aimJitter) {
        random.setSeed(seed);
        hit.clear();

        Seq<Vec2> lines = new Seq<>();
        bhit = false;

        float x = startX;
        float y = startY;
        float currentAngle = baseAngle;

        // 计算到目标的总距离和方向
        float totalDst = Mathf.dst(startX, startY, targetX, targetY);
        float angleToTarget = Angles.angle(startX, startY, targetX, targetY);

        // 如果目标很近，直接短闪电
        if (totalDst < 60f) {
            length = Math.max(length / 2, 4);
        }

        for (int i = 0; i < length; i++) {
            // 计算当前到目标的剩余距离和方向
            float remainingDst = Mathf.dst(x, y, targetX, targetY);
            float currentAngleToTarget = Angles.angle(x, y, targetX, targetY);

            // 创建伤害子弹（每段都产生伤害）
            hitCreate.create(null, team, x, y, currentAngle, damage * (hitter == null ? 1f : hitter.damageMultiplier()),
                    1f, 1f, hitter);

            // 添加当前点到线段列表（带随机抖动）
            lines.add(new Vec2(x + Mathf.range(aimJitter), y + Mathf.range(aimJitter)));

            // 射线检测绝缘体
            if (lines.size > 1) {
                bhit = false;
                Vec2 from = lines.get(lines.size - 2);
                Vec2 to = lines.get(lines.size - 1);
                World.raycastEach(World.toTile(from.x), World.toTile(from.y),
                        World.toTile(to.x), World.toTile(to.y), (wx, wy) -> {
                            Tile tile = world.tile(wx, wy);
                            if (tile != null && (tile.build != null && tile.build.isInsulated())
                                    && tile.team() != team) {
                                bhit = true;
                                lines.get(lines.size - 1).set(wx * tilesize, wy * tilesize);
                                return true;
                            }
                            return false;
                        });
                if (bhit)
                    break;
            }

            // ========== 定向角度计算 ==========

            // 剩余段数比例（0=刚开始，1=快结束）
            float progress = (float) i / (length - 1);

            // 基础随机偏移（随进度减小，越接近目标越精确）
            float randomOffset = random.range(maxAngleOffset * (1f - progress * 0.5f));

            // 向目标点拉拢的角度修正
            float pullAngle = 0f;
            if (remainingDst > 10f) {
                // 计算当前角度与目标角度的差
                float angleDiff = Angles.angleDist(currentAngle, currentAngleToTarget);
                // 向目标方向拉拢
                float signedDiff = currentAngleToTarget - currentAngle;
                while (signedDiff > 180f)
                    signedDiff -= 360f;
                while (signedDiff < -180f)
                    signedDiff += 360f;
                pullAngle = Mathf.sign(signedDiff) * Math.abs(angleDiff) * targetPullStrength * (1f + progress);
            }

            // 强制到达目标：最后几段直接指向目标
            if ((length - i) <= forceReachThreshold && remainingDst > 5f) {
                currentAngle = currentAngleToTarget;
                randomOffset = random.range(10f); // 最后阶段小偏移
            } else {
                // 正常情况：基础角度 + 随机偏移 + 目标拉拢
                currentAngle = currentAngle + randomOffset + pullAngle;
            }

            // 额外转折：根据maxBends添加随机大角度转折
            if (maxBends > 0 && i > 0 && i < length - 2 && random.chance(0.3f)) {
                float bendAngle = random.random(30f, 60f) * (random.chance(0.5f) ? 1 : -1);
                currentAngle += bendAngle;
                maxBends--;
            }

            // 计算步长（越接近目标步长越短，确保精确到达）
            float stepLength;
            if (remainingDst < 20f) {
                stepLength = remainingDst * 0.8f; // 接近目标时步长缩短
            } else {
                stepLength = Mathf.random(stepLengthMin, stepLengthMax);
            }

            // 更新位置
            x += Angles.trnsx(currentAngle, stepLength);
            y += Angles.trnsy(currentAngle, stepLength);

            // 目标点吸附：非常接近目标时直接跳到目标
            if (remainingDst < stepLength * 1.5f && i >= length - 2) {
                x = targetX + Mathf.range(aimJitter * 0.5f);
                y = targetY + Mathf.range(aimJitter * 0.5f);
            }

            // 检测敌人（连锁闪电效果）
            rect.setSize(hitRange).setCenter(x, y);
            entities.clear();
            if (hit.size < maxChain) {
                Units.nearbyEnemies(team, rect, u -> {
                    if (!hit.contains(u.id())
                            && (hitter == null || u.checkTarget(hitter.type.collidesAir, hitter.type.collidesGround))) {
                        entities.add(u);
                    }
                });
            }

            Unit furthest = Geometry.findFurthest(x, y, entities);
            if (furthest != null) {
                hit.add(furthest.id());
                // 如果击中敌人，稍微向敌人方向偏移
                float enemyAngle = Angles.angle(x, y, furthest.x, furthest.y);
                currentAngle = Mathf.slerp(currentAngle, enemyAngle, 0.3f);
                x = furthest.x();
                y = furthest.y();
            }
        }

        // 确保最后一点包含目标位置（用于视觉效果）
        if (lines.size > 0) {
            Vec2 last = lines.peek();
            // 如果最后一点离目标太远，添加一个趋向目标的点
            if (Mathf.dst(last.x, last.y, targetX, targetY) > 20f) {
                lines.add(new Vec2(
                        targetX + Mathf.range(aimJitter),
                        targetY + Mathf.range(aimJitter)));
            }
        }

        // 在目标点产生闪电效果（带所有线段）
        lightning.at(targetX, targetY, currentAngle, color, lines);
    }
}
