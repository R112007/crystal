package crystal.core;

import java.util.Date;

import arc.Events;
import arc.util.Log;
import crystal.Crystal;
import crystal.game.UnitInfo;
import crystal.game.UnitInfoFileStorage;
import mindustry.core.GameState;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.game.EventType.SectorLaunchLoadoutEvent;
import mindustry.game.EventType.SectorLoseEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.type.Sector;

public class UnitInfoSystem {
  static Date d = new Date();

  public static void init() {
    Events.on(SectorLaunchEvent.class, e -> {
      addNewUnitInfo(e.sector);
    });
    Events.on(SectorLaunchLoadoutEvent.class, e -> {
      addNewUnitInfo(e.sector);
    });
    save();
    Events.on(SectorLoseEvent.class, e -> {
      UnitInfo.get(e.sector).clear();
    });
  }

  public static void update() {
    if (Crystal.timer % 300 == 1) {
      saveUnitInfo();
      UnitInfoFileStorage.saveAll();
      Log.info(d.getTime());
    }
  }

  public static boolean check(Sector sector) {
    for (int i = 0; i < UnitInfo.lastId; i++) {
      if (UnitInfo.all[i] != null) {
        if (UnitInfo.all[i].getBoundSector() == sector)
          return true;
      }
    }
    return false;
  }

  public static void addNewUnitInfo(Sector sector) {
    if (check(sector)) {
      return;
    }
    new UnitInfo(sector);
  }

  public static void save() {
    Events.on(StateChangeEvent.class, event -> {
      if (event.to == GameState.State.menu) {
        saveUnitInfo();
        UnitInfoFileStorage.saveAll();
        Log.info("lastid " + UnitInfo.returnLastId());
        for (var u : UnitInfo.all) {
          if (u != null)
            Log.info("id+" + u.id + "  sector " + u.getBoundSector());
        }
      }
    });
  }

  public static void saveUnitInfo() {
    int amount = UnitInfo.returnLastId();
    for (int i = 0; i < amount; i++) {
      if (UnitInfo.all[i] != null) {
        UnitInfo.all[i].saveInfo();
        Log.info("已保存" + UnitInfo.all[i]);
      }
    }
  }
}
