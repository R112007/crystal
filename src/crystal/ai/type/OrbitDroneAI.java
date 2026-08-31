package crystal.ai.type;

import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

public class OrbitDroneAI extends AIController {

  /** 全局子弹认领表：bullet.id → 认领该子弹的 Unit */
  public static final IntMap<Unit> bulletClaims = new IntMap<>();

  transient Vec2 orbitPos = new Vec2();
  transient float orbitRot;
  transient boolean hasOrbit;

  transient Bullet interceptBullet;
  public transient Position protectedTarget;
  transient boolean intercepting;
  transient float interceptTimer;

  public float threatRange = 180f;
  public float interceptMaxRange = 120f;

  transient boolean arriving;
  transient Vec2 arriveFrom = new Vec2();
  transient Vec2 arriveTo = new Vec2();
  transient float arriveTargetRot;
  transient float arriveTime;
  transient float arriveDuration;

  transient float returnTimer;

  public void setOrbit(float x, float y, float rotation) {
    if (hasOrbit && !arriving && !intercepting && orbitPos.dst(x, y) > 8f && unit != null) {
      startArrive(unit.x, unit.y, x, y, rotation, arriveDuration);
    }
    orbitPos.set(x, y);
    orbitRot = rotation;
    hasOrbit = true;
  }

  public void setOrbitAndIntercept(Bullet bullet, Position protect,
      float orbitX, float orbitY, float orbitRotation) {
    setOrbit(orbitX, orbitY, orbitRotation);
    setIntercept(bullet, protect);
  }

  public void setIntercept(Bullet bullet, Position protect) {
    if (this.interceptBullet != null && this.interceptBullet.isAdded()
        && this.interceptBullet.id == bullet.id) {
      this.protectedTarget = protect;
      this.interceptTimer = 90f;
      return;
    }
    this.interceptBullet = bullet;
    this.protectedTarget = protect;
    this.intercepting = true;
    this.interceptTimer = 90f;
  }

  public void clearIntercept() {
    releaseClaim();
    interceptBullet = null;
    intercepting = false;
    protectedTarget = null;
    returnTimer = 30f;
  }

  public void startArrive(float fromX, float fromY, float toX, float toY,
      float targetRot, float duration) {
    arriving = true;
    arriveFrom.set(fromX, fromY);
    arriveTo.set(toX, toY);
    arriveTargetRot = targetRot;
    arriveTime = duration;
    arriveDuration = duration;
  }

  public boolean isArriving() {
    return arriving;
  }

  public Bullet getInterceptBullet() {
    return interceptBullet;
  }

  @Override
  public void updateUnit() {
    if (unit.type.hasWeapons()) {
      updateWeapons();
    }

    if (arriving) {
      arriveTime -= Time.delta;
      if (hasOrbit)
        arriveTo.set(orbitPos);
      float p = 1f - Mathf.clamp(arriveTime / arriveDuration);
      p = Interp.smooth.apply(p);
      unit.x = Mathf.lerp(arriveFrom.x, arriveTo.x, p);
      unit.y = Mathf.lerp(arriveFrom.y, arriveTo.y, p);

      // 飞行过程中朝向目标点，实现"转向并飞过去"
      float flyAngle = Angles.angle(unit.x, unit.y, arriveTo.x, arriveTo.y);
      unit.rotation = Angles.moveToward(unit.rotation, flyAngle,
          unit.type.rotateSpeed * Time.delta);

      if (arriveTime <= 0)
        arriving = false;
      return;
    }

    if (hasOrbit && protectedTarget != null) {
      updateIntercept();
      if (intercepting)
        return;
    }

    if (hasOrbit) {
      if (returnTimer > 0 && !unit.within(orbitPos, 4f)) {
        returnTimer -= Time.delta;
        moveTo(orbitPos, 0f, 1f);
        unit.rotation = Angles.moveToward(unit.rotation, orbitRot,
            unit.type.rotateSpeed * Time.delta);
        return;
      }
      unit.x = orbitPos.x;
      unit.y = orbitPos.y;
      unit.vel().set(0, 0);
      unit.rotation = Angles.moveToward(unit.rotation, orbitRot,
          unit.type.rotateSpeed * Time.delta);
    }
  }

  void updateIntercept() {
    if (intercepting && interceptBullet != null) {
      if (!interceptBullet.isAdded() || interceptTimer <= 0
          || interceptBullet.dst(protectedTarget) > interceptMaxRange * 1.5f) {
        clearIntercept();
      } else {
        Unit claimer = bulletClaims.get(interceptBullet.id);
        if (claimer != unit) {
          clearIntercept();
        } else {
          interceptTimer -= Time.delta;
          doIntercept();
          return;
        }
      }
    }

    if (!intercepting && protectedTarget != null) {
      Bullet threat = findThreatBullet();
      if (threat != null && tryClaimBullet(threat)) {
        setIntercept(threat, protectedTarget);
        doIntercept();
      }
    }
  }

  Bullet findThreatBullet() {
    Bullet closest = null;
    float closestDst = Float.MAX_VALUE;

    for (Bullet b : Groups.bullet) {
      if (b.team == unit.team)
        continue;
      if (!b.within(protectedTarget, threatRange))
        continue;

      float toTarget = b.angleTo(protectedTarget);
      float velAngle = b.vel().angle();
      if (!Angles.within(toTarget, velAngle, 55f))
        continue;

      Unit claimer = bulletClaims.get(b.id);
      if (claimer != null && claimer.isValid() && claimer != unit)
        continue;

      float dst = b.dst(unit);
      if (dst > interceptMaxRange * 2.5f)
        continue;

      if (dst < closestDst) {
        closestDst = dst;
        closest = b;
      }
    }
    return closest;
  }

  boolean tryClaimBullet(Bullet b) {
    Unit claimer = bulletClaims.get(b.id);
    if (claimer != null && claimer.isValid() && claimer != unit)
      return false;
    bulletClaims.put(b.id, unit);
    return true;
  }

  void releaseClaim() {
    if (interceptBullet != null) {
      Unit claimer = bulletClaims.get(interceptBullet.id);
      if (claimer == unit)
        bulletClaims.remove(interceptBullet.id);
    }
  }

  void doIntercept() {
    if (interceptBullet == null || !interceptBullet.isAdded())
      return;

    float angle = interceptBullet.angleTo(protectedTarget);
    float dist = interceptBullet.dst(protectedTarget);
    float blockDist = Math.min(dist * 0.35f, 30f);
    float ix = interceptBullet.x + Angles.trnsx(angle, blockDist);
    float iy = interceptBullet.y + Angles.trnsy(angle, blockDist);

    moveTo(Tmp.v1.set(ix, iy), unit.hitSize * 0.5f, 1f);

    if (unit.within(ix, iy, unit.hitSize)) {
      unit.vel().scl(0.6f);
    }
  }

  @Override
  public void updateWeapons() {
    Teamc target = null;

    if (intercepting) {
      // 拦截期间：只打被拦截子弹附近的敌人，或子弹本身
      if (interceptBullet != null && interceptBullet.isAdded()) {
        target = Units.closestTarget(unit.team, interceptBullet.x, interceptBullet.y,
            unit.type.range,
            u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
            t -> unit.type.targetGround);
        if (target == null)
          target = interceptBullet;
      }
      // 关键：拦截期间不 fallback 索敌，子弹一死立刻停火
    } else {
      // 非拦截期间：正常索敌
      target = Units.closestTarget(unit.team, unit.x, unit.y, unit.type.range,
          u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
          t -> unit.type.targetGround);
    }

    // 兼容 Java 8 的死亡校验
    if (target instanceof Unit) {
      Unit u = (Unit) target;
      if (!u.isValid() || u.dead)
        target = null;
    }

    if (target != null) {
      unit.aimLook(target);
      unit.isShooting = true;
    } else {
      unit.isShooting = false;
    }

    for (var mount : unit.mounts) {
      mount.weapon.update(unit, mount);
    }
  }

  @Override
  public void moveTo(Position target, float circleLength, float smooth) {
    if (unit == null || target == null)
      return;
    Vec2 to = Tmp.v1.set(target.getX(), target.getY()).sub(unit.x, unit.y);
    float dst = to.len();
    if (dst <= circleLength)
      return;
    to.nor().scl(Math.min(dst - circleLength, unit.speed() * Time.delta * 2.5f));
    unit.movePref(to);
  }
}
