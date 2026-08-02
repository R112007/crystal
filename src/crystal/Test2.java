package crystal;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import crystal.content.CFx;
import crystal.entities.abilities.AddWeaponFieldAbility;
import crystal.entities.abilities.PayloadFieldAbility;
import crystal.entities.bullet.ContinuousSectorLaserBulletType;
import crystal.entities.bullet.HealUnitBulletType;
import crystal.entities.bullet.SectorLaserBulletType;
import crystal.gen.Rammingc;
import crystal.type.RammingUnitType;
import crystal.type.weapons.ContinuousLightningWeapon;
import crystal.type.weapons.HealUnitWeapon;
import crystal.world.blocks.defence.turrets.HealingItemTurret;
import crystal.world.blocks.defence.turrets.HealingPowerTurret;
import crystal.world.blocks.payloads.DronePayloadAssembler;
import crystal.world.blocks.payloads.PayloadAssembler;
import crystal.world.blocks.production.MultipleCrafter;
import ent.anno.Annotations.EntityDef;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.EmpBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.Team;
import mindustry.gen.MechUnit;
import mindustry.gen.Mechc;
import mindustry.gen.PayloadUnit;
import mindustry.gen.Payloadc;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.gen.Unitc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.consumers.ConsumeItems;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static mindustry.content.Items.*;
import static mindustry.content.Blocks.*;
import static mindustry.type.ItemStack.*;

public class Test2 {

  public static boolean allow = false;
  public static Block payloadAssembler, dronePayloadAssembler, healbulletturret;
  public static UnitType u1, u2, u3, u4, u5;
  public static UnitType eastWind;
  public static @EntityDef(value = { Unitc.class, Rammingc.class, Mechc.class }) UnitType u6;
  public static UnitType healUnit;
  public static Block healItemTurret, healPowerTurret, mu;

  public static void load() {
    if (!allow)
      return;
    mu = new MultipleCrafter("mu") {
      {
        requirements(Category.crafting, with(Items.copper, 60, Items.silicon, 40, Items.metaglass, 30));
        size = 2;
        hasItems = true;
        hasLiquids = true;
        formulas = new Seq<>();
        formulas.add(new Formula() {
          {
            inputs.add(new ConsumeItems(with(Items.copper, 1, Items.lead, 2)));
            outputItems.add(with(Items.coal, 1));
          }
        });
        formulas.add(new Formula() {
          {
            inputs.add(new ConsumeItems(with(Items.thorium, 1, Items.titanium, 2)));
            outputItems.add(with(Items.surgeAlloy, 1));
          }
        });
      }
    };
    BulletType healBullet = new HealUnitBulletType(4.5f, 0f) {
      {
        healpercent = 15f;
        collidesTeam = true; // 必须：让子弹能命中友方
        hittable = false; // 敌人不会拦截这枚治疗弹
        lifetime = 60f;
        trailColor = healColor;
        shootEffect = Fx.shootHeal;
        smokeEffect = Fx.hitLaser;
        despawnEffect = Fx.hitLaser;
        hitEffect = Fx.heal;
        status = StatusEffects.none;
      }
    };

    // 3. 治疗无人机/单位上的治疗武器
    Weapon healWeapon = new HealUnitWeapon("heal-weapon") {
      {
        reload = 45f;
        x = 6f;
        y = 0f;
        shootY = 3f;
        rotate = true;
        bullet = healBullet;
      }
    };

    healUnit = new UnitType("heal-unit") {
      {
        health = 320f;
        speed = 2.3f;
        flying = true;
        hitSize = 10f;
        range = 140f;

        weapons.add(healWeapon);
      }
    };
    healItemTurret = new HealingItemTurret("heal-item-turret") {
      {
        requirements(Category.turret, with(Items.copper, 60, Items.silicon, 40, Items.metaglass, 30));
        size = 2;
        reload = 30f;
        range = 140f;
        shootCone = 15f;
        rotateSpeed = 5f;
        inaccuracy = 2f;

        ammo(Items.phaseFabric, healBullet);
      }
    };

    // 3. 电力治疗炮台（继承 PowerTurret）
    healPowerTurret = new HealingPowerTurret("heal-power-turret") {
      {
        requirements(Category.turret, with(Items.copper, 80, Items.silicon, 60, Items.titanium, 40));
        size = 2;
        reload = 25f;
        range = 160f;
        shootCone = 15f;
        rotateSpeed = 6f;
        inaccuracy = 1f;

        shootType = healBullet;
      }
    };
    healbulletturret = new PowerTurret("healbulletturret") {
      {
        this.health = 320;
        this.reload = 23;
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
            ItemStack.with(new Object[] {}));
        this.consumePower(1.5f);
        this.coolant = consumeCoolant(0.2f);
        this.shootType = new HealUnitBulletType() {
          {
            this.speed = 5;
            this.lifetime = 40;
            this.width = 4;
            this.height = 6;
            this.ammoMultiplier = 1;
            this.collidesAir = true;
            this.collidesGround = false;
            this.collidesTeam = false;
            this.damage = 15;
            healpercent = 5;
          }
        };
      }
    };
    dronePayloadAssembler = new DronePayloadAssembler("drone-payload-assembler") {
      {
        requirements(Category.units, with(silicon, 200, beryllium, 150, tungsten, 100));

        size = 5;
        regionSuffix = "-dark";
        maxPayloadSize = 4f;
        areaSize = 5;
        dronesCreated = 4;
        droneConstructTime = 60f * 4f;

        // 配方 1：2 个路由器 + 50 硅 → 1 个分类器
        plans.add(new AssemblerPlan(
            sorter, // 输出：分类器
            60f * 10f, // 耗时 10 秒
            PayloadStack.list(router, 2), // 输入载荷：2 个路由器
            with(silicon, 50) // 可选输入物品：50 硅
        ));

        // 配方 2：1 个脉冲导管 + 1 个路由器 + 80 硅 → 1 个载荷路由器
        plans.add(new AssemblerPlan(
            payloadRouter,
            60f * 15f,
            PayloadStack.list(pulseConduit, 1, router, 1),
            with(silicon, 80)));

        // 配方 3：仅载荷，不需要物品
        plans.add(new AssemblerPlan(
            overflowGate,
            60f * 8f,
            PayloadStack.list(router, 1, sorter, 1)));

        consumePower(3f);
      }
    };
    payloadAssembler = new PayloadAssembler("payload-assembler") {
      {
        requirements(Category.units, with(silicon, 200));

        size = 5;
        regionSuffix = "-dark";
        maxPayloadSize = 4f;
        craftSpeed = 1f;

        // 配方 1：2 个路由器 + 50 硅 → 1 个分类器
        plans.add(new AssemblerPlan(
            sorter, // 输出：分类器
            60f * 10f, // 耗时 10 秒
            PayloadStack.list(router, 2), // 输入载荷：2 个路由器
            with(silicon, 50) // 可选输入物品：50 硅
        ));

        // 配方 2：1 个脉冲导管 + 1 个路由器 + 80 硅 → 1 个载荷路由器
        plans.add(new AssemblerPlan(
            payloadRouter,
            60f * 15f,
            PayloadStack.list(pulseConduit, 1, router, 1),
            with(silicon, 80)));

        // 配方 3：仅载荷，不需要物品
        plans.add(new AssemblerPlan(
            overflowGate,
            60f * 8f,
            PayloadStack.list(router, 1, sorter, 1)));

        consumePower(3f);
      }
    };
    Weapon w = new Weapon("corvus-weapon") {
      {
        shootSound = Sounds.shootCorvus;
        chargeSound = Sounds.chargeCorvus;
        soundPitchMin = 1f;
        top = false;
        mirror = false;
        shake = 14f;
        shootY = 5f;
        x = y = 0;
        reload = 100f;
        recoil = 0f;

        cooldownTime = 100f;

        shoot.firstShotDelay = Fx.greenLaserCharge.lifetime;
        parentizeEffects = true;

        bullet = new LaserBulletType() {
          {
            length = 200f;
            damage = 560f;
            width = 25f;

            lifetime = 65f;

            lightningSpacing = 35f;
            lightningLength = 5;
            lightningDelay = 1.1f;
            lightningLengthRand = 15;
            lightningDamage = 50;
            lightningAngleRand = 40f;
            largeHit = true;
            lightColor = lightningColor = Pal.heal;

            chargeEffect = Fx.greenLaserCharge;

            healPercent = 25f;
            collidesTeam = true;

            sideAngle = 15f;
            sideWidth = 0f;
            sideLength = 0f;
            colors = new Color[] { Pal.heal.cpy().a(0.4f), Pal.heal, Color.white };
          }
        };
      }
    };
    u1 = new UnitType("u1") {
      {
        this.hitSize = 67;
        this.rotateSpeed = 1f;
        this.itemCapacity = 200;
        this.mineSpeed = 12f;
        this.mineTier = 8;
        this.mineRange = 250f;
        this.buildRange = 288f;
        this.buildSpeed = 10f;
        this.buildBeamOffset = 5f;
        this.canDrown = false;
        this.circleTarget = false;
        this.forceMultiTarget = true;
        this.flying = true;
        this.speed = 1.2f;
        this.health = 348000;
        this.lowAltitude = true;
        this.engineSize = 12f;
        this.engineOffset = 45f;
        this.armor = 25;
        this.constructor = UnitEntity::create;
        this.abilities.add(new AddWeaponFieldAbility(200, 180, 360, w));
      }
    };
    u2 = new UnitType("u2") {
      {
        constructor = MechUnit::create;
        speed = 0.42f;
        hitSize = 21f;
        health = 42000f;
        itemCapacity = 0;
        armor = 8f;
        rotateSpeed = 1.2f;
        canBoost = false;
        weapons.add(new ContinuousLightningWeapon("crystal-u2-w") {
          {
            y = 0f;
            x = 11.5f;
            shootY = 7f;
            rotate = false;
            top = false;
            reload = 2f;
            glowRadius = 4f;
            glowRadiusMag = 3f;
            glowPulsePeriod = 40f;
            glowColor = Color.valueOf("ffaa44");
            glowColorInner = Color.valueOf("ffff88");
            glowAlpha = 0.7f;
            lightningCount = 10;
            lightningLength = 20;
            lightningLengthRand = 12;
            lightningDamage = 15f;
            lightningColor = Pal.surge;
            lightningInterval = 4f;
            lightningRange = 400f;

            maxAngleOffset = 40f;
            targetPullStrength = 0.5f;
            stepLengthMin = 16f;
            stepLengthMax = 24f;
            maxBends = 12;
            aimJitter = 10f;
            forceReachThreshold = 3;
            useMountAim = true;
          }
        });
      }
    };
    u3 = new UnitType("u3") {
      {
        this.hitSize = 30;
        this.rotateSpeed = 1f;
        this.itemCapacity = 200;
        this.mineSpeed = 12f;
        this.mineTier = 8;
        this.mineRange = 250f;
        this.buildRange = 288f;
        this.buildSpeed = 10f;
        this.buildBeamOffset = 5f;
        this.canDrown = false;
        this.circleTarget = false;
        this.forceMultiTarget = true;
        this.flying = true;
        this.speed = 1.2f;
        this.health = 348000;
        this.lowAltitude = true;
        this.engineSize = 12f;
        this.engineOffset = 45f;
        this.armor = 25;
        this.constructor = UnitEntity::create;
        weapons.add(new Weapon() {
          {
            x = y = 0;
            reload = 60;
            mirror = false;
            rotateSpeed = 1f;
            rotate = true;
            bullet = new SectorLaserBulletType(120) {
              {
                sectorLength = 160f;
                sectorAngle = 60f;
                lifetime = 40f;
                // 已删除 speed = 6f，保持父类 speed = 0f，扇形固定在发射位置
                status = StatusEffects.burning;
                statusDuration = 120f;
                colors = new Color[] { Pal.heal.cpy().a(0.4f), Pal.heal, Color.white };

              }
            };
          }
        });
      }
    };
    u4 = new UnitType("u4") {
      {
        this.hitSize = 30;
        this.rotateSpeed = 1f;
        this.itemCapacity = 200;
        this.mineSpeed = 12f;
        this.mineTier = 8;
        this.mineRange = 250f;
        this.buildRange = 288f;
        this.buildSpeed = 10f;
        this.buildBeamOffset = 5f;
        this.canDrown = false;
        this.circleTarget = false;
        this.forceMultiTarget = true;
        this.flying = true;
        this.speed = 1.2f;
        this.health = 348000;
        this.lowAltitude = true;
        this.engineSize = 12f;
        this.engineOffset = 45f;
        this.armor = 25;
        this.constructor = UnitEntity::create;
        weapons.add(new Weapon() {
          {
            x = y = 0;
            reload = 300;
            mirror = false;
            rotateSpeed = 1f;
            rotate = true;
            bullet = new ContinuousSectorLaserBulletType(120) {
              {
                continuous = true;
                length = 180f;
                sectorAngle = 60f;
                damageInterval = 5f;
                lifetime = 120f;
                timescaleDamage = true;
                status = StatusEffects.melting;
                statusDuration = 60f;
                colors = new Color[] { Pal.heal.cpy().a(0.4f), Pal.heal, Color.white };
                hitColor = Color.white;
                lightColor = Color.valueOf("ff9c5a");
              }
            };
          }
        });
      }
    };
    u5 = new UnitType("u5") {
      {
        this.hitSize = 30;
        this.rotateSpeed = 1f;
        this.itemCapacity = 200;
        this.mineSpeed = 12f;
        this.mineTier = 8;
        this.mineRange = 250f;
        this.buildRange = 288f;
        this.buildSpeed = 10f;
        this.buildBeamOffset = 5f;
        this.canDrown = false;
        this.circleTarget = false;
        this.forceMultiTarget = true;
        this.flying = true;
        this.speed = 1.2f;
        this.health = 348000;
        this.lowAltitude = true;
        this.engineSize = 12f;
        this.engineOffset = 45f;
        this.armor = 25;
        this.constructor = UnitEntity::create;
        weapons.add(new Weapon("crystal-moveCore1-w1") {
          {
            x = -22.75f;
            y = 1.5f;
            rotate = false;
            reload = 65f;
            shake = 3f;
            recoil = 2f;
            cooldownTime = reload - 10f;
            shootSound = Sounds.shootNavanax;
            bullet = new EmpBulletType() {
              {
                float rad = 100f;
                scaleLife = true;
                lightOpacity = 0.7f;
                unitDamageScl = 0.8f;
                healPercent = 10f;
                timeIncrease = 3f;
                timeDuration = 60f * 20f;
                powerDamageScl = 3f;
                damage = 60;
                hitColor = lightColor = Pal.heal;
                lightRadius = 50f;
                clipSize = 160f;
                shootEffect = Fx.hitEmpSpark;
                smokeEffect = Fx.shootBigSmoke2;
                lifetime = 60f;
                sprite = "circle-bullet";
                backColor = Pal.heal;
                frontColor = Color.white;
                width = height = 12f;
                shrinkY = 0f;
                speed = 5f;
                trailLength = 20;
                trailWidth = 6f;
                trailColor = Pal.heal;
                trailInterval = 3f;
                splashDamage = 70f;
                splashDamageRadius = rad;
                hitShake = 4f;
                trailRotation = true;
                status = StatusEffects.electrified;
                hitSound = Sounds.explosionNavanax;

                trailEffect = new Effect(16f, e -> {
                  color(Pal.heal);
                  for (int s : Mathf.signs) {
                    Drawf.tri(e.x, e.y, 4f, 30f * e.fslope(), e.rotation + 90f * s);
                  }
                });
                hitEffect = despawnEffect = CFx.energySphere;
              }
            };
          }
        });
      }
    };
    eastWind = new UnitType("eastWind") {
      {
        health = 4000;
        hitSize = 30;
        flying = true;
        lowAltitude = true;
        accel = 0.08f;
        drag = 0.03f;
        rotateSpeed = 1f;
        payloadCapacity = (4 * 4) * Vars.tilePayload;
        constructor = PayloadUnit::create;
        controller = UnitTypes.quad.controller;
        engineSize = 7.5f;
        engineOffset = 25.75f;
        setEnginesMirror(new UnitEngine(16f, -20.5f, 5, 0));
        weapons.add(new Weapon("crystal-eastWind1") {
          {
            x = 0;
            y = -0.25f;
            mirror = false;
          }
        });
        weapons.add(new Weapon("crystal-eastWind1") {
          {
            x = -15.75f;
            y = -10.5f;
            mirror = true;
          }
        });
        abilities.add(new PayloadFieldAbility());
      }
    };
    u6 = new RammingUnitType("u6") {
      {
        speed = 5;
      }
    };
  }
}
