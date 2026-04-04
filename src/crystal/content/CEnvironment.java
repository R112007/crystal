package crystal.content;

import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.meta.Attribute;

public class CEnvironment {
  public static Block oreLv;
  public static Block oreLi;
  public static Block oreTandanzhi;
  public static Block oreXi;
  public static Block heisha;
  public static Block sha;

  public static void load() {
    oreLv = new OreBlock("ore-lv", CItems.lv) {
      {
        oreDefault = true;
        oreThreshold = 0.81f;
        oreScale = 23.47619f;
      }
    };
    oreLi = new OreBlock("ore-li", CItems.li) {
      {
        oreDefault = true;
        oreThreshold = 0.81f;
        oreScale = 23.47619f;
      }
    };
    oreTandanzhi = new OreBlock("ore-tandanzhi", CItems.tandanzhi) {
      {
        oreDefault = true;
        oreThreshold = 0.81f;
        oreScale = 24.655f;
      }
    };
    oreXi = new OreBlock("ore-xi", CItems.xi) {
      {
        oreDefault = true;
        oreThreshold = 0.81f;
        oreScale = 24.655f;
      }
    };
    sha = new Floor("sha") {
      {
        this.speedMultiplier = 1f;
        this.variants = 3;
        this.dragMultiplier = 0f;
        this.damageTaken = 0f;
        this.hasShadow = true;
        this.attributes.set(Attribute.oil, 0.7f);
      }
    };
    heisha = new Floor("heisha") {
      {
        this.speedMultiplier = 1f;
        this.variants = 3;
        this.dragMultiplier = 0f;
        this.damageTaken = 0f;
        this.hasShadow = true;
        this.attributes.set(Attribute.oil, 1.4f);
      }
    };
  }
}
