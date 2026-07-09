package crystal.content.blocks;

import crystal.world.blocks.magic.NatureMagicGenerater;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import static crystal.content.CItems.*;

public class MagicBlocks {
  public static Block magicgener1;

  public static void load() {
    magicgener1 = new NatureMagicGenerater("magicgener1") {
      {
        health = 300;
        size = 2;
        requirements(Category.effect, ItemStack.with(new Object[] { lv, 100, li, 150,
            cuguijing, 180 }));
        reload = 200;
        amount = 0.1f;
        maxBlocks = 10;
      }
    };
  }
}
