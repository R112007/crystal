package crystal.world.blocks.stroage;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import crystal.world.blocks.stroage.MoveBlock.MoveBlockBuild;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.game.EventType.TapEvent;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class MoveBlockSystem {

  private static float lastTapX, lastTapY;
  private static long lastTapTime = 0;
  private static final long DOUBLE_TAP_THRESHOLD = 400;
  public static Seq<String> names = new Seq<>();
  public static ObjectMap<String, Block> map = new ObjectMap<>();

  public static void init() {
    Events.on(TapEvent.class, e -> {
      long now = Time.millis();
      if (now - lastTapTime < DOUBLE_TAP_THRESHOLD
          && Math.abs(e.tile.worldx() - lastTapX) < tilesize * 2
          && Math.abs(e.tile.worldy() - lastTapY) < tilesize * 2) {
        Unit unit = Units.closest(e.player.team(), e.tile.worldx(), e.tile.worldy(), 8f, u -> !u.dead);

        Building build = e.tile.build;

        if (build != null && build instanceof MoveBlockBuild b) {
          Log.info("双击建筑");
          b.removeButToUnit();
        }
        if (unit != null) {
          Log.info("双击单位");
          String str = unit.type.name;
          Log.info("str " + str);
          Log.info("开始names");
          for (var s : names) {
            Log.info(s);
          }
          Log.info("开始map");
          for (var s : map.keys()) {
            Log.info(s);
          }
          if (names.contains(str)) {
            Tile tile = unit.tileOn();
            Team t = unit.team;
            Timer.schedule(() -> {
              tile.setBlock(map.get(str), t);
            }, 0.2f);
            unit.kill();
          }
        }
      }
      lastTapX = e.tile.worldx();
      lastTapY = e.tile.worldy();
      lastTapTime = now;
    });
  }
}
