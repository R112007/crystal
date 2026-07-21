package crystal.entities;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.ai.BlockIndexer;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/**
 * 静态剑光效果。在指定坐标处以随机角度挥出一道弯曲的蓝色剑光，
 * 对路径附近非己方队伍的 Healthc 单位与建筑造成伤害。
 *
 * 用法：
 * 
 * <pre>
 * SwordLight.create(Team.sharded, player.x, player.y, 120f, 160f, 8f);
 * </pre>
 */
public class SwordLight {
    /** 剑光主色调 */
    public static final Color swordColor = Color.valueOf("67d4ff");
    /** 剑光外层暗色 */
    public static final Color swordBackColor = Color.valueOf("0066ff");
    /** 剑光默认宽度 */
    public static final float baseWidth = 5f;
    /** 曲线采样段数 */
    public static final int segments = 36;
    /** 伤害判定宽度加成 */
    public static final float hitExtra = 3f;

    private static final Vec2 tmp = new Vec2();
    private static final Vec2 tmp2 = new Vec2();
    private static final Seq<Vec2> pointPool = new Seq<>();
    private static final Rect rect = new Rect();

    /** 剑光视觉效果 */
    public static final Effect swordLightFx = new Effect(28f, e -> {
        if (!(e.data instanceof SwordLightData data))
            return;
        draw(data.points, data.width, 1f - e.fin());
    }).layer(Layer.effect + 0.1f);

    /**
     * 在指定坐标处创建一道剑光，角度随机，使用默认宽度。
     * 传入的 (x, y) 为剑光中点，起点和终点由随机角度决定。
     */
    public static void create(Team team, float x, float y, float damage, float length) {
        create(team, x, y, damage, length, Mathf.random(360f), baseWidth);
    }

    /**
     * 在指定坐标处创建一道剑光，角度随机，可指定宽度。
     */
    public static void createWide(Team team, float x, float y, float damage, float length, float width) {
        create(team, x, y, damage, length, Mathf.random(360f), width);
    }

    /**
     * 在指定坐标处以指定角度创建一道剑光，使用默认宽度。
     * 传入的 (x, y) 为剑光中点。
     */
    public static void create(Team team, float x, float y, float damage, float length, float angle) {
        create(team, x, y, damage, length, angle, baseWidth);
    }

    /**
     * 在指定坐标处以指定角度创建一道剑光，可指定宽度。
     */
    public static void create(Team team, float x, float y, float damage, float length, float angle, float width) {
        Seq<Vec2> points = generateCurve(x, y, angle, length);

        applyDamage(team, points, damage, width);

        if (!headless) {
            swordLightFx.at(x, y, angle, swordColor, new SwordLightData(points, width));
        }
    }

    /** 生成一条以 (x, y) 为中点的二次贝塞尔弯曲曲线。 */
    private static Seq<Vec2> generateCurve(float x, float y, float angle, float length) {
        pointPool.clear();

        Vec2 mid = new Vec2(x, y);
        tmp.trns(angle, length / 2f);
        Vec2 start = new Vec2(mid).sub(tmp);
        Vec2 end = new Vec2(mid).add(tmp);

        // 控制点：中点沿垂直方向偏移，产生自然弯曲
        float curve = length * Mathf.random(0.12f, 0.32f) * (Mathf.random() < 0.5f ? -1f : 1f);
        Vec2 control = new Vec2(mid);
        tmp2.trns(angle + 90f, curve);
        control.add(tmp2);

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            pointPool.add(bezier(start, control, end, t));
        }
        return pointPool;
    }

    private static Vec2 bezier(Vec2 a, Vec2 b, Vec2 c, float t) {
        float u = 1f - t;
        return new Vec2(
                u * u * a.x + 2f * u * t * b.x + t * t * c.x,
                u * u * a.y + 2f * u * t * b.y + t * t * c.y);
    }

    /** 对曲线附近的敌方单位与建筑造成伤害。 */
    private static void applyDamage(Team team, Seq<Vec2> points, float damage, float width) {
        if (damage <= 0f || points.size < 2)
            return;

        // 计算曲线包围盒
        float minX = points.first().x, minY = points.first().y;
        float maxX = points.first().x, maxY = points.first().y;
        for (Vec2 p : points) {
            if (p.x < minX)
                minX = p.x;
            if (p.y < minY)
                minY = p.y;
            if (p.x > maxX)
                maxX = p.x;
            if (p.y > maxY)
                maxY = p.y;
        }
        rect.setPosition(minX, minY)
                .setSize(maxX - minX, maxY - minY)
                .normalize()
                .grow((width + hitExtra) * 2f);

        // 伤害敌方单位
        Units.nearbyEnemies(team, rect, u -> {
            if (!u.hittable())
                return;
            float dst = distanceToCurve(u.x, u.y, points);
            if (dst < width + hitExtra + u.hitSize / 2f) {
                u.damage(damage);
                u.hitTime = 10f;
            }
        });

        // 伤害敌方建筑
        indexer.allBuildings(rect.x + rect.width / 2f, rect.y + rect.height / 2f,
                Math.max(rect.width, rect.height) / 2f, b -> {
                    if (b.team == team || !(b instanceof Healthc))
                        return;
                    float dst = distanceToCurve(b.x, b.y, points);
                    float size = b.block.size * tilesize / 2f;
                    if (dst < width + hitExtra + size) {
                        b.damage(damage);
                    }
                });
    }

    /** 计算点到折线的最短距离。 */
    private static float distanceToCurve(float px, float py, Seq<Vec2> points) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            float d = Intersector.distanceSegmentPoint(a.x, a.y, b.x, b.y, px, py);
            if (d < min)
                min = d;
        }
        return min;
    }

    /** 绘制剑光：使用白色图片作为主体，通过 Draw.color 染色，沿曲线旋转拼接。 */
    private static void draw(Seq<Vec2> points, float width, float alpha) {
        if (points.size < 2)
            return;

        TextureRegion white = Core.atlas.find("crystal-swordlight");
        Draw.z(Layer.effect + 0.1f);

        int n = points.size - 1;

        for (int i = 0; i < n; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            float mx = (a.x + b.x) / 2f;
            float my = (a.y + b.y) / 2f;
            float len = a.dst(b);
            float ang = Angles.angle(a.x, a.y, b.x, b.y);
            float t = i / (float) n;

            // 剑光形状：起点略窄 -> 中间最宽 -> 终点快速收尖
            float shape = Mathf.sin(t * Mathf.pi) * 0.7f + 0.3f;
            if (t > 0.7f) {
                shape *= 1f - (t - 0.7f) / 0.3f;
            }

            float w = len + 4f;
            float h = width * 2.2f * shape;

            // 外层光晕（宽、半透明）
            Draw.color(swordBackColor, alpha * 0.45f);
            Draw.rect(white, mx, my, w, h * 1.7f, ang);

            // 主体颜色
            Draw.color(swordColor, alpha * 0.9f);
            Draw.rect(white, mx, my, w, h, ang);

            // 白色亮芯
            if (t > 0.1f && t < 0.85f) {
                Draw.color(Color.white, alpha * 0.85f);
                Draw.rect(white, mx, my, w, h * 0.35f, ang);
            }
        }

        // 沿途点光源
        for (int i = 0; i < points.size; i += 4) {
            Vec2 p = points.get(i);
            Drawf.light(p.x, p.y, width * 4f * alpha, swordColor, 0.35f * alpha);
        }

        Draw.reset();
    }

    /** 供 Effect 传递的曲线数据。 */
    public static class SwordLightData {
        public final Seq<Vec2> points;
        public final float width;

        public SwordLightData(Seq<Vec2> points, float width) {
            this.points = points;
            this.width = width;
        }
    }
}
