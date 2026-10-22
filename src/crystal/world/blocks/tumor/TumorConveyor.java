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

/** Living one-way conveyors used by every established drainage route. */
public class TumorConveyor extends Block {
  public float speed = 0.055f;

  public TumorConveyor(String name) {
    super(name);
    size = 1;
    health = 150;
    armor = 1f;
    solid = false;
    underBullets = true;
    update = true;
    destructible = true;
    rotate = true;
    hasItems = true;
    itemCapacity = 3;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorConveyorBuild extends Building implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public Item carried;
    public float progress;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      if (TumorNetwork.updateTissue(this, tissue)) return;
      if (net.client()) return;

      if (carried == null) {
        if (items.total() > 0) {
          carried = items.first();
          items.remove(carried, 1);
          progress = 0f;
        }
        return;
      }

      progress += speed * Time.delta;
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
      return items.total() < itemCapacity;
    }

    @Override
    public int getMaximumAccepted(Item item) {
      return Math.max(0, itemCapacity - items.total() - (carried == null ? 0 : 1));
    }

    @Override
    public void handleItem(Building source, Item item) {
      if (carried != null || items.total() >= itemCapacity) return;
      items.add(item, 1);
    }

    @Override
    public void draw() {
      float scale = tissue.visualScale();
      Draw.alpha(tissue.visualAlpha());
      TumorRender.draw(region, x, y, rotation * 90f, scale);
      if (carried != null) {
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
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      int itemId = read.i();
      carried = itemId < 0 ? null : content.item(itemId);
      progress = read.f();
    }
  }
}
