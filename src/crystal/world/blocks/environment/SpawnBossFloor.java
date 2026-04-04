package crystal.world.blocks.environment;

import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Rand;
import arc.math.geom.Position;
import arc.struct.Seq;
import arc.util.Log;
import crystal.game.CEventType.MapChangeEvent;
import mindustry.maps.Map;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import static mindustry.Vars.*;

public class SpawnBossFloor extends Floor implements Position {
  public static final Rand rand = new Rand();
  public float x, y;
  public int seq = 0;

  public SpawnBossFloor(String name) {
    super(name);
  }

  @Override
  public float getX() {
    return x;
  }

  @Override
  public float getY() {
    return y;
  }

  @Override
  public void drawMain(Tile tile) {
    x = tile.drawx();
    y = tile.drawy();
    for (int i = 0; i < 3; i++) {
      variantRegions[i] = tile.nearby(i).floor().variantRegions[rand.random(0,
          tile.nearby(i).floor().variantRegions.length - 1)];
    } /*
       * TextureRegion tex = variantRegions[rand.random(0, 3)];
       * // 注意这个东西不能相邻放
       * Draw.rect(tex, tile.worldx(), tile.worldy());
       * Draw.alpha(1f);
       */
    super.drawMain(tile);
  }

  public static Seq<Tile> getFloors(Map map, int seq) {
    int mapx = map.width;
    int mapy = map.height;
    Seq<Tile> tiles = new Seq<>();
    for (int x = 1; x <= mapx; x++) {
      for (int y = 1; y <= mapy; y++) {
        if (world.floor(x, y) instanceof SpawnBossFloor boss) {
          if (boss.seq == seq) {
            tiles.add(world.tile(x, y));
            // Log.info(x + " " + y);
          }
        }
      }
    }
    return tiles;
  }
}
