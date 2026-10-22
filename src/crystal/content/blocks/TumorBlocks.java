package crystal.content.blocks;

import crystal.world.blocks.tumor.TumorConveyor;
import crystal.world.blocks.tumor.TumorCore;
import crystal.world.blocks.tumor.TumorDrill;
import crystal.world.blocks.tumor.TumorMeristem;
import crystal.world.blocks.tumor.TumorNursery;
import crystal.world.blocks.tumor.TumorTurret;
import crystal.world.blocks.tumor.TumorWall;
import crystal.world.tumor.TumorNetwork;
import mindustry.content.Items;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.draw.DrawTurret;
import mindustry.world.meta.BuildVisibility;

import arc.struct.Seq;

import static mindustry.Vars.content;

/** Content registration for the autonomous hostile tumor. */
public final class TumorBlocks {
  public static Block core;
  public static Block meristem;
  public static Block conveyor;
  public static Block drill;
  public static Block turret;
  public static Block wall;
  public static Block nursery;

  private TumorBlocks() {}

  public static void load() {
    TumorNetwork.refreshMineralDrops();

    core = new TumorCore("tumor-core");
    meristem = new TumorMeristem("tumor-meristem");
    conveyor = new TumorConveyor("tumor-conveyor");
    drill = new TumorDrill("tumor-drill");
    wall = new TumorWall("tumor-wall");
    nursery = new TumorNursery("tumor-nursery");

    TumorTurret tumorTurret = new TumorTurret("tumor-turret");
    Seq<Item> ammoItems = new Seq<>();
    for (Item item : content.items()) {
      if (item.hardness <= 4) {
        ammoItems.add(item);
      }
    }
    if (ammoItems.isEmpty()) ammoItems.add(Items.copper);
    Object[] mappings = new Object[ammoItems.size * 2];
    for (int i = 0; i < ammoItems.size; i++) {
      mappings[i * 2] = ammoItems.get(i);
      mappings[i * 2 + 1] = projectile(ammoItems.get(i));
    }
    tumorTurret.ammo(mappings);
    tumorTurret.drawer = new DrawTurret("");
    turret = tumorTurret;

    hide(core);
    hide(meristem);
    hide(conveyor);
    hide(drill);
    hide(turret);
    hide(wall);
    hide(nursery);
  }

  private static void hide(Block block) {
    block.buildVisibility = BuildVisibility.sandboxOnly;
    block.alwaysUnlocked = true;
    block.requirements(Category.defense, BuildVisibility.sandboxOnly, ItemStack.with());
  }

  private static BasicBulletType projectile(Item item) {
    float hardness = Math.max(item.hardness, 1);
    return new BasicBulletType(5.3f + hardness * 0.25f, 11f + hardness * 4f) {
      {
        width = 4f;
        height = 8f;
        lifetime = 43f;
        frontColor = item.color;
        backColor = item.color.cpy().mul(0.58f);
        hitColor = item.color;
      }
    };
  }
}
