package crystal.content.blocks;

import arc.graphics.Color;
import crystal.content.CBullets;
import crystal.content.CItems;
import crystal.graphics.CPal;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.part.HaloPart;
import mindustry.entities.part.RegionPart;
import mindustry.entities.part.ShapePart;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootBarrel;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawTurret;

import static mindustry.type.ItemStack.*;

public class CTurrets {
  public static Block qianfeng;
  public static Block duoguanpao;
  public static Block zhentian;
  public static Block liuxing;
  public static Block tujin1;
  public static Block tuxi;
  public static Block powerair1;
  public static Block chuantou;

  public static void load() {
    qianfeng = new ItemTurret("qianfeng") {
      {
        drawer = new DrawTurret() {
          {
            parts.add(
                new HaloPart() {
                  {
                    shapeRotation = 0;
                    progress = PartProgress.life;
                    sides = 3;
                    shapes = 4;
                    y = -6;
                    color = CPal.sharedyellow;
                    layer = 110;
                    tri = true;
                    radius = 1f;
                    stroke = 2;
                    strokeTo = 0;
                    radiusTo = 1f;
                    triLength = 12;
                    triLengthTo = 12;
                    haloRadius = 0;
                    haloRadiusTo = 0;
                    haloRotateSpeed = -1f;
                  }
                });
          }
        };
        requirements(Category.turret, with(CItems.yellowcopper, 35));
        ammo(CItems.yellowcopper, CBullets.qianfengbullet);
        recoils = 2;
        recoil = 1.3f;
        shootY = 3f;
        reload = 20f;
        range = 160;
        size = 2;
        shootCone = 15f;
        ammoUseEffect = Fx.casing1;
        health = 500;
        inaccuracy = 2f;
        rotateSpeed = 10f;
        coolant = consumeCoolant(0.2f);
        researchCostMultiplier = 0.05f;
      }
    };
    duoguanpao = new ItemTurret("duoguanpao") {
      {
        this.health = 240;
        this.size = 1;
        this.reload = 23.0f;
        this.range = 190.0f;
        this.inaccuracy = 0.0f;
        this.recoil = 1.0f;
        this.rotateSpeed = 5.0f;
        this.maxAmmo = 40;
        this.coolant = consumeCoolant(0.1f);
        this.requirements(Category.turret, ItemStack.with(new Object[] { CItems.lv, 20, CItems.li, 20 }));
        this.shoot = new ShootBarrel() {
          {
            this.shots = 2;
          }
        };
        this.ammo(
            CItems.lv, new BasicBulletType(4f, 17f) {
              {
                this.width = 5.0f;
                this.height = 8.0f;
                this.lifetime = 190f / 4f;
                this.reloadMultiplier = 1.0f;
                this.ammoMultiplier = 1.3f;
              }
            },
            CItems.li, new BasicBulletType(4f, 17f) {
              {
                this.width = 5.0f;
                this.height = 8.0f;
                this.lifetime = 190f / 4f;
                this.reloadMultiplier = 1.0f;
                this.ammoMultiplier = 1.3f;
              }
            },
            CItems.cuguijing, new BasicBulletType(4f, 25f) {
              {
                this.width = 5.0f;
                this.height = 8.0f;
                this.lifetime = 190f / 4f;
                this.reloadMultiplier = 1.0f;
                this.ammoMultiplier = 1.3f;
                this.fragBullets = 2;
                this.fragBullet = new BasicBulletType(5.5f, 15f) {
                  {
                    this.lifetime = 15f;
                    this.height = 8f;
                    this.width = 8f;
                    this.hitEffect = Fx.none;
                  }
                };
              }
            });
      }
    };
    BulletType zhentianbullet = new BasicBulletType(6, 6f) {
      {
        this.reloadMultiplier = 1.5f;
        this.knockback = 0.2f;
        this.lifetime = 32f;
        this.width = 6f;
        this.height = 10f;
        this.splashDamage = 25f;
        this.splashDamageRadius = 15;
      }
    };

    zhentian = new ItemTurret("zhentian") {
      {
        this.size = 2;
        this.health = 550;
        this.ammoPerShot = 1;
        this.range = 210f;
        this.reload = 15f;
        this.inaccuracy = 5f;
        this.ammoUseEffect = Fx.casing1;
        this.rotateSpeed = 2f;
        this.maxAmmo = 35;
        this.targetAir = true;
        this.targetGround = false;
        this.coolantMultiplier = 2;
        this.heatColor = Color.valueOf("#FF0000");
        this.coolant = consumeCoolant(0.2f);
        this.requirements(Category.turret, ItemStack.with(new Object[] { CItems.lv, 45, CItems.li, 45 }));
        this.ammo(CItems.lv, zhentianbullet, CItems.li, zhentianbullet, CItems.cuguijing,
            new BasicBulletType(6, 6f) {
              {
                this.reloadMultiplier = 1.5f;
                this.knockback = 0.2f;
                this.lifetime = 32f;
                this.width = 6f;
                this.height = 10f;
                this.splashDamage = 30f;
                this.splashDamageRadius = 18;
                this.fragBullets = 4;
                this.fragBullet = new BasicBulletType(2f, 16f) {
                  {
                    this.lifetime = 30;
                  }
                };
              }
            },
            CItems.chunguijing,
            new BasicBulletType(6, 6f) {
              {
                this.reloadMultiplier = 1.5f;
                this.knockback = 0.2f;
                this.lifetime = 32f;
                this.width = 6f;
                this.height = 10f;
                this.splashDamage = 40f;
                this.splashDamageRadius = 25f;
                this.fragBullets = 3;
                this.fragBullet = new BasicBulletType(2f, 12f) {
                  {
                    this.lifetime = 30;
                    this.fragBullets = 3;
                    this.fragBullet = new BasicBulletType(2f, 10f) {
                      {
                        this.lifetime = 30;
                        this.splashDamage = 15f;
                        this.splashDamageRadius = 15f;
                      }
                    };
                  }
                };
              }
            });
        recoils = 2;
        drawer = new DrawTurret() {
          {
            parts.add(new RegionPart("-side") {
              {
                progress = PartProgress.warmup;
                moveX = 0.6f;
                moveRot = -15f;
                mirror = true;
                layerOffset = 0.001f;
                under = true;
                moves.add(new PartMove(PartProgress.recoil, 0.5f, -0.5f, -4f));
              }
            });
          }
        };
      }
    };
    liuxing = new ItemTurret("liuxing") {
      {
        this.size = 1;
        this.health = 530;
        this.ammoPerShot = 1;
        this.coolant = consumeCoolant(0.1f);
        this.requirements(Category.turret, ItemStack.with(new Object[] { CItems.lv, 60, CItems.cuguijing, 35 }));
        this.range = 200;
        this.reload = 60;
        this.inaccuracy = 0;
        this.ammoPerShot = 1;
        this.ammoUseEffect = Fx.casing2;
        this.rotateSpeed = 1;
        this.maxAmmo = 30;
        this.targetAir = false;
        this.targetGround = true;
        this.coolantMultiplier = 2;
        this.heatColor = Color.valueOf("#FF0000");
        this.shootSound = Sounds.shootArtillerySmall;
        this.ammo(
            CItems.lv, new ArtilleryBulletType(3f, 10f) {
              {
                this.reloadMultiplier = 1;
                this.knockback = 1;
                this.lifetime = 210f / 3f;
                this.width = 8;
                this.height = 13;
                this.ammoMultiplier = 2;
                this.splashDamageRadius = 20;
                this.splashDamage = 25;
              }
            },
            CItems.tandanzhi, new ArtilleryBulletType(3f, 14f) {
              {
                this.reloadMultiplier = 1;
                this.status = StatusEffects.burning;
                this.makeFire = true;
                this.incendChance = 0.5f;
                this.incendSpread = 1;
                this.incendAmount = 10;
                this.knockback = 1;
                this.lifetime = 210f / 3f;
                this.width = 8;
                this.height = 13;
                this.ammoMultiplier = 2;
                this.splashDamageRadius = 12;
                this.splashDamage = 28;
              }
            },
            CItems.cuguijing, new ArtilleryBulletType(3f, 20f) {
              {
                this.reloadMultiplier = 1.2f;
                this.knockback = 1;
                this.lifetime = 220f / 3f;
                this.width = 8;
                this.homingPower = 0.03f;
                this.homingRange = 40;
                this.height = 13;
                this.ammoMultiplier = 3;
                this.splashDamageRadius = 12;
                this.splashDamage = 33;
              }
            });
      }
    };
    tujin1 = new ItemTurret("tujin1") {
      {
        this.health = 860;
        this.ammoPerShot = 2;
        this.size = 2;
        this.reload = 20;
        this.range = 120;
        this.inaccuracy = 0;
        this.recoil = 3;
        this.requirements(Category.turret,
            ItemStack.with(new Object[] { CItems.lv, 60, CItems.li, 80, CItems.cuguijing, 45 }));
        this.rotateSpeed = 3;
        this.maxAmmo = 20;
        this.alwaysUnlocked = false;
        this.shoot = new ShootAlternate(5f) {
          {
            this.barrels = 2;
            this.shots = 2;
            this.shotDelay = 2;
          }
        };
        this.consumePower(1f);
        this.coolant = consumeCoolant(0.2f);
        this.ammo(
            CItems.lv, new BasicBulletType(6f, 22f) {
              {
                this.reloadMultiplier = 1.2f;
                this.ammoMultiplier = 4;
                this.width = 8;
                this.height = 14;
                this.inaccuracy = 3;
                this.smokeEffect = Fx.pulverizeRed;
                this.lifetime = 125 / 6f;
              }
            },
            CItems.li, new BasicBulletType(6f, 22f) {
              {
                this.reloadMultiplier = 1.2f;
                this.ammoMultiplier = 4;
                this.width = 8;
                this.height = 14;
                this.inaccuracy = 3;
                this.smokeEffect = Fx.pulverizeRed;
                this.lifetime = 125 / 6f;
              }
            },
            CItems.xi, new BasicBulletType(6f, 39f) {
              {
                this.reloadMultiplier = 1.2f;
                this.ammoMultiplier = 4;
                this.width = 8;
                this.height = 14;
                this.inaccuracy = 3;
                this.smokeEffect = Fx.pulverizeRed;
                this.lifetime = 125 / 6;
                this.reloadMultiplier = 1;
                this.ammoMultiplier = 4;
                this.width = 14;
                this.pierce = true;
                this.pierceCap = 3;
                this.height = 8;
                this.backColor = Color.valueOf("l#00ffff");
                this.frontColor = Color.valueOf("#00ffff");
                this.trailColor = Color.valueOf("#00ffff");
                this.inaccuracy = 2;
                this.smokeEffect = Fx.pulverizeRed;
                this.lifetime = 125 / 6f;
              }
            },
            CItems.tandanzhi, new MissileBulletType(6f, 27f) {
              {
                this.width = 8;
                this.height = 18;
                this.status = StatusEffects.burning;
                this.lifetime = 125 / 6f;
                this.homingPower = 0.1f;
                this.homingRange = 40;
                this.ammoMultiplier = 5;
                this.shrinkY = 0;
                this.drag = -0.01f;
                this.splashDamageRadius = 15;
                this.splashDamage = 14;
                this.backColor = Color.valueOf("CC6600");
                this.frontColor = Color.valueOf("FFAA33");
                this.trailColor = Color.valueOf("CC6600");
                this.hitEffect = Fx.massiveExplosion;
              }
            },
            CItems.cuguijing, new BasicBulletType(5f, 32f) {
              {
                this.knockback = 1;
                this.width = 8;
                this.height = 18;
                this.spin = 0;
                this.shrinkY = 0;
                this.shrinkX = 0;
                this.lifetime = 125 / 6f;
                this.hitEffect = Fx.explosion;
                this.trailLength = 4;
                this.trailWidth = 5;
                this.trailColor = Color.valueOf("ffce7b");
                this.trailRotation = true;
                this.trailEffect = Fx.disperseTrail;
                this.trailInterval = 3;
                this.fragBullets = 5;
                this.fragRandomSpread = 0;
                this.fragSpread = 25;
                this.fragAngle = 22.5f;
                this.fragVelocityMin = 0.5f;
                this.fragVelocityMax = 0.5f;
                this.fragBullet = new BasicBulletType(4f, 12f) {
                  {
                    this.splashDamageRadius = 8;
                    this.splashDamage = 15;
                    this.trailEffect = Fx.missileTrail;
                    this.trailInterval = 8;
                    this.trailLength = 2;
                    this.trailWidth = 2;
                    this.trailColor = Color.valueOf("ffce7b");
                    this.lifetime = 16;
                  }
                };
              }
            });
      }
    };
    tuxi = new ItemTurret("tuxi") {
      {
        this.health = 1200;
        this.ammoPerShot = 5;
        this.size = 2;
        this.reload = 30;
        this.range = 200;
        this.inaccuracy = 15;
        this.recoil = 2;
        this.requirements(Category.turret,
            ItemStack.with(new Object[] { CItems.lv, 120, CItems.li, 150, CItems.cuguijing, 80, CItems.xi, 85 }));
        this.rotateSpeed = 5;
        this.maxAmmo = 30;
        this.alwaysUnlocked = false;
        this.shoot = new ShootAlternate(4f) {
          {
            this.barrels = 3;
            this.shots = 5;
            this.shotDelay = 4f;
          }
        };
        this.consumePower(3f);
        this.coolant = consumeCoolant(0.2f);
        this.ammo(
            CItems.cuguijing, CBullets.tuxibullet1,
            CItems.tandanzhi, CBullets.tuxibullet2,
            CItems.xi, CBullets.tuxibullet3

        );
      }
    };
    powerair1 = new PowerTurret("powerair1") {
      {
        this.health = 320;
        this.reload = 15;
        this.size = 1;
        this.range = 200;
        this.inaccuracy = 0;
        this.targetAir = true;
        this.hasPower = true;
        this.targetGround = false;
        this.targetHealing = false;
        this.shootCone = 2;
        this.recoil = 0;
        this.rotateSpeed = 4;
        this.requirements(Category.turret,
            ItemStack.with(new Object[] { CItems.lv, 20, CItems.li, 30, CItems.cuguijing, 20 }));
        this.consumePower(1.5f);
        this.coolant = consumeCoolant(0.2f);
        this.shootType = new BasicBulletType() {
          {
            this.speed = 5;
            this.lifetime = 40;
            this.width = 4;
            this.height = 6;
            this.ammoMultiplier = 1;
            this.collidesAir = true;
            this.collidesGround = false;
            this.collidesTeam = false;
            this.damage = 20;
          }
        };
      }
    };
    chuantou = new PowerTurret("chuantou") {
      {
        this.size = 3;
        this.health = 2000;
        this.range = 160;
        this.reload = 120;
        this.shootSound = Sounds.shootLaser;
        this.rotateSpeed = 2;
        this.shake = 1.5f;
        this.targetAir = false;
        this.hasPower = true;
        this.targetGround = true;
        this.recoil = 0.8f;
        this.consumePower(15f);
        this.coolant = consumeCoolant(0.3f);
        this.shootType = new LaserBulletType() {
          {
            this.length = 170;
            this.width = 30;
            this.hitSize = 10;
            this.damage = 120;
            this.hitEffect = Fx.hitLancer;
            this.hitSize = 7;
            this.ammoMultiplier = 1;
            this.knockback = 0.36f;
            this.shootEffect = Fx.lancerLaserShoot;
            this.smokeEffect = Fx.lancerLaserCharge;
            this.collidesAir = true;
            this.sideAngle = 15;
            this.sideWidth = 0;
            this.sideLength = 0;
          }
        };
        this.drawer = new DrawTurret() {
          {
            for (int i = 0; i < 2; i++) {
              int f = i;
              this.parts.add(new RegionPart("-" + (f == 0 ? "l" : "r")) {
                {
                  this.progress = PartProgress.recoil;
                  this.recoilIndex = f;
                  this.moveX = (f == 0 ? -1 : 1) * (0.7f);
                  this.under = false;
                  this.mirror = false;
                  this.moveY = -2f;
                  this.heatColor = Color.valueOf("FF7055FF");
                }
              });
            }
          }
        };
        this.requirements(Category.turret,
            ItemStack.with(new Object[] { CItems.lv, 200, CItems.li, 180, CItems.cuguijing, 180, CItems.xi, 140,
                CItems.chunguijing, 45 }));
      }
    };
  }
}
