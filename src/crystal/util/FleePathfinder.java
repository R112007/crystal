package crystal.util;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.Nullable;
import mindustry.entities.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * 逃离寻路器 — 优先选最安全的点（离敌人最远），其次才考虑距离。
 *
 * 算法：
 * 1. 找到最近敌人，计算逃离方向（远离敌人）。
 * 2. 扫描所有方向，收集可通行候选点。
 * 3. 优先使用逃离方向（±170°内）的候选，选离敌人最远的。
 * 4. 逃离方向完全无路时，才尝试突围方向（向敌人左右侧 ±90°）。
 * 5. 若真的无路，退化为最近安全点 / 势场滑行。
 */
public class FleePathfinder {

  /** 单例 */
  public static final FleePathfinder inst = new FleePathfinder();

  private FleePathfinder() {
  }

  /**
   * 计算逃离目标 — 优先选离敌人最远的可通行点。
   *
   * @param unit        逃跑的单位
   * @param team        单位所属队伍
   * @param detectRange 敌人检测范围
   * @param safeRange   安全距离上限（目标不会超过此距离）
   * @param samples     已废弃，保留兼容旧调用
   * @return 逃离目标坐标（世界坐标），如果无路可走返回 null
   */
  public @Nullable Vec2 findFleeTarget(Unit unit, Team team, float detectRange, float safeRange, int samples) {
    if (unit == null || !unit.isValid())
      return null;

    Unit nearestThreat = Units.closestEnemy(team, unit.x, unit.y, detectRange,
        u -> u.isValid() && u.targetable(team));
    if (nearestThreat == null)
      return null;

    boolean isFlying = unit.type.flying;
    float step = tilesize * 0.8f;
    int maxSteps = (int) (safeRange / step);

    // 逃离方向：从敌人指向单位（远离敌人）
    float fleeAngle = nearestThreat.angleTo(unit.x, unit.y);

    // === 阶段1：逃离方向（±170°），选离敌人最远的 ===
    Vec2 bestEscape = null;
    float bestEscapeSafety = -1f;

    for (int offset = 0; offset < 36; offset++) {
      float angle = fleeAngle + offset * 10f;
      float angleDiff = Math.abs(arc.math.Angles.angleDist(angle, fleeAngle));
      if (angleDiff > 170f)
        continue;

      Vec2 pt = traceFleeLine(unit, angle, step, maxSteps, isFlying, true, team);
      if (pt != null && pt.dst(unit.x, unit.y) > tilesize * 1.5f) {
        float safety = Mathf.dst(pt.x, pt.y, nearestThreat.x, nearestThreat.y);
        if (safety > bestEscapeSafety) {
          bestEscapeSafety = safety;
          bestEscape = pt;
        }
      }
    }

    if (bestEscape != null)
      return bestEscape;

    // === 阶段2：逃离方向完全无路，尝试侧翼突围（±90° ~ ±170° 向敌人方向）===
    Vec2 bestBreakout = null;
    float bestBreakoutSafety = -1f;

    for (int offset = 0; offset < 36; offset++) {
      float angle = fleeAngle + 180f + offset * 10f;
      // 限制在敌人左右 ±90° 内，不要正对敌人
      float angleDiff = Math.abs(arc.math.Angles.angleDist(angle, fleeAngle + 180f));
      if (angleDiff > 90f)
        continue;

      Vec2 pt = traceFleeLine(unit, angle, step, maxSteps, isFlying, false, team);
      if (pt != null && pt.dst(unit.x, unit.y) > tilesize * 3f) {
        float safety = Mathf.dst(pt.x, pt.y, nearestThreat.x, nearestThreat.y);
        if (safety > bestBreakoutSafety) {
          bestBreakoutSafety = safety;
          bestBreakout = pt;
        }
      }
    }

    if (bestBreakout != null)
      return bestBreakout;

    // === 阶段3：完全无路，找任何方向离敌人最远的点（哪怕只移动一小段）===
    Vec2 nearestSafe = findNearestSafePoint(unit, nearestThreat, step, isFlying, team);
    if (nearestSafe != null)
      return nearestSafe;

    // === 阶段4：真的无路，退化为势场滑行 ===
    return calculateSlide(unit, nearestThreat, safeRange);
  }

  /**
   * 沿指定方向逐步外推，返回安全范围内最后一个可通行点。
   *
   * @param checkEnemies 是否检查路径上的敌人单位（true=遇到敌人停止）
   */
  public @Nullable Vec2 traceFleeLine(Unit unit, float angle, float step, int maxSteps, boolean isFlying,
      boolean checkEnemies, Team team) {
    Vec2 lastGood = null;

    for (int i = 1; i <= maxSteps; i++) {
      float dst = step * i;
      float tx = unit.x + Mathf.cosDeg(angle) * dst;
      float ty = unit.y + Mathf.sinDeg(angle) * dst;

      tx = Mathf.clamp(tx, 0, world.unitWidth());
      ty = Mathf.clamp(ty, 0, world.unitHeight());

      Tile tile = world.tileWorld(tx, ty);
      if (tile == null)
        break;

      if (!isFlying) {
        if (tile.solid())
          break;
        if (tile.floor() != null && tile.floor().isDeep())
          break;
      }

      if (checkEnemies && team != null) {
        Unit enemy = Units.closestEnemy(team, tx, ty, tilesize * 1.2f,
            u -> u.isValid() && u.targetable(team));
        if (enemy != null)
          break;
      }

      lastGood = new Vec2(tx, ty);
    }

    return lastGood;
  }

  /**
   * 找最近的安全点：在所有方向中，选离敌人最远的可通行点。
   * 即使只能移动一小段，也比原地不动好。
   */
  protected @Nullable Vec2 findNearestSafePoint(Unit unit, Unit threat, float step, boolean isFlying, Team team) {
    Vec2 best = null;
    float bestSafety = -1f;

    for (int offset = 0; offset < 36; offset++) {
      float angle = offset * 10f;
      Vec2 pt = traceFleeLine(unit, angle, step, 5, isFlying, true, team);
      if (pt != null) {
        float safety = Mathf.dst(pt.x, pt.y, threat.x, threat.y);
        if (safety > bestSafety) {
          bestSafety = safety;
          best = pt;
        }
      }
    }

    return best;
  }

  /** 势场滑行 */
  protected Vec2 calculateSlide(Unit unit, Unit threat, float safeRange) {
    Vec2 force = new Vec2();

    float dst = Mathf.dst(unit.x, unit.y, threat.x, threat.y);
    if (dst >= 1f) {
      float angle = threat.angleTo(unit);
      float strength = Mathf.sqr((safeRange - dst) / safeRange) * 100f;
      force.add(Mathf.cosDeg(angle) * strength, Mathf.sinDeg(angle) * strength);
    }

    for (int i = 0; i < 8; i++) {
      float a = i * 45f;
      float cx = unit.x + Mathf.cosDeg(a) * tilesize * 2f;
      float cy = unit.y + Mathf.sinDeg(a) * tilesize * 2f;
      Tile t = world.tileWorld(cx, cy);
      if (t != null && (t.solid() || (t.floor() != null && t.floor().isDeep()))) {
        force.add(Mathf.cosDeg(a + 180f) * 60f, Mathf.sinDeg(a + 180f) * 60f);
      }
    }

    if (force.isZero())
      force.set(1, 0);
    force.nor();

    Vec2 result = new Vec2(
        unit.x + force.x * safeRange * 0.5f,
        unit.y + force.y * safeRange * 0.5f);
    result.x = Mathf.clamp(result.x, 0, world.unitWidth());
    result.y = Mathf.clamp(result.y, 0, world.unitHeight());
    return result;
  }
}
