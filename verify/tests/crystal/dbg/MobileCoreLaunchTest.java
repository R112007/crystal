package crystal.dbg;

import arc.Core;
import arc.Events;
import crystal.content.CPlanets;
import crystal.content.LxMaps;
import crystal.content.SpecialUnits;
import crystal.type.MobileCoreSectorPreset;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.content.UnitTypes;
import mindustry.type.Sector;

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
      check("mobile core controls itself by default", SpecialUnits.moveCore1,
          SpecialUnits.moveCore1.playerUnitType());

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
