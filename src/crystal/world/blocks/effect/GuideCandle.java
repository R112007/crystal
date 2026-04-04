package crystal.world.blocks.effect;

import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Geometry;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import crystal.Crystal;
import crystal.entities.effect.DirectionalSpreadParticleEffect;
import crystal.game.CEventType.MapChangeEvent;
import crystal.graphics.CPal;
import crystal.util.CTmp;
import crystal.world.blocks.environment.SpawnBossFloor;
import crystal.world.meta.CStat;
import mindustry.content.Items;
import mindustry.entities.effect.WaveEffect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.maps.Map;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class GuideCandle extends Block {
  public int maxBlocks = 3;
  public int seq = 0;
  public DirectionalSpreadParticleEffect directEffect = new DirectionalSpreadParticleEffect() {
    {
      this.colorFrom = CPal.blue2;
      this.colorTo = Color.white;
      this.particleCount = 1;
      this.moveDistance = 80;
      this.sizeFrom = 1;
      this.sizeTo = 0;
      this.spreadAngle = 10;
      this.randLength = true;
    }
  };
  public WaveEffect pointEffect = new WaveEffect() {
    {
      this.lifetime = 30;
      this.sides = 4;
      this.rotation = 90;
      this.sizeFrom = 20;
      this.sizeTo = 0;
      this.colorFrom = CPal.blue1;
    }
  };
  public Seq<Tile> floors = new Seq<>();
  public static Seq<GuideCandleBuild> entities = new Seq<>();

  public GuideCandle(String name) {
    super(name);
    solid = true;
    update = true;
    // buildVisibility = CBuildVisibility.leadFindFloor;
    requirements(Category.effect, ItemStack.with(new Object[] { Items.copper, 1 }));
    Events.on(MapChangeEvent.class, e -> {
      floors = SpawnBossFloor.getFloors(e.map, seq);
      entities.clear();
      entities = getGuides(e.map, this.seq);

    });
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.maxBlock, this.maxBlocks);
  }

  public static Seq<GuideCandleBuild> getGuides(Map map, int seq) {
    int mapx = map.width;
    int mapy = map.height;
    Seq<GuideCandleBuild> builds = new Seq<>();
    for (int x = 1; x <= mapx; x++) {
      for (int y = 1; y <= mapy; y++) {
        if (world.build(x, y) instanceof GuideCandleBuild candle) {
          if (((GuideCandle) candle.block).seq == seq) {
            builds.add(candle);
          }
        }
      }
    }
    return builds;
  }

  @Override
  public boolean canPlaceOn(Tile tile, Team team, int rotation) {
    return entities.size < maxBlocks;
  }

  public class GuideCandleBuild extends Building {
    @Override
    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      if (entities.size < maxBlocks)
        entities.add((GuideCandleBuild) super.init(tile, team, shouldAdd, rotation));
      else {
        Timer.schedule(() -> {
          this.killed();
        }, 0.2f);
      }
      return super.init(tile, team, shouldAdd, rotation);
    }

    public Tile closestFloor() {
      return Geometry.findClosest(x(), y(), floors);
    }

    @Override
    public void updateTile() {

      if (closestFloor() != null) {
        if (CTmp.v1.set(x, y).dst(closestFloor().drawx(), closestFloor().drawy()) <= 30) {
          if (Crystal.timer % 30 == 0)
            pointEffect.at(closestFloor().drawx(), closestFloor().drawy());
        } else if (Crystal.timer % 4.5f == 0) {
          directEffect.trigger(x(), y(), closestFloor().drawx(), closestFloor().drawy());
        }
      } else {

      }
    }

    @Override
    public void remove() {
      super.remove();
      entities.remove(this);
    }

    @Override
    public void drawSelect() {
      if (closestFloor() == null) {
        ui.showLabel("咦，好像没有特殊地板呢", id, 0.05f, x, y);
      }
    }
  }
}
