package crystal.world;

import mindustry.world.meta.Attribute;

public class CAttributes {
  public static Attribute underpower;
  public static Attribute waste;

  public static void load() {

    CAttributes.underpower = Attribute.add("underpower");
    CAttributes.waste = Attribute.add("waste");
  }

}
