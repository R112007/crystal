package crystal.content.blocks;

import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawFade;
import mindustry.world.draw.DrawFlame;
import mindustry.world.draw.DrawLiquidRegion;
import mindustry.world.draw.DrawMulti;

import static crystal.content.CItems.*;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import crystal.content.CFx;
import crystal.world.blocks.production.CombinedCrafter;
import crystal.world.draw.DrawCombinedLiquid;

public class CFactories {
  public static Block guicuzhiji;
  public static Block cuzhiganguo;
  public static Block guitichunji;
  public static Block youjiboliji;
  public static Block lvgangf1;

  public static void load() {
    CFactories.guicuzhiji = new CombinedCrafter("guicuzhiji") {
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
    CFactories.cuzhiganguo = new CombinedCrafter("cuzhiganguo") {
      {
        this.health = 280;
        this.size = 3;
        this.craftTime = 70.0f;
        updateEffect = new Effect(40, e -> {
          Angles.randLenVectors(e.id, 5, 3f + e.fin() * 5f, (x, y) -> {
            Draw.color(Color.valueOf("#D1EFFFFF"), Color.gray, e.fin());
            Fill.circle(e.x + x, e.y + y, e.fout());
          });
        });
        craftEffect = new Effect(40, e -> {
          Angles.randLenVectors(e.id, 6, 5f + e.fin() * 8f, (x, y) -> {
            Draw.color(Color.valueOf("#D1EFFFFF"), Color.lightGray, e.fin());
            Fill.square(e.x + x, e.y + y, 0.2f + e.fout() * 2f, 45);
          });
        });
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
    CFactories.guitichunji = new CombinedCrafter("guitichunji") {
      {
        this.health = 210;
        this.size = 2;
        this.craftTime = 60.0f;
        this.itemCapacity = 10;
        this.hasItems = true;
        this.hasPower = true;
        this.hasLiquids = false;
        updateEffect = new Effect(40, e -> {
          Angles.randLenVectors(e.id, 5, 3f + e.fin() * 5f, (x, y) -> {
            Draw.color(Color.valueOf("#E8F7FFFF"), Color.gray, e.fin());
            Fill.circle(e.x + x, e.y + y, e.fout());
          });
        });
        craftEffect = new Effect(40, e -> {
          Angles.randLenVectors(e.id, 6, 5f + e.fin() * 8f, (x, y) -> {
            Draw.color(Color.valueOf("#E8F7FFFF"), Color.lightGray, e.fin());
            Fill.square(e.x + x, e.y + y, 0.2f + e.fout() * 2f, 45);
          });
        });
        this.requirements(Category.crafting,
            ItemStack.with(new Object[] { lv, 75, li, 80, cuguijing, 35, xi, 60 }));
        this.outputItem = new ItemStack(chunguijing, 1);
        this.consumeItems(ItemStack.with(cuguijing, 2, tandanzhi, 1));
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFade() {
          {
            suffix = "-f";
            scale = 7;
          }
        }, new DrawFlame());
        this.consumePower(2.0f);
      }
    };
    youjiboliji = new CombinedCrafter("youjiboliji") {
      {
        this.health = 150;
        this.size = 2;
        this.craftTime = 70.0f;
        this.itemCapacity = 10;
        this.hasItems = true;
        this.hasPower = true;
        this.hasLiquids = false;
        this.requirements(Category.crafting,
            ItemStack.with(new Object[] { lv, 50, li, 60, cuguijing, 45 }));
        this.outputItem = new ItemStack(boli, 1);
        this.consumeItems(ItemStack.with(li, 2));
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFlame());
        this.consumePower(1.5f);
      }
    };
    lvgangf1 = new CombinedCrafter("lvgangf1") {
      {
        this.health = 510;
        this.size = 3;
        this.craftTime = 80.0f;
        this.itemCapacity = 20;
        this.liquidCapacity = 30.0f;
        this.hasItems = true;
        this.hasPower = true;
        this.hasLiquids = true;
        this.requirements(Category.crafting, ItemStack.with(
            new Object[] { lv, 125, li, 70, cuguijing, 60, xi, 80 }));
        this.outputItem = new ItemStack(lvgang, 2);
        this.consumeItems(ItemStack.with(lv, 3, xi, 2));
        this.consumeLiquid(Liquids.water, 0.15f);
        this.drawer = new DrawMulti(new DrawDefault(), new DrawFlame(), new DrawCombinedLiquid());
        this.consumePower(2.0f);
        this.updateEffect = CFx.lvgangf1work;
      }
    };
  }
}
