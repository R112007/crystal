package crystal.content;

import arc.graphics.Color;
import arc.struct.Seq;
import crystal.type.CItemType;

public class CItems {
  public static CItemType yellowcopper;
  public final static Seq<CItemType> citems = new Seq<>();

  public static void load() {
    yellowcopper = new CItemType("yellow-copper", Color.valueOf("#F8C367")) {
      {
        cost = 0.5f;
        crystalEnergy = 0.5f;
        hardness = 1;
      }
    };
    citems.addAll(yellowcopper);
  }
}
