package crystal.map.planet;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec3;
import arc.util.noise.Simplex;
import mindustry.maps.planet.SerpuloPlanetGenerator;
import mindustry.type.Sector;

public class LxPlanetGenerator extends SerpuloPlanetGenerator {
  public Color c1 = Color.valueOf("#D2F0FFFF");
  public Color c2 = Color.valueOf("#349988FF");
  public float heightScl = 1f, octaves = 8, persistence = 0.7f;
  public final Rand rand = new Rand();

  float rawHeights(float minVal, float maxVal, int seed, float x, float y, float z, double octaves, double persistence,
      double scale) {
    float rawNoise = Simplex.noise3d(seed, octaves, persistence, scale, x, y, z);
    float normalized = (rawNoise + 1.0f) / 2f;
    return minVal + (maxVal - minVal) * normalized;
  }

  @Override
  public float getHeight(Vec3 position) {
    return Math
        .abs(rawHeights(0f, 5f, 13, position.x, position.y, position.z, 4, 0.5, 0.05) - super.getHeight(position));
  }

  @Override
  public boolean allowLanding(Sector sector) {
    return true;
  }

  @Override
  public void getColor(Vec3 position, Color out) {
    float depth = Simplex.noise3d(seed, 2, 0.56, 1.7f, position.x, position.y, position.z) / 2f;
    out.set(c1).lerp(c2, Mathf.clamp(Mathf.round(depth, 0.15f))).a(1f - 0.2f).toFloatBits();
  }

}
