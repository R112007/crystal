package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.util.Log;
import crystal.CVars;
import crystal.Crystal;
import crystal.game.UnitInfo;
import crystal.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.game.EventType.SectorLaunchLoadoutEvent;
import mindustry.game.EventType.SectorLoseEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.type.Sector;

public class UnitInfoSystem {
  private static final ObjectMap<Sector, Boolean> lastSaveState = new ObjectMap<>();

  public static void init() {
    Events.on(SectorLaunchEvent.class, e -> {
      addNewUnitInfo(e.sector);
    });
    Events.on(SectorLaunchLoadoutEvent.class, e -> {
      addNewUnitInfo(e.sector);
    });
    save();
    Events.on(SectorLoseEvent.class, e -> {
      if (UnitInfo.get(e.sector) != null)
        UnitInfo.get(e.sector).clear();
    });
    Events.on(GameOverEvent.class, e -> {
      if (e.winner != Vars.player.team()) {
        Sector currentSector = Vars.state.rules.sector;
        UnitInfo.get(currentSector).clear();
      }
    });
  }

  public static void update() {
    if (Crystal.timer % 30 == 1) {
      checkSectorSaveChanges();
    }
    if (Crystal.timer % 300 == 1) {
      saveUnitInfo();
      // UnitInfo.saveArray();
      if (CVars.debug)
        Time.logTime();
    }
  }

  private static void checkSectorSaveChanges() {
    for (var planet : Vars.content.planets()) {
      for (var sector : planet.sectors) {
        boolean currentSaveExists = sector.save != null;
        boolean lastSaveExists = lastSaveState.get(sector, false);

        if (lastSaveExists && !currentSaveExists) {
          onSectorSaveDeleted(sector); // 触发自定义逻辑
        }

        lastSaveState.put(sector, currentSaveExists);
      }
    }
  }

  private static void onSectorSaveDeleted(Sector sector) {
    UnitInfo.get(sector).clear();
    if (CVars.debug)
      Log.info("区块 " + sector.name() + " 的save已被删除并置空");
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
      }
    });
  }

  public static void saveUnitInfo() {
    int amount = UnitInfo.returnLastId();
    for (int i = 0; i < amount; i++) {
      if (UnitInfo.all[i] != null) {
        UnitInfo.all[i].saveInfo();
      }
    }
  }

  public static void checkAllSector() {
    for (var planet : Vars.content.planets()) {
      for (var sector : planet.sectors) {
        if (sector.hasBase()) {
          if (UnitInfo.get(sector) == null) {
            if (CVars.debug)
              Log.err("发现区块" + sector + "没有UnitInfo");
            try {
              addNewUnitInfo(sector);
              if (CVars.debug) {
                Log.info("成功创建");
                Log.info("已为区块" + sector + "新建UnitInfo");
                Log.info(UnitInfo.get(sector));
              }
            } catch (Exception e) {
              Log.err(e);
              Log.info("区块" + sector + "新建UnitInfo失败");
            }
          }
        }
      }
    }
  }

  public static void loadUnitInfo() {
    String[] load = Core.settings.getJson("unitinfo.jsonkeys", String[].class, () -> new String[UnitInfo.loadLastId()]);
    UnitInfo.jsonKeys.addAll(load);
    for (var k : UnitInfo.jsonKeys) {
      UnitInfo.loadUnitInfo(k);
    }
  }
}
