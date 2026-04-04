package crystal.util;

import static arc.math.Mathf.*;
import arc.math.geom.Vec2;

public class CMath {
  public static Vec2 getOutMiddle(float x1, float y1, float x2, float y2, float x3, float y3) {
    float x = ((y2 - y1) * (pow(x3, 2) + pow(y3, 2) - pow(x2, 2) - pow(y2, 2))
        - (y3 - y2) * (pow(x2, 2) + pow(y2, 2) - pow(x1, 2) - pow(y1, 2)))
        / 2 * ((x2 - x1) * (y3 - y2) - (x3 - x2) * (y2 - y1));
    float y = ((x3 - x2) * (pow(x2, 2) + pow(y2, 2) - pow(x1, 2) - pow(y1, 2))
        - (x2 - x1) * (pow(x3, 2) + pow(y3, 2) - pow(x2, 2) - pow(y2, 2)))
        / 2 * ((x2 - x1) * (y3 - y2) - (x3 - x2) * (y2 - y1));
    return new Vec2(x, y);
  }
}
