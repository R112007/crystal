package crystal.content;

import arc.graphics.Color;
import crystal.gen.EntityRegistry;
import crystal.gen.ShieldBuilderUnit;
import crystal.gen.ShieldBuilderc;
import crystal.type.BuildShieldUnitType;
import ent.anno.Annotations.EntityDef;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.ammo.PowerAmmoType;

public class CUnitTypes {
  public static @EntityDef({ Unitc.class, ShieldBuilderc.class }) BuildShieldUnitType buildshield;

  public static void load() {
    CUnitTypes.buildshield = EntityRegistry.content("buildshield", ShieldBuilderUnit.class,
        name -> new BuildShieldUnitType(name) {
          {
            this.shieldHealth = 1800f;
            this.regenRate = 1f;
            this.cooldown = 600f;
            this.health = 1500f;
            this.flying = true;
            this.ammoType = new PowerAmmoType(800);
            this.speed = 6.0f;
            this.drag = 0.1f;
            this.rotateSpeed = 360f;
            this.accel = 0.3f;
            this.outlineColor = Color.valueOf("212121");
            this.lowAltitude = true;
            this.weapons.add(new Weapon("sc-chujia1-weapon") {
              {
                this.reload = 5.0f;
                this.x = -4.0f;
                this.y = 0.75f;
                this.top = false;
                this.ejectEffect = Fx.casing1;
                this.bullet = new BasicBulletType(3.0f, 16.0f) {
                  {
                    this.width = 7.0f;
                    this.height = 13.0f;
                    this.lifetime = 30.0f;
                    this.shootEffect = Fx.shootSmall;
                    this.smokeEffect = Fx.shootSmallSmoke;
                    this.ammoMultiplier = 1.5f;
                  }
                };
              }
            });
          }
        });
  }
}
