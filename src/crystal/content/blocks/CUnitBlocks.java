package crystal.content.blocks;

import crystal.content.CUnits;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.units.UnitFactory;

import static crystal.content.CItems.*;

public class CUnitBlocks {
  public static UnitFactory unitfactory1, unitfactory2;

  public static void load() {
    unitfactory1 = new UnitFactory("unitfactory1") {
      {
        this.size = 3;
        this.health = 450;
        this.consumePower(2.5f);
        this.plans.add(
            new UnitFactory.UnitPlan(CUnits.chujia1, 60f * 30, ItemStack.with(li, 30, cuguijing, 30)));
        this.requirements(Category.units,
            ItemStack.with(new Object[] { lv, 90, li, 70, cuguijing, 80 }));
      }
    };
    unitfactory2 = new UnitFactory("unitfactory2") {
      {
        this.size = 3;
        this.health = 350;
        this.consumePower(1.5f);
        this.plans.add(
            new UnitFactory.UnitPlan(CUnits.liekong1, 60f * 18, ItemStack.with(lv, 15, cuguijing, 15)));
        this.requirements(Category.units, ItemStack.with(new Object[] { lv, 60, li, 70 }));
      }
    };
  }
}
