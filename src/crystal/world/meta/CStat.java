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
  public static final Stat produceCrystal;
  public static final Stat MaxCrystalE;
  public static final Stat insideCrystalE;
  public static final Stat hasCrystal;
  public static final Stat healpercent;
  public static final Stat dependbuild;
  public static final Stat dependfloor;
  public static final Stat maxBlock;
  public static final Stat shenTong;
  public static final Stat xiuWei;
  public static final Stat magicPower;
  public static final Stat magicPowerRegenTime;
  public static final Stat magicPowerRegen;
  public static final Stat storageCapacity;
  public static final Stat suckRange;

  // aviation
  public static final Stat satelliteLimit;
  public static final Stat expansionArea;
  public static final Stat expansionCost;
  public static final Stat injectAmount;
  public static final Stat requestAmount;
  public static final Stat transferInterval;
  public static final Stat launchCost;
  public static final Stat missileLifetime;
  public static final Stat splashDamage;
  public static final Stat missileTypes;
  public static final Stat itemCapacityIncrease;
  public static final Stat liquidCapacityIncrease;

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
    produceCrystal = new Stat("produceCrystal", CStatCat.crystal);
    healpercent = new Stat("healpercent", StatCat.function);
    dependbuild = new Stat("dependbuild", CStatCat.depend);
    dependfloor = new Stat("dependfloor", CStatCat.depend);
    maxBlock = new Stat("maxBlock", StatCat.function);
    shenTong = new Stat("shenTong");
    xiuWei = new Stat("xiuWei", CStatCat.magic);
    magicPower = new Stat("magicPower", CStatCat.magic);
    magicPowerRegen = new Stat("magicPowerRegen", CStatCat.magic);
    magicPowerRegenTime = new Stat("magicPowerRegenTime", CStatCat.magic);
    storageCapacity = new Stat("storageCapacity", StatCat.items);
    suckRange = new Stat("suckRange");

    satelliteLimit = new Stat("satelliteLimit", StatCat.function);
    expansionArea = new Stat("expansionArea", StatCat.function);
    expansionCost = new Stat("expansionCost", StatCat.items);
    injectAmount = new Stat("injectAmount", StatCat.items);
    requestAmount = new Stat("requestAmount", StatCat.items);
    transferInterval = new Stat("transferInterval", StatCat.function);
    launchCost = new Stat("launchCost", StatCat.items);
    missileLifetime = new Stat("missileLifetime", StatCat.function);
    splashDamage = new Stat("splashDamage", StatCat.function);
    missileTypes = new Stat("missileTypes", StatCat.function);
    itemCapacityIncrease = new Stat("itemCapacityIncrease", StatCat.items);
    liquidCapacityIncrease = new Stat("liquidCapacityIncrease", StatCat.liquids);
  }

}
