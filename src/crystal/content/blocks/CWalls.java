package crystal.content.blocks;

import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;

import static crystal.content.CItems.*;

public class CWalls {
  public static Block lvwall1;
  public static Block lvwall2;
  public static Block lvwall3;
  public static Block lvwall4;
  public static Block xiwall1;
  public static Block xiwall2;
  public static Block xiwall3;
  public static Block xiwall4;

  public static void load() {
    CWalls.lvwall1 = new Wall("lvwall1") {
      {
        this.size = 1;
        this.health = 500;
        this.armor = 3;
        this.requirements(Category.defense, ItemStack.with(new Object[] { lv, 6 }));
      }
    };
    CWalls.lvwall2 = new Wall("lvwall2") {
      {
        this.size = 2;
        this.health = 2000;
        this.armor = 3;
        this.requirements(Category.defense, ItemStack.with(new Object[] { lv, 24 }));
      }
    };
    CWalls.lvwall3 = new Wall("lvwall3") {
      {
        this.size = 3;
        this.health = 4500;
        this.armor = 3;
        this.requirements(Category.defense, ItemStack.with(new Object[] { lv, 48 }));
      }
    };
    CWalls.lvwall4 = new Wall("lvwall4") {
      {
        this.size = 4;
        this.health = 5000;
        this.armor = 6;
        this.placeableLiquid = true;
        this.requirements(Category.defense, ItemStack.with(new Object[] { lv, 60 }));
      }
    };
    CWalls.xiwall1 = new Wall("xiwall1") {
      {
        this.size = 1;
        this.health = 800;
        this.armor = 4;
        this.requirements(Category.defense, ItemStack.with(new Object[] { xi, 6 }));
      }
    };
    CWalls.xiwall2 = new Wall("xiwall2") {
      {
        this.size = 2;
        this.health = 800 * 4;
        this.armor = 4;
        this.requirements(Category.defense, ItemStack.with(new Object[] { xi, 24 }));
      }
    };
    CWalls.xiwall3 = new Wall("xiwall3") {
      {
        this.size = 3;
        this.health = 800 * 8;
        this.armor = 4;
        this.requirements(Category.defense, ItemStack.with(new Object[] { xi, 48 }));
      }
    };
    CWalls.xiwall4 = new Wall("xiwall4") {
      {
        this.size = 4;
        this.health = 800 * 10;
        this.placeableLiquid = true;
        this.armor = 8;
        this.requirements(Category.defense, ItemStack.with(new Object[] { xi, 60 }));
      }
    };
  }
}
