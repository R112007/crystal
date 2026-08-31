package crystal.entities.abilities;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Payloadc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.RegenProjector;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.consumers.ConsumeLiquid;

import static mindustry.Vars.*;

public class PayloadFieldAbility extends Ability {
  float timer = 0;
  public Seq<Building> targets = new Seq<>();
  public boolean hasOver, hasRegen;

  // Turret multi-state support
  static class TurretState {
    Block block;
    Block lastBlock;
    float timer;
    float rotation;
    Teamc target;
    Bullet bullet;
    boolean continuous;
    boolean isContinuous;
    boolean isLaser;
    float bulletLife;
    float bulletLifeMax;
    float firingMoveFract;
    float range, reload, rotateSpeed, inaccuracy, shootCone;
    int shots;
    boolean targetAir, targetGround;
    ObjectMap<Item, BulletType> itemAmmo;
    BulletType powerAmmo;
    float cooldownRate;

    TurretState() {
      rotation = 0f;
      timer = 0f;
    }
  }

  transient ObjectMap<BuildPayload, TurretState> turretStates = new ObjectMap<>();

  // Force projector
  transient Block forceBlock;
  transient float shieldHealth;
  transient float shieldRadius;
  transient float shieldRadiusScale;
  transient boolean shieldBroken;
  transient float shieldBuildup;
  transient float forceMaxHealth;
  transient float forceRegenRate;
  transient float forceCooldownNormal;
  transient float forceCooldownBroken;
  transient int forceSides;
  transient float shieldHit;

  // Nuclear reactor heat
  transient NuclearReactor reactorBlock;
  transient float reactorHeat;
  transient float reactorFuelTimer;

  public PayloadFieldAbility() {
  }

  @Override
  public void update(Unit unit) {
    timer += Time.delta;
    hasOver = false;
    hasRegen = false;

    updateOverrider(unit);
    updateMendProjector(unit);
    updateRegenProjector(unit);
    updateTurrets(unit);
    updateForceProjector(unit);
    updateReactorHeat(unit);
  }

  public void updateOverrider(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload b && b.build.block instanceof OverdriveProjector over) {
          indexer.eachBlock(unit.team, unit.x, unit.y, over.range,
              other -> other.team == unit.team && other.block.canOverdrive, other -> {
                other.applyBoost(over.speedBoost, over.reload + 1f);
              });
          hasOver = true;
        }
      }
    }
  }

  public void updateMendProjector(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof MendProjector mend) {
          if (timer >= mend.reload) {
            indexer.eachBlock(unit.team, unit.x, unit.y, mend.range, b -> b.damaged() && !b.isHealSuppressed(),
                other -> {
                  other.heal(other.maxHealth() * (mend.healPercent) / 100f);
                  other.recentlyHealed();
                  Fx.healBlockFull.at(other.x, other.y, other.block.size, mend.baseColor, other.block);
                  timer = 0f;
                });
          }
          hasRegen = true;
        }
      }
    }
  }

  public void updateRegenProjector(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof RegenProjector regen) {
          updateTargets(unit, regen);
          for (var b : targets) {
            if (b.damaged())
              b.heal(regen.healPercent);
          }
          hasRegen = true;
        }
      }
    }
  }

  public void updateTargets(Unit unit, RegenProjector regen) {
    targets.clear();
    indexer.eachBlock(unit.team, Tmp.r1.setCentered(unit.x, unit.y, regen.range * tilesize), b -> true, targets::add);
  }

  void updateReactorHeat(Unit unit) {
    Seq<NuclearReactor> reactors = new Seq<>();
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof NuclearReactor n) {
          reactors.add(n);
        }
      }
    }

    if (reactors.isEmpty()) {
      reactorBlock = null;
      reactorHeat = 0f;
      reactorFuelTimer = 0f;
      return;
    }

    // Use slowest heating rate (smallest heating)
    NuclearReactor slowest = reactors.first();
    for (int i = 1; i < reactors.size; i++) {
      if (reactors.get(i).heating < slowest.heating) {
        slowest = reactors.get(i);
      }
    }
    reactorBlock = slowest;

    // Count how many reactors have matching fuelItem
    int matchingReactors = 0;
    for (NuclearReactor nr : reactors) {
      if (unit.stack.item == nr.fuelItem && unit.stack.amount > 0) {
        matchingReactors++;
      }
    }

    if (matchingReactors > 0) {
      if (reactorFuelTimer <= 0f) {
        // Consume items for each matching reactor
        int toConsume = Math.min(matchingReactors, unit.stack.amount);
        unit.stack.amount -= toConsume;
        if (unit.stack.amount <= 0) {
          unit.stack.item = null;
          unit.stack.amount = 0;
        }
        reactorFuelTimer = 30f;
      }

      if (reactorFuelTimer > 0f) {
        reactorFuelTimer -= Time.delta;
        // Heat does NOT stack: use slowest reactor's heating rate
        reactorHeat += slowest.heating * Time.delta;
      }
    } else {
      reactorHeat = Mathf.lerpDelta(reactorHeat, 0f, 0.01f);
      reactorFuelTimer = 0f;
    }

    reactorHeat = Mathf.clamp(reactorHeat, 0f, 1f);

    if (reactorHeat >= 1f) {
      triggerReactorExplosion(unit, reactors);
      reactorHeat = 0f;
      reactorFuelTimer = 0f;
    }
  }

  void triggerReactorExplosion(Unit unit, Seq<NuclearReactor> reactors) {
    if (!state.rules.reactorExplosions)
      return;

    float x = unit.x, y = unit.y;
    float[] totalDamage = { 0f };
    float[] maxRadius = { 0f };

    for (NuclearReactor nr : reactors) {
      if (unit.stack.item == nr.fuelItem) {
        totalDamage[0] += nr.explosionDamage;
        if (nr.explosionRadius > maxRadius[0])
          maxRadius[0] = nr.explosionRadius;
      }
    }

    if (totalDamage[0] <= 0f)
      return;

    float radius = maxRadius[0] * tilesize;

    reactors.first().explodeEffect.at(x, y);
    reactors.first().explodeSound.at(x, y);

    Units.nearby(null, x, y, radius, u -> {
      if (!u.isValid() || u.team == unit.team)
        return;
      float dist = Math.max(0f, u.dst(x, y) - u.type.hitSize / 2f);
      float scaled = radius <= 0.00001f ? 1f : Mathf.lerp(1f - dist / radius, 1f, 0.4f);
      u.damage(totalDamage[0] * scaled);
    });

    indexer.eachBlock(null, x, y, radius, b -> b.team != unit.team, b -> {
      float dist = b.dst(x, y);
      float scaled = radius <= 0.00001f ? 1f : Mathf.lerp(1f - dist / radius, 1f, 0.4f);
      b.damage(totalDamage[0] * scaled);
    });

    unit.kill();
  }

  // ==================== MULTI-TURRET SUPPORT ====================

  void updateTurrets(Unit unit) {
    Seq<BuildPayload> current = new Seq<>();
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof Turret) {
          current.add(bp);
        }
      }
    }

    // Remove stale states
    Seq<BuildPayload> toRemove = new Seq<>();
    for (BuildPayload bp : turretStates.keys()) {
      boolean found = false;
      for (BuildPayload c : current) {
        if (c == bp) {
          found = true;
          break;
        }
      }
      if (!found) {
        TurretState s = turretStates.get(bp);
        if (s != null && s.bullet != null && s.bullet.isAdded())
          s.bullet.remove();
        toRemove.add(bp);
      }
    }
    for (BuildPayload bp : toRemove)
      turretStates.remove(bp);

    // Update each turret
    for (BuildPayload bp : current) {
      TurretState state = turretStates.get(bp);
      if (state == null) {
        state = new TurretState();
        state.rotation = unit.rotation;
        turretStates.put(bp, state);
      }

      if (state.block != bp.build.block) {
        syncTurretState(state, bp.build.block);
      }

      updateTurretState(unit, state);
    }
  }

  void updateTurretState(Unit unit, TurretState state) {
    state.target = Units.bestEnemy(unit.team, unit.x, unit.y, state.range,
        u -> u.checkTarget(state.targetAir, state.targetGround),
        ((Turret) state.block).unitSort);

    if (state.target == null && state.targetGround) {
      state.target = Units.closestTarget(unit.team, unit.x, unit.y, state.range,
          u -> false, b -> true);
    }

    float targetRot;
    if (state.target != null) {
      BulletType sample = getTurretSampleBullet(state);
      if (sample != null && sample.speed > 0 && state.target instanceof Hitboxc h) {
        Vec2 pt = Predict.intercept(unit, h, sample.speed);
        targetRot = Angles.angle(unit.x, unit.y, pt.x, pt.y);
      } else {
        targetRot = unit.angleTo(state.target);
      }
    } else {
      targetRot = unit.rotation;
    }

    float rotateScl = (state.isLaser && state.bullet != null) ? state.firingMoveFract : 1f;
    state.rotation = Angles.moveToward(state.rotation, targetRot, state.rotateSpeed * Time.delta * rotateScl);

    if (state.continuous) {
      if (state.isContinuous) {
        updateContinuousTurretState(unit, state, targetRot);
      } else {
        updateLaserTurretState(unit, state, targetRot);
      }
    } else {
      state.timer += Time.delta * unit.reloadMultiplier;
      if (state.target != null && Angles.within(state.rotation, targetRot, state.shootCone)
          && state.timer >= state.reload) {
        if (tryShootTurret(unit, state)) {
          state.timer = 0f;
        }
      }
    }
  }

  void updateLaserTurretState(Unit unit, TurretState state, float targetRot) {
    if (state.bullet != null
        && (!state.bullet.isAdded() || state.bullet.type != getTurretSampleBullet(state) || state.bulletLife <= 0f)) {
      if (state.bullet.isAdded())
        state.bullet.remove();
      state.bullet = null;
    }

    boolean canShoot = state.target != null && Angles.within(state.rotation, targetRot, state.shootCone);

    if (canShoot) {
      if (state.bullet != null) {
        maintainContinuousBullet(unit, state);
        state.bulletLife -= Time.delta;
      } else if (state.timer >= state.reload) {
        if (tryShootTurret(unit, state)) {
          state.bulletLife = state.bulletLifeMax;
          state.timer = 0f;
        }
      }
    } else {
      if (state.bullet != null) {
        maintainContinuousBullet(unit, state);
        state.bulletLife -= Time.delta;
      }
    }

    if (state.bullet == null && state.timer < state.reload) {
      state.timer += Time.delta * unit.reloadMultiplier * state.cooldownRate;
    }
  }

  void updateContinuousTurretState(Unit unit, TurretState state, float targetRot) {
    if (state.bullet != null && (!state.bullet.isAdded() || state.bullet.type != getTurretSampleBullet(state))) {
      state.bullet = null;
    }

    boolean canShoot = state.target != null && Angles.within(state.rotation, targetRot, state.shootCone);

    if (canShoot) {
      if (state.bullet == null && state.timer >= state.reload) {
        if (tryShootTurret(unit, state)) {
          state.timer = 0f;
        }
      }
    }

    if (state.bullet != null) {
      state.bullet.rotation(state.rotation);
      state.bullet.set(unit.x, unit.y);
      Tmp.v1.trns(state.rotation, state.range).add(unit.x, unit.y);
      state.bullet.aimX = Tmp.v1.x;
      state.bullet.aimY = Tmp.v1.y;
      state.bullet.time = state.bullet.lifetime * state.bullet.type.optimalLifeFract;
      state.bullet.keepAlive = true;
    }

    if (state.bullet == null && state.timer < state.reload) {
      state.timer += Time.delta * unit.reloadMultiplier * state.cooldownRate;
    }
  }

  void maintainContinuousBullet(Unit unit, TurretState state) {
    if (state.bullet == null)
      return;
    state.bullet.rotation(state.rotation);
    state.bullet.set(unit.x, unit.y);
    Tmp.v1.trns(state.rotation, state.range).add(unit.x, unit.y);
    state.bullet.aimX = Tmp.v1.x;
    state.bullet.aimY = Tmp.v1.y;
    state.bullet.time = state.bullet.lifetime * state.bullet.type.optimalLifeFract;
    state.bullet.keepAlive = true;
  }

  void syncTurretState(TurretState state, Block b) {
    if (!(b instanceof Turret t))
      return;

    state.block = b;
    state.lastBlock = b;
    state.range = t.range;
    state.reload = t.reload;
    state.rotateSpeed = t.rotateSpeed;
    state.inaccuracy = t.inaccuracy;
    state.shootCone = t.shootCone;
    state.shots = t.shoot.shots;
    state.targetAir = t.targetAir;
    state.targetGround = t.targetGround;
    state.continuous = false;
    state.isContinuous = false;
    state.isLaser = false;
    state.bulletLife = 0f;
    state.bulletLifeMax = 0f;
    state.firingMoveFract = 1f;
    state.cooldownRate = 1f;
    state.itemAmmo = null;
    state.powerAmmo = null;
    state.bullet = null;
    state.target = null;

    if (b instanceof ItemTurret it) {
      state.itemAmmo = it.ammoTypes;
    } else if (b instanceof PowerTurret pt) {
      state.powerAmmo = pt.shootType;
    }

    BulletType sample = getTurretSampleBullet(state);
    if (sample instanceof ContinuousBulletType) {
      state.continuous = true;
      state.isContinuous = b instanceof ContinuousTurret;
      state.isLaser = !(b instanceof ContinuousTurret);

      if (b instanceof LaserTurret lt) {
        state.bulletLifeMax = lt.shootDuration;
        state.firingMoveFract = lt.firingMoveFract;
      } else if (state.isContinuous) {
        state.bulletLifeMax = sample != null ? sample.lifetime : state.reload;
        state.firingMoveFract = 1f;
      } else {
        state.bulletLifeMax = state.reload;
        state.firingMoveFract = 1f;
      }

      float coolantAmount = 0.5f;
      if (t.consumers != null) {
        for (var cons : t.consumers) {
          if (cons instanceof ConsumeLiquid cl) {
            coolantAmount = cl.amount;
            break;
          }
        }
      }
      state.cooldownRate = t.coolantMultiplier * coolantAmount * 0.4f;
      if (state.cooldownRate <= 0f || state.cooldownRate > 1f)
        state.cooldownRate = 1f;
    }
  }

  BulletType getTurretSampleBullet(TurretState state) {
    if (state.powerAmmo != null)
      return state.powerAmmo;
    if (state.itemAmmo != null && state.itemAmmo.size > 0) {
      return state.itemAmmo.values().next();
    }
    return null;
  }

  boolean tryShootTurret(Unit unit, TurretState state) {
    BulletType type = null;
    Item consume = null;
    int ammoPerShot = 1;

    if (state.block instanceof ItemTurret it) {
      Item item = unit.stack.item;
      ammoPerShot = it.ammoPerShot;
      if (item != null && unit.stack.amount >= ammoPerShot && state.itemAmmo != null) {
        type = state.itemAmmo.get(item);
        if (type != null)
          consume = item;
      }
    } else if (state.block instanceof PowerTurret) {
      type = state.powerAmmo;
    }

    if (type == null)
      return false;

    if (state.continuous) {
      float rot = state.rotation + Mathf.range(state.inaccuracy);
      float lifetimeScl;

      if (state.isContinuous) {
        lifetimeScl = 1f;
      } else {
        float desiredLife = state.bulletLifeMax > 0 ? state.bulletLifeMax : state.reload;
        lifetimeScl = type.lifetime > 0 ? desiredLife / type.lifetime : 1f;
      }

      Bullet b = type.create(unit, unit.team, unit.x, unit.y, rot, type.damage, 1f, lifetimeScl, null);
      state.bullet = b;
    } else {
      for (int i = 0; i < state.shots; i++) {
        float rot = state.rotation + Mathf.range(state.inaccuracy);
        type.create(unit, unit.team, unit.x, unit.y, rot);
      }
    }

    if (consume != null) {
      unit.stack.amount -= ammoPerShot;
      if (unit.stack.amount <= 0) {
        unit.stack.item = null;
        unit.stack.amount = 0;
      }
    }

    Fx.shootSmall.at(unit.x, unit.y, state.rotation);
    return true;
  }

  // ==================== FORCE PROJECTOR ====================

  void updateForceProjector(Unit unit) {
    Seq<ForceProjector> fps = new Seq<>();
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof ForceProjector f) {
          fps.add(f);
        }
      }
    }

    if (fps.isEmpty()) {
      forceBlock = null;
      shieldHealth = 0f;
      shieldRadiusScale = 0f;
      shieldBroken = false;
      shieldBuildup = 0f;
      return;
    }

    // Aggregate: health and regen stack, others use max
    ForceProjector fp = fps.first();
    float maxRadius = fp.radius;
    float totalHealth = fp.shieldHealth;
    float totalRegen = fp.shieldHealth / 120f;
    float maxCooldownNormal = fp.cooldownNormal;
    float maxCooldownBroken = fp.cooldownBrokenBase;
    int maxSides = fp.sides;

    for (int i = 1; i < fps.size; i++) {
      ForceProjector f = fps.get(i);
      if (f.radius > maxRadius)
        maxRadius = f.radius;
      totalHealth += f.shieldHealth;
      totalRegen += f.shieldHealth / 120f;
      if (f.cooldownNormal > maxCooldownNormal)
        maxCooldownNormal = f.cooldownNormal;
      if (f.cooldownBrokenBase > maxCooldownBroken)
        maxCooldownBroken = f.cooldownBrokenBase;
      if (f.sides > maxSides)
        maxSides = f.sides;
    }

    forceBlock = fp;
    shieldRadius = maxRadius;
    forceMaxHealth = totalHealth;
    forceRegenRate = totalRegen;
    forceCooldownNormal = maxCooldownNormal;
    forceCooldownBroken = maxCooldownBroken;
    forceSides = maxSides;

    if (shieldBroken) {
      shieldBuildup -= forceCooldownBroken * Time.delta;
      if (shieldBuildup <= 0) {
        shieldBuildup = 0;
        shieldBroken = false;
      }
      shieldRadiusScale = Mathf.lerpDelta(shieldRadiusScale, 0, 0.06f);
      shieldHit -= Time.delta / 10f;
      if (shieldHit < 0f)
        shieldHit = 0f;
    } else {
      shieldHealth += forceRegenRate * Time.delta;
      if (shieldHealth > forceMaxHealth)
        shieldHealth = forceMaxHealth;

      shieldBuildup -= forceCooldownNormal * Time.delta;
      if (shieldBuildup < 0)
        shieldBuildup = 0;

      shieldRadiusScale = Mathf.lerpDelta(shieldRadiusScale, 1, 0.06f);
      shieldHit -= Time.delta / 10f;
      if (shieldHit < 0f)
        shieldHit = 0f;

      float realRad = shieldRadiusScale * shieldRadius;
      if (realRad > 0) {
        Groups.bullet.intersect(unit.x - realRad, unit.y - realRad, realRad * 2f, realRad * 2f, b -> {
          if (b.team != unit.team && b.type.absorbable && unit.dst(b.x, b.y) < realRad) {
            b.absorb();
            Fx.absorb.at(b);
            float dmg = b.damage;
            shieldHealth -= dmg;
            shieldBuildup += dmg;
            shieldHit = 1f;
            if (shieldHealth <= 0) {
              shieldHealth = 0;
              shieldBroken = true;
              Fx.shieldBreak.at(unit.x, unit.y, realRad, unit.team.color);
            }
          }
        });
      }
    }
  }

  @Override
  public void draw(Unit unit) {
    TextureRegion back = Core.atlas.find("crystal-function");
    TextureRegion over1 = Core.atlas.find("crystal-overrider");
    TextureRegion regen1 = Core.atlas.find("crystal-regen");
    float z = unit.elevation > 0.5f ? unit.type.flyingLayer : unit.type.groundLayer + unit.hitSize / 4000f;
    Draw.z(z + 0.01f);
    float rotation = unit.rotation - 90;
    float wx = unit.x + Angles.trnsx(rotation, 0, -18),
        wy = unit.y + Angles.trnsy(rotation, 0, -18);
    float wx1 = unit.x + Angles.trnsx(rotation, 0, -16),
        wy1 = unit.y + Angles.trnsy(rotation, 0, -16);
    Draw.color(Color.valueOf("#FFD37F"));
    Draw.rect(back, wx, wy, rotation);
    Draw.blend(Blending.additive);
    if (hasOver) {
      Draw.z(z + 0.02f);
      Draw.color(Color.valueOf("#FF9166"));
      Draw.rect(over1, wx1, wy1, rotation);
    }
    if (hasRegen) {
      Draw.z(z + 0.03f);
      Draw.color(Pal.heal);
      Draw.rect(regen1, wx1, wy1, rotation);
    }
    Draw.blend();
    Draw.reset();

    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload b && b.build.block instanceof OverdriveProjector over) {
          indexer.eachBlock(unit.team, unit.x, unit.y, over.range, other -> other.block.canOverdrive,
              other -> Drawf.selected(other, Tmp.c1.set(over.baseColor).a(Mathf.absin(4f, 1f))));
          Drawf.dashCircle(unit.x, unit.y, over.range, over.baseColor);
        }
      }
    }
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof MendProjector mend) {
          indexer.eachBlock(unit.team, unit.x, unit.y, mend.range, other -> other.block.canOverdrive,
              other -> Drawf.selected(other, Tmp.c1.set(mend.baseColor).a(Mathf.absin(4f, 1f))));
          Drawf.dashCircle(unit.x, unit.y, mend.range, mend.baseColor);
        }
      }
    }
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof RegenProjector regen) {
          Drawf.dashSquare(regen.baseColor, unit.x, unit.y, regen.range * tilesize);
          for (var target : targets) {
            Drawf.selected(target, Tmp.c1.set(regen.baseColor).a(Mathf.absin(4f, 1f)));
          }
        }
      }
    }

    // Draw range for each turret
    for (TurretState state : turretStates.values()) {
      if (state.block instanceof Turret) {
        Drawf.dashCircle(unit.x, unit.y, state.range, Pal.accent);
      }
    }

    if (forceBlock instanceof ForceProjector fp && !shieldBroken) {
      float realRad = shieldRadiusScale * shieldRadius;
      if (realRad > 0) {
        Draw.z(Layer.shields);
        Draw.color(unit.team.color, Color.white, Mathf.clamp(shieldHit));
        Fill.poly(unit.x, unit.y, forceSides, realRad, Time.time);
        Draw.color();
        Draw.z(z + 0.04f);
        Drawf.dashCircle(unit.x, unit.y, shieldRadius, unit.team.color);
      }
    }
  }
}
