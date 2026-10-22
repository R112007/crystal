package crystal.world.tumor;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import crystal.world.blocks.tumor.TumorCore.TumorCoreBuild;
import crystal.world.blocks.tumor.TumorMeristem.TumorMeristemBuild;
import crystal.world.blocks.tumor.TumorTurret.TumorTurretBuild;
import crystal.world.blocks.tumor.TumorWall.TumorWallBuild;
import mindustry.content.Blocks;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import crystal.content.blocks.TumorBlocks;

import static mindustry.Vars.*;

/** Runtime brain for tumor growth, evolution, logistics and necrosis. */
public final class TumorNetwork {
  public static final Seq<Item> mineralDrops = new Seq<>();

  private TumorNetwork() {}

  /**
   * Rebuilds the mineral whitelist from actual floor item drops. Keeping this as a Seq makes it easy
   * for later tumor variants to consume a different mineral set.
   */
  public static void refreshMineralDrops() {
    mineralDrops.clear();
    for (Block block : content.blocks()) {
      if (block.itemDrop != null && !mineralDrops.contains(block.itemDrop)) {
        mineralDrops.add(block.itemDrop);
      }
    }
  }

  public static boolean isMineral(Tile tile) {
    if (tile == null) return false;
    Item drop = tile.drop();
    if (drop == null) return false;
    if (mineralDrops.isEmpty()) refreshMineralDrops();
    return mineralDrops.contains(drop);
  }

  public static boolean isTissue(Building build) {
    return build instanceof TumorTissue;
  }

  public static TumorCoreBuild coreAt(int x, int y) {
    if (world == null) return null;
    if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) return null;
    Tile tile = world.tile(x, y);
    if (tile != null && tile.build instanceof TumorCoreBuild core) return core;
    return null;
  }

  public static TumorCoreBuild rootCore(Building owner, TissueState state) {
    if (state.coreX < 0 || state.coreY < 0) return null;
    TumorCoreBuild core = coreAt(state.coreX, state.coreY);
    if (core == null || core.dead || core.team != owner.team) return null;
    return core;
  }

  public static int levelOf(Building owner, TissueState state) {
    TumorCoreBuild core = rootCore(owner, state);
    return core == null ? 1 : core.level;
  }

  public static boolean isLinked(Building owner, TissueState state) {
    if (state.parentX < 0 || state.parentY < 0) return false;
    if (world == null) return false;
    if (state.parentX < 0 || state.parentY < 0
        || state.parentX >= world.width() || state.parentY >= world.height()) return false;

    Tile parent = world.tile(state.parentX, state.parentY);
    Building build = parent == null ? null : parent.build;
    if (build instanceof TumorCoreBuild core) {
      return !core.dead && core.team == owner.team
          && core.tileX() == state.coreX && core.tileY() == state.coreY;
    }
    if (build instanceof TumorTissue tissue) {
      TissueState parentState = tissue.tumor();
      return build.team == owner.team
          && parentState.coreX == state.coreX
          && parentState.coreY == state.coreY
          && !parentState.withering;
    }
    return false;
  }

  /** Runs link validation and necrosis for a tissue build. */
  public static boolean updateTissue(Building owner, TissueState state) {
    if (net.client()) {
      if (!state.mature) state.maturation = Math.min(TissueState.maturationTime, state.maturation + Time.delta);
      state.update(Time.delta);
      return false;
    }

    boolean justMatured = false;
    if (!state.withering && !state.mature) {
      state.maturation += Time.delta;
      if (state.maturation >= TissueState.maturationTime) {
        state.mature = true;
        state.maturation = TissueState.maturationTime;
        state.growthTimer = 0f;
        justMatured = true;
      }
    }

    if (!state.withering && state.linkTimer >= 15f) {
      state.linkTimer = 0f;
      if (!isLinked(owner, state)) state.beginWither(0f);
    }

    boolean expired = state.update(Time.delta);
    if (expired) owner.kill();
    if (expired || state.withering || !state.mature) return expired;

    int level = levelOf(owner, state);
    if (justMatured && (isMineral(owner.tile) || enemyNear(owner.team, owner.x, owner.y,
        (8f + level * 2f) * tilesize))) {
      TumorCoreBuild core = rootCore(owner, state);
      if (core != null) spawnDefense(core, owner, level);
    }

    state.growthTimer += Time.delta;
    float interval = 240f - (level - 1) * 15f;
    if (state.growthTimer >= interval) {
      state.growthTimer = 0f;
      float chance = 0.08f + level * 0.04f;
      if (Mathf.chance(chance)) tryBud(owner, state, level);
    }
    return expired;
  }

  public static void collapseFromCore(TumorCoreBuild core) {
    if (net.client()) return;

    Groups.build.each(build -> {
      if (build instanceof TumorTissue tissue) {
        TissueState state = tissue.tumor();
        if (state.coreX == core.tileX() && state.coreY == core.tileY()) {
          state.beginWither(state.depth * 7f);
        }
      }
    });
  }

  public static int countDefenses(TumorCoreBuild core) {
    int[] count = {0};
    Groups.build.each(build -> {
      if (!build.dead && build.team == core.team
          && (build.block == TumorBlocks.turret || build.block == TumorBlocks.wall)
          && build instanceof TumorTissue tissue) {
        TissueState state = tissue.tumor();
        if (state.coreX == core.tileX() && state.coreY == core.tileY()) count[0]++;
      }
    });
    return count[0];
  }

  /** Any mature organ can bud a new immature growth tip into an adjacent buildable tile. */
  public static Building tryBud(Building owner, TissueState parentState, int level) {
    if (net.client() || owner == null || owner.dead || owner.tile == null) return null;
    if (!hasMemoryBudget(48L * 1024 * 1024)) return null;
    TumorCoreBuild core = owner instanceof TumorCoreBuild direct
        ? direct
        : parentState == null ? null : rootCore(owner, parentState);
    if (core == null) return null;

    Seq<Tile> available = new Seq<>();
    for (Tile tile : frontier(owner)) {
      if (canSpreadOnto(tile)) available.add(tile);
    }
    if (available.isEmpty()) return null;
    if (!core.consumeGrowthCredit()) return null;

    Tile target = available.random();
    TissueState child = new TissueState();
    child.coreX = parentState != null ? parentState.coreX : owner.tileX();
    child.coreY = parentState != null ? parentState.coreY : owner.tileY();
    child.parentX = owner.tileX();
    child.parentY = owner.tileY();
    child.depth = parentState != null ? parentState.depth + 1 : 1;
    child.mature = false;
    child.maturation = 0f;
    child.growthTimer = Mathf.random(0f, 45f);
    return place(TumorBlocks.meristem, target, owner.team, 0, child);
  }

  public static Building tryBud(TumorCoreBuild core) {
    return tryBud(core, null, core.level);
  }

  /** Replaces a mature mineral growth with a real drill while preserving its network state. */
  public static boolean differentiateToDrill(TumorMeristemBuild growth, TissueState state) {
    if (net.client() || growth == null || growth.dead || growth.tile == null) return false;
    Tile tile = growth.tile;
    TissueState saved = new TissueState();
    saved.copyFrom(state);
    tile.setBlock(TumorBlocks.drill, growth.team, 0);
    if (tile.build instanceof TumorTissue replacement) {
      replacement.tumor().copyFrom(saved);
      return true;
    }
    return false;
  }

  public static boolean hasMemoryBudget(long reserveBytes) {
    Runtime runtime = Runtime.getRuntime();
    long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    return available > reserveBytes;
  }

  /** Player placement is intentionally restricted to sandbox and map-editor environments. */
  public static boolean allowsManualPlacement() {
    return state != null && (state.rules.infiniteResources || state.rules.editor);
  }

  /** Environmental walls, existing buildings, and non-buildable/deep floors stop growth. */
  public static boolean canSpreadOnto(Tile tile) {
    return tile != null
        && tile.floor() != null
        && !tile.floor().isDeep()
        && tile.block() == Blocks.air
        && tile.build == null;
  }

  /** Checks the complete multiblock footprint, not only its anchor tile. */
  public static boolean canPlaceAt(Block block, Tile anchor) {
    if (block == null || anchor == null) return false;
    int start = -(block.size - 1) / 2;
    for (int dx = start; dx < start + block.size; dx++) {
      for (int dy = start; dy < start + block.size; dy++) {
        Tile tile = world == null ? null : world.tile(anchor.x + dx, anchor.y + dy);
        if (!canSpreadOnto(tile)) return false;
      }
    }
    return true;
  }

  private static Seq<Tile> frontier(Building source) {
    Seq<Tile> tiles = new Seq<>(8);
    int half = source.block.size / 2;
    for (int dx = -half; dx <= half; dx++) {
      addIfAir(tiles, source.tileX() + dx, source.tileY() - half - 1);
      addIfAir(tiles, source.tileX() + dx, source.tileY() + half + 1);
    }
    for (int dy = -half; dy <= half; dy++) {
      addIfAir(tiles, source.tileX() - half - 1, source.tileY() + dy);
      addIfAir(tiles, source.tileX() + half + 1, source.tileY() + dy);
    }
    return tiles;
  }

  private static void addIfAir(Seq<Tile> tiles, int x, int y) {
    if (world == null) return;
    if (x < 0 || y < 0 || x >= world.width() || y >= world.height()) return;
    Tile tile = world.tile(x, y);
    if (tile != null) tiles.add(tile);
  }

  public static Building place(Block block, Tile tile, mindustry.game.Team team, int rotation, TissueState state) {
    if (net.client() || !canPlaceAt(block, tile)) return null;
    tile.setBlock(block, team, rotation);
    Building build = tile.build;
    if (build instanceof TumorTissue tissue) tissue.tumor().copyFrom(state);
    return build;
  }

  /**
   * Converts the ancestry below a new drill into one-way, flesh-lined conveyors.
   *
   * <p>The lineage itself is the route: every new meristem records the tile that budded it, so a
   * route can be produced without a global pathfinder or a second route search.</p>
   */
  public static void connectPathToCore(Tile tip, mindustry.game.Team team) {
    if (net.client() || tip == null) return;
    Building current = tip.build;
    if (!(current instanceof TumorTissue currentNode)) return;

    TissueState currentState = currentNode.tumor();
    int guard = 0;
    while (currentState.parentX >= 0 && currentState.parentY >= 0 && guard++ < 256) {
      Tile parentTile = world.tile(currentState.parentX, currentState.parentY);
      if (parentTile == null) break;

      Building parent = parentTile.build;
      if (parent instanceof TumorCoreBuild) break;
      if (!(parent instanceof TumorTissue parentNode) || parent.team != team) break;

      TissueState saved = new TissueState();
      saved.copyFrom(parentNode.tumor());
      Tile grandParent = saved.parentX >= 0 ? world.tile(saved.parentX, saved.parentY) : null;
      int rotation = grandParent == null
          ? defaultRotation(parentTile)
          : outputRotation(parentTile, grandParent);

      if (parent.block == TumorBlocks.conveyor) {
        parent.rotation = rotation;
      } else if (parent instanceof TumorMeristemBuild) {
        // Keep the same Building object. Replacing it with setBlock() spawned block-break effects
        // that looked like the tumor was repeatedly exploding.
        parentNode.tumor().transport = true;
        parent.rotation = rotation;
      } else {
        break;
      }

      currentState = saved;
    }
  }

  public static int outputRotation(Tile from, Tile owner) {
    if (from == null || owner == null) return 0;
    Building ownerBuild = owner.build;
    if (ownerBuild instanceof TumorCoreBuild) {
      for (int direction = 0; direction < 4; direction++) {
        Tile nearby = from.nearby(direction);
        if (nearby != null && nearby.build == ownerBuild) return direction;
      }
    }
    return rotationTo(from.x, from.y, owner.x, owner.y);
  }

  public static int rotationTo(int x, int y, int targetX, int targetY) {
    if (targetX > x) return 0;
    if (targetY < y) return 1;
    if (targetX < x) return 2;
    return 3;
  }

  private static int defaultRotation(Tile tile) {
    return 0;
  }

  public static boolean enemyNear(mindustry.game.Team team, float x, float y, float range) {
    final boolean[] found = {false};
    Units.nearbyEnemies(team, x, y, range, unit -> {
      if (unit.isValid()) found[0] = true;
    });
    if (found[0]) return true;
    return Units.findEnemyTile(team, x, y, range, build -> build.isValid()) != null;
  }

  /** Builds a level-scaled turret/wall pocket around a meristem or mining drill. */
  public static void spawnDefense(TumorCoreBuild core, Building center, int level) {
    if (net.client() || center == null || center.dead) return;
    if (!hasMemoryBudget(32L * 1024 * 1024)) return;
    if (core.defenseBuildings >= core.maxDefenseBuildings()) return;
    if (center instanceof TumorTissue tissue) {
      if (tissue.tumor().defended) return;
    }

    Seq<Tile> candidates = new Seq<>();
    for (int radius = 1; radius <= 2; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
          if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
          Tile tile = world.tile(center.tileX() + dx, center.tileY() + dy);
          if (tile != null && tile.block() == Blocks.air && !tile.floor().isLiquid && !isMineral(tile)) {
            candidates.add(tile);
          }
        }
      }
    }
    if (candidates.isEmpty()) return;

    Seq<Tile> structures = new Seq<>();
    int turretCount = 1 + (level >= 3 ? 1 : 0);
    for (int i = 0; i < turretCount && structures.size < candidates.size; i++) {
      Tile turretTile = candidates.get(i);
      if (turretTile.block() != Blocks.air) continue;
      TissueState turretState = childState(core, center, turretTile);
      if (place(TumorBlocks.turret, turretTile, core.team, 0, turretState) != null) {
        structures.add(turretTile);
      }
    }
    if (structures.isEmpty()) return;
    if (center instanceof TumorTissue tissue) tissue.tumor().defended = true;

    int wallCount = 3 + level * 2;
    int placed = 0;
    for (Tile tile : candidates) {
      if (placed >= wallCount) break;
      if (structures.contains(tile) || tile.block() != Blocks.air || isMineral(tile)) continue;
      if (Math.abs(tile.x - center.tileX()) + Math.abs(tile.y - center.tileY()) > 3) continue;
      TissueState wallState = childState(core, center, tile);
      if (place(TumorBlocks.wall, tile, core.team, 0, wallState) != null) placed++;
    }
    core.defenseBuildings += structures.size + placed;
  }

  private static TissueState childState(TumorCoreBuild core, Building parent, Tile target) {
    TissueState state = new TissueState();
    state.coreX = core.tileX();
    state.coreY = core.tileY();
    state.parentX = parent.tileX();
    state.parentY = parent.tileY();
    state.depth = parent instanceof TumorTissue tissue ? tissue.tumor().depth + 1 : 1;
    return state;
  }

  public static void ensureNursery(TumorCoreBuild core) {
    if (net.client() || core.level < 2) return;
    final boolean[] exists = {false};
    Groups.build.each(build -> {
      if (build.block == TumorBlocks.nursery && build.team == core.team && !build.dead
          && build instanceof TumorTissue tissue) {
        TissueState state = tissue.tumor();
        if (state.coreX == core.tileX() && state.coreY == core.tileY()) exists[0] = true;
      }
    });
    if (exists[0]) return;

    for (int radius = 2; radius <= 6 && !exists[0]; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
          if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
          Tile tile = world.tile(core.tileX() + dx, core.tileY() + dy);
          if (tile == null || tile.block() != Blocks.air || tile.floor().isLiquid || isMineral(tile)) continue;
          TissueState state = new TissueState();
          state.coreX = core.tileX();
          state.coreY = core.tileY();
          state.parentX = core.tileX();
          state.parentY = core.tileY();
          state.depth = 1;
          if (place(TumorBlocks.nursery, tile, core.team, 0, state) != null) {
            exists[0] = true;
            break;
          }
        }
        if (exists[0]) break;
      }
    }
  }
}
