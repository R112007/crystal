package crystal.entities;

import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import mindustry.core.World;
import mindustry.gen.Bullet;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class CDamage {
  private static final Vec2 vec = new Vec2();
  private static Tile furthest;

  public static float findLaserLength(Bullet b, float length, Vec2 circleCenter, float circleRadius) {
    // 1. 计算激光子弹的理论线段（起点：子弹当前位置，终点：初始长度方向的最远点）
    Vec2 start = new Vec2(b.x, b.y); // 激光起点（子弹位置）
    Vec2 end = new Vec2().trnsExact(b.rotation(), length).add(start); // 激光理论终点
    float squareRadius = circleRadius * circleRadius; // 圆的平方半径（避免开根号，提升效率）

    // 2. 检测线段（start→end）是否与目标圆相交
    boolean isIntersect = Intersector.intersectSegmentCircle(start, end, circleCenter, squareRadius);
    if (isIntersect) {
      // 3. 计算线段与圆的碰撞点，取碰撞点到子弹的距离作为激光实际长度
      Vec2 collisionPoint = new Vec2();
      Intersector.intersectSegmentCircleDisplace(start, end, circleCenter, squareRadius, collisionPoint);
      float actualLength = b.dst(collisionPoint.x, collisionPoint.y);
      return Math.max(6f, actualLength); // 确保长度不小于6（避免异常）
    }

    // 4. 若未碰到圆，按原逻辑检测是否有吸收激光的建筑（可选，也可直接返回初始长度）
    // （保留原建筑检测逻辑，避免完全丢弃原有功能，若仅需碰圆可删除这部分）
    vec.trnsExact(b.rotation(), length);
    boolean foundBuilding = World.raycast(b.tileX(), b.tileY(), World.toTile(b.x + vec.x), World.toTile(b.y + vec.y),
        (x, y) -> (furthest = world.tile(x, y)) != null && furthest.team() != b.team
            && (furthest.build != null && furthest.build.absorbLasers()));
    if (foundBuilding && furthest != null) {
      return Math.max(6f, b.dst(furthest.worldx(), furthest.worldy()));
    }

    // 5. 既没碰到圆也没碰到建筑，返回初始长度
    return length;
  }
}
