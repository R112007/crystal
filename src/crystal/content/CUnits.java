package crystal.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.util.Tmp;
import crystal.entities.abilities.ContinueRepairField;
import crystal.entities.shentong.FaTianXiangDi;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.gen.Crawlc;
import mindustry.gen.Mechc;
import mindustry.gen.Sounds;
import mindustry.gen.UnderwaterMovec;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.gen.WaterMovec;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.ammo.ItemAmmoType;
import mindustry.type.ammo.PowerAmmoType;
import mindustry.world.meta.BlockFlag;

public class CUnits {
  public static @EntityDef({ Unitc.class, ShieldBuilderc.class }) BuildShieldUnitType taichu;
  public static @EntityDef({ Unitc.class, Magicc.class, Mechc.class }) MagicUnitType chujia1;
  public static @EntityDef({ Unitc.class, Magicc.class, Crawlc.class }) MagicUnitType papa1;
  public static @EntityDef({ Unitc.class, Magicc.class }) MagicUnitType liekong1;
  public static @EntityDef({ Unitc.class, Magicc.class, WaterMovec.class }) MagicUnitType xiji;
  public static @EntityDef({ Unitc.class, ShieldBlockc.class }) BuildShieldUnitType block;

  public static void load() {
    taichu = new BuildShieldUnitType("taichu") {
      {
        this.health = 280.0f;
        this.constructor = ShieldBuilderUnit::create;
        this.controller = UnitTypes.alpha.controller;
        this.flying = true;
        this.ammoType = new PowerAmmoType(800);
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
        this.engineOffset = 7.5f;
        this.engineLayer = 1;
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
        this.ammoType = new ItemAmmoType(CItems.lv);
        this.health = 480.0f;
        this.itemCapacity = 0;
        this.armor = 1.0f;
        this.rotateSpeed = 3.0f;
        this.hitSize = 8.0f;
        this.shenTongs.add(new FaTianXiangDi(3f, 3f, 3, 480, 50));
        this.xiuWeiAmount = 0.5f;
        this.weapons.add(new Weapon("crystal-chujia1-weapon") {
          {
            this.reload = 10.0f;
            this.x = -5.0f;
            this.y = 0f;
            this.top = false;
            this.ejectEffect = Fx.casing1;
            this.bullet = new BasicBulletType(3.0f, 26.0f) {
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
    liekong1 = new MagicUnitType("liekong1") {
      {
        this.hitSize = 7f;
        this.immunities.add(StatusEffects.burning);
        this.rotateSpeed = 5f;
        this.itemCapacity = 10;
        this.constructor = UnitTypes.flare.constructor;
        this.controller = UnitTypes.flare.controller;
        this.canDrown = false;
        this.circleTarget = true;
        this.forceMultiTarget = true;
        this.buildSpeed = 0f;
        this.ammoType = new ItemAmmoType(CItems.cuguijing);
        this.flying = true;
        this.speed = 1.8f;
        this.health = 350;
        this.engineSize = 2.5f;
        this.engineOffset = 4f;
        this.armor = 3;
        this.targetFlags = new BlockFlag[] { BlockFlag.generator, null };
        this.weapons.add(new Weapon("crystal-liekong1-weapon") {
          {
            this.mirror = true;
            this.alternate = true;
            this.x = -2.6f;
            this.y = 0;
            this.shootSound = Sounds.shootMissileShort;
            this.reload = 20f;
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
        this.weapons.add(new Weapon("crystal-liekong1-weapon") {
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
  }
}
