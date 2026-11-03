package crystal.content;

import crystal.type.MobileCoreSectorPreset;

public class LxMaps {
  public static MobileCoreSectorPreset jianglindian, jingliuduanxia;

  public static void load() {
    jianglindian = new MobileCoreSectorPreset("jianglindian", CPlanets.lx, 0) {
      {
        this.startSector = true;
        this.alwaysUnlocked = true;
        this.difficulty = 1;
        this.captureWave = 30;
        this.overrideLaunchDefaults = true;
        this.startWaveTimeMultiplier = 3f;
      }
    };
    jingliuduanxia = new MobileCoreSectorPreset("jingliuduanxia", CPlanets.lx, 96) {
      {
        this.difficulty = 1;
        this.captureWave = 40;
        this.startWaveTimeMultiplier = 2.3f;
      }
    };
  }
}
