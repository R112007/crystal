package crystal.ai.type;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.Predict;
import mindustry.entities.units.*;
import mindustry.gen.*;

public class OrbitalCommandAI extends AIController {

  /** 期望轨道半径 (世界单位) */
  public float orbitRadius = 75f;
  /** 轨道角速度 (度/帧)。推荐 2~4 */
  public float orbitSpeed = 2.8f;
  /** 半径修正刚度。越大越"粘"在 orbitRadius 上 */
  public float radiusStiffness = 2.5f;
  /** 目标移动预测补偿 (0~1) */
  public float targetVelocityFactor = 0.35f;

  /** 1=逆时针，-1=顺时针。基于 unit.id 自动分配 */
  protected int orbitDir;

  @Override
  public void init() {
    super.init();
    orbitDir = Mathf.randomSeed(unit.id, 0, 1) == 0 ? 1 : -1;
  }

  // ========== 关键修复1：不再因为"目标太远"就丢弃它 ==========
  // 原版 invalid() 会检查目标是否在射程内，导致全图索敌后武器不瞄准
  @Override
  public boolean invalid(Teamc target) {
    return target == null || (target instanceof Healthc h && !h.isValid());
  }

  // ========== 关键修复2：飞行单位不再强制面朝移动方向 ==========
  // 原版 updateVisuals() 会在 updateTargeting() 之前把朝向设为 vel().angle()（切线）
  // 这会覆盖我们后续想让单位面朝目标的意图
  @Override
  public void updateVisuals() {
    if (unit.isFlying() && unit.type.wobble) {
      unit.wobble();
    }
    // 不再调用 unit.lookAt(unit.prefRotation());
    // 朝向完全由 updateMovement() 接管
  }

  @Override
  public void updateMovement() {
    // 每帧主动索敌（兼容无武器单位，也不受 retarget 间隔限制）
    if (target == null || invalid(target)) {
      target = findMainTarget(unit.x, unit.y, unit.range(), unit.type.targetAir, unit.type.targetGround);
    }

    if (target == null || invalid(target)) {
      unit.vel.approachDelta(Tmp.v1.setZero(), unit.type.accel * unit.speed() * 0.3f);
      return;
    }

    float dst = unit.dst(target);

    // 阶段1：距离过远时先接近
    if (dst > orbitRadius * 2.5f) {
      moveTo(target, orbitRadius, 100f, unit.isFlying(), null, true);
      // 接近过程中也要面朝目标
      faceTargetRaw();
      return;
    }

    // ========== 阶段2：轨道力学 ==========
    Vec2 toTarget = Tmp.v1.set(target).sub(unit);
    float dstToTarget = toTarget.len();

    // 切线方向
    Vec2 tangent = Tmp.v2.set(-toTarget.y, toTarget.x).nor().scl(orbitDir);

    // 切向速度 v = ω * r
    float tangentSpeed = orbitRadius * orbitSpeed * Mathf.degreesToRadians;

    // 径向弹簧修正：dst > R 时向内拉，dst < R 时向外推
    float radialSpeed = (dstToTarget - orbitRadius) * radiusStiffness;
    radialSpeed = Mathf.clamp(radialSpeed, -unit.speed() * 0.5f, unit.speed() * 0.5f);

    Vec2 desiredVel = Tmp.v3.set(tangent).scl(tangentSpeed)
        .add(toTarget.nor().scl(radialSpeed));

    // 目标移动前馈
    if (target instanceof Velc v) {
      desiredVel.add(v.vel().x * targetVelocityFactor, v.vel().y * targetVelocityFactor);
    }

    desiredVel.limit(unit.speed());

    // 核心惯性感：平滑修正速度向量，而不是直接设置位置
    unit.vel.approachDelta(desiredVel, unit.type.accel * unit.speed());

    // ========== 关键修复3：强制面朝目标 ==========
    faceTargetRaw();
  }

  /** 无条件让单位本体和武器都朝向目标，无视 faceTarget 配置 */
  protected void faceTargetRaw() {
    if (target == null || invalid(target) || !unit.type.hasWeapons())
      return;

    // 单位本体朝向目标（对固定武器至关重要）
    unit.lookAt(Predict.intercept(unit, target, unit.type.weapons.first().bullet));

    // 额外保险：手动给所有可控武器注入瞄准点，确保它们即使不在射程内也会旋转追踪
    for (WeaponMount mount : unit.mounts) {
      if (!mount.weapon.controllable || mount.weapon.noAttack)
        continue;

      Vec2 to = Predict.intercept(unit, target, mount.weapon.bullet);
      mount.aimX = to.x;
      mount.aimY = to.y;
      mount.rotate = true;
    }
  }

  // ========== 关键修复4：全图索敌 ==========
  @Override
  public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
    Teamc result = super.findMainTarget(x, y, range, air, ground);
    if (result == null) {
      result = target(x, y, Float.MAX_VALUE, air, ground);
    }
    return result;
  }
}
