package crystal.content;

import arc.graphics.Color;
import crystal.entities.abilities.ContinueRepairField;
import mindustry.content.UnitTypes;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.ammo.PowerAmmoType;

public class CUnits {
  public static UnitType
  // 核心机
  taichu;

  public static void load() {
    taichu = new UnitType("taichu") {
      {
        this.health = 400.0f;
        this.constructor = UnitTypes.alpha.constructor;
        this.controller = UnitTypes.alpha.controller;
        this.flying = true;
        this.ammoType = new PowerAmmoType(800);
        this.speed = 3.0f;
        this.drag = 0.1f;
        this.accel = 0.3f;
        this.lowAltitude = true;
        this.itemCapacity = 50;
        this.rotateSpeed = 22.0f;
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
        this.abilities.add(new ContinueRepairField(1.5f, 64, true, true));
        this.weapons.add(new Weapon() {
          {
            this.reload = 65.0f;
            this.x = 0f;
            this.y = 0.75f;
            this.top = false;
            this.inaccuracy = 0f;
            this.bullet = CBullets.taichu1;
          }
        });
        this.weapons.add(new Weapon() {
          {
            this.reload = 65.0f;
            this.x = 0f;
            this.y = 0.75f;
            this.top = false;
            this.inaccuracy = 0f;
            this.bullet = CBullets.taichu2;
          }
        });
      }
    };
  }
}
