package crystal.world.blocks.tumor;

import arc.graphics.g2d.Draw;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.tumor.TissueState;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorRender;
import crystal.world.tumor.TumorTissue;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/** A mineralized growth tip that drills only items present in the mineral-drop Seq. */
public class TumorDrill extends Block {
  public float drillTime = 105f;
  public int tier = 4;

  public TumorDrill(String name) {
    super(name);
    size = 1;
    health = 360;
    armor = 3f;
    solid = true;
    update = true;
    destructible = true;
    hasItems = true;
    itemCapacity = 20;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorDrillBuild extends Building implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public float progress;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      if (TumorNetwork.updateTissue(this, tissue) || net.client()) return;

      Item drop = tile == null ? null : tile.drop();
      TumorCore.TumorCoreBuild core = TumorNetwork.rootCore(this, tissue);
      if (drop != null && TumorNetwork.isMineral(tile) && drop.hardness <= tier && items.total() < itemCapacity) {
        progress += Time.delta * (1f + (TumorNetwork.levelOf(this, tissue) - 1) * 0.15f);
        if (progress >= drillTime) {
          progress -= drillTime;
          if (core != null && core.acceptItem(this, drop)) core.handleItem(this, drop);
          else items.add(drop, 1);
        }
      }

      if (core != null && items.total() > 0) {
        Item stored = items.first();
        if (core.acceptItem(this, stored)) {
          items.remove(stored, 1);
          core.handleItem(this, stored);
        }
      } else if (items.total() > 0) {
        dump(items.first());
      }
    }

    @Override
    public boolean acceptItem(Building source, Item item) {
      return false;
    }

    @Override
    public void draw() {
      float scale = tissue.visualScale();
      Draw.alpha(tissue.visualAlpha());
      TumorRender.draw(region, x, y, 0f, scale);
      Draw.reset();
    }

    @Override
    public byte version() {
      return 3;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      tissue.write(write);
      write.f(progress);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      progress = read.f();
    }
  }
}
