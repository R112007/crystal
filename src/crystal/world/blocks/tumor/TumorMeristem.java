package crystal.world.blocks.tumor;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
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

/** A mutable growth tip. Multiple tips may bud from the same tendril. */
public class TumorMeristem extends Block {
  public TextureRegion conduitRegion;
  public TextureRegion drillRegion;
  public float drillTime = 105f;

  public TumorMeristem(String name) {
    super(name);
    size = 1;
    health = 260;
    armor = 2f;
    solid = false;
    update = true;
    destructible = true;
    hasItems = true;
    itemCapacity = 3;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public void load() {
    super.load();
    conduitRegion = Core.atlas.find("tumor-conveyor", region);
    drillRegion = Core.atlas.find("tumor-drill", region);
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorMeristemBuild extends Building implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public Item carried;
    public float progress;
    public float drillProgress;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      if (TumorNetwork.updateTissue(this, tissue) || net.client()) return;
      if (!tissue.mature) return;

      if (TumorNetwork.isMineral(tile)) {
        TumorNetwork.differentiateToDrill(this, tissue);
        return;
      }
      if (!tissue.transport) return;

      if (carried == null) {
        if (items.total() > 0) {
          carried = items.first();
          items.remove(carried, 1);
          progress = 0f;
        }
        return;
      }

      progress += 0.055f * Time.delta;
      if (progress < 1f) return;

      Building next = front();
      if (next != null && !next.dead && next.acceptItem(this, carried)) {
        next.handleItem(this, carried);
        carried = null;
        progress = 0f;
      } else {
        progress = 0.96f;
      }
    }

    public Building front() {
      if (tile == null) return null;
      var frontTile = tile.nearby(rotation);
      return frontTile == null ? null : frontTile.build;
    }

    @Override
    public boolean acceptItem(Building source, Item item) {
      return tissue.transport && items.total() < itemCapacity;
    }

    @Override
    public int getMaximumAccepted(Item item) {
      return tissue.transport ? Math.max(0, itemCapacity - items.total()) : 0;
    }

    @Override
    public void handleItem(Building source, Item item) {
      if (tissue.transport && items.total() < itemCapacity) items.add(item, 1);
    }

    @Override
    public void draw() {
      float scale = tissue.visualScale();
      if (!tissue.mature) {
        scale *= 0.24f + 0.76f * Math.min(1f, tissue.maturation / TissueState.maturationTime);
      }
      TextureRegion visual = tissue.transport ? conduitRegion : region;
      float angle = tissue.transport ? rotation * 90f : 0f;
      Draw.alpha(tissue.visualAlpha());
      TumorRender.draw(visual, x, y, angle, scale);
      if (tissue.transport && carried != null) {
        Draw.rect(carried.fullIcon, x, y);
      }
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
      write.i(carried == null ? -1 : carried.id);
      write.f(progress);
      write.f(drillProgress);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      if (revision >= 2) {
        int itemId = read.i();
        carried = itemId < 0 ? null : content.item(itemId);
        progress = read.f();
      }
      if (revision >= 3) drillProgress = read.f();
    }
  }
}
