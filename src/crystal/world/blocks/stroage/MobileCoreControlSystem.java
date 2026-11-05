package crystal.world.blocks.stroage;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
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
import mindustry.input.Binding;
import mindustry.type.UnitType;
import mindustry.ui.fragments.HintsFragment.DefaultHint;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static mindustry.Vars.*;

/**
 * Coordinates player control between a mobile core and its configured unit.
 *
 * <p>In an ordinary sector, a requested mobile core can coexist with its
 * configured player unit. In sectors marked by {@link MobileCoreSectorPreset},
 * the player and {@link Corec} are one entity: the player always controls the
 * core itself and never possesses a separate summon unit. Unit changes are
 * checked here as well because upgrades can call {@link Player#unit(Unit)}
 * directly and bypass normal input restrictions.</p>
 */
public final class MobileCoreControlSystem {
  public static final String preferredCoreSetting = "mobile_core_type";
  public static final String launchCoreSetting = "mobile_core_launch";
  private static final float COMPANION_MAGIC_DRAIN_PER_FRAME = 0.0033333f;
  private static boolean initialized;
  private static boolean enforcing;
  private static boolean rebinding;
  private static boolean companionControlled;
  private static final ObjectMap<Integer, Unit> playerUnits = new ObjectMap<>();
  private static boolean respawnHintHandled;
  private static KeyBind.Axis savedRespawnBinding;
  private static Field hintCurrentField;
  private static Method hintHideMethod;
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
      companionControlled = false;
      playerUnits.clear();
      respawnHintHandled = false;
      magicSaveTimer = 0f;
      restoreRespawnBinding();
    });

    // Catch direct player.unit(...) calls, including unit upgrades and saves.
    Events.on(UnitChangeEvent.class, e -> {
      if (e.player == null || e.player != player || !enforcing || rebinding || !isEnabledHere()) {
        return;
      }
      Corec core = findPlayerCore(e.player);
      if (core != null) {
        Unit companion = activeCompanion(e.player, core);
        if (companion != null && e.unit == companion) {
          companionControlled = true;
          core.spawnedByCore(false);
          companion.controller(e.player);
          setRespawnSuppressed(false);
        } else if (e.unit == (Unit) core || e.unit == null) {
          companionControlled = false;
          removeCompanion(e.player, core, companion);
          setRespawnSuppressed(true);
        } else {
          Unit expected = companionControlled && companion != null ? companion : (Unit) core;
          bind(e.player, expected);
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
      updateIntegratedControl();
    });
  }

  /** Applies one integrated-control tick; called by Trigger.update and tests. */
  public static void updateIntegratedControl() {
    if (!enforcing || player == null || !state.isPlaying() || !isEnabledHere()) {
      return;
    }

    Corec core = findPlayerCore(player);
    if (core == null) {
      return;
    }
    Unit companion = activeCompanion(player, core);
    if (companionControlled && companion != null
        && updateCompanionMagic(player, core, companion)) {
      return;
    }
    Unit expected = companionControlled && companion != null ? companion : (Unit) core;
    if (companionControlled && companion == null) {
      companionControlled = false;
    }
    if (player.unit() != expected) {
      bind(player, expected);
    }
  }

  /** Whether the player can focus the mobile core through the skill HUD. */
  public static boolean canTogglePlayerUnit() {
    return !headless
        && player != null
        && state != null
        && state.isPlaying()
        && state.rules != null
        && !state.rules.editor
        && isEnabledHere()
        && findPlayerCore(player) != null;
  }

  /**
   * Skill entry used by the existing continuous skill runtime. It creates the
   * configured companion if needed, then switches control between that
   * companion and the mobile core without destroying the companion.
   */
  public static void togglePlayerUnit() {
    if (player == null || !state.isPlaying() || !isEnabledHere()) {
      return;
    }
    Corec core = findPlayerCore(player);
    if (core == null) {
      return;
    }

    Unit active = activeCompanion(player, core);
    if (active != null) {
      companionControlled = false;
      removeCompanion(player, core, active);
      setRespawnSuppressed(true);
      Log.info("[MobileCoreControl] dismissed companion=@, core=@", active.id(), core.id());
      return;
    }

    if (active == null) {
      if (CultivationState.availableMagicPower <= 0f) {
        return;
      }
      UnitType unitType = playerUnitType(core);
      if (unitType == null || unitType == core.type() || unitType.constructor == null) {
        return;
      }
      active = spawnCompanion(player, core, unitType);
      if (active == null) {
        return;
      }
      playerUnits.put(core.id(), active);
      Log.info("[MobileCoreControl] companion spawned=@, core=@, type=@",
          active.id(), core.id(), active.type.name);
    }
    companionControlled = true;
    core.spawnedByCore(false);
    bind(player, active);
    active.controller(player);
    setRespawnSuppressed(false);
    Log.info("[MobileCoreControl] player possessed companion=@, core=@", active.id(), core.id());
  }

  /** Whether the player is currently possessing the skill's companion unit. */
  public static boolean isPlayerUnitActive() {
    Corec core = findPlayerCore(player);
    return core != null && player.unit() == activeCompanion(player, core);
  }

  /** Whether this player is currently controlling the mobile core entity itself. */
  public static boolean isControllingCore(@Nullable Player target) {
    Corec core = findPlayerCore(target);
    return core != null && target != null && target.unit() == (Unit) core;
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
        companionControlled = false;
        clearPlayerUnits();
        removeLegacyAvatar(target, core);
        bind(target, core);
        setRespawnSuppressed(true);
      } else {
        clearPlayerUnits();
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
        Unit companion = activeCompanion(target, core);
        Unit expected = companionControlled && companion != null ? companion : (Unit) core;
        bind(target, expected);
        setRespawnSuppressed(!companionControlled);
      } else {
        clearPlayerUnits();
        ensurePlayerAvatar(target, core);
      }
      enforcing = isEnabledHere();
    } else if (isEnabledHere() && target.unit() != null) {
      target.unit((Unit) null);
    }
  }

  /**
   * Handles a respawn request. Ordinary sectors spawn the configured unit;
   * marked sectors always respawn as the mobile core itself.
   */
  public static @Nullable Unit spawnPlayerFromCore(Player target, Corec core) {
    if (target == null || core == null || core.dead()) {
      return null;
    }
    Fx.spawn.at(core);
    Unit avatar;
    if (isEnabledHere()) {
      companionControlled = false;
      removeLegacyAvatar(target, core);
      removeCompanion(target, core, activeCompanion(target, core));
      bind(target, core);
      setRespawnSuppressed(true);
      enforcing = true;
      return (Unit) core;
    } else {
      clearPlayerUnits();
      avatar = ensurePlayerAvatar(target, core);
    }
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
    // Vanilla treats non-core-spawned bodies as possession and offers the
    // respawn tutorial/action. The integrated Corec is the player's core-born
    // body, so mark it accordingly to bypass that entire path.
    core.spawnedByCore(true);
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

  private static void clearPlayerUnits() {
    playerUnits.clear();
  }

  /**
   * Removes an avatar created by the old marked-sector summon flow so loading
   * a legacy save cannot leave a second controlled-looking player body behind.
   */
  private static void removeLegacyAvatar(Player target, Corec core) {
    Unit current = target.unit();
    Unit companion = activeCompanion(target, core);
    UnitType avatarType = playerUnitType(core);
    if (current != companion
        && current != null
        && current != core
        && current.spawnedByCore()
        && (avatarType == null || current.type == avatarType)) {
      current.remove();
    }
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

  private static @Nullable Unit spawnCompanion(Player target, Corec core, UnitType unitType) {
    Unit companion = unitType.create(target.team());
    companion.set(core.x(), core.y());
    companion.rotation(90f);
    companion.spawnedByCore(true);
    if (unitType.controller != null) {
      companion.controller(unitType.controller.get(companion));
    }
    companion.add();
    return companion;
  }

  private static void removeCompanion(
      Player target, Corec core, @Nullable Unit companion) {
    playerUnits.remove(core.id());
    if (companion != null && companion.isAdded()) {
      companion.remove();
    }
    companionControlled = false;
    bind(target, core);
    magicSaveTimer = 0f;
    PlayerXiuWeiSystem.savePower();
  }

  private static boolean updateCompanionMagic(
      Player target, Corec core, Unit companion) {
    float drain = coreUnitMagicDrainPerFrame();
    if (drain <= 0f) {
      return false;
    }
    if (CultivationState.availableMagicPower <= 0f) {
      removeCompanion(target, core, companion);
      return true;
    }

    CultivationState.availableMagicPower = Math.max(
        0f, CultivationState.availableMagicPower - drain);
    magicSaveTimer += 1f;
    if (magicSaveTimer >= 60f) {
      PlayerXiuWeiSystem.savePower();
      magicSaveTimer = 0f;
    }
    if (CultivationState.availableMagicPower <= 0f) {
      removeCompanion(target, core, companion);
      return true;
    }
    return false;
  }

  public static float coreUnitMagicDrainPerFrame() {
    JingJie realm = CultivationState.currentJingJie();
    return COMPANION_MAGIC_DRAIN_PER_FRAME * Math.max(realm.multiplier(), 1f);
  }

  private static @Nullable Unit activeCompanion(Player target, Corec core) {
    Unit tracked = playerUnits.get(core.id());
    UnitType companionType = playerUnitType(core);
    if (isUsableUnit(tracked, target.team())
        && tracked != core
        && (companionType == null || tracked.type == companionType)) {
      return tracked;
    }
    playerUnits.remove(core.id());
    return null;
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
      if (isEnabledHere()) {
        suppressRespawnHint();
        setRespawnSuppressed(!companionControlled);
      } else {
        restoreRespawnBinding();
      }
    }
  }

  /**
   * The player's body is the core in integral mode, so vanilla's "press V to
   * respawn as the initial unit" tutorial is both misleading and unusable.
   */
  private static void suppressRespawnHint() {
    if (respawnHintHandled || ui == null || ui.hints == null) {
      return;
    }
    respawnHintHandled = true;
    ui.hints.hints.remove(DefaultHint.respawn);

    try {
      if (hintCurrentField == null) {
        hintCurrentField = ui.hints.getClass().getDeclaredField("current");
        hintCurrentField.setAccessible(true);
      }
      if (hintCurrentField.get(ui.hints) == DefaultHint.respawn) {
        if (hintHideMethod == null) {
          hintHideMethod = ui.hints.getClass().getDeclaredMethod("hide");
          hintHideMethod.setAccessible(true);
        }
        hintHideMethod.invoke(ui.hints);
      }
    } catch (ReflectiveOperationException ignored) {
      // The hint is already removed from the queue; older game versions may
      // use different private field names, so keep this best-effort.
    }
  }

  private static void setRespawnSuppressed(boolean suppress) {
    if (suppress) {
      if (savedRespawnBinding == null && Binding.respawn.value != null) {
        savedRespawnBinding = Binding.respawn.value;
        Binding.respawn.unset();
      }
    } else {
      restoreRespawnBinding();
    }
  }

  private static void restoreRespawnBinding() {
    if (savedRespawnBinding != null) {
      Binding.respawn.value = savedRespawnBinding;
      savedRespawnBinding = null;
    }
  }

}
