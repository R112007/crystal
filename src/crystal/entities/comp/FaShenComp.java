package crystal.entities.comp;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import crystal.Crystal;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.gen.FaShen;
import crystal.gen.FaShenc;
import crystal.gen.Magicc;
import crystal.gen.MechMagicUnit;
import crystal.util.DLog;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.Replace;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Bulletc;
import mindustry.gen.Crawlc;
import mindustry.gen.Drawc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Mechc;
import mindustry.gen.Segmentc;
import mindustry.gen.Teamc;
import mindustry.gen.UnderwaterMovec;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.gen.Velc;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.UnitType.UnitEngine;

import static mindustry.Vars.*;

@EntityDef({ FaShenc.class })
@EntityComponent
public abstract class FaShenComp implements Teamc, Hitboxc, Velc, Drawc, Healthc {
  public static final float visualScale = 1.1f;
  public float mltiplier;
  public float sizeMultiplier, sizeScl;

  @Import
  public float hitSize;
  @Import
  float x, y, health, maxHealth, hitTime;
  @Import
  Team team;
  @Import
  public boolean dead, added;
  public int magicId;
  public float baseHitSize;
  protected float radiusScale;
  private transient Unit cachedMagicUnit;
  private transient long lastCheckTime = 0L;
  private float realRad;
  private final Cons<Bullet> shieldConsumer = b -> {
    if (b.team != this.team && b.type.absorbable
        && Intersector.isInRegularPolygon(4, this.x, this.y, realRad, 45, b.x(), b.y())) {
      if (this.health >= b.damage()) {
        b.absorb();
        Fx.absorb.at(b);
        b.type.hitEffect.at(b);
        b.type.hitSound.at(b.x, b.y, 1f + Mathf.range(0.1f), 0.12f);
        this.damage(b.damage());
      } else {
        b.damage -= this.health;
        b.type.hitEffect.at(b);
        b.type.hitSound.at(b.x, b.y, 1f + Mathf.range(0.1f), 0.12f);
      }
    }
  };

  public WeaponMount[] mounts = {};
  public float aimX, aimY;
  public boolean isShooting, isRotate, autoShoot;
  public float rotation;
  // private final Seq<Weapon> baseWeapons = new Seq<>();
  private float lastMultiplier = -1f;

  public Unit getMagicUnit() {
    if (magicId < 0)
      return null;
    if (cachedMagicUnit == null || cachedMagicUnit.dead() || !cachedMagicUnit.isAdded() || Crystal.timer % 60 == 0) {
      cachedMagicUnit = Groups.unit.getByID(magicId);
    }
    return cachedMagicUnit;
  }

  public void refreshHitSize() {
    if (baseHitSize > 0 && sizeMultiplier > 0) {
      this.hitSize = baseHitSize * sizeMultiplier;
    }
  }

  public float physicSize() {
    return hitSize * 0.7f;
  }

  public void checkRadius(Unit unit) {
    realRad = physicSize() * radiusScale;
  }

  @Override
  public void add() {
    if (added == true)
      return;
    FaTianXiangDi.faShens.add(self());
    refreshHitSize();
  }

  @Replace
  public void damage(float amount) {
    if (Float.isNaN(health))
      health = 0.0F;
    health -= amount;
    hitTime = 1.0F;
    if (health <= 0 && !dead) {
      kill();
    }
  }

  @Replace
  public boolean collides(Hitboxc other) {
    Unit unit = getMagicUnit();
    if (unit == null || unit.dead() || !unit.isAdded()) {
      return false;
    }
    if (other instanceof Teamc otherTeam) {
      if (other instanceof Bulletc)
        DLog.info("碰撞子弹true");
      return otherTeam.team() != this.team();
    }
    return false;
  }

  @Override
  public void draw() {
    Unit unit = getMagicUnit();
    if (unit == null || unit.dead() || !unit.isAdded()) {
      return;
    }
    // float scl = xscl;
    if (unit.inFogTo(Vars.player.team()))
      return;

    boolean isPayload = !unit.isAdded();
    Mechc mech = unit instanceof Mechc m ? m : null;
    Segmentc seg = unit instanceof Segmentc c ? c : null;
    float z = isPayload ? Draw.z()
        : unit.elevation > 0.5f || (unit.type().flying && unit.dead) ? (unit.type().flyingLayer)
            : seg != null
                ? unit.type().groundLayer + seg.segmentIndex() / 4000f * Mathf.sign(unit.type().segmentLayerOrder)
                    + (!unit.type().segmentLayerOrder ? 0.01f : 0f)
                : unit.type().groundLayer + Mathf.clamp(hitSize / 4000f, 0, 0.01f) + 0.05f;
    if (unit.type().drawBody)
      drawOutline(unit);
    // drawWeaponOutlines(unit);
    Draw.z(z - 0.02f);
    Draw.z(Math.min(z - 0.01f, Layer.bullet - 1f));
    if (unit.type().engines.size > 0)
      drawEngines(unit);
    Draw.z(z);
    if (unit.type().drawBody)
      drawBody(unit);
    if (unit.type().drawCell && !(unit instanceof Crawlc))
      drawCell(unit);
    drawWeapons(unit);
    // drawShield(unit);
    Draw.reset();
  }

  public void drawEngines(Unit unit) {
    if ((unit.type().useEngineElevation ? unit.elevation : 1f) <= 0.0001f)
      return;

    for (var engine : unit.type().engines) {
      drawEngine(engine, unit);
    }

    Draw.color();
  }

  public void drawEngine(UnitEngine engine, Unit unit) {
    float x = engine.x * sizeMultiplier, y = engine.y * sizeMultiplier, radius = engine.radius * sizeMultiplier,
        rotation = engine.rotation;
    UnitType type = unit.type;
    float scale = type.useEngineElevation ? unit.elevation : 1f;

    if (scale <= 0.0001f)
      return;

    float rot = unit.rotation - 90;
    Color color = type.engineColor == null ? unit.team.color : type.engineColor;

    Tmp.v1.set(x, y).rotate(rot);
    float ex = Tmp.v1.x, ey = Tmp.v1.y;
    float rad = (radius + Mathf.absin(Time.time, 2f, radius / 4f)) * scale;

    applyOutlineColor(unit);
    float alpha = drawAlpha(unit);
    Draw.color(color, alpha);
    Fill.circle(
        unit.x + ex,
        unit.y + ey,
        rad);
    Draw.color(type.engineColorInner, alpha);
    Fill.circle(
        unit.x + ex - Angles.trnsx(rot + rotation, rad / 4f),
        unit.y + ey - Angles.trnsy(rot + rotation, rad / 4f),
        rad / 2f);
  }

  public void drawShield(Unit unit) {
    Draw.color(unit.type.shieldColor(unit), Color.white, Mathf.clamp(1));

    if (Vars.renderer.animateShields) {
      Draw.z(Layer.shields + 0.001f * 1);
      Fill.poly(unit.x, unit.y, 4, realRad, 45);
    } else {
      Draw.z(Layer.shields);
      Lines.stroke(1.5f);
      Draw.alpha(0.09f);
      Fill.poly(unit.x, unit.y, 4, physicSize(), 45);
      Draw.alpha(1f);
      Lines.poly(unit.x, unit.y, 4, physicSize(), 46);
    }
  }

  public void rect(TextureRegion tex, float x, float y, float rotation) {
    float finalScale = sizeMultiplier * visualScale;
    float scaledWidth = tex.width * finalScale * tex.scl() * Draw.xscl;
    float scaledHeight = tex.height * finalScale * tex.scl() * Draw.yscl;
    Draw.rect(tex, x, y, scaledWidth, scaledHeight, rotation);
    // DLog.info("名称" + tex.toString() + "原width:" + tex.width + "原heiht" +
    // tex.height + "新width"
    // + tex.width * sizeMultiplier + "新height" + tex.height * sizeMultiplier /
    // tex.ratio() + "x坐标" + x + "y坐标" + y);
  }

  public void drawOutline(Unit unit) {
    Draw.reset();

    if (Core.atlas.isFound(unit.type().outlineRegion)) {
      applyColor(unit);
      applyOutlineColor(unit);
      Draw.alpha(drawAlpha(unit));
      rect(unit.type().outlineRegion, unit.x, unit.y, unit.rotation - 90);
      Draw.reset();
    }

  }

  public void applyOutlineColor(Unit unit) {
    if (unit.drownTime > 0 && unit.lastDrownFloor != null) {
      Draw.color(Color.white, Tmp.c1.set(unit.lastDrownFloor.mapColor).mul(0.8f), unit.drownTime * 0.9f);
    }
  }

  public float drawAlpha(Unit unit) {
    return (this.health / (unit.type().health * mltiplier)) * 0.7f;
  }

  @Replace
  public boolean canPass(int tileX, int tileY) {
    return true;
  }

  @Override
  public void update() {
    Unit unit = getMagicUnit();
    if (unit == null || unit.dead() || !unit.isAdded()) {
      kill();
      return;
    }
    if (!dead()) {
      if (maxHealth != getMagicUnit().type().health * mltiplier)
        maxHealth = getMagicUnit().type().health * mltiplier;
      radiusScale = Mathf.lerpDelta(radiusScale, 1f, 0.06f);
      if (this.hitSize <= 0) {
        refreshHitSize();
      }
      checkRadius(unit);
      if (!inFogTo(player.team()) && (!net.client() || isLocal())) {
        Groups.bullet.intersect(x - realRad, y - realRad, realRad * 2f, realRad * 2f, shieldConsumer);
        if (mounts.length > 0) {
          if (!autoShoot)
            for (WeaponMount mount : mounts) {
              Weapon weapon = mount.weapon;
              mount.aimX = this.aimX;
              mount.aimY = this.aimY;
              float faShenRot = this.rotation;
              float mountAxisX = this.x + Angles.trnsx(faShenRot - 90, weapon.x, weapon.y);
              float mountAxisY = this.y + Angles.trnsy(faShenRot - 90, weapon.x, weapon.y);
              float targetWorldRot = Angles.angle(mountAxisX, mountAxisY, mount.aimX, mount.aimY);
              float targetRelativeRot = targetWorldRot - faShenRot;
              if (weapon.rotationLimit < 360) {
                float angleDist = Angles.angleDist(targetRelativeRot, weapon.baseRotation);
                if (angleDist > weapon.rotationLimit / 2f) {
                  targetRelativeRot = Angles.moveToward(targetRelativeRot, weapon.baseRotation,
                      angleDist - weapon.rotationLimit / 2f);
                }
              }
              if (weapon.rotate && (mount.rotate || mount.shoot)) {
                mount.rotation = Angles.moveToward(mount.rotation, targetRelativeRot, weapon.rotateSpeed * Time.delta);
              } else {
                mount.rotation = weapon.baseRotation; // 非旋转武器固定基础角度
              }
              mount.targetRotation = targetRelativeRot;
              float reloadMultiplier = 1f;
              mount.reload = Math.max(mount.reload - Time.delta * reloadMultiplier, 0);
              mount.recoil = Mathf.approachDelta(mount.recoil, 0,
                  reloadMultiplier / (weapon.recoilTime < 0 ? weapon.reload : weapon.recoilTime));
              mount.warmup = Mathf.lerpDelta(mount.warmup, (mount.shoot && !unit.disarmed) ? 1f : 0f,
                  weapon.shootWarmupSpeed);
              mount.heat = Math.max(mount.heat - Time.delta * reloadMultiplier / weapon.cooldownTime, 0);
              boolean canShoot = mount.shoot && mount.reload <= 0.0001f && !unit.disarmed && weapon.bullet != null;
              boolean inShootCone = (getMagicUnit() instanceof MechMagicUnit mech
                  && Angles.within(mech.rotation, targetRelativeRot, 95))
                  || weapon.alwaysShooting
                  || Angles.within(mount.rotation, targetRelativeRot, 95);
              if (canShoot && inShootCone && mount.warmup >= weapon.minWarmup) {
                float finalWeaponRot = faShenRot - 90 + mount.rotation;
                float realRecoil = Mathf.pow(mount.recoil, weapon.recoilPow) * weapon.recoil;
                float shootX = mountAxisX + Angles.trnsx(finalWeaponRot, 0, -realRecoil);
                float shootY = mountAxisY + Angles.trnsy(finalWeaponRot, 0, -realRecoil);
                float shootAngle = Angles.angle(shootX, shootY, mount.aimX, mount.aimY)
                    + Mathf.range(weapon.inaccuracy);
                Bullet bullet = weapon.bullet.create(
                    getMagicUnit(),
                    this.team,
                    shootX,
                    shootY,
                    shootAngle + 180,
                    -1f,
                    1f,
                    null);
                mount.recoil = 1f;
                mount.heat = 1f;
                mount.totalShots++;
                if (weapon.shoot.shots > 1 && mount.totalShots % weapon.shoot.shots != 0) {
                  mount.reload = weapon.shoot.shotDelay;
                } else {
                  mount.reload = weapon.reload;
                }
                weapon.shootSound.at(shootX, shootY, Mathf.random(weapon.soundPitchMin, weapon.soundPitchMax),
                    weapon.shootSoundVolume);
                weapon.ejectEffect.at(mountAxisX, mountAxisY, finalWeaponRot);
              }
            }
          else {

            if (!dead() && (!net.client() || isLocal()) && !getMagicUnit().isPlayer()) {
              float attackRange = getmaxFaShenRange(self()); // 自定义攻击范围
              // 搜索最近的敌方目标（单位+建筑）
              Teamc target = Units.closestTarget(
                  team, x, y, attackRange,
                  t -> t.isValid() && t.checkTarget(true, true), // 可攻击空中+地面单位
                  t -> t.isValid() && t.team() != team // 可攻击敌方建筑
              );

              if (target != null) {
                aimX = target.getX();
                aimY = target.getY();
                controlWeapons(true, true);
                for (WeaponMount mount : mounts) {
                  Weapon weapon = mount.weapon;
                  mount.aimX = aimX;
                  mount.aimY = aimY;
                  getMagicUnit().lookAt(Angles.angle(this.x, this.y, aimX, aimY));
                  float faShenRot = this.rotation;
                  // 计算武器挂载点的世界坐标（基于法身自身旋转）
                  float mountAxisX = this.x + Angles.trnsx(faShenRot - 90, weapon.x, weapon.y);
                  float mountAxisY = this.y + Angles.trnsy(faShenRot - 90, weapon.x, weapon.y);

                  // 计算武器目标角度（世界坐标→相对法身的旋转）
                  float targetWorldRot = Angles.angle(mountAxisX, mountAxisY, mount.aimX, mount.aimY);
                  float targetRelativeRot = targetWorldRot - faShenRot;

                  // 处理武器旋转角度限制
                  if (weapon.rotationLimit < 360) {
                    float angleDist = Angles.angleDist(targetRelativeRot, weapon.baseRotation);
                    if (angleDist > weapon.rotationLimit / 2f) {
                      targetRelativeRot = Angles.moveToward(targetRelativeRot, weapon.baseRotation,
                          angleDist - weapon.rotationLimit / 2f);
                    }
                  }

                  // 平滑旋转武器（仅当武器允许旋转时生效）
                  if (weapon.rotate && (mount.rotate || mount.shoot)) {
                    mount.rotation = Angles.moveToward(mount.rotation, targetRelativeRot,
                        weapon.rotateSpeed * Time.delta);
                  } else {
                    mount.rotation = weapon.baseRotation; // 非旋转武器固定基础角度
                  }
                  mount.targetRotation = targetRelativeRot;
                  float reloadMultiplier = 1f;
                  mount.reload = Math.max(mount.reload - Time.delta * reloadMultiplier, 0);
                  mount.recoil = Mathf.approachDelta(mount.recoil, 0,
                      reloadMultiplier / (weapon.recoilTime < 0 ? weapon.reload : weapon.recoilTime));
                  mount.warmup = Mathf.lerpDelta(mount.warmup, (mount.shoot && !unit.disarmed) ? 1f : 0f,
                      weapon.shootWarmupSpeed);
                  mount.heat = Math.max(mount.heat - Time.delta * reloadMultiplier / weapon.cooldownTime, 0);

                  boolean canShoot = mount.shoot && mount.reload <= 0.0001f && !unit.disarmed && weapon.bullet != null;
                  boolean inShootCone = (getMagicUnit() instanceof MechMagicUnit mech
                      && Angles.within(mech.rotation, targetRelativeRot, 95))
                      || weapon.alwaysShooting
                      || Angles.within(mount.rotation, targetRelativeRot, 95);

                  if (canShoot && inShootCone && mount.warmup >= weapon.minWarmup) {
                    float finalWeaponRot = faShenRot - 90 + mount.rotation;
                    float realRecoil = Mathf.pow(mount.recoil, weapon.recoilPow) * weapon.recoil;
                    float shootX = mountAxisX + Angles.trnsx(finalWeaponRot, 0, -realRecoil);
                    float shootY = mountAxisY + Angles.trnsy(finalWeaponRot, 0, -realRecoil);
                    float shootAngle = Angles.angle(shootX, shootY, mount.aimX, mount.aimY)
                        + Mathf.range(weapon.inaccuracy);
                    Bullet bullet = weapon.bullet.create(
                        getMagicUnit(),
                        this.team,
                        shootX,
                        shootY,
                        shootAngle + 180,
                        -1f,
                        1f,
                        null);
                    // 还原原版武器射击反馈
                    mount.recoil = 1f;
                    mount.heat = 1f;
                    mount.totalShots++;
                    // 处理连发射击延迟
                    if (weapon.shoot.shots > 1 && mount.totalShots % weapon.shoot.shots != 0) {
                      mount.reload = weapon.shoot.shotDelay;
                    } else {
                      mount.reload = weapon.reload;
                    }
                    // 播放音效与特效
                    weapon.shootSound.at(shootX, shootY, Mathf.random(weapon.soundPitchMin, weapon.soundPitchMax),
                        weapon.shootSoundVolume);
                    weapon.ejectEffect.at(mountAxisX, mountAxisY, finalWeaponRot);
                  }
                }

              } else {
                // 无目标时停止射击
                controlWeapons(true, false);
              }
            }
          }
        }
      }
    } else {
      radiusScale = 0f;
    }

  }

  public float getWeaponRange(Weapon wea) {
    BulletType w = wea.bullet;
    if (w.rangeOverride > 0)
      return w.rangeOverride;
    if (w.spawnUnit != null)
      return w.spawnUnit.lifetime * w.spawnUnit.speed;
    if (w.despawnUnit != null)
      return w.despawnUnit.lifetime * w.despawnUnit.speed;
    return Math.max(
        Mathf.zero(w.drag) ? w.speed * w.lifetime : w.speed * (1f - Mathf.pow(1f - w.drag, w.lifetime)) / w.drag,
        w.maxRange);
  }

  public float getmaxFaShenRange(FaShen faShen) {
    float maxFaShenRange = 0;

    for (WeaponMount mount : faShen.mounts) {
      if (!mount.weapon.useAttackRange)
        continue;

      maxFaShenRange = Math.max(maxFaShenRange, getWeaponRange(mount.weapon) - 4f);
    }
    return maxFaShenRange;
  }

  @Replace
  public boolean isLocal() {
    Unit unit = Groups.unit.getByID(magicId);
    return unit != null && unit.isLocal();
  }

  @Override
  public void remove() {
    FaTianXiangDi.faShens.remove(self());
    cachedMagicUnit = null;
    for (WeaponMount mount : mounts) {
      if (mount.weapon.continuous && mount.bullet != null && mount.bullet.owner == this) {
        mount.bullet.time = mount.bullet.lifetime - 10.0F;
        mount.bullet = null;
      }
      if (mount.sound != null) {
        mount.sound.stop();
      }
    }
  }

  public void setField(float hitSize, float multiplier, float sizeMultiplier, int magicId) {
    this.baseHitSize = hitSize;
    this.sizeMultiplier = sizeMultiplier;
    refreshHitSize();
    this.mltiplier = multiplier;
    this.magicId = magicId;
    this.radiusScale = 1.0f;
  }

  public void afterRead() {
    refreshHitSize();
    cachedMagicUnit = null;
    lastCheckTime = 0l;
  }

  public void drawCell(Unit unit) {
    applyColor(unit);

    Draw.color(cellColor(unit));
    Draw.alpha(drawAlpha(unit));
    rect(unit.type().cellRegion, unit.x, unit.y, unit.rotation - 90);
    Draw.reset();
  }

  public void drawBody(Unit unit) {
    applyColor(unit);

    if (unit instanceof UnderwaterMovec) {
      Draw.alpha(drawAlpha(unit));
      Draw.mixcol(unit.floorOn().mapColor.write(Tmp.c1).mul(0.9f), 1f);
    }
    Draw.alpha(drawAlpha(unit));
    rect(unit.type().region, unit.x, unit.y, unit.rotation - 90);

    Draw.reset();
  }

  public void applyColor(Unit unit) {
    Draw.color();
    if (unit.type().healFlash) {
      Tmp.c1.set(Color.white).lerp(unit.type().healColor, Mathf.clamp(unit.healTime - unit.hitTime));
    }
    Draw.mixcol(Tmp.c1, Math.max(unit.hitTime, !unit.type().healFlash ? 0f : Mathf.clamp(unit.healTime)));

    if (unit.drownTime > 0 && unit.lastDrownFloor != null) {
      Draw.mixcol(Tmp.c1.set(unit.lastDrownFloor.mapColor).mul(0.83f), unit.drownTime * 0.9f);
    }
    // this is horribly scuffed.
    if (renderer != null && renderer.overlays != null) {
      renderer.overlays.checkApplySelection(unit);
    }
  }

  public Color cellColor(Unit unit) {
    float f = Mathf.clamp(unit.healthf());
    return Tmp.c1.set(Color.black).lerp(unit.team.color, f + Mathf.absin(Time.time, Math.max(f * 5f, 1f), 1f - f));
  }

  public void setupWeapons(Weapon[] weapons) {
    mounts = new WeaponMount[weapons.length];
    for (int i = 0; i < mounts.length; i++) {
      mounts[i] = weapons[i].mountType.get(weapons[i]);
    }
  }

  public void controlWeapons(boolean rotate, boolean shoot) {
    for (WeaponMount mount : mounts) {
      if (mount.weapon.controllable) {
        mount.rotate = rotate;
        mount.shoot = shoot;
      }
    }
    isRotate = rotate;
    isShooting = shoot;
  }

  public void controlWeapons(boolean rotateShoot) {
    controlWeapons(rotateShoot, rotateShoot);
  }

  public void drawWeaponOutlines(Unit unit) {
    for (WeaponMount mount : mounts) {
      var w = mount.weapon;
      if (!w.outlineRegion.found())
        return;

      float rotation = unit.rotation - 90;
      float realRecoil;
      if (state.isPaused()) {
        realRecoil = 0;
      } else {
        realRecoil = Mathf.pow(mount.recoil, w.recoilPow) * w.recoil;
      }
      float weaponRotation = rotation + (w.rotate ? mount.rotation : w.baseRotation);
      float scaledWX = w.x * sizeScl;
      float scaledWY = w.y * sizeScl;
      float wx = unit.x + Angles.trnsx(rotation, scaledWX, scaledWY) + Angles.trnsx(weaponRotation, 0, -realRecoil);
      float wy = unit.y + Angles.trnsy(rotation, scaledWX, scaledWY) + Angles.trnsy(weaponRotation, 0, -realRecoil);
      Draw.xscl = -Mathf.sign(w.flipSprite);
      Draw.alpha(drawAlpha(unit));
      rect(w.outlineRegion, wx, wy, weaponRotation);
      Draw.xscl = 1f;
    }
  }

  public void drawWOutline(Unit unit, WeaponMount mount) {
    Weapon w = mount.weapon;
    if (!w.outlineRegion.found())
      return;
    float rotation = unit.rotation - 90;
    float realRecoil;
    if (state.isPaused()) {
      realRecoil = 0;
    } else {
      realRecoil = Mathf.pow(mount.recoil, w.recoilPow) * w.recoil;
    }
    float weaponRotation = rotation + (w.rotate ? mount.rotation : w.baseRotation);
    float scaledWX = w.x * sizeScl;
    float scaledWY = w.y * sizeScl;
    float wx = unit.x + Angles.trnsx(rotation, scaledWX, scaledWY) + Angles.trnsx(weaponRotation, 0, -realRecoil);
    float wy = unit.y + Angles.trnsy(rotation, scaledWX, scaledWY) + Angles.trnsy(weaponRotation, 0, -realRecoil);

    Draw.xscl = -Mathf.sign(w.flipSprite);
    Draw.alpha(drawAlpha(unit));
    rect(w.outlineRegion, wx, wy, weaponRotation);
    Draw.xscl = 1f;
  }

  public void drawWeapons(Unit unit) {
    // DLog.info(mounts.length + "武器组长度");
    for (WeaponMount mount : mounts) {
      Weapon w = mount.weapon;
      float z = Draw.z();
      Draw.z(z + w.layerOffset);
      float rotation = unit.rotation - 90;
      float realRecoil;
      if (state.isPaused()) {
        realRecoil = 0;
      } else {
        realRecoil = Mathf.pow(mount.recoil, w.recoilPow) * w.recoil;
      }
      float weaponRotation = rotation + (w.rotate ? mount.rotation : w.baseRotation);
      float scaledWX = w.x * sizeScl;
      float scaledWY = w.y * sizeScl;
      float wx = unit.x + Angles.trnsx(rotation, scaledWX, scaledWY) + Angles.trnsx(weaponRotation, 0, -realRecoil);
      float wy = unit.y + Angles.trnsy(rotation, scaledWX, scaledWY) + Angles.trnsy(weaponRotation, 0, -realRecoil);
      if (w.top) {
        drawWOutline(unit, mount);
      }

      float prev = Draw.xscl;

      Draw.xscl *= -Mathf.sign(w.flipSprite);

      // fix color
      unit.type.applyColor(unit);

      if (w.region.found()) {
        Draw.alpha(drawAlpha(unit));
        rect(w.region, wx, wy, weaponRotation);
      }
      if (w.cellRegion.found()) {
        Draw.color(unit.type.cellColor(unit));
        Draw.alpha(drawAlpha(unit));
        rect(w.cellRegion, wx, wy, weaponRotation);
        Draw.color();
      }

      /*
       * if (w.heatRegion.found() && mount.heat > 0) {
       * Draw.color(w.heatColor, mount.heat);
       * Draw.blend(Blending.additive);
       * Draw.alpha(drawAlpha(unit));
       * rect(w.heatRegion, wx, wy, weaponRotation);
       * Draw.blend();
       * Draw.color();
       * }
       */
      Draw.xscl = 1f;
      Draw.z(z);
    }
    Draw.reset();
  }
}
