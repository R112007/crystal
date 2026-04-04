package crystal.content;

import arc.graphics.Color;
import arc.struct.Seq;
import crystal.type.CItemType;

public class CItems {
  public static CItemType yellowcopper;
  public static CItemType lv;
  public static CItemType li;
  public static CItemType boli;
  public static CItemType tandanzhi;
  public static CItemType cuguijing;
  public static CItemType chunguijing;
  public static CItemType xi;
  public static CItemType lvgang;
  public final static Seq<CItemType> citems = new Seq<>();

  public static void load() {
    yellowcopper = new CItemType("yellow-copper", Color.valueOf("#F8C367")) {
      {
        cost = 0.5f;
        crystalEnergy = 0.5f;
        hardness = 1;
      }
    };
    lv = new CItemType("lv", Color.valueOf("#E5F0E8")) {
      {
        this.hardness = 1;
        this.cost = 1.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 0.5f;
        this.explosiveness = 0.0f;
      }
    };
    li = new CItemType("li", Color.valueOf("#E5F0E8")) {
      {
        this.hardness = 1;
        this.cost = 1.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 0.5f;
        this.explosiveness = 0.0f;
      }
    };
    boli = new CItemType("boli", Color.valueOf("#ffffff")) {
      {
        this.hardness = 1;
        this.cost = 2.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 0.8f;
        this.explosiveness = 0.0f;
      }
    };
    tandanzhi = new CItemType("tandanzhi", Color.valueOf("#000000")) {
      {
        this.hardness = 2;
        this.cost = 1.0f;
        this.radioactivity = 0.0f;
        this.flammability = 1.2f;
        this.charge = 0.0f;
        this.crystalEnergy = 0.2f;
        this.explosiveness = 0.0f;
      }
    };
    cuguijing = new CItemType("cuguijing", Color.valueOf("#E5F0E8")) {
      {
        this.hardness = 1;
        this.cost = 1.8f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 1.0f;
        this.explosiveness = 0.0f;
      }
    };
    chunguijing = new CItemType("chunguijing", Color.valueOf("#E5F0E8")) {
      {
        this.hardness = 1;
        this.cost = 5.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 3.0f;
        this.explosiveness = 0.0f;
      }
    };
    xi = new CItemType("xi", Color.valueOf("#00ffff")) {
      {
        this.hardness = 3;
        this.cost = 5.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.explosiveness = 0.0f;
      }
    };
    lvgang = new CItemType("lvgang", Color.valueOf("#a8c1eb")) {
      {
        this.hardness = 1;
        this.cost = 6.0f;
        this.radioactivity = 0.0f;
        this.flammability = 0.0f;
        this.charge = 0.0f;
        this.crystalEnergy = 2.5f;
        this.explosiveness = 0.0f;
      }
    };
    citems.addAll(yellowcopper, lv, li, tandanzhi, cuguijing, chunguijing, xi);
  }
}
