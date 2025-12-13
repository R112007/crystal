package crystal.content.blocks;

import crystal.content.CItems;
import crystal.content.CUnits;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

public class CStroage {
  public static Block core1, core0;

  public static void load() {
    core1 = new CoreBlock("core1") {
      {
        this.health = 4000;
        this.itemCapacity = 6000;
        this.size = 3;
        this.unitCapModifier = 8;
        this.alwaysUnlocked = true;
        this.unitType = CUnits.taichu;
        this.requirements(Category.effect, ItemStack.with(new Object[] { CItems.yellowcopper, 1200 }));
      }
    };
    core0 = new CoreBlock("core0") {
      {
        this.health = 10000;
        this.itemCapacity = 1000;
        this.size = 1;
        this.unitCapModifier = 0;
        this.alwaysUnlocked = true;
        this.unitType = UnitTypes.alpha;
        this.requirements(Category.effect, ItemStack.with(new Object[] {}));
        buildVisibility = BuildVisibility.sandboxOnly;
      }

      @Override
      public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        return true;
      }
    };
  }
}
