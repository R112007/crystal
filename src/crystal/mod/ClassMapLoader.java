package crystal.mod;

import mindustry.mod.ClassMap;

public class ClassMapLoader {
  public static void load() {
    ClassMap.classes.put("CoreUnitType", crystal.type.CoreUnitType.class);
  }
}
