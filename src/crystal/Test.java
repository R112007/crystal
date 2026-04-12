package crystal;

import arc.Events;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import crystal.content.CBullets;
import crystal.content.CItems;
import crystal.content.Tree;
import crystal.entities.abilities.ReduceBoostAbility;
import crystal.entities.bullet.GravityBullet;
import crystal.entities.bullet.ReduceBoostBullet;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.units.MultiStageMechUnit;
import crystal.entities.units.UnitEnum.Mode;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.FaShenc;
import crystal.gen.Magicc;
import crystal.gen.MagicUnit;
import crystal.type.MagicUnitType;
import crystal.type.MultiStageUnitType;
import crystal.type.weapons.StageWeapon;
import crystal.util.DLog;
import crystal.world.blocks.crystal.CrystalDrill;
import crystal.world.blocks.crystal.CrystalSource;
import crystal.world.blocks.defence.LinkWall;
import crystal.world.blocks.defence.towers.ExtendRangeTower;
import crystal.world.blocks.defence.towers.PowerAttackTower;
import crystal.world.blocks.defence.towers.Tower;
import crystal.world.blocks.defence.turrets.LevelUpTurret;
import crystal.world.blocks.effect.CoverFloorMachine;
import crystal.world.blocks.effect.GuideCandle;
import crystal.world.blocks.effect.ReplaceFloor;
import crystal.world.blocks.effect.ScrambleUnitLanding;
import crystal.world.blocks.effect.SummonUnitBlock;
import crystal.world.blocks.environment.DamageFloor;
import crystal.world.blocks.environment.SpawnBossFloor;
import crystal.world.blocks.liquid.LiquidRangeBridge;
import crystal.world.blocks.payloads.UnitLaunchPad;
import crystal.world.blocks.payloads.UnitReceivePad;
import ent.anno.Annotations.EntityDef;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.content.TechTree;
import mindustry.content.UnitTypes;
import mindustry.entities.Damage;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.game.EventType.BuildDamageEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Mechc;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.gen.Unitc;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.ammo.ItemAmmoType;
import mindustry.type.ammo.PowerAmmoType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BuildVisibility;

public class Test {
    public static Block t;
    public static Block t1;
    public static Block t2;
    public static Block a1;
    public static Block a2;
    public static Block a3;
    public static Block unitlanuch;
    public static Block unitreceive;
    public static Block c1;
    public static Block d1;
    public static Block f1;
    public static Block tu1;
    public static UnitType a6;
    public static Block test8;
    public static Block test10;
    public static Block test9;
    public static Block w;
    public static Block spfloor;
    public static Block guidecandle;
    public static Block summonblock;
    public static Block tower;
    public static Block powertower;
    public static Block itemtower;
    public static Block extendtower;
    public static ReplaceFloor replaceFloor;
    public static CoverFloorMachine coverFloorMachine;;
    public static MultiStageUnitType multiStageUnitType;
    public static LaserBulletType laser;
    public static @EntityDef({ Unitc.class, Magicc.class, Mechc.class }) MagicUnitType magic, magic3, magic4;
    public static @EntityDef({ Unitc.class, Magicc.class }) MagicUnitType magic2;
    public static ScrambleUnitLanding scrambleUnitLanding;

    public static void load() {
        scrambleUnitLanding = new ScrambleUnitLanding("scrambleUnitLanding") {
            {
                size = 2;
                this.requirements(Category.effect, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
                influence = influence.destory;
            }
        };
        magic4 = new MagicUnitType("magic4") {
            {
                speed = 0.4f;
                hitSize = 30f;
                rotateSpeed = 1.65f;
                health = 24000;
                armor = 18f;
                mechStepParticles = true;
                stepShake = 0.75f;
                drownTimeMultiplier = 1.6f;
                mechFrontSway = 1.9f;
                mechSideSway = 0.6f;
                ammoType = new ItemAmmoType(Items.thorium);
                stepSound = Sounds.mechStepHeavy;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.45f;

                weapons.add(
                        new Weapon("reign-weapon") {
                            {
                                top = false;
                                y = 1f;
                                x = 21.5f;
                                shootY = 11f;
                                reload = 9f;
                                recoil = 5f;
                                shake = 2f;
                                ejectEffect = Fx.casing4;
                                shootSound = Sounds.shootReign;

                                bullet = new BasicBulletType(13f, 80) {
                                    {
                                        pierce = true;
                                        pierceCap = 10;
                                        width = 14f;
                                        height = 33f;
                                        lifetime = 15f;
                                        shootEffect = Fx.shootBig;
                                        fragVelocityMin = 0.4f;

                                        hitEffect = Fx.blastExplosion;
                                        splashDamage = 18f;
                                        splashDamageRadius = 13f;

                                        fragBullets = 3;
                                        fragLifeMin = 0f;
                                        fragRandomSpread = 30f;
                                        despawnSound = Sounds.explosion;

                                        fragBullet = new BasicBulletType(9f, 20) {
                                            {
                                                width = 10f;
                                                height = 10f;
                                                pierce = true;
                                                pierceBuilding = true;
                                                pierceCap = 3;

                                                lifetime = 20f;
                                                hitEffect = Fx.flakExplosion;
                                                splashDamage = 15f;
                                                splashDamageRadius = 10f;
                                            }
                                        };
                                    }
                                };
                            }
                        }

                );
                this.xiuWei = XiuWei.fan;
                this.magicPowerRegenTime = 60;
                this.magicPowerRegenTime = 60;
                this.magicPower = 1000;
                this.shenTongs.add(new FaTianXiangDi(3f, 3f, 3, 480));
            }
        };
        magic3 = new MagicUnitType("magic3") {
            {
                speed = 0.4f;
                hitSize = 30f;
                rotateSpeed = 1.65f;
                health = 24000;
                armor = 18f;
                mechStepParticles = true;
                stepShake = 0.75f;
                drownTimeMultiplier = 1.6f;
                mechFrontSway = 1.9f;
                mechSideSway = 0.6f;
                ammoType = new ItemAmmoType(Items.thorium);
                stepSound = Sounds.mechStepHeavy;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.45f;
                alwaysUnlocked = true;
                weapons.add(
                        new Weapon("reign-weapon") {
                            {
                                top = false;
                                y = 1f;
                                x = 21.5f;
                                shootY = 11f;
                                reload = 9f;
                                recoil = 5f;
                                shake = 2f;
                                ejectEffect = Fx.casing4;
                                shootSound = Sounds.shootReign;

                                bullet = new BasicBulletType(13f, 80) {
                                    {
                                        pierce = true;
                                        pierceCap = 10;
                                        width = 14f;
                                        height = 33f;
                                        lifetime = 15f;
                                        shootEffect = Fx.shootBig;
                                        fragVelocityMin = 0.4f;

                                        hitEffect = Fx.blastExplosion;
                                        splashDamage = 18f;
                                        splashDamageRadius = 13f;

                                        fragBullets = 3;
                                        fragLifeMin = 0f;
                                        fragRandomSpread = 30f;
                                        despawnSound = Sounds.explosion;

                                        fragBullet = new BasicBulletType(9f, 20) {
                                            {
                                                width = 10f;
                                                height = 10f;
                                                pierce = true;
                                                pierceBuilding = true;
                                                pierceCap = 3;

                                                lifetime = 20f;
                                                hitEffect = Fx.flakExplosion;
                                                splashDamage = 15f;
                                                splashDamageRadius = 10f;
                                            }
                                        };
                                    }
                                };
                            }
                        }

                );
                this.xiuWei = XiuWei.dijun;
                this.magicPowerRegenTime = 60;
                this.magicPowerRegenTime = 60;
                this.magicPower = 1000;
                this.shenTongs.add(new FaTianXiangDi(3f, 3f, 3, 480));
            }
        };
        magic2 = new MagicUnitType("magic2") {
            {
                constructor = MagicUnit::create;
                speed = 1.8f;
                accel = 0.04f;
                drag = 0.04f;
                rotateSpeed = 1.9f;
                flying = true;
                lowAltitude = true;
                health = 7200;
                armor = 9f;
                engineOffset = 21;
                engineSize = 5.3f;
                hitSize = 46f;
                targetFlags = new BlockFlag[] { BlockFlag.generator, BlockFlag.core, null };
                ammoType = new ItemAmmoType(Items.thorium);

                loopSound = Sounds.loopHover;

                BulletType missiles = new MissileBulletType(2.7f, 18) {
                    {
                        width = 8f;
                        height = 8f;
                        shrinkY = 0f;
                        drag = -0.01f;
                        splashDamageRadius = 20f;
                        splashDamage = 37f;
                        ammoMultiplier = 4f;
                        lifetime = 50f;
                        hitEffect = Fx.blastExplosion;
                        despawnEffect = Fx.blastExplosion;

                        status = StatusEffects.blasted;
                        statusDuration = 60f;
                    }
                };

                weapons.add(
                        new Weapon("missiles-mount") {
                            {
                                y = 8f;
                                x = 17f;
                                reload = 20f;
                                ejectEffect = Fx.casing1;
                                rotateSpeed = 8f;
                                bullet = missiles;
                                shootSound = Sounds.shootMissile;
                                rotate = true;
                                shadow = 6f;
                            }
                        },
                        new Weapon("missiles-mount") {
                            {
                                y = -8f;
                                x = 17f;
                                reload = 35;
                                rotateSpeed = 8f;
                                ejectEffect = Fx.casing1;
                                bullet = missiles;
                                shootSound = Sounds.shootMissile;
                                rotate = true;
                                shadow = 6f;
                            }
                        },
                        new Weapon("large-bullet-mount") {
                            {
                                y = 2f;
                                x = 10f;
                                shootY = 10f;
                                reload = 12;
                                shake = 1f;
                                rotateSpeed = 2f;
                                ejectEffect = Fx.casing1;
                                shootSound = Sounds.shootSpectre;
                                rotate = true;
                                shadow = 8f;
                                bullet = new BasicBulletType(7f, 55) {
                                    {
                                        width = 12f;
                                        height = 18f;
                                        lifetime = 25f;
                                        shootEffect = Fx.shootBig;
                                    }
                                };
                            }
                        });
                this.xiuWei = XiuWei.sheng;
                this.magicPowerRegenTime = 60;
                this.magicPowerRegenTime = 60;
                this.magicPower = 1000;
                this.shenTongs.add(new FaTianXiangDi(3f, 3f, 3, 480));
            }
        };
        magic = new MagicUnitType("magic") {
            {
                hitSize = 80;
                this.shenTongs.add(new FaTianXiangDi(5f, 5f, 1, 600));
                this.health = 400.0f;
                this.controller = UnitTypes.dagger.controller;
                this.ammoType = new PowerAmmoType(800);
                this.speed = 3.0f;
                this.drag = 0.1f;
                this.accel = 0.3f;
                this.itemCapacity = 50;
                this.rotateSpeed = 22.0f;

                this.weapons.add(new Weapon("ceystal-w1") {
                    {
                        this.reload = 65.0f;
                        this.x = 0f;
                        this.y = 0.75f;
                        this.top = false;
                        this.inaccuracy = 0f;
                        this.bullet = CBullets.taichu1;
                    }
                });
            }
        };
        laser = new LaserBulletType(10) {
            @Override
            public void init(Bullet b) {
                float resultLength = Damage.collideLaser(b, length, largeHit, laserAbsorb, pierceCap),
                        rot = b.rotation();
                Log.info("collideLaser已执行" + b.x + b.y);
                laserEffect.at(b.x, b.y, rot, resultLength * 0.75f);

                if (lightningSpacing > 0) {
                    int idx = 0;
                    for (float i = 0; i <= resultLength; i += lightningSpacing) {
                        float cx = b.x + Angles.trnsx(rot, i),
                                cy = b.y + Angles.trnsy(rot, i);

                        int f = idx++;

                        for (int s : Mathf.signs) {
                            Time.run(f * lightningDelay, () -> {
                                if (b.isAdded() && b.type == this) {
                                    Lightning.create(b, lightningColor,
                                            lightningDamage < 0 ? damage : lightningDamage,
                                            cx, cy, rot + 90 * s + Mathf.range(lightningAngleRand),
                                            lightningLength + Mathf.random(lightningLengthRand));
                                }
                            });
                        }
                    }
                }
            }
        };
        coverFloorMachine = new CoverFloorMachine("coverFloorMachine") {
            {
                size = 2;
                size = 2;
                this.requirements(Category.defense, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
                this.requirements(Category.defense, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        replaceFloor = new ReplaceFloor("replaceFloor") {
            {
                size = 2;
                this.requirements(Category.defense, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
                floors.add(Blocks.grass, Blocks.stone, Blocks.hotrock, Blocks.oreCopper);
            }
        };
        tower = new Tower("tower") {
            {
                size = 2;
                this.requirements(Category.effect, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        powertower = new PowerAttackTower("powertower") {
            {
                size = 2;
                this.requirements(Category.effect, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        extendtower = new ExtendRangeTower("extendtower") {
            {
                size = 2;
                this.requirements(Category.effect, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        multiStageUnitType = new MultiStageUnitType("multiStageUnitType") {
            {
                this.mode = Mode.easy;
                this.health = 600;
                this.weapons.add(
                        new StageWeapon("large-weapon", false) {
                            {
                                weappnStage = 1;
                                reload = 13f;
                                x = 2f;
                                y = 6f;
                                top = false;
                                ejectEffect = Fx.casing1;
                                bullet = new BasicBulletType(2.5f, 9) {
                                    {
                                        width = 7f;
                                        height = 9f;
                                        lifetime = 60f;
                                    }
                                };
                            }
                        });
                this.weapons.add(
                        new StageWeapon("large-weapon", false) {
                            {
                                weappnStage = 2;
                                reload = 13f;
                                x = 4;
                                y = 6f;
                                top = false;
                                ejectEffect = Fx.casing1;
                                bullet = new BasicBulletType(2.5f, 9) {
                                    {
                                        width = 7f;
                                        height = 9f;
                                        lifetime = 60f;
                                    }
                                };
                            }
                        });
                this.weapons.add(
                        new StageWeapon("large-weapon", false) {
                            {
                                weappnStage = 3;
                                reload = 13f;
                                x = 6f;
                                y = 6f;
                                top = false;
                                ejectEffect = Fx.casing1;
                                bullet = new BasicBulletType(2.5f, 9) {
                                    {
                                        width = 7f;
                                        height = 9f;
                                        lifetime = 60f;
                                    }
                                };
                            }
                        });
                this.weapons.add(
                        new StageWeapon("large-weapon", false) {
                            {
                                weappnStage = 4;
                                reload = 13f;
                                x = 10f;
                                y = 6f;
                                top = false;
                                ejectEffect = Fx.casing1;
                                bullet = new GravityBullet(2.5f, 9) {
                                    {
                                        foece = 2;
                                        foeceRange = 32;
                                        width = 7f;
                                        height = 9f;
                                        lifetime = 120f;
                                    }
                                };
                            }
                        });
            }
        };
        guidecandle = new GuideCandle("guidecandle");
        summonblock = new SummonUnitBlock("summonblock") {
            {
                buildVisibility = BuildVisibility.shown;
                category = Category.effect;
                requirements = ItemStack.with(Items.copper, 2);
            }
        };
        spfloor = new SpawnBossFloor("spfloor") {
            {
            }
        };
        t = new LiquidRangeBridge("t") {
            {
                range = 35;
                liquidCapacity = 30;
                health = 300;
                hasPower = true;
                consumePower(2);
                size = 3;
                buildVisibility = BuildVisibility.shown;
                category = Category.liquid;
                requirements = ItemStack.with(Items.copper, 2);
                alwaysUnlocked = true;
            }
        };
        unitlanuch = new UnitLaunchPad("unitlanuch") {
            {
                size = 3;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        unitreceive = new UnitReceivePad("unitreceive") {
            {
                size = 3;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        a6 = new UnitType("a6") {
            {
                speed = 0.5f;
                hitSize = 8f;
                health = 150;
                constructor = UnitEntity::create;
                this.abilities.add(new ReduceBoostAbility(80, 0.1f));
                // this.abilities.add(new AddWeaponAbility(UnitTypes.dagger.weapons.get(0), 300,
                // 80, 500, false));
            }
        };
        t1 = new LinkWall("t1") {
            {
                size = 1;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        t2 = new LinkWall("t2") {
            {
                size = 2;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        c1 = new LevelUpTurret("c1") {
            {
                size = 1;
                this.requirements(Category.production, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        d1 = new CrystalDrill("d1") {
            {
                this.consumeCrystal = 1f;
                this.crystalCapacity = 60f;
                size = 2;
                this.requirements(Category.production, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        f1 = new CrystalSource("f1") {
            {
                size = 2;
                this.requirements(Category.production, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        tu1 = new PowerTurret("lancerLaserShoot") {
            {
                requirements(Category.turret, ItemStack.with(Items.copper, 60));
                range = 165f;

                shoot.firstShotDelay = 40f;

                recoil = 2f;
                reload = 70f;
                shake = 2f;
                shootEffect = Fx.lancerLaserShoot;
                smokeEffect = Fx.none;
                heatColor = Color.red;
                size = 2;
                scaledHealth = 280;
                moveWhileCharging = false;
                accurateDelay = false;
                coolant = consumeCoolant(0.2f);

                consumePower(6f);
                shootType = laser;
                /*
                 * shootType = new reduceBoostBullet(3f, 1f) {
                 * {
                 * this.range = 80f;
                 * this.percent = 0.1f;
                 * this.duration = 120;
                 * }
                 * };
                 */
            }
        };
        Blocks.payloadSource.buildVisibility = BuildVisibility.shown;
        Blocks.payloadSource.alwaysUnlocked = true;
    }
}
