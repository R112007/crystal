package crystal.ai.type;

import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.ai.types.GroundAI;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;
import static mindustry.Vars.*;

/**
 * 冲撞 AI（地面版）：寻找最近敌方目标并冲撞。
 * <p>
 * 继承 {@link GroundAI} 以获得地面单位的基础支持（卡住检测等），
 * 但不调用 {@code pathfind()}（流场会绕开敌方建筑，与冲撞需求冲突），
 * 改用 {@code moveAt()} 直接控制移动方向。
 * <p>
 * 寻路规则：
 * <ul>
 * <li>规避环境墙（StaticWall）和非人工实心地形</li>
 * <li>规避己方建筑</li>
 * <li>不规避敌方建筑/单位（直接撞过去）</li>
 * </ul>
 * <p>
 * 撞击循环：
 * <ol>
 * <li>CHARGING：全速冲向目标</li>
 * <li>撞到敌方建筑速度骤降 → 切换到 BACKING</li>
 * <li>BACKING：背对目标后退到足够距离</li>
 * <li>距离足够 → 切换回 CHARGING，再次冲撞</li>
 * </ol>
 */
public class RammingAI extends GroundAI {

    private enum RamState {
        CHARGING, BACKING
    }

    private RamState state = RamState.CHARGING;
    private float backTimer = 0f;

    private static final float maxBackTime = 20f; // 后退持续帧数（约2秒）
    private static final float backSpeedScale = 0.25f; // 后退速度倍率

    @Override
    public void updateMovement() {
        if (target == null || (target instanceof Healthc h && !h.isValid())) {
            target = findTarget(unit.x, unit.y, unit.range(), false, true);
            if (target == null) {
                var core = unit.closestEnemyCore();
                if (core != null) {
                    chargeTo(core);
                }
                return;
            }
        }

        float tx = target.getX();
        float ty = target.getY();
        float dstToTarget = unit.dst(tx, ty);

        // 计算目标碰撞半径
        float targetSize = 8f;
        if (target instanceof Building b)
            targetSize = b.block.size * tilesize;
        else if (target instanceof Unit u)
            targetSize = u.hitSize;
        float contactDist = (unit.hitSize + targetSize) / 2f;

        switch (state) {
            case CHARGING -> {
                chargeTo(target);

                // 贴上目标了 → 开始后退
                if (dstToTarget < contactDist + 4f) {
                    state = RamState.BACKING;
                    backTimer = 0f;
                }
                if (unit.vel.len() > 0.1f) {
                    unit.lookAt(unit.vel.angle());
                }
            }
            case BACKING -> {
                // 背离目标方向，直接设置速度
                float dx = unit.x - tx;
                float dy = unit.y - ty;
                float len = Mathf.len(dx, dy);
                if (len > 0.001f) {
                    float backSpeed = unit.type.speed * backSpeedScale;
                    unit.vel.set(dx / len * backSpeed, dy / len * backSpeed);
                }

                backTimer += Time.delta;

                // 后退够久了，重新冲锋
                if (backTimer > maxBackTime) {
                    state = RamState.CHARGING;
                }
            }
        }

        // 朝移动方向看
    }

    /**
     * 全速冲向目标，途中规避环境墙和己方建筑，不规避敌方建筑。
     */
    private void chargeTo(Position target) {
        float ang = unit.angleTo(target);

        // 检查前方是否有需要规避的障碍物
        float checkDist = unit.hitSize * 2.5f;
        Vec2 moveVec = avoidObstacles(ang, checkDist);

        // 无障碍则直接朝目标
        if (moveVec.isZero()) {
            moveVec.set(Mathf.cosDeg(ang), Mathf.sinDeg(ang));
        }

        moveVec.setLength(unit.type.speed);
        unit.moveAt(moveVec, unit.type.accel);
    }

    /**
     * 障碍规避：检查前方 3 个方向，只规避环境墙、非人工实心地形、己方建筑。
     * 返回修正后的移动方向（零向量表示前方无障碍）。
     */
    private Vec2 avoidObstacles(float angle, float dist) {
        Vec2 result = new Vec2();
        float[] checkAngles = { angle, angle - 35f, angle + 35f };

        for (float a : checkAngles) {
            float cx = unit.x + Mathf.cosDeg(a) * dist;
            float cy = unit.y + Mathf.sinDeg(a) * dist;
            Tile tile = world.tileWorld(cx, cy);
            if (tile == null)
                continue;

            boolean shouldAvoid = false;

            // 环境墙
            if (tile.block() instanceof StaticWall) {
                shouldAvoid = true;
            }
            // 非人工实心地形（岩石等）
            else if (tile.solid() && !tile.block().synthetic()) {
                shouldAvoid = true;
            }
            // 己方建筑
            else if (tile.build != null && tile.build.team == unit.team) {
                shouldAvoid = true;
            }

            if (shouldAvoid) {
                // 向该方向的垂直方向偏转
                result.add(Mathf.cosDeg(a + 90f), Mathf.sinDeg(a + 90f));
            }
        }

        return result;
    }

    @Override
    public Teamc findTarget(float x, float y, float range, boolean air, boolean ground) {
        // 优先找最近敌方单位
        Unit u = Units.closestEnemy(unit.team, x, y, range, Unit::isValid);
        if (u != null)
            return u;

        // 其次找最近敌方建筑（瓦片扫描）
        Building closest = null;
        float closestDst = Float.MAX_VALUE;
        int tr = Mathf.ceil(range / tilesize);
        int tx = world.toTile(x), ty = world.toTile(y);
        for (int dx = -tr; dx <= tr; dx++) {
            for (int dy = -tr; dy <= tr; dy++) {
                Tile tile = world.tile(tx + dx, ty + dy);
                if (tile == null || tile.build == null)
                    continue;
                Building b = tile.build;
                if (b.team == unit.team || !b.isValid())
                    continue;
                float d = Mathf.dst(x, y, b.x, b.y);
                if (d < closestDst && d <= range) {
                    closestDst = d;
                    closest = b;
                }
            }
        }
        if (closest != null)
            return closest;

        // 最后找敌方核心
        return unit.closestEnemyCore();
    }

    @Override
    public boolean checkTarget(Teamc target, float x, float y, float range) {
        return target == null || (target instanceof Healthc h && !h.isValid());
    }

    @Override
    public boolean shouldShoot() {
        return false;
    }
}
