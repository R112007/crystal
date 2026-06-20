package crystal.content;

import arc.graphics.Color;
import crystal.gen.*;
import crystal.type.CoreUnitType;
import ent.anno.Annotations.EntityDef;
import mindustry.content.UnitTypes;
import mindustry.gen.Crawlc;
import mindustry.gen.Legsc;
import mindustry.gen.Unitc;
import mindustry.type.Weapon;

public class SpecialUnits {

  public static @EntityDef({ Unitc.class, Corec.class, Crawlc.class }) CoreUnitType moveCore1;
  public static @EntityDef({ Unitc.class, Corec.class, Legsc.class }) CoreUnitType moveCore2;

  public static void load() {
    moveCore1 = new CoreUnitType("moveCore1") {
      {
        unitCapBonus = 10;
        mineSpeed = 1;
        mineTier = 3;
        buildSpeed = 1;
        storageCapacity = 1000;
        controller = UnitTypes.poly.controller;
        constructor = LegsCoreUnit::create;
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
          }
        });
        weapons.add(new Weapon("crystal-moveCore1-w2") {
          {
            x = 13.75f;
            y = 15.75f;
          }
        });
        weapons.add(new Weapon("crystal-moveCore1-w2") {
          {
            x = 18.25f;
            y = -10.25f;
          }
        });
      }
    };
  }
}
