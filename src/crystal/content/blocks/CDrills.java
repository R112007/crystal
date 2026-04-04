package crystal.content.blocks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;

import static crystal.content.CItems.*;

public class CDrills {
  public static Block lvdrill;
  public static Block guijingdrill;

  public static void load() {
    CDrills.lvdrill = new Drill("lvdrill") {
      {
        this.size = 2;
        this.health = 200;
        this.drillTime = 500.0f;
        this.armor = 2;
        this.tier = 2;
        this.hasLiquids = true;
        this.liquidCapacity = 18.0f;
        this.drawMineItem = true;
        this.drillEffect = Fx.mine;
        this.updateEffect = Fx.lightningCharge;
        this.rotateSpeed = 2.0f;
        this.drawRim = false;
        this.heatColor = Color.valueOf("#71d5ff");
        this.warmupSpeed = 0.005f;
        this.liquidBoostIntensity = 1.85f;
        this.consumeLiquid(Liquids.water, 0.1f).boost().optional = true;
        this.requirements(Category.production, ItemStack.with(new Object[] { lv, 10 }));
      }
    };
    CDrills.guijingdrill = new Drill("guijingdrill") {
      {
        this.size = 2;
        this.health = 200;
        this.drillTime = 300.0f;
        this.armor = 2;
        this.tier = 3;
        this.hasLiquids = true;
        this.liquidCapacity = 18.0f;
        this.drawMineItem = true;
        this.drillEffect = Fx.mine;
        this.updateEffect = Fx.lightningCharge;
        this.rotateSpeed = 2.0f;
        this.drawRim = false;
        this.heatColor = Color.valueOf("#71d5ff");
        this.warmupSpeed = 0.005f;
        this.liquidBoostIntensity = 1.4f;
        this.consumeLiquid(Liquids.water, 0.1f).boost().optional = true;
        this.requirements(Category.production, ItemStack.with(new Object[] { lv, 25, cuguijing, 20 }));
      }
    };
  }
}
