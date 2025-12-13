package crystal.type.weather;

import arc.graphics.Color;
import arc.struct.ObjectSet;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Weather;

public class LightningStorm extends Weather {
  static ObjectSet<Building> powers = new ObjectSet<>();
  public Color lightningColor = Pal.surge;
  public int lightning;
  public int lightningLength = 5;
  public int lightningLengthRand = 0;
  public float lightningDamage = -1;
  public float lightningCone = 360f;
  public float lightningAngle = 0f;

  public LightningStorm(String name) {
    super(name);
  }
}
