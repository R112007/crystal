package crystal.dbg;

import arc.Core;
import arc.util.Time;
import crystal.content.CPlanets;
import crystal.content.LxMaps;
import crystal.content.blocks.CStroage;
import crystal.gen.Corec;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.core.GameState.State;
import mindustry.core.World;
import mindustry.game.SectorInfo;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.game.Universe;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

/** Regression test for foreground item intake driving unloaded-sector production. */
public class MobileCoreProductionTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    try {
      checkBackgroundProduction();
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }
    System.out.println("[MOBILE-PRODUCTION] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void checkBackgroundProduction() {
    Vars.state.teams = new Teams();
    Vars.state.set(State.playing);
    Vars.state.gameOver = false;
    Vars.state.rules.defaultTeam = Team.sharded;
    Vars.state.rules.waveTeam = Team.crux;

    Sector sector = LxMaps.jianglindian.sector;
    Vars.state.rules.sector = sector;
    sector.info = new SectorInfo();
    sector.info.hasCore = true;
    sector.info.items = new ItemSeq();
    sector.info.storageCapacity = CStroage.core1.storageCapacity();
    sector.info.waves = false;
    sector.info.attack = false;

    Vars.world = new World();
    Tiles tiles = new Tiles(32, 32);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 32; x++) {
        tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
      }
    }
    Vars.world.tiles = tiles;

    Tile tile = Vars.world.tile(16, 16);
    tile.setBlock(CStroage.core1Block, Team.sharded);
    CoreBuild source = (CoreBuild) tile.build;
    Corec core = MobileCoreControlSystem.convertCore(source, CStroage.core1);
    check("mobile core converts from a physical core", core != null);
    if (core == null) {
      return;
    }

    Item item = Items.copper;
    int produced = 20;
    sector.info.handleProduction(item, produced);
    for (int i = 0; i < produced; i++) {
      core.handleItem(null, item);
    }

    // SectorInfo samples core deltas on its refresh interval. Advance the
    // shared clock if the interval was initialized during this test frame.
    sector.info.update();
    for (int i = 0; i < 2 && sector.info.production.get(item) == null; i++) {
      Time.time += 61f;
      sector.info.update();
    }

    SectorInfo.ExportStat stat = sector.info.production.get(item);
    check("mobile-core intake is counted as production", stat != null && stat.mean > 0f);
    check("mobile-core inventory receives conveyor items", produced, core.items().get(item));

    sector.save = createSaveSlotStub();
    sector.info.prepare(sector);
    check("leaving captures the mobile-core inventory", produced, sector.info.items.get(item));
    Vars.state.set(State.menu);

    int before = sector.info.items.get(item);
    new Universe().runTurn();
    int after = sector.info.items.get(item);
    check("unloaded mobile-core sector gains background items", after > before);
  }

  private static mindustry.game.Saves.SaveSlot createSaveSlotStub() {
    try {
      Class<?> slotType = Class.forName("mindustry.game.Saves$SaveSlot");
      var ctor = slotType.getDeclaredConstructor(Class.forName("mindustry.game.Saves"),
          arc.files.Fi.class);
      ctor.setAccessible(true);
      return (mindustry.game.Saves.SaveSlot) ctor.newInstance(null,
          Core.files.local("mobile-core-production-probe"));
    } catch (Throwable t) {
      throw new RuntimeException("cannot create save-slot probe", t);
    }
  }

  private static void check(String label, boolean actual) {
    ok &= actual;
    System.out.println("[MOBILE-PRODUCTION] " + label + ": " + actual + (actual ? " OK" : " FAIL"));
  }

  private static void check(String label, int expected, int actual) {
    boolean pass = expected == actual;
    ok &= pass;
    System.out.println("[MOBILE-PRODUCTION] " + label + ": " + actual + "/" + expected
        + (pass ? " OK" : " FAIL"));
  }
}
