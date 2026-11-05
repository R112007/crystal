package crystal.dbg;

import arc.Core;
import arc.Events;
import crystal.content.CUnits;
import crystal.content.CPlanets;
import crystal.content.LxMaps;
import crystal.content.SpecialUnits;
import crystal.content.blocks.CStroage;
import crystal.core.CultivationState;
import crystal.gen.Corec;
import crystal.type.MobileCoreSectorPreset;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.world.blocks.stroage.MCoreBlock;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import mindustry.core.GameState.State;
import mindustry.core.World;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.input.Binding;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.lang.reflect.Method;

public class MobileCoreLaunchTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    String previousLaunch = Core.settings.getString(MobileCoreControlSystem.launchCoreSetting, "");
    String previousPreferred = Core.settings.getString(MobileCoreControlSystem.preferredCoreSetting, "");
    try {
      Core.settings.put(MobileCoreControlSystem.launchCoreSetting, "");
      Core.settings.put(MobileCoreControlSystem.preferredCoreSetting, SpecialUnits.moveCore1.name);
      check("normal sectors ignore the marked-sector preference", false,
          MobileCoreControlSystem.shouldCreateMobileCore());

      Core.settings.put(MobileCoreControlSystem.launchCoreSetting, SpecialUnits.moveCore1.name);
      check("normal sectors can request a one-shot mobile launch", true,
          MobileCoreControlSystem.shouldCreateMobileCore());
      check("requested mobile core type is resolved", SpecialUnits.moveCore1,
          MobileCoreControlSystem.requestedLaunchType());
      check("mobile core spawns its configured player unit", CUnits.taichu,
          SpecialUnits.moveCore1.playerUnitType());
      check("core1 allocates a Corec entity", true,
          CStroage.core1.create(Team.sharded) instanceof Corec);
      check("core1 keeps the original core storage capacity", 6000,
          CStroage.core1.storageCapacity());
      check("planet default core is not a mobile-core proxy", false,
          CPlanets.lx.defaultCore instanceof MCoreBlock);

      check("marked sector is detected", true,
          MobileCoreSectorPreset.isMarked(LxMaps.jianglindian.sector));
      check("null sector is not marked", false,
          MobileCoreSectorPreset.isMarked(null));

      CPlanetDialog dialog = allocateWithoutConstructor(CPlanetDialog.class);
      dialog.mode = CPlanetDialog.Mode.look;
      Method canPlay = CPlanetDialog.class.getDeclaredMethod("canPlaySector", Sector.class);
      canPlay.setAccessible(true);
      check("marked sector without a base can be launched directly", true,
          canPlay.invoke(dialog, LxMaps.jianglindian.sector));
      check("decorative galaxy sectors cannot start a world", false,
          canPlay.invoke(dialog, CPlanets.milkyWay.sectors.first()));

      Core.settings.put(MobileCoreControlSystem.launchCoreSetting, "");
      mindustry.Vars.state.rules.possessionAllowed = false;
      Events.fire(new WorldLoadEvent());
      check("ordinary sectors restore normal unit possession", true,
          mindustry.Vars.state.rules.possessionAllowed);
      check("ordinary unit controllability is not globally overridden", true,
          UnitTypes.alpha.playerControllable);

      checkPhysicalCoreConversion();
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    } finally {
      Core.settings.put(MobileCoreControlSystem.launchCoreSetting, previousLaunch);
      Core.settings.put(MobileCoreControlSystem.preferredCoreSetting, previousPreferred);
    }

    System.out.println("[MOBILE-LAUNCH] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void check(String label, Object expected, Object actual) {
    boolean pass = expected == null ? actual == null : expected.equals(actual);
    ok &= pass;
    System.out.println("[MOBILE-LAUNCH] " + label + ": " + actual + "/" + expected
        + (pass ? " OK" : " FAIL"));
  }

  private static void checkPhysicalCoreConversion() {
    mindustry.Vars.state.teams = new Teams();
    mindustry.Vars.state.set(State.playing);
    mindustry.Vars.state.rules.defaultTeam = Team.sharded;
    mindustry.Vars.state.rules.waveTeam = Team.crux;
    mindustry.Vars.state.rules.sector = LxMaps.jianglindian.sector;

    mindustry.Vars.world = new World();
    Tiles tiles = new Tiles(32, 32);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 32; x++) {
        tiles.set(x, y, new Tile(x, y, mindustry.content.Blocks.stone,
            mindustry.content.Blocks.air, mindustry.content.Blocks.air));
      }
    }
    mindustry.Vars.world.tiles = tiles;

    Tile tile = mindustry.Vars.world.tile(16, 16);
    tile.setBlock(CStroage.core1Block, Team.sharded);
    CoreBuild source = (CoreBuild) tile.build;
    Corec converted = MobileCoreControlSystem.convertCore(source, CStroage.core1);
    check("physical core converts to the selected mobile core", true,
        converted != null
            && converted.type() == CStroage.core1
            && converted.proxy() != null
            && converted.isAdded()
            && tile.build == null);

    if (converted == null) {
      return;
    }

    Player previousPlayer = mindustry.Vars.player;
    Player testPlayer = Player.create();
    testPlayer.id = 9001;
    testPlayer.team(Team.sharded);
    mindustry.Vars.player = testPlayer;
    try {
      Unit legacyAvatar = CUnits.taichu.create(Team.sharded);
      legacyAvatar.set(converted.x(), converted.y());
      legacyAvatar.spawnedByCore(true);
      legacyAvatar.controller(testPlayer);
      legacyAvatar.add();
      testPlayer.unit(legacyAvatar);

      MobileCoreControlSystem.createPlayerMobileCore(testPlayer);
      check("integrated mode binds the core itself", converted, testPlayer.unit());
      check("integrated mode reports core control", true,
          MobileCoreControlSystem.isControllingCore(testPlayer));
      check("integrated core is marked as core-spawned", true, converted.spawnedByCore());
      check("integrated mode suppresses vanilla respawn key", true, Binding.respawn.isUnset());
      check("legacy possessed avatar is removed", false, legacyAvatar.isAdded());

      testPlayer.clearUnit();
      check("clearing core control does not crash or leave the core", converted,
          testPlayer.unit());

      CultivationState.availableMagicPower = 100f;
      int avatarCount = countConfiguredAvatar();
      check("no configured avatar is spawned", avatarCount, countConfiguredAvatar());

      MobileCoreControlSystem.togglePlayerUnit();
      check("core skill creates one independent companion", avatarCount + 1,
          countConfiguredAvatar());
      check("core skill possesses the companion", true,
          testPlayer.unit() != null && testPlayer.unit() != converted);
      check("possessing the companion does not remove the mobile core", true,
          converted.isAdded() && converted.proxy() != null);
      check("core skill reports its companion active", true,
          MobileCoreControlSystem.isPlayerUnitActive());
      check("V respawn key is available while possessing", false,
          Binding.respawn.isUnset());

      MobileCoreControlSystem.togglePlayerUnit();
      check("core skill returns player to the core", converted, testPlayer.unit());
      check("returning restores the mobile core", true,
          converted.isAdded() && converted.proxy() != null);
      check("voluntary detach removes the companion", avatarCount,
          countConfiguredAvatar());
      check("core skill reports companion not possessed", false,
          MobileCoreControlSystem.isPlayerUnitActive());
      check("V respawn key is suppressed while on the core", true,
          Binding.respawn.isUnset());

      MobileCoreControlSystem.togglePlayerUnit();
      check("core skill creates a fresh companion", avatarCount + 1,
          countConfiguredAvatar());

      testPlayer.clearUnit();
      testPlayer.checkSpawn();
      check("V-style respawn returns the player to the core", converted, testPlayer.unit());
      check("V-style respawn removes the companion", avatarCount,
          countConfiguredAvatar());
      check("V-style respawn releases companion control", false,
          MobileCoreControlSystem.isPlayerUnitActive());

      CultivationState.availableMagicPower = 1f;
      MobileCoreControlSystem.togglePlayerUnit();
      check("companion forms with available spirit power", avatarCount + 1,
          countConfiguredAvatar());
      CultivationState.availableMagicPower = 0f;
      MobileCoreControlSystem.updateIntegratedControl();
      check("zero spirit power dismisses the companion", avatarCount,
          countConfiguredAvatar());
      check("zero spirit power returns control to the core", converted, testPlayer.unit());
    } finally {
      mindustry.Vars.player = previousPlayer;
    }
  }

  private static int countConfiguredAvatar() {
    int count = 0;
    for (Unit unit : Groups.unit) {
      if (unit.type == CUnits.taichu) {
        count++;
      }
    }
    return count;
  }

  private static <T> T allocateWithoutConstructor(Class<T> type) {
    try {
      var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      return type.cast(((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(type));
    } catch (Throwable t) {
      throw new RuntimeException("cannot allocate " + type.getName(), t);
    }
  }
}
