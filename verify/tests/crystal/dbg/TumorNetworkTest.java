package crystal.dbg;

import arc.util.io.Reads;
import arc.util.io.Writes;
import arc.util.Time;
import crystal.content.CEnvironment;
import crystal.content.CItems;
import crystal.content.blocks.TumorBlocks;
import crystal.world.blocks.tumor.TumorCore.TumorCoreBuild;
import crystal.world.tumor.TissueState;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorTissue;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.GameState.State;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Building;
import mindustry.world.Tile;
import mindustry.world.Tiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Headless checks for the autonomous tumor's registration, path and necrosis state. */
public class TumorNetworkTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    try {
      checkContent();
      checkTopology();
      checkSerialization();
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }
    System.out.println("[TUMOR] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void checkContent() {
    check("core registered", TumorBlocks.core != null);
    check("core occupies a 3x3 game tile footprint", TumorBlocks.core.size == 3);
    check("mineral Seq populated", TumorNetwork.mineralDrops.contains(CItems.lv));
    check("turret accepts multiple minerals",
        ((mindustry.world.blocks.defense.turrets.ItemTurret) TumorBlocks.turret).ammoTypes.size > 2);
  }

  private static void checkTopology() {
    Vars.state.teams = new Teams();
    Vars.state.set(State.playing);
    Vars.state.rules.defaultTeam = Team.sharded;
    Vars.state.rules.waveTeam = Team.crux;

    World world = new World();
    Tiles tiles = new Tiles(32, 32);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 32; x++) {
        tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
      }
    }
    world.tiles = tiles;
    Vars.world = world;

    Vars.state.rules.infiniteResources = false;
    Vars.state.rules.editor = false;
    check("tumor blocks cannot be placed in normal play",
        !TumorBlocks.core.canPlaceOn(world.tile(2, 2), Team.sharded, 0));
    Vars.state.rules.editor = true;
    check("tumor blocks remain available in editor placement",
        TumorBlocks.core.canPlaceOn(world.tile(2, 2), Team.sharded, 0));
    Vars.state.rules.editor = false;

    Tile coreTile = world.tile(12, 16);
    coreTile.setBlock(TumorBlocks.core, Team.crux);
    TumorCoreBuild core = (TumorCoreBuild) coreTile.build;
    check("core changes to hostile crux team", core.team == Team.crux);
    check("core owns shared liquid storage", core.liquids != null);

    Tile alliedCoreTile = world.tile(22, 16);
    alliedCoreTile.setBlock(TumorBlocks.core, Team.sharded);
    TumorCoreBuild alliedCore = (TumorCoreBuild) alliedCoreTile.build;
    check("core respects its placement team", alliedCore.team == Team.sharded);
    float oldDelta = Time.delta;
    try {
      Time.delta = 1f;
      for (int i = 0; i < 260; i++) alliedCore.updateTile();
    } finally {
      Time.delta = oldDelta;
    }
    check("core survives autonomous growth without combat", !alliedCore.dead && alliedCore.health > 0f);

    check("meristem has no unit collision", !TumorBlocks.meristem.solid);

    Tile sand = world.tile(20, 20);
    sand.setFloor(Blocks.sand.asFloor());
    check("meristem can spread over buildable floors", TumorNetwork.canSpreadOnto(sand));
    Tile deepWater = world.tile(21, 20);
    deepWater.setFloor(Blocks.deepwater.asFloor());
    check("non-buildable deep floors stop growth", !TumorNetwork.canSpreadOnto(deepWater));
    check("environmental blocks stop growth", !TumorNetwork.canSpreadOnto(coreTile));

    Tile blockedNurseryAnchor = world.tile(25, 20);
    world.tile(26, 20).setBlock(TumorBlocks.wall, Team.crux);
    check("multiblock placement checks full footprint",
        !TumorNetwork.canPlaceAt(TumorBlocks.nursery, blockedNurseryAnchor));

    TissueState wallState = new TissueState();
    setState(wallState, core, 1, 12, 16);
    Building buddingWall = TumorNetwork.place(TumorBlocks.wall, world.tile(5, 5), Team.crux, 0, wallState);
    core.growthCredits = 1f;
    Building bud = TumorNetwork.tryBud(buddingWall, wallState, 1);
    check("any tumor building can bud", bud instanceof TumorTissue);
    check("new growth starts immature",
        bud != null && !((TumorTissue) bud).tumor().mature);
    Time.delta = 181f;
    TumorNetwork.updateTissue(bud, ((TumorTissue) bud).tumor());
    check("immature growth needs time to differentiate",
        ((TumorTissue) bud).tumor().mature);
    bud.tile.setOverlay(CEnvironment.oreLv);
    bud.updateTile();
    check("mature mineral growth becomes a real drill", bud.tile.block() == TumorBlocks.drill);
    Time.delta = 1f;

    Tile enclosed = world.tile(29, 29);
    TissueState enclosedState = new TissueState();
    setState(enclosedState, core, 1, 12, 16);
    Building enclosedWall = TumorNetwork.place(TumorBlocks.wall, enclosed, Team.crux, 0, enclosedState);
    world.tile(28, 29).setBlock(TumorBlocks.wall, Team.crux);
    world.tile(30, 29).setBlock(TumorBlocks.wall, Team.crux);
    world.tile(29, 28).setBlock(TumorBlocks.wall, Team.crux);
    world.tile(29, 30).setBlock(TumorBlocks.wall, Team.crux);
    core.growthCredits = 1f;
    check("blocked growth does not consume credit",
        TumorNetwork.tryBud(enclosedWall, enclosedState, 1) == null && core.growthCredits == 1f);

    placeTissue(TumorBlocks.meristem, world.tile(14, 16), core, 1, 12, 16);
    placeTissue(TumorBlocks.meristem, world.tile(15, 16), core, 2, 14, 16);
    placeTissue(TumorBlocks.meristem, world.tile(16, 16), core, 3, 15, 16);
    Building firstPathBuild = world.tile(14, 16).build;
    Building secondPathBuild = world.tile(15, 16).build;
    world.tile(16, 16).setOverlay(CEnvironment.oreLv);
    Tile drillTile = world.tile(16, 16);
    drillTile.setBlock(TumorBlocks.drill, Team.crux, 0);
    TissueState drillState = ((TumorTissue) drillTile.build).tumor();
    setState(drillState, core, 3, 15, 16);

    TumorNetwork.connectPathToCore(drillTile, Team.crux);
    check("path conversion keeps the first building alive", world.tile(14, 16).build == firstPathBuild);
    check("path conversion keeps the second building alive", world.tile(15, 16).build == secondPathBuild);
    check("first ancestry node enters transport state", ((TumorTissue) firstPathBuild).tumor().transport);
    check("second ancestry node enters transport state", ((TumorTissue) secondPathBuild).tumor().transport);
    check("rootward path points west", firstPathBuild.rotation == 2);
    check("inner path points west", secondPathBuild.rotation == 2);

    world.tile(15, 16).setBlock(Blocks.air);
    drillState.linkTimer = 15f;
    TumorNetwork.updateTissue(drillTile.build, drillState);
    check("breaking the ancestry starts distal withering", drillState.withering);

    TumorNetwork.collapseFromCore(core);
    TissueState rootward = ((TumorTissue) world.tile(14, 16).build).tumor();
    check("core death withers inner ring first", rootward.withering && rootward.witherElapsed < drillState.witherElapsed);
  }

  private static void placeTissue(
      mindustry.world.Block block, Tile tile, TumorCoreBuild core, int depth, int parentX, int parentY) {
    TissueState state = new TissueState();
    state.coreX = core.tileX();
    state.coreY = core.tileY();
    state.parentX = parentX;
    state.parentY = parentY;
    state.depth = depth;
    tile.setBlock(block, Team.crux);
    ((TumorTissue) tile.build).tumor().copyFrom(state);
  }

  private static void setState(TissueState state, TumorCoreBuild core, int depth, int parentX, int parentY) {
    state.coreX = core.tileX();
    state.coreY = core.tileY();
    state.parentX = parentX;
    state.parentY = parentY;
    state.depth = depth;
  }

  private static void checkSerialization() {
    try {
      TissueState before = new TissueState();
      before.coreX = 11;
      before.coreY = 22;
      before.parentX = 10;
      before.parentY = 21;
      before.depth = 4;
      before.defended = true;
      before.transport = true;
      before.withering = true;
      before.witherElapsed = 37.5f;
      before.linkTimer = 8.25f;
      before.mature = false;
      before.growthTimer = 12.25f;
      before.maturation = 55.5f;

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      Writes writes = new Writes(new DataOutputStream(bytes));
      before.write(writes);
      writes.close();

      TissueState after = new TissueState();
      Reads reads = new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
      after.read(reads, (byte) 3);
      reads.close();

      check("topology round-trips", after.coreX == 11 && after.parentY == 21 && after.depth == 4);
      check("wither state round-trips", after.withering && after.witherElapsed == 37.5f);
      check("transport state round-trips", after.transport);
      check("maturation state round-trips", !after.mature && after.maturation == 55.5f);
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }
  }

  private static void check(String label, boolean pass) {
    ok &= pass;
    System.out.println("[TUMOR] " + label + (pass ? " OK" : " FAIL"));
  }
}
