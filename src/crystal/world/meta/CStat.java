package crystal.world.meta;

import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

public class CStat {
  public static final Stat armorMultiplier;
  public static final Stat crystalEnergy;
  public static final Stat basechangetime;
  public static final Stat changetime;
  public static final Stat waittime;
  public static final Stat length;
  public static final Stat reducepercent;
  public static final Stat consumeCrystalE;
  public static final Stat MaxCrystalE;
  public static final Stat insideCrystalE;;
  public static final Stat hasCrystal;
  public static final Stat healpercent;
  public static final Stat dependbuild;
  public static final Stat dependfloor;

  static {
    armorMultiplier = new Stat("armorMultiplier");
    crystalEnergy = new Stat("crystalEnergy");
    basechangetime = new Stat("basechangetime");
    changetime = new Stat("changetime", StatCat.function);
    waittime = new Stat("waittime", StatCat.function);
    length = new Stat("length");
    reducepercent = new Stat("reducepercent", StatCat.function);
    hasCrystal = new Stat("hasCrystal", CStatCat.crystal);
    consumeCrystalE = new Stat("consumeCrystalE", CStatCat.crystal);
    MaxCrystalE = new Stat("MaxCrystalE", CStatCat.crystal);
    insideCrystalE = new Stat("insideCrystalE", CStatCat.crystal);
    healpercent = new Stat("healpercent", StatCat.function);
    dependbuild = new Stat("dependbuild", CStatCat.depend);
    dependfloor = new Stat("dependfloor", CStatCat.depend);
  }

}
