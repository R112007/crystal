package crystal.content.blocks;

import crystal.content.CItems;
import crystal.world.blocks.distribution.DropDrillItem;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.distribution.OverflowGate;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.distribution.Sorter;

public class CDistribution {
  public static Block lvconveyor;
  public static Block xiconveyor;
  public static Block lvlianjieqi;
  public static Block lvluyouqi;
  public static Block lvfenpeiqi;
  public static Block lvfenliuqi;
  public static Block lvbridge;
  public static Block xibridge;
  public static Block lvfenleiqi;
  public static Block lvfanxiangfenleiqi;
  public static Block lvyiliumeng;
  public static Block lvfanxiangyiliumeng;
  public static Block dropdrill1;
  public static Block massdriver1;

  public static void load() {
    CDistribution.lvconveyor = new Conveyor("lvconveyor") {
      {
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 1 }));
        this.armor = 2f;
        this.speed = 0.05f;
        this.displayedSpeed = 7f;
      }

      @Override
      public void init() {
        super.init();
        this.bridgeReplacement = CDistribution.lvbridge;
        this.junctionReplacement = CDistribution.lvlianjieqi;
      }

    };
    CDistribution.xiconveyor = new Conveyor("xiconveyor") {
      {
        this.size = 1;
        this.health = 70;
        this.requirements(Category.distribution,
            ItemStack.with(new Object[] { CItems.lv, 1, CItems.li, 1, CItems.xi, 1 }));
        this.armor = 2f;
        this.speed = 0.123f;
        this.displayedSpeed = 15f;
      }

      @Override
      public void init() {
        super.init();
        this.bridgeReplacement = CDistribution.xibridge;
        this.junctionReplacement = CDistribution.lvlianjieqi;
      }
    };
    CDistribution.lvlianjieqi = new Junction("lvlianjieqi") {
      {
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 2 }));
        this.armor = 2f;
        this.speed = 20f;
        this.itemCapacity = 24;
      }
    };
    CDistribution.lvluyouqi = new Router("lvluyouqi") {
      {
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 2 }));
        this.armor = 2f;
        this.speed = 1f;
      }
    };
    CDistribution.lvfenpeiqi = new Router("lvfenpeiqi") {
      {
        this.size = 2;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 8, CItems.li, 8 }));
        this.armor = 2f;
        this.speed = 1f;
      }
    };
    CDistribution.lvfenliuqi = new Router("lvfenliuqi") {
      {
        this.size = 3;
        this.health = 100;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 18, CItems.li, 18 }));
        this.armor = 2f;
        this.speed = 1f;
      }
    };
    CDistribution.lvbridge = new ItemBridge("lvbridge") {
      {
        this.size = 1;
        this.health = 80;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 4, CItems.li, 4 }));
        this.armor = 2f;
        this.transportTime = 5f;
        this.itemCapacity = 15;
        this.range = 5;
        this.arrowSpacing = 6f;
        this.hasPower = false;
      }
    };
    CDistribution.xibridge = new ItemBridge("xibridge") {
      {
        this.size = 1;
        this.health = 120;
        this.requirements(Category.distribution,
            ItemStack.with(new Object[] { CItems.xi, 6, CItems.cuguijing, 8 }));
        this.armor = 2f;
        this.transportTime = 2.8f;
        this.itemCapacity = 20;
        this.range = 7;
        this.arrowSpacing = 6f;
        this.hasPower = false;
      }
    };
    CDistribution.lvfenleiqi = new Sorter("lvfenleiqi") {
      {
        this.solid = false;
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 3, CItems.li, 3 }));
        this.armor = 2f;
      }
    };
    CDistribution.lvfanxiangfenleiqi = new Sorter("lvfanxiangfenleiqi") {
      {
        this.invert = true;
        this.solid = false;
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 3, CItems.li, 3 }));
        this.armor = 2f;
      }
    };
    CDistribution.lvyiliumeng = new OverflowGate("lvyiliumeng") {
      {
        this.solid = false;
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 3, CItems.li, 3 }));
        this.armor = 2f;
      }
    };
    CDistribution.lvfanxiangyiliumeng = new OverflowGate("lvfanxiangyiliumeng") {
      {
        this.solid = false;
        this.invert = true;
        this.size = 1;
        this.health = 40;
        this.requirements(Category.distribution, ItemStack.with(new Object[] { CItems.lv, 3, CItems.li, 3 }));
        this.armor = 2f;
      }
    };
    CDistribution.dropdrill1 = new DropDrillItem("dropdrill1") {
      {
        this.size = 2;
        this.hasPower = true;
        this.consumePower(7.5f);
        this.itemCapacity = 50;
        this.range = 72f;
        this.reload = 720f;
        this.requirements(Category.distribution,
            ItemStack.with(new Object[] { CItems.lv, 120, CItems.li, 150, CItems.cuguijing, 120 }));
      }
    };
    CDistribution.massdriver1 = new MassDriver("massdriver1") {
      {
        this.size = 1;
        this.itemCapacity = 30;
        this.range = 200;
        this.bulletSpeed = 8;
        this.consumePower(2f);
        this.requirements(Category.distribution,
            ItemStack.with(new Object[] { CItems.lv, 25, CItems.li, 40, CItems.cuguijing, 30, CItems.lvgang, 45 }));
      }
    };
  }
}
