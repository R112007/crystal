package crystal.world.blocks.stroage;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import crystal.content.SpecialUnits;
import crystal.core.CultivationState;
import crystal.core.PlayerXiuWeiSystem;
import crystal.entities.units.UnitEnum.JingJie;
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
 * Coordinates player control between a mobile core and its configured core unit.
 *
 * <p>The mobile core remains the team's core while the player controls the
 * {@link CoreUnit#playerUnitType()} configured on that core. Ordinary sectors
 * spawn that unit automatically. Sectors marked by
 * {@link MobileCoreSectorPreset} start on the mobile core and require the
 * spirit-powered summon command. Unit changes are checked here as well because
 * upgrades can call {@link Player#unit(Unit)} directly and bypass normal input
 * restrictions.</p>
 */
public final class MobileCoreControlSystem {
  public static final String preferredCoreSetting = "mobile_core_type";
  public static final String launchCoreSetting = "mobile_core_launch";
  public static final KeyBind playerUnitKey = KeyBind.add(
      "crystal_core_unit_skill", KeyCode.u, "crystal");
  private static final float PLAYER_UNIT_MAGIC_DRAIN_PER_FRAME = 0.0033333f;
  private static boolean initialized;
  private static boolean enforcing;
  private static boolean rebinding;
  private static final ObjectMap<Integer, Unit> playerUnits = new ObjectMap<>();
  private static boolean playerUnitActive;
  private static int activePlayerUnitId = -1;
  private static float magicSaveTimer;

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
      playerUnitActive = false;
      activePlayerUnitId = -1;
      magicSaveTimer = 0f;
      playerUnits.clear();
    });

    // Catch direct player.unit(...) calls, including unit upgrades and saves.
    Events.on(UnitChangeEvent.class, e -> {
      if (e.player == null || e.player != player || !enforcing || rebinding || !isEnabledHere()) {
        return;
      }
      Corec core = findPlayerCore(e.player);
      if (core != null) {
        if (playerUnitActive) {
          Unit avatar = activePlayerUnit(e.player, core);
          if (avatar != null) {
            if (e.unit != avatar) {
              bind(e.player, avatar);
            }
          } else {
            returnToCore(e.player, core);
          }
        } else {
          bind(e.player, core);
        }
      } else if (e.unit != null) {
        e.player.unit((Unit) null);
      }
    });

    Events.run(Trigger.update, () -> {
      // Rules can be replaced while a save is opened or a sector launches.
      // Always restore this mod-owned flag before the early exit, so a marked
      // sector cannot leak possession restrictions into ordinary sectors.
      applyWorldRules();
      if (isEnabledHere() && state.isPlaying() && !Core.scene.hasDialog()
          && !Core.scene.hasKeyboard() && Core.input.keyTap(playerUnitKey)) {
        togglePlayerUnit();
      }
      if (!enforcing || player == null || !state.isPlaying() || !isEnabledHere()) {
        return;
      }

      Corec core = findPlayerCore(player);
      if (core != null) {
        if (playerUnitActive) {
          updatePlayerUnitMagic(player, core);
        } else if (player.unit() != (Unit) core) {
          bind(player, core);
        }
      }
    });
  }

  public static void togglePlayerUnit() {
    if (headless || player == null || !state.isPlaying() || !isEnabledHere()) {
      return;
    }
    Corec core = findPlayerCore(player);
    if (core == null) {
      return;
    }
    if (playerUnitActive && activePlayerUnit(player, core) != null) {
      returnToCore(player, core);
    } else {
      summonPlayerUnit(core);
    }
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
      if (restrictControl) {
        if (created) {
          // A newly launched sector may already have a vanilla core unit.
          // It was not summoned by the player and must not start the skill.
          clearPlayerUnitState(core);
          bind(target, core);
        } else {
          restoreMarkedControl(target, core);
        }
      } else {
        clearPlayerUnitState(core);
        ensurePlayerAvatar(target, core);
      }
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
      if (isEnabledHere()) {
        if (playerUnitActive) {
          Unit avatar = activePlayerUnit(target, core);
          if (avatar != null) {
            bind(target, avatar);
          } else {
            returnToCore(target, core);
          }
        } else {
          bind(target, core);
        }
      } else {
        clearPlayerUnitState(core);
        ensurePlayerAvatar(target, core);
      }
      enforcing = isEnabledHere();
    } else if (isEnabledHere() && target.unit() != null) {
      target.unit((Unit) null);
    }
  }

  /**
   * Handles a respawn request. Ordinary sectors spawn the configured unit;
   * marked sectors return to the mobile core unless its summoned unit is alive.
   */
  public static @Nullable Unit spawnPlayerFromCore(Player target, Corec core) {
    if (target == null || core == null || core.dead()) {
      return null;
    }
    Fx.spawn.at(core);
    Unit avatar;
    if (isEnabledHere()) {
      avatar = playerUnitActive ? activePlayerUnit(target, core) : null;
      if (avatar == null) {
        returnToCore(target, core);
        return (Unit) core;
      }
      bind(target, avatar);
    } else {
      clearPlayerUnitState(core);
      avatar = ensurePlayerAvatar(target, core);
    }
    enforcing = isEnabledHere();
    return avatar;
  }

  /**
   * Summons the mobile core's configured unit in a marked sector. The summon
   * costs spirit power up front and keeps draining it while the unit exists.
   */
  public static boolean summonPlayerUnit(Corec core) {
    if (headless || player == null || core == null || !isEnabledHere()
        || !isUsable(core, player.team()) || core != findPlayerCore(player)) {
      return false;
    }
    if (playerUnitActive) {
      Unit existing = activePlayerUnit(player, core);
      if (existing != null) {
        bind(player, existing);
        return true;
      }
      clearPlayerUnitState(core);
    }

    UnitType unitType = configuredPlayerUnit(core);
    if (unitType == null) {
      return false;
    }

    if (CultivationState.availableMagicPower <= 0f) {
      if (ui != null) {
        ui.showInfoToast(Core.bundle.get("mobilecore.summon.nomagic"), 3f);
      }
      return false;
    }

    Unit avatar = spawnPlayerUnit(player, core, unitType);
    if (avatar == null) {
      return false;
    }

    playerUnitActive = true;
    activePlayerUnitId = avatar.id;
    playerUnits.put(core.id(), avatar);
    magicSaveTimer = 0f;
    bind(player, avatar);
    Fx.spawn.at(core);
    return true;
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

  private static void restoreMarkedControl(Player target, Corec core) {
    UnitType avatarType = playerUnitType(core);
    Unit avatar = avatarType != null && avatarType != core.type()
        ? playerUnitFor(target, core)
        : null;
    if (avatar != null && avatar != core) {
      playerUnitActive = true;
      activePlayerUnitId = avatar.id;
      playerUnits.put(core.id(), avatar);
      bind(target, avatar);
    } else {
      clearPlayerUnitState(core);
      bind(target, core);
    }
  }

  private static void updatePlayerUnitMagic(Player target, Corec core) {
    Unit avatar = activePlayerUnit(target, core);
    if (avatar == null) {
      returnToCore(target, core);
      return;
    }

    float drain = coreUnitMagicDrainPerFrame();
    if (CultivationState.availableMagicPower <= 0f && drain > 0f) {
      returnToCore(target, core);
      return;
    }

    if (drain > 0f) {
      CultivationState.availableMagicPower = Math.max(
          0f, CultivationState.availableMagicPower - drain);
      magicSaveTimer += 1f;
      if (magicSaveTimer >= 60f) {
        PlayerXiuWeiSystem.savePower();
        magicSaveTimer = 0f;
      }
      if (CultivationState.availableMagicPower <= 0f) {
        returnToCore(target, core);
        return;
      }
    }

    if (target.unit() != avatar) {
      bind(target, avatar);
    }
  }

  private static @Nullable Unit activePlayerUnit(Player target, Corec core) {
    Unit tracked = activePlayerUnitId >= 0
        ? Groups.unit.getByID(activePlayerUnitId)
        : null;
    UnitType avatarType = playerUnitType(core);
    if (!isUsableUnit(tracked, target.team())
        || (avatarType != null && tracked.type != avatarType)) {
      tracked = playerUnits.get(core.id());
    }
    if (isUsableUnit(tracked, target.team())
        && tracked != core
        && (avatarType == null || tracked.type == avatarType)) {
      playerUnits.put(core.id(), tracked);
      activePlayerUnitId = tracked.id;
      return tracked;
    }
    playerUnits.remove(core.id());
    activePlayerUnitId = -1;
    return null;
  }

  private static void returnToCore(Player target, Corec core) {
    clearPlayerUnitState(core);
    if (isUsable(core, target.team())) {
      bind(target, core);
    }
    magicSaveTimer = 0f;
    PlayerXiuWeiSystem.savePower();
  }

  private static void clearPlayerUnitState(Corec core) {
    playerUnitActive = false;
    activePlayerUnitId = -1;
    playerUnits.remove(core.id());
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

  private static @Nullable Unit spawnPlayerUnit(Player target, Corec core, UnitType unitType) {
    if (unitType == null || unitType.constructor == null) {
      return null;
    }
    Unit avatar = unitType.create(target.team());
    avatar.set(core.x(), core.y());
    avatar.rotation(90f);
    avatar.spawnedByCore(true);
    avatar.controller(target);
    avatar.add();
    return avatar;
  }

  private static @Nullable Unit ensurePlayerAvatar(Player target, Corec core) {
    Unit avatar = playerUnitFor(target, core);
    if (avatar == null) {
      UnitType avatarType = playerUnitType(core);
      if (avatarType != null && avatarType.constructor != null && avatarType != core.type()) {
        avatar = spawnPlayerUnit(target, core, avatarType);
        if (avatar != null) {
          playerUnits.put(core.id(), avatar);
        }
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

  private static @Nullable UnitType configuredPlayerUnit(Corec core) {
    UnitType unit = playerUnitType(core);
    return unit == null || unit == core.type() || unit.constructor == null ? null : unit;
  }

  public static float coreUnitMagicDrainPerFrame() {
    JingJie realm = CultivationState.currentJingJie();
    return PLAYER_UNIT_MAGIC_DRAIN_PER_FRAME * Math.max(realm.multiplier(), 1f);
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
