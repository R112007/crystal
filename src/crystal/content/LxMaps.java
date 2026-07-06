package crystal.content;

import mindustry.type.SectorPreset;

public class LxMaps {
  public static SectorPreset jianglindian, jingliuduanxia;

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
    jingliuduanxia = new SectorPreset("jingliuduanxia", CPlanets.lx, 1) {
      {
        this.difficulty = 1;
        this.captureWave = 40;
        this.startWaveTimeMultiplier = 2.3f;
      }
    };
  }
}
