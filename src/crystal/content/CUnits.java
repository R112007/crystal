package crystal.content;

import arc.graphics.Color;
import crystal.entities.abilities.AddWeaponAbility;
import crystal.entities.abilities.ContinueRepairField;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.gen.MagicUnit;
import crystal.gen.Magicc;
import crystal.gen.ShieldBlockc;
import crystal.gen.ShieldBuilderUnit;
import crystal.gen.ShieldBuilderc;
import crystal.graphics.CPal;
import crystal.type.BuildShieldUnitType;
import crystal.type.MagicUnitType;
import ent.anno.Annotations.EntityDef;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Crawlc;
import mindustry.gen.Mechc;
import mindustry.gen.Sounds;
import mindustry.gen.Unitc;
import mindustry.gen.WaterMovec;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import mindustry.world.meta.BlockFlag;

public class CUnits {
  public static @EntityDef({ Unitc.class, ShieldBuilderc.class }) BuildShieldUnitType taichu;
  public static @EntityDef({ Unitc.class, Magicc.class, Mechc.class }) MagicUnitType chujia1, chujia2;
  public static @EntityDef({ Unitc.class, Magicc.class, Crawlc.class }) MagicUnitType papa1;
  public static @EntityDef({ Unitc.class, Magicc.class }) MagicUnitType liekong1, liekong2, liekong3;
  public static @EntityDef({ Unitc.class, Magicc.class, WaterMovec.class }) MagicUnitType xiji;
  public static @EntityDef({ Unitc.class, ShieldBlockc.class }) BuildShieldUnitType block;

  public static void load() {
    taichu = new BuildShieldUnitType("taichu") {
      {
        this.health = 280.0f;
        this.constructor = ShieldBuilderUnit::create;
        this.controller = UnitTypes.alpha.controller;
        this.flying = true;
        this.speed = 3.0f;
        this.drag = 0.1f;
        this.accel = 0.3f;
        this.lowAltitude = true;
        this.itemCapacity = 50;
        this.rotateSpeed = 5f;
        this.buildSpeed = 1.5f;
        this.buildBeamOffset = 3.0f;
        this.mineWalls = true;
        this.mineSpeed = 4.0f;
        this.mineTier = 1;
        this.mineRange = 100f;
        this.engineOffset = 6.5f;
        this.engineLayer = 1;
        this.engineSize = 4.5f;
        this.engineColor = Color.valueOf("#79C5C5FF");
        this.trailLength = 6;
        this.trailColor = Color.valueOf("#79C5C5FF");
        this.hitSize = 12;
        this.coreUnitDock = true;
        this.abilities.add(new ContinueRepairField(1f, 64, true, true));
        this.weapons.add(new Weapon() {
          {
            this.reload = 60.0f;
            this.x = 0f;
            this.y = 0.75f;
            this.top = false;
            this.inaccuracy = 0f;
            this.bullet = CBullets.taichu1;
          }
        });
        this.weapons.add(new Weapon() {
          {
            this.reload = 120.0f;
            this.x = 0f;
            this.y = 0.75f;
            this.top = false;
            this.inaccuracy = 0f;
            this.bullet = CBullets.taichu2;
          }
        });
      }
    };
    chujia1 = new MagicUnitType("chujia1") {
      {
        this.controller = UnitTypes.dagger.controller;
        this.speed = 0.3f;
        this.health = 480.0f;
        this.itemCapacity = 0;
        this.armor = 1.0f;
        this.rotateSpeed = 3.0f;
        this.hitSize = 8.0f;
        this.magicPowerRegen = 10;
        this.shenTongs.add(new FaTianXiangDi(3f, 2f, 3, 480, 50));
        this.xiuWeiAmount = 0.1f;
        this.weapons.add(new Weapon("crystal-chujia1-weapon") {
          {
            this.reload = 10.0f;
            this.x = -5.0f;
            this.y = 0f;
            this.top = false;
            this.ejectEffect = Fx.casing1;
            this.bullet = new BasicBulletType(3.0f, 30.0f) {
              {
                this.width = 7.0f;
                this.height = 13.0f;
                this.lifetime = 50.0f;
                this.shootEffect = Fx.shootSmall;
                this.smokeEffect = Fx.shootSmallSmoke;
                this.ammoMultiplier = 1.5f;
              }
            };
          }
        });
      }
    };
    chujia2 = new MagicUnitType("chujia2") {
      {
        this.controller = UnitTypes.dagger.controller;
        this.speed = 0.30f;
        this.health = 1260.0f;
        this.itemCapacity = 0;
        this.armor = 4.0f;
        this.rotateSpeed = 2.0f;
        this.hitSize = 8.0f;
        this.magicPowerRegenTime = 90;
        this.magicPowerRegen = 20;
        this.magicPower = 400;
        this.shenTongs.add(new FaTianXiangDi(3.5f, 2f, 3, 480, 100));
        this.xiuWeiAmount = 0.2f;
        this.weapons.add(new Weapon("crystal-chujia2-weapon") {
          {
            this.reload = 7.0f;
            this.top = false;
            this.x = -6.75f;
            this.y = 0.0f;
            this.ejectEffect = Fx.casing1;
            this.bullet = new MissileBulletType(7.0f, 32.0f) {
              {
                this.width = 4.0f;
                this.height = 12.0f;
                this.lifetime = 30.0f;
                this.splashDamage = 35.0f;
                this.splashDamageRadius = 25.0f;
                this.status = StatusEffects.blasted;
                this.hitEffect = Fx.flakExplosion;
                this.homingPower = 2.0f;
                this.trailChance = 0.4f;
              }
            };
          }
        });
      }
    };
    liekong1 = new MagicUnitType("liekong1") {
      {
        this.hitSize = 7f;
        this.immunities.add(StatusEffects.burning);
        this.rotateSpeed = 5f;
        this.itemCapacity = 10;
        this.constructor = MagicUnit::create;
        this.controller = UnitTypes.flare.controller;
        this.canDrown = false;
        this.circleTarget = true;
        this.forceMultiTarget = true;
        this.buildSpeed = 0f;
        this.flying = true;
        this.speed = 1.8f;
        this.health = 350;
        this.engineSize = 2.5f;
        this.engineOffset = 4f;
        this.armor = 3;
        this.targetFlags = new BlockFlag[] { BlockFlag.generator, null };
        this.magicPowerRegenTime = 60;
        this.magicPowerRegen = 10;
        this.magicPower = 200;
        this.xiuWeiAmount = 0.1f;
        this.weapons.add(new Weapon() {
          {
            this.mirror = true;
            this.alternate = true;
            this.x = -2.6f;
            this.y = 0;
            this.shootSound = Sounds.shootMissileShort;
            this.reload = 15f;
            this.recoil = 0f;
            this.top = false;
            this.bullet = new MissileBulletType(5f, 12f) {
              {
                this.splashDamage = 13;
                this.splashDamageRadius = 12f;
                this.status = StatusEffects.blasted;
                this.width = 4f;
                this.height = 12f;
                this.hitEffect = Fx.flakExplosion;
                this.lifetime = 30;
                this.homingPower = 6f;
                this.trailChance = 0.4f;
                this.trailColor = CPal.light_blue1;
                this.frontColor = CPal.light_blue1;
                this.hitEffect = this.despawnEffect = CFx.airmisslesmall;
              }
            };
          }
        });
        this.weapons.add(new Weapon() {
          {
            this.y = 0f;
            this.x = -2.6f;
            this.shootY = 3f;
            this.mirror = true;
            this.alternate = true;
            this.recoil = 0;
            this.reload = 15f;
            this.shake = 0f;
            this.shootSound = Sounds.shootMissileSmall;
            this.rotate = false;
            this.top = false;
            this.bullet = new BasicBulletType(6f, 15f) {
              {
                this.width = 8;
                this.height = 12;
                this.lifetime = 30;
                this.shootEffect = Fx.shootSmallSmoke;
                this.frontColor = CPal.light_blue1;
                this.hitEffect = CFx.airmisslesmall;
                this.despawnEffect = CFx.airmisslesmall;
              }
            };
          }
        });
      }
    };
    liekong2 = new MagicUnitType("liekong2") {
      {
        this.magicPowerRegenTime = 60;
        this.magicPowerRegen = 25;
        this.magicPower = 400;
        this.xiuWeiAmount = 0.3f;
        this.hitSize = 11f;
        this.rotateSpeed = 5f;
        this.itemCapacity = 30;
        this.constructor = MagicUnit::create;
        this.controller = UnitTypes.horizon.controller;
        this.canDrown = false;
        this.circleTarget = false;
        this.abilities.add(new AddWeaponAbility(0.5f, new Weapon() {
          {
            x = 0;
            y = 0;
            this.shootSound = Sounds.shootMissileShort;
            this.reload = 5f;
            this.recoil = 0f;
            this.top = false;
            this.bullet = new MissileBulletType(5f, 5f) {
              {
                this.width = 2f;
                this.height = 6f;
                this.hitEffect = Fx.flakExplosion;
                this.lifetime = 30;
                this.homingPower = 6f;
                this.trailChance = 0.4f;
                this.trailColor = CPal.light_blue1;
                this.frontColor = CPal.light_blue1;
                this.hitEffect = this.despawnEffect = CFx.airmisslesmall;
              }
            };
          }
        }));
        this.forceMultiTarget = true;
        this.buildSpeed = 0f;
        this.flying = true;
        this.speed = 1.3f;
        this.health = 1150;
        this.engineSize = 2.5f;
        this.engineOffset = 7.2f;
        this.armor = 5;
        this.targetFlags = new BlockFlag[] { BlockFlag.factory, null };
        this.weapons.add(new Weapon("crystal-liekong2-weapon") {
          {
            // this.layerOffset = -0.01f;
            this.top = false;
            this.mirror = true;
            this.alternate = true;
            this.x = -3f;
            this.y = 2.75f;
            this.reload = 10f;
            this.recoil = 2f;
            this.bullet = new MissileBulletType(4f, 24f) {
              {
                this.splashDamage = 58;
                this.splashDamageRadius = 12f;
                this.status = StatusEffects.blasted;
                this.width = 7f;
                this.height = 14f;
                // this.hitEffect = Fx.flakExplosion;
                this.lifetime = 40;
                this.homingPower = 6f;
                this.trailChance = 0.4f;
                this.trailColor = CPal.light_blue1;
                this.frontColor = CPal.light_blue1;
                this.hitEffect = this.despawnEffect = CFx.airmisslemiddle;
              }
            };
          }
        });
      }
    };
    liekong3 = new MagicUnitType("liekong3") {
      {
        this.magicPowerRegenTime = 60;
        this.magicPowerRegen = 60;
        this.magicPower = 900;
        this.xiuWeiAmount = 0.6f;
        this.hitSize = 30f;
        this.rotateSpeed = 3f;
        this.itemCapacity = 30;
        this.abilities.add(new ForceFieldAbility(42f, 0.3f, 600f, 160f));
        this.abilities.add(new AddWeaponAbility(0.5f, new Weapon() {
          {
            x = 0;
            y = 0;
            rotate = false;
            top = false;
            inaccuracy = 360;
            reload = 240;
            recoil = 0;
            mirror = false;
            shootCone = 360;
            velocityRnd = 0.9f;
            shoot = new ShootPattern() {
              {
                shots = 20;
                shotDelay = 1;
              }
            };
            bullet = new ArtilleryBulletType(2, 24) {
              {
                reflectable = false;
                absorbable = false;
                width = 6;
                height = 6;
                splashDamage = 20;
                splashDamageRadius = 18;
                hitEffect = despawnEffect = Fx.explosion;
                lifetime = 80;
              }
            };
          }
        }));
        this.constructor = MagicUnit::create;
        this.controller = UnitTypes.zenith.controller;
        this.canDrown = false;
        this.circleTarget = false;
        this.forceMultiTarget = true;
        this.buildSpeed = 0f;
        this.flying = true;
        this.speed = 1f;
        this.health = 3500;
        this.engineSize = 3.5f;
        this.engineOffset = 12f;
        this.lowAltitude = true;
        this.armor = 10;
        this.targetFlags = new BlockFlag[] { BlockFlag.storage, BlockFlag.battery, null };
        this.weapons.add(new Weapon("crystal-liekong3-weapon2") {
          {
            this.mirror = true;
            this.rotate = true;
            this.rotateSpeed = 2.1f;
            this.alternate = true;
            this.x = 0f;
            this.y = -4.75f;
            this.reload = 15f;
            this.recoil = 0.5f;
            this.top = true;
            this.shoot = new ShootAlternate(2f) {
              {
                this.barrels = 3;
                this.shots = 2;
                this.shotDelay = 2;
              }
            };
            this.bullet = new MissileBulletType(4f, 52f) {
              {
                this.splashDamage = 53;
                this.splashDamageRadius = 24f;
                this.status = StatusEffects.blasted;
                this.width = 4f;
                this.height = 10f;
                this.lifetime = 40;
                this.homingPower = 6f;
                this.trailChance = 0.1f;
                this.trailColor = CPal.light_blue1;
                this.frontColor = CPal.light_blue1;
                this.hitEffect = this.despawnEffect = CFx.airmisslemiddle;
              }
            };
          }
        });
        this.weapons.add(new Weapon("crystal-liekong3-weapon1") {
          {
            this.reload = 20.0f;
            this.x = -7.5f;
            this.y = -4.25f;
            this.top = true;
            this.mirror = true;
            this.rotate = true;
            this.rotateSpeed = 2.1f;
            this.ejectEffect = Fx.casing1;
            this.bullet = new BasicBulletType(4.5f, 85.0f) {
              {
                this.width = 5.0f;
                this.height = 12.0f;
                this.lifetime = 30.0f;
                this.shootEffect = Fx.shootSmall;
                this.ammoMultiplier = 1.5f;
                this.pierce = true;
                this.pierceCap = 2;
                this.pierceBuilding = true;
                this.trailLength = 5;
                this.trailWidth = 0.6f;
                this.trailColor = CPal.blue1;
                this.hitEffect = this.despawnEffect = CFx.airpiercedown;
              }
            };
          }
        });
      }
    };
  }
}
