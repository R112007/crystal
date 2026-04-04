package crystal.content.blocks;

import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.liquid.LiquidRouter;
import mindustry.world.blocks.production.Pump;

import static crystal.content.CItems.*;

public class CLiquidBlocks {
  public static Block beng1;
  public static Block lvdaoguan;
  public static Block xidaoguan;
  public static Block lvyetijiaochaqi;
  public static Block lvyetiluyouqi;
  public static Block lvdaoguanqiao;
  public static Block xidaoguanqiao;
  public static Block yetichuguan;

  public static void load() {
    CLiquidBlocks.beng1 = new Pump("beng1") {
      {
        this.size = 1;
        this.health = 300;
        this.liquidCapacity = 10;
        this.hasLiquids = true;
        this.pumpAmount = 0.16666666667f;
        this.requirements(Category.liquid, ItemStack.with(new Object[] { lv, 10, boli, 15 }));
      }
    };
    CLiquidBlocks.lvdaoguan = new Conduit("lvdaoguan") {
      {
        this.size = 1;
        this.health = 140;
        this.liquidCapacity = 10;
        this.hasLiquids = true;
        this.requirements(Category.liquid, ItemStack.with(new Object[] { lv, 1, boli, 2 }));
      }

      @Override
      public void init() {
        super.init();
        this.bridgeReplacement = CLiquidBlocks.lvdaoguanqiao;
        this.junctionReplacement = CLiquidBlocks.lvyetijiaochaqi;
      }
    };
    CLiquidBlocks.xidaoguan = new Conduit("xidaoguan") {
      {
        this.size = 1;
        this.health = 180;
        this.liquidCapacity = 20;
        this.hasLiquids = true;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { xi, 2, boli, 6, chunguijing, 2 }));
        this.liquidPressure = 5f;
      }

      @Override
      public void init() {
        super.init();
        this.bridgeReplacement = CLiquidBlocks.lvdaoguanqiao;
        this.junctionReplacement = CLiquidBlocks.xidaoguanqiao;
      }
    };
    CLiquidBlocks.lvyetijiaochaqi = new LiquidJunction("lvyetijiaochaqi") {
      {
        this.size = 1;
        this.health = 180;
        this.liquidCapacity = 20;
        this.hasLiquids = true;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { lv, 2, boli, 2 }));

      }
    };
    CLiquidBlocks.lvyetiluyouqi = new LiquidRouter("lvyetiluyouqi") {
      {
        this.size = 1;
        this.health = 180;
        this.liquidCapacity = 20;
        this.hasLiquids = true;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { lv, 8, boli, 4 }));
      }
    };
    CLiquidBlocks.lvdaoguanqiao = new LiquidBridge("lvdaoguanqiao") {
      {
        this.size = 1;
        this.health = 250;
        this.liquidCapacity = 20;
        this.hasLiquids = true;
        this.hasPower = false;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { lv, 12, boli, 9 }));
        this.range = 5;
      }
    };
    CLiquidBlocks.xidaoguanqiao = new LiquidBridge("xidaoguanqiao") {
      {
        this.size = 1;
        this.health = 430;
        this.liquidCapacity = 40;
        this.hasLiquids = true;
        this.hasPower = false;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { xi, 12, boli, 25, chunguijing, 10 }));
        this.range = 8;
      }
    };
    CLiquidBlocks.yetichuguan = new LiquidRouter("yetichuguan") {
      {
        this.size = 2;
        this.health = 450;
        this.liquidCapacity = 600;
        this.hasLiquids = true;
        this.requirements(Category.liquid,
            ItemStack.with(new Object[] { lv, 30, boli, 45 }));
      }
    };
  }
}
