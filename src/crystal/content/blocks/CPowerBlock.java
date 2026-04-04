package crystal.content.blocks;

import arc.graphics.Color;
import crystal.graphics.CPal;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.power.Battery;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.consumers.ConsumeItemFlammable;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawLiquidRegion;
import mindustry.world.draw.DrawMulti;
import mindustry.world.draw.DrawPower;
import mindustry.world.draw.DrawRegion;
import mindustry.world.draw.DrawWarmupRegion;

import static crystal.content.CItems.*;

public class CPowerBlock {
  public static Block powernode1;
  public static Block powernode2;
  public static Block sun1;
  public static Block battery1;
  public static Block battery2;
  public static Block firepower1;
  public static Block firepower2;
  public static Block qilunji;

  public static void load() {
    CPowerBlock.powernode1 = new PowerNode("powernode1") {
      {
        this.size = 1;
        this.health = 80;
        this.laserColor1 = CPal.light_blue1;
        this.laserColor2 = CPal.dark_blue1;
        this.maxNodes = 7;
        this.armor = 1;
        this.laserRange = 8;
        this.hasPower = true;
        this.requirements(Category.power, ItemStack.with(new Object[] { lv, 2, li, 3 }));
      }
    };
    CPowerBlock.powernode2 = new PowerNode("powernode2") {
      {
        this.size = 2;
        this.health = 270;
        this.laserColor1 = CPal.light_blue1;
        this.laserColor2 = CPal.dark_blue1;
        this.maxNodes = 13;
        this.armor = 1;
        this.laserRange = 18;
        this.hasPower = true;
        this.requirements(Category.power, ItemStack.with(new Object[] { lv, 4, li, 5, xi, 4 }));
      }
    };
    CPowerBlock.battery1 = new Battery("battery1") {
      {
        this.size = 1;
        this.health = 60;
        this.drawer = new DrawMulti(new DrawDefault(), new DrawPower() {
          {
            this.emptyLightColor = Color.valueOf("#F8C266");
            this.fullLightColor = Color.valueOf("#FFFFFF");
          }
        }, new DrawRegion("-top"));
        this.consumePowerBuffered(3000f);
        this.requirements(Category.power, ItemStack.with(new Object[] { lv, 8, li, 20 }));
      }
    };
    CPowerBlock.battery2 = new Battery("battery2") {
      {
        this.size = 2;
        this.health = 240;
        this.drawer = new DrawMulti(new DrawDefault(), new DrawPower() {
          {
            this.emptyLightColor = Color.valueOf("#F8C266");
            this.fullLightColor = Color.valueOf("#FFFFFF");
          }
        }, new DrawRegion("-top"));
        this.consumePowerBuffered(20000f);
        this.requirements(Category.power,
            ItemStack.with(new Object[] { lv, 35, li, 20, cuguijing, 20, xi, 20 }));
      }
    };
    CPowerBlock.firepower1 = new ConsumeGenerator("firepower1") {
      {
        this.size = 1;
        this.health = 100;
        this.buildCostMultiplier = 0.9f;
        this.powerProduction = 1.5f;
        this.lightRadius = 2.0f;
        this.requirements(Category.power, ItemStack.with(new Object[] { lv, 20, li, 15 }));
        this.itemDuration = 120f;
        this.generateEffect = Fx.generatespark;
        this.consume(new ConsumeItemFlammable());
        this.drawer = new DrawMulti(new DrawDefault(), new DrawWarmupRegion());
      }
    };
    CPowerBlock.firepower2 = new ConsumeGenerator("firepower2") {
      {
        this.size = 1;
        this.health = 300;
        this.buildCostMultiplier = 0.75f;
        this.powerProduction = 4f;
        this.lightRadius = 3.0f;
        this.requirements(Category.power,
            ItemStack.with(new Object[] { lv, 40, li, 35, cuguijing, 25, xi, 20 }));
        this.itemDuration = 120f;
        this.generateEffect = Fx.generatespark;
        this.consume(new ConsumeItemFlammable());
        this.drawer = new DrawMulti(new DrawDefault(), new DrawWarmupRegion());
      }
    };
    CPowerBlock.qilunji = new ConsumeGenerator("qilunji") {
      {
        this.size = 2;
        this.health = 300;
        this.buildCostMultiplier = 0.9f;
        this.powerProduction = 7.5f;
        this.hasLiquids = true;
        this.lightRadius = 6.0f;
        this.liquidCapacity = 15f;
        this.drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-turbine", 2f), new DrawRegion("-turbine", 2f) {
          {
            this.rotation = 45f;
          }
        }, new DrawRegion("-cap"), new DrawLiquidRegion(Liquids.water));
        this.requirements(Category.power,
            ItemStack.with(new Object[] { lv, 35, li, 40, cuguijing, 65 }));
        this.itemDuration = 135f;
        this.generateEffect = Fx.generatespark;
        this.consume(new ConsumeItemFlammable());
        this.consumeLiquid(Liquids.water, 0.14666f);
      }
    };
  }
}
