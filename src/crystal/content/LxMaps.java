package crystal.content;

import mindustry.type.SectorPreset;

public class LxMaps {
  public static SectorPreset jianglindian;

  public static void load() {
    jianglindian = new SectorPreset("jianglindian", CPlanets.lx, 0) {
      {
        this.alwaysUnlocked = true;
        this.difficulty = 1;
        this.captureWave = 30;
        this.overrideLaunchDefaults = true;
        this.startWaveTimeMultiplier = 3f;
      }
    };
  }
}
