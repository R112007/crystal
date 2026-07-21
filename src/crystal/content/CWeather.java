package crystal.content;

import crystal.type.weather.LightningStorm;

public class CWeather {
  public static LightningStorm lightningStorm;

  public static void load() {
    lightningStorm = new LightningStorm("lightningStorm") {
      {
        lightning = 40;
        lightningLength = 120;
        lightningLengthRand = 40;
        lightningDamage = 40;
        delay = 5;
      }
    };
  }
}
