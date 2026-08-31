package crystal.graphics;

import java.util.Arrays;

import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.util.Structs;
import static mindustry.Vars.*;

public class CDrawf {
  private static final Vec2[] beamVecs = { new Vec2(), new Vec2(), new Vec2(), new Vec2() };

  public static void buildBeam(float x, float y, float tx, float ty, float radius) {
    float ang = Angles.angle(x, y, tx, ty);

    beamVecs[0].set(tx - radius, ty - radius);
    beamVecs[1].set(tx + radius, ty - radius);
    beamVecs[2].set(tx - radius, ty + radius);
    beamVecs[3].set(tx + radius, ty + radius);

    Arrays.sort(beamVecs, Structs.comparingFloat(vec -> -Angles.angleDist(Angles.angle(x, y, vec.x, vec.y), ang)));

    Vec2 close = Geometry.findClosest(x, y, beamVecs);

    float x1 = beamVecs[0].x, y1 = beamVecs[0].y,
        x2 = close.x, y2 = close.y,
        x3 = beamVecs[1].x, y3 = beamVecs[1].y;

    if (renderer.animateShields) {
      if (close != beamVecs[0] && close != beamVecs[1]) {
        Fill.tri(x, y, x1, y1, x2, y2);
        Fill.tri(x, y, x3, y3, x2, y2);
      } else {
        Fill.tri(x, y, x1, y1, x3, y3);
      }
    } else {
      Lines.line(x, y, x1, y1);
      Lines.line(x, y, x3, y3);
    }
    Fill.rect(tx, ty, radius * 2f, radius * 2f);
  }

}
