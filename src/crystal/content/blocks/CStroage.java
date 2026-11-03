package crystal.content.blocks;

import arc.Core;
import crystal.content.CItems;
import crystal.content.CUnits;
import crystal.gen.MagicUnit;
import crystal.gen.MechCoreUnit;
import crystal.type.CoreUnitType;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.io.SaveFileReader;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

public class CStroage {
  public static CoreUnitType core1;
  public static CoreBlock core1Block, core0;

  public static void load() {
    // core1 used to be a CoreBlock. Keep bundled maps and existing saves loadable
    // after it became the mobile-core UnitType.
    SaveFileReader.fallback.put("crystal-core1", "crystal-core1Base");
    core1 = new CoreUnitType("core1") {
      {
        this.constructor = MechCoreUnit::create;
        this.flying = false;
        this.itemInputRequiresDeployment = false;
        this.hitSize = 8;
        this.health = 4000;
        this.mineRange = 150;
        this.buildRange = 150;
        this.mineSpeed = 5;
        this.mineTier = 1;
        this.buildSpeed = 2;
        this.itemCapacity = 6000;
        this.storageCapacity = 6000;
        this.unitCapBonus = 8;
        this.alwaysUnlocked = true;
        this.unit = CUnits.taichu;
        this.core.size = 3;
        this.requirements = ItemStack.with(new Object[] { CItems.lv, 1500, CItems.li, 1200 });
      }

      @Override
      public void load() {
        super.load();
        // core1 is a unit now; reuse the old block artwork for its body and icon.
        var region = Core.atlas.find("crystal-core1");
        if (region.found()) {
          this.region = region;
          this.fullIcon = region;
          this.uiIcon = region;
        }
      }
    };
    core1Block = new CoreBlock("core1Base") {
      {
        this.health = 4000;
        this.itemCapacity = 6000;
        this.size = 3;
        this.unitCapModifier = 8;
        this.alwaysUnlocked = true;
        this.unitType = CUnits.taichu;
        this.requirements(Category.effect, ItemStack.with(new Object[] { CItems.lv, 1500, CItems.li, 1200 }));
        buildVisibility = BuildVisibility.sandboxOnly;
      }

      @Override
      public void load() {
        super.load();
        var region = Core.atlas.find("crystal-core1", this.region);
        this.region = region;
        this.fullIcon = region;
        this.uiIcon = region;

        var team = Core.atlas.find("crystal-core1-team", this.teamRegion);
        if (team.found()) {
          this.teamRegion = team;
          for (Team t : Team.all) {
            this.teamRegions[t.id] = Core.atlas.find("crystal-core1-team-" + t.name, team);
          }
        }
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
