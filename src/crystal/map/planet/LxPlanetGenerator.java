package crystal.map.planet;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.math.geom.Vec3;
import arc.util.Tmp;
import arc.util.noise.Simplex;
import crystal.graphics.CPal;
import mindustry.content.Blocks;
import mindustry.game.Schematics;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.world.Block;
import mindustry.world.TileGen;

import static mindustry.Vars.*;

public class LxPlanetGenerator extends PlanetGenerator {
  float heightYOffset = 48.82f;
  // 山尖不尖，越大越尖
  float scl = 2.8f;
  float waterOffset = 0.01f;
  float heightScl = 1.08f;
  Block[][] arr = {
      { Blocks.redmat, Blocks.redmat, Blocks.darksand, Blocks.bluemat, Blocks.bluemat }
  };
  float water = 2f / arr[0].length;

  float rawHeight(Vec3 position) {
    return (Mathf.pow(
        Simplex.noise3d(seed, 7, 0.5f, 1f / 3f, position.x * scl, position.y * scl + heightYOffset, position.z * scl)
            * heightScl,
        2.3f) + waterOffset) / (1f + waterOffset);
  }

  @Override
  public float getHeight(Vec3 position) {
    float height = rawHeight(position);
    return Math.max(height, water);
  }

  @Override
  public void getColor(Vec3 position, Color out) {
    float depth = Simplex.noise3d(seed, 2, 0.56, 1.7f, position.x, position.y, position.z) / 2f;
    out.set(CPal.light_blue1).lerp(CPal.dark_blue1, Mathf.clamp(Mathf.round(depth, 0.15f))).a(1f - 0.2f).toFloatBits();
  }

  @Override
  public float getSizeScl() {
    return 2000;
  }

  @Override
  public void genTile(Vec3 position, TileGen tile) {
    tile.floor = getBlock(position);
    if (tile.floor == Blocks.redmat && rand.chance(0.1)) {
      tile.block = Blocks.redweed;
    }
    if (tile.floor == Blocks.bluemat && rand.chance(0.03)) {
      tile.block = Blocks.purbush;
    }
    if (tile.floor == Blocks.bluemat && rand.chance(0.002)) {
      tile.block = Blocks.yellowCoral;
    }
  }

  @Override
  protected void generate() {
    pass((x, y) -> {
      float max = 0;
      for (Point2 p : Geometry.d8) {
        max = Math.max(max, world.getDarkness(x + p.x, y + p.y));
      }
      if (max > 0) {
        block = floor.asFloor().wall;
      }

      if (noise(x, y, 40f, 1f) > 0.9) {
        block = Blocks.deepwater;
      }
    });

    Schematics.placeLaunchLoadout(width / 2, height / 2);
  }

  Block getBlock(Vec3 position) {
    float height = rawHeight(position);
    Tmp.v31.set(position);
    position = Tmp.v33.set(position).scl(2f);
    float temp = Simplex.noise3d(seed, 8, 0.6, 1f / 2f, position.x, position.y + 99f, position.z);
    height *= 1.2f;
    height = Mathf.clamp(height);
    return arr[Mathf.clamp((int) (temp * arr.length), 0, arr[0].length - 1)][Mathf.clamp((int) (height * arr[0].length),
        0, arr[0].length - 1)];
  }
}
