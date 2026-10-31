package crystal.world.blocks.stroage;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import crystal.content.SpecialUnits;
import crystal.gen.Corec;
import crystal.type.CoreUnit;
import crystal.type.MobileCoreSectorPreset;
import mindustry.content.Fx;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitChangeEvent;
import mindustry.game.EventType.WorldLoadEndEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

/**
 * Keeps the local player on a mobile core's configured player unit.
 *
 * <p>The mobile core remains the team's core while the player controls the
 * {@link CoreUnit#playerUnitType()} configured on that core. This restriction
 * is only active in sectors marked by {@link MobileCoreSectorPreset}. Unit
 * changes are checked here as well because upgrades can call
 * {@link Player#unit(Unit)} directly and bypass normal input restrictions.</p>
 */
public final class MobileCoreControlSystem {
  public static final String preferredCoreSetting = "mobile_core_type";
  public static final String launchCoreSetting = "mobile_core_launch";
  private static boolean initialized;
  private static boolean enforcing;
  private static boolean rebinding;
  private static final ObjectMap<Integer, Unit> playerUnits = new ObjectMap<>();

  private MobileCoreControlSystem() {
  }

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;

    Events.on(WorldLoadEvent.class, e -> applyWorldRules());
    Events.on(WorldLoadEndEvent.class, e -> {
      applyWorldRules();
      // Player spawning can finish after WorldLoadEndEvent; run once on the next frame.
      Core.app.post(() -> createPlayerMobileCore(player));
    });
    Events.run(Trigger.newGame, () -> {
      applyWorldRules();
      if (headless || player == null || state.isMenu()) {
        return;
      }

      // Control's newGame listener runs before this one and may start the
      // entity-core landing animation. Capture the mobile launch intent now;
      // the post task then replaces that animation with the mobile core.
      boolean mobileLanding = shouldCreateMobileCore()
          || findPlayerCore(player) instanceof Corec;
      UnitType requested = requestedLaunchType();
      Core.app.post(() -> {
        Corec core = createPlayerMobileCore(player);
        if (core != null && mobileLanding && isMobileLaunchType(core, requested)) {
          startLanding(core);
        }
      });
    });
    Events.on(ResetEvent.class, e -> {
      enforcing = false;
      playerUnits.clear();
    });

    // Catch direct player.unit(...) calls, including unit upgrades and saves.
    Events.on(UnitChangeEvent.class, e -> {
      if (e.player == null || e.player != player || !enforcing || rebinding || !isEnabledHere()) {
        return;
      }
      Corec core = findPlayerCore(e.player);
      if (core != null) {
        Unit avatar = playerUnitFor(e.player, core);
        if (e.unit != null && e.unit == avatar && isUsableUnit(avatar, e.player.team())) {
          return;
        }
        ensurePlayerAvatar(e.player, core);
      } else if (e.unit != null) {
        e.player.unit((Unit) null);
      }
    });

    Events.run(Trigger.update, () -> {
      // Rules can be replaced while a save is opened or a sector launches.
      // Always restore this mod-owned flag before the early exit, so a marked
      // sector cannot leak possession restrictions into ordinary sectors.
      applyWorldRules();
      if (!enforcing || player == null || !state.isPlaying() || !isEnabledHere()) {
        return;
      }

      Corec core = findPlayerCore(player);
      if (core != null) {
        Unit avatar = playerUnitFor(player, core);
        if (player.unit() != avatar) {
          ensurePlayerAvatar(player, core);
        }
      }
    });
  }

  /** Returns the existing player-owned mobile core, or creates one from the team's core. */
  public static @Nullable Corec createPlayerMobileCore(@Nullable Player target) {
    if (target == null || !state.isGame() || !shouldCreateMobileCore()) {
      enforcing = false;
      return null;
    }

    applyWorldRules();
    boolean restrictControl = isEnabledHere();
    UnitType requestedType = restrictControl ? preferredCoreType() : requestedLaunchType();
    Corec core = findPlayerCore(target);
    boolean created = false;
    if (core == null) {
      CoreBuild source = findSourceCore(target);
      if (source != null) {
        core = convertCore(source, requestedType);
        created = core != null;
      }
    }

    if (core != null) {
      Log.info("[MobileCoreControl] core=@, created=@, marked=@, launch=@",
          core.id(), created, restrictControl, requestedType == null ? "none" : requestedType.name);
      ensurePlayerAvatar(target, core);
      enforcing = restrictControl;
      if (!restrictControl) {
        Core.settings.put(launchCoreSetting, "");
      }
    }
    return core;
  }

  /**
   * Rebinds the player to an already existing mobile core. Unlike
   * {@link #createPlayerMobileCore(Player)}, this never creates or replaces a
   * core and is safe to call from update or repair paths.
   */
  public static void enforcePlayerControl(@Nullable Player target) {
    if (target == null) {
      return;
    }
    Corec core = findPlayerCore(target);
    if (core != null) {
      ensurePlayerAvatar(target, core);
      enforcing = isEnabledHere();
    } else if (isEnabledHere() && target.unit() != null) {
      target.unit((Unit) null);
    }
  }

  /**
   * Spawns or re-binds the mobile core's configured player unit type while
   * keeping the mobile core itself in the world as the team's core.
   */
  public static @Nullable Unit spawnPlayerFromCore(Player target, Corec core) {
    if (target == null || core == null || core.dead()) {
      return null;
    }
    Fx.spawn.at(core);
    Unit avatar = ensurePlayerAvatar(target, core);
    enforcing = isEnabledHere();
    return avatar;
  }

  /** Converts a normal core block into a mobile core while preserving its items. */
  public static @Nullable Corec convertCore(@Nullable CoreBuild source, @Nullable UnitType requestedType) {
    if (source == null
        || source instanceof MCoreBlock.MCoreBuild
        || source.tile == null
        || source.tile.build != source) {
      return null;
    }

    UnitType type = requestedType instanceof CoreUnit ? requestedType : preferredCoreType();
    if (type == null || type.constructor == null) {
      return null;
    }

    Unit unit = type.create(source.team);
    if (!(unit instanceof Corec core)) {
      return null;
    }

    ItemModule storedItems = new ItemModule();
    if (source.items != null) {
      storedItems.set(source.items);
    }

    float x = source.x();
    float y = source.y();
    Tile tile = source.tile;

    unit.set(x, y);
    unit.elevation = 0f;
    core.items().set(storedItems);
    tile.remove();
    unit.add();

    // injectCore shares the first core's ItemModule; restore the transferred inventory after injection.
    core.items().set(storedItems);
    return core;
  }

  public static @Nullable Corec findPlayerCore(@Nullable Player target) {
    if (target == null || target.team() == null) {
      return null;
    }
    Team team = target.team();

    for (Unit unit : Groups.unit) {
      if (unit instanceof Corec core && isUsable(core, team)) {
        return core;
      }
    }
    for (Corec core : MoveCoreSystem.getCores(team)) {
      if (isUsable(core, team)) {
        return core;
      }
    }
    return null;
  }

  public static boolean isMobileCore(@Nullable Unit unit) {
    return unit instanceof Corec;
  }

  public static boolean isEnabledHere() {
    return state != null && state.rules != null
        && MobileCoreSectorPreset.isMarked(state.rules.sector);
  }

  public static boolean shouldCreateMobileCore() {
    return isEnabledHere() || requestedLaunchType() != null;
  }

  public static @Nullable UnitType requestedLaunchType() {
    String selected = Core.settings.getString(launchCoreSetting, "");
    UnitType type = selected.isEmpty() ? null : content.unit(selected);
    return type instanceof CoreUnit ? type : null;
  }

  private static boolean isMobileLaunchType(Corec core, @Nullable UnitType requested) {
    return isEnabledHere()
        || requested != null
        || core.type() instanceof CoreUnit;
  }

  private static void startLanding(Corec core) {
    if (headless || Core.settings.getBool("skipcoreanimation")) {
      return;
    }
    core.elevation(1f);
    Core.camera.position.set(core);
    renderer.showLanding(new MobileCoreLaunch((Unit) core, core.x(), core.y(), 160f));
    Log.info("[MobileCoreControl] landing started, core=@, renderer.landTime=@",
        core.id(), renderer.getLandTime());
  }

  private static void bind(Player target, Corec core) {
    bind(target, (Unit) core);
  }

  private static void bind(Player target, Unit unit) {
    if (target.unit() == unit) {
      return;
    }

    boolean previous = rebinding;
    rebinding = true;
    try {
      target.unit(unit);
    } finally {
      rebinding = previous;
    }
  }

  private static boolean isUsable(Corec core, Team team) {
    return core != null && core.team() == team && !core.dead() && core.isAdded();
  }

  private static boolean isUsableUnit(@Nullable Unit unit, Team team) {
    return unit != null && unit.team == team && !unit.dead() && unit.isAdded();
  }

  private static @Nullable Unit playerUnitFor(Player target, Corec core) {
    Unit tracked = playerUnits.get(core.id());
    UnitType avatarType = playerUnitType(core);
    if (avatarType == core.type() && target.unit() == (Unit) core && isUsable(core, target.team())) {
      playerUnits.put(core.id(), (Unit) core);
      return (Unit) core;
    }
    if (isUsableUnit(tracked, target.team())
        && (avatarType == null || tracked.type == avatarType)) {
      return tracked;
    }
    playerUnits.remove(core.id());

    Unit current = target.unit();
    if (isUsableUnit(current, target.team())
        && current != core
        && current.spawnedByCore
        && (avatarType == null || current.type == avatarType)) {
      playerUnits.put(core.id(), current);
      return current;
    }

    for (Unit unit : Groups.unit) {
      if (unit != core
          && isUsableUnit(unit, target.team())
          && unit.spawnedByCore
          && (avatarType == null || unit.type == avatarType)
          && unit.controller() == target) {
        playerUnits.put(core.id(), unit);
        return unit;
      }
    }
    return null;
  }

  private static @Nullable Unit ensurePlayerAvatar(Player target, Corec core) {
    Unit avatar = playerUnitFor(target, core);
    if (avatar == null) {
      UnitType avatarType = playerUnitType(core);
      if (avatarType != null && avatarType.constructor != null && avatarType != core.type()) {
        avatar = avatarType.create(target.team());
        avatar.set(core.x(), core.y());
        avatar.rotation(90f);
        avatar.spawnedByCore(true);
        avatar.controller(target);
        avatar.add();
        playerUnits.put(core.id(), avatar);
      }
    }

    if (avatar != null) {
      bind(target, avatar);
      return avatar;
    }

    // Defensive fallback for malformed/legacy core types without a player unit type.
    bind(target, core);
    return (Unit) core;
  }

  private static @Nullable UnitType playerUnitType(Corec core) {
    return core.type() instanceof CoreUnit type ? type.playerUnitType() : null;
  }

  private static @Nullable CoreBuild findSourceCore(Player target) {
    CoreBuild best = target.bestCore();
    if (best != null && !(best instanceof MCoreBlock.MCoreBuild)) {
      return best;
    }

    if (state.teams != null) {
      for (CoreBuild core : state.teams.get(target.team()).cores) {
        if (core != null && !(core instanceof MCoreBlock.MCoreBuild) && core.isValid()) {
          return core;
        }
      }
    }
    return null;
  }

  public static @Nullable UnitType preferredCoreType() {
    String selected = Core.settings.getString(preferredCoreSetting, "");
    UnitType type = selected.isEmpty() ? null : content.unit(selected);
    if (type instanceof CoreUnit) {
      return type;
    }
    return SpecialUnits.moveCore1;
  }

  private static void applyWorldRules() {
    if (state != null && state.rules != null) {
      // Marked sectors are mobile-core-only. Ordinary sectors must explicitly
      // restore possession so the flag cannot leak across sector transitions.
      state.rules.possessionAllowed = !isEnabledHere();
    }
  }
}
