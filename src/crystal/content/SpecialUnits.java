package crystal.content;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import crystal.gen.*;
import crystal.type.CoreUnitType;
import ent.anno.Annotations.EntityDef;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.EmpBulletType;
import mindustry.gen.Crawlc;
import mindustry.gen.Sounds;
import mindustry.gen.Unitc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import mindustry.type.weapons.MineWeapon;
import mindustry.type.weapons.PointDefenseWeapon;
import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;

public class SpecialUnits {

  public static @EntityDef({ Unitc.class, Corec.class, Crawlc.class }) CoreUnitType moveCore1;
  public static @EntityDef({ Unitc.class, Corec.class, RetractableLegsc.class }) CoreUnitType moveCore2;

  public static void load() {
    moveCore1 = new CoreUnitType("moveCore1") {
      {
        alwaysUnlocked = true;
        unitCapBonus = 10;
        mineSpeed = 1;
        mineTier = 3;
        buildSpeed = 1;
        storageCapacity = 1000;
        controller = UnitTypes.poly.controller;
        constructor = RetractableLegsCoreUnit::create;
        speed = 0.5f;
        rotateSpeed = 0.7f;
        legCount = 6;
        legGroupSize = 2;
        legLength = 30;
        legBaseOffset = 6;
        legSpeed = 0.06f;
        legMoveSpace = 1f;
        legForwardScl = 0.7f;
        legMinLength = 0.7f;
        legMaxLength = 1.2f;
        legSplashDamage = 8;
        legSplashRange = 10;
        legBaseUnder = true;
        stepShake = 3;
        legSplashDamage = 80;
        legSplashRange = 60;
        outlineColor = Color.valueOf("#43434FFF");
        hitSize = 40;
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

                hitEffect = new Effect(50f, 100f, e -> {
                  e.scaled(7f, b -> {
                    color(Pal.heal, b.fout());
                    Fill.circle(e.x, e.y, rad);
                  });

                  color(Pal.heal);
                  stroke(e.fout() * 3f);
                  Lines.circle(e.x, e.y, rad);

                  int points = 10;
                  float offset = Mathf.randomSeed(e.id, 360f);
                  for (int i = 0; i < points; i++) {
                    float angle = i * 360f / points + offset;
                    Drawf.tri(e.x + Angles.trnsx(angle, rad), e.y + Angles.trnsy(angle, rad), 6f, 50f * e.fout(),
                        angle);
                  }

                  Fill.circle(e.x, e.y, 12f * e.fout());
                  color();
                  Fill.circle(e.x, e.y, 6f * e.fout());
                  Drawf.light(e.x, e.y, rad * 1.6f, Pal.heal, e.fout());
                });
              }
            };
          }
        });
        weapons.add(new MineWeapon("crystal-moveCore1-w2") {
          {
            x = 13.75f;
            y = 15.75f;
            shootY = 6;
          }
        });
        weapons.add(new PointDefenseWeapon("crystal-moveCore1-w2") {
          {
            x = 18.25f;
            y = -10.25f;
            reload = 4f;
            targetInterval = 8f;
            targetSwitchInterval = 8f;
            bullet = new BulletType() {
              {
                shootEffect = Fx.sparkShoot;
                hitEffect = Fx.pointHit;
                maxRange = 180f;
                damage = 30f;
              }
            };
          }
        });
        abilities.add(new EnergyFieldAbility(60f, 120f, 200f) {
          {
            y = 16.5f;
            layer = Layer.groundUnit - 0.01f;
            effectRadius = 3f;
          }
        });
      }
    };
  }
}
