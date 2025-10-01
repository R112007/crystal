package crystal;

import crystal.entities.abilities.AddWeaponAbility;
import crystal.world.blocks.liquid.LiquidRangeBridge;
import crystal.world.blocks.payloads.A;
import crystal.world.blocks.payloads.UnitLanuchPad;
import crystal.world.blocks.payloads.UnitReceivePad;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.gen.MechUnit;
import mindustry.gen.UnitEntity;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class Test {
  public static Block t;
  public static Block a1;
  public static Block a2;
  public static Block a3;
  public static Block a4;
  public static Block a5;
  public static UnitType a6;

  public static void load() {
    t = new LiquidRangeBridge("t") {
      {
        range = 35;
        liquidCapacity = 30;
        health = 300;
        hasPower = true;
        consumePower(2);
        size = 3;
        buildVisibility = BuildVisibility.shown;
        category = Category.liquid;
        requirements = ItemStack.with(Items.copper, 2);
        alwaysUnlocked = true;
      }
    };
    a1 = new A("a1") {
      {
        u = UnitTypes.dagger;
        size = 3;
        buildVisibility = BuildVisibility.shown;
        requirements = ItemStack.with(Items.copper, 2);
        category = Category.liquid;
        alwaysUnlocked = true;
      }
    };
    a2 = new A("a2") {
      {
        u = UnitTypes.mace;
        buildVisibility = BuildVisibility.shown;
        size = 3;
        requirements = ItemStack.with(Items.copper, 2);
        category = Category.liquid;
        alwaysUnlocked = true;
      }
    };
    a3 = new A("a3") {
      {
        u = UnitTypes.fortress;
        requirements = ItemStack.with(Items.copper, 2);
        buildVisibility = BuildVisibility.shown;
        alwaysUnlocked = true;
        category = Category.liquid;
        size = 3;
      }
    };
    a4 = new UnitLanuchPad("a4") {
      {
        size = 3;
        this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
        this.alwaysUnlocked = true;
      }
    };
    a5 = new UnitReceivePad("a5") {
      {
        size = 3;
        this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
        this.alwaysUnlocked = true;
      }
    };
    a6 = new UnitType("a6") {
      {
        speed = 0.5f;
        hitSize = 8f;
        health = 150;
        constructor = UnitEntity::create;
        this.abilities.add(new AddWeaponAbility(UnitTypes.dagger.weapons.get(0), 300, 80, 480, 0));
      }
    };
  }
}
