package crystal.content.blocks;

import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawFlame;
import mindustry.world.draw.DrawMulti;

import static crystal.content.CItems.*;

public class CFactories {
  public static Block guicuzhiji;
  public static Block cuzhiganguo;
  public static Block guitichunji;

  public static void load() {
    CFactories.guicuzhiji = new GenericCrafter("guicuzhiji") {
      {
        this.health = 130;
        this.size = 2;
        this.craftTime = 30.0f;
        this.itemCapacity = 10;
        this.hasItems = true;
        this.hasPower = false;
        this.hasLiquids = false;
        this.requirements(Category.crafting, ItemStack.with(new Object[] { lv, 80, li, 65 }));
        this.outputItem = new ItemStack(cuguijing, 1);
        this.consumeItems(ItemStack.with(lv, 1, tandanzhi, 1));
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFlame());
      }
    };
    CFactories.cuzhiganguo = new GenericCrafter("cuzhiganguo") {
      {
        this.health = 280;
        this.size = 2;
        this.craftTime = 70.0f;
        this.itemCapacity = 20;
        this.hasItems = true;
        this.hasPower = true;
        this.hasLiquids = false;
        this.requirements(Category.crafting,
            ItemStack.with(new Object[] { lv, 80, li, 65, xi, 80, chunguijing, 30 }));
        this.outputItem = new ItemStack(cuguijing, 3);
        this.consumeItems(ItemStack.with(lv, 4, tandanzhi, 2));
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFlame());
        this.consumePower(1.5f);
      }
    };
    CFactories.guitichunji = new GenericCrafter("guitichunji") {
      {
        this.health = 210;
        this.size = 2;
        this.craftTime = 60.0f;
        this.itemCapacity = 10;
        this.hasItems = true;
        this.hasPower = true;
        this.hasLiquids = false;
        this.requirements(Category.crafting,
            ItemStack.with(new Object[] { lv, 75, li, 80, cuguijing, 35, xi, 60 }));
        this.outputItem = new ItemStack(chunguijing, 1);
        this.consumeItems(ItemStack.with(cuguijing, 2, tandanzhi, 1));
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFlame());
        this.consumePower(2.0f);
      }
    };
  }
}
