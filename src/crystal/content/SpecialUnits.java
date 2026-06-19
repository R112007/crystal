package crystal.content;

import crystal.gen.*;
import crystal.type.CoreUnitType;
import ent.anno.Annotations.EntityDef;
import mindustry.content.UnitTypes;
import mindustry.gen.Crawlc;
import mindustry.gen.Legsc;
import mindustry.gen.Unitc;

public class SpecialUnits {

  public static @EntityDef({ Unitc.class, Corec.class, Crawlc.class }) CoreUnitType coreUnit1;
  public static @EntityDef({ Unitc.class, Corec.class, Legsc.class }) CoreUnitType coreUnit2;

  public static void load() {
    coreUnit1 = new CoreUnitType("coreUnit1") {
      {
        unitCapBonus = 10;
        mineSpeed = 1;
        mineTier = 3;
        buildSpeed = 1;
        storageCapacity = 1000;
        controller = UnitTypes.poly.controller;
        constructor = LegsCoreUnit::create;
      }
    };
  }
}
