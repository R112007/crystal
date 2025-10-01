package crystal.game;

import arc.Core;
import arc.math.WindowedMean;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import crystal.type.UnitStack;
import mindustry.Vars;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.type.UnitType;

public class UnitInfo {

  private static final int valueWindow = 60;
  public String planetName;
  public int id;
  public int sectorId;
  public static final UnitInfo[] all = new UnitInfo[3000];
  public Seq<UnitStack> possessed = new Seq<>();
  public ObjectMap<UnitType, ExportStat> export = new ObjectMap<>();
  public ObjectMap<UnitType, ExportStat> imports = new ObjectMap<>();
  public static int lastId = loadLastId();

  public UnitInfo(Sector sector) {
    this.planetName = sector.planet.name;
    this.sectorId = sector.id;
    this.id = lastId;
    all[id] = this;
    lastId++;
    saveLastId();
  }

  public UnitInfo(String planetName, int sectorId, int id) {
    this.planetName = planetName;
    this.sectorId = sectorId;
    this.id = id;
  }

  public UnitInfo() {
  }

  public void handUnitsPossessed(UnitStack[] stacks) {
    for (var stack : stacks) {
      handUnitsExport(stack);
    }
  }

  public void handUnitsPossessed(UnitStack stack) {
    handUnitsPossessed(stack.unit, stack.amount);
  }

  public UnitStack getPossessedUnitStack(UnitType unit) {
    UnitStack tmp = null;
    for (var stack : possessed) {
      if (stack.unit == unit)
        tmp = stack;
    }
    return tmp;
  }

  public void handUnitsPossessed(UnitType unit, int amount) {
    if (hasUnitType(unit)) {
      for (var stack : possessed) {
        if (stack.unit == unit)
          stack.amount += amount;
      }
    } else {
      possessed.add(new UnitStack(unit, amount));
    }
    // for (var stack : possessed) {
    // Log.info("种类" + stack.unit.name + " " + "数量" + stack.amount);
    // }
  }

  public boolean hasUnitType(UnitType unit) {
    // Log.info("传入单位" + unit.name);
    for (var stack : possessed) {
      if (stack.unit.name.equals(unit.name)) {
        // Log.info("配对成功的单位" + stack.unit.name);
        return true;
      }
    }
    return false;
  }

  public void handUnitsExport(UnitStack stack) {
    handUnitsExport(stack.unit, stack.amount);
  }

  public void handUnitsExport(UnitType unit, int amount) {
    export.get(unit, ExportStat::new).counter += amount;
  }

  public void handUnitsImport(UnitType unit, int amount) {
    imports.get(unit, ExportStat::new).counter += amount;
  }

  public void handUnitsImport(UnitStack stack) {
    handUnitsImport(stack.unit, stack.amount);
  }

  public static boolean equal(Sector sector) {
    return get(sector) != null;
  }

  public void clear() {
    imports.clear();
    export.clear();
    Log.info(getBoundSector() + "丢失，清空单位储存库 " + "import " + imports + " export " + export);
    saveInfo();
  }

  public static UnitInfo get(Sector targetSector) {
    for (UnitInfo info : all) {
      if (info == null) {
        continue;
      }
      if (info.getBoundSector().equals(targetSector)) {
        return info;
      }
    }
    return null;
  }

  private static int loadLastId() {
    return Core.settings.getInt("unitinfo.lastid", 0);
  }

  public static int returnLastId() {
    return Core.settings.getInt("unitinfo.lastid", 0);
  }

  private static void saveLastId() {
    Core.settings.put("unitinfo.lastid", lastId);
    Core.settings.manualSave();
  }

  public Sector getBoundSector() {
    Planet planet = Vars.content.planet(planetName);
    if (planet == null)
      return null;
    return planet.sectors.get(sectorId);
  }

  public void saveInfo() {
    Core.settings.putJson(planetName + "-s-" + id + "-unitinfo", this);
  }

  public void loadUnitInfo() {
    UnitInfo info = Core.settings.getJson(planetName + "-s-" + id + "-unitinfo", UnitInfo.class, UnitInfo::new);
    all[info.id] = info;
    Log.info(info + "已被恢复");
  }

  public static class ExportStat {
    public transient float counter;
    public transient WindowedMean means = new WindowedMean(valueWindow);
    public transient boolean loaded;

    public float mean;
    public int amount;

    public String toString() {
      return mean + "";
    }
  }
}
