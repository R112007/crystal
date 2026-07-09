package crystal.entities.bullet;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.core.World;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Tile;

import static mindustry.Vars.*;

/**
 * 持续链式闪电子弹类型。
 * 
 * 仿照 ContinuousLaserBulletType 设计：
 * - 子弹位置由 Weapon.update() 外部同步到枪口
 * - 本类只负责：伤害逻辑 + 闪电视觉效果绘制
 */
public class ContinuousChainLightningBulletType extends ContinuousBulletType {

    // ★ 闪电外观参数
    public float width = 6f;
    public float segmentLength = 6f;
    public float arc = 0.15f;
    public Color lightningColor = Pal.techBlue;

    // ★ 闪电行为参数
    public float jumpDamageFactor = 0.85f;
    public float distanceDamageFalloff = 0.65f;
    public float targetRange = -1f;
    public int chainLightning = 3;
    public int branches = 2;

    // ★ 集中度控制 (0=完全分散, 1=全部集中到一条线)
    public float focus = 1f;

    public ContinuousChainLightningBulletType() {
        super();
        speed = 0f;
        lifetime = 16f;
        damageInterval = 5f;
        despawnEffect = Fx.none;
        hitEffect = Fx.hitLancer;
        keepVelocity = false;
        hittable = false;
        collides = false;
        collidesTeam = false;
        continuous = true;
        largeHit = true;
        drawSize = 420f;
    }

    @Override
    public void init() {
        super.init();
        if (targetRange == -1)
            targetRange = range;
    }

    @Override
    protected float calculateRange() {
        return Math.max(range, targetRange);
    }

    @Override
    public float continuousDamage() {
        if (!continuous)
            return -1f;
        return damage * chainLightning * 60f / damageInterval;
    }

    @Override
    public float estimateDPS() {
        if (!continuous)
            return super.estimateDPS();
        return damage * 100f / damageInterval * chainLightning * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new LightningData();
    }

    @Override
    public void update(Bullet b) {
        if (!continuous)
            return;

        if (b.timer(1, damageInterval)) {
            applyDamage(b);
        }

        if (shake > 0) {
            Effect.shake(shake, shake, b);
        }

        updateBulletInterval(b);
    }

    @Override
    public void applyDamage(Bullet b) {
        boolean hasAimTile = b.aimTile != null;
        float aimX = hasAimTile ? b.aimTile.worldx() : b.aimX;
        float aimY = hasAimTile ? b.aimTile.worldy() : b.aimY;

        if (Float.isNaN(aimX) || Float.isNaN(aimY)) {
            aimX = b.x;
            aimY = b.y;
        }

        Vec2 aimPos = Tmp.v1.set(aimX, aimY);

        Seq<Unit> units = new Seq<>();
        Groups.unit.intersect(b.x - range, b.y - range, range * 2, range * 2, unit -> {
            if (unit != null && unit.team != b.team && unit.isAdded() && !unit.dead) {
                units.add(unit);
            }
        });

        units.sort(u -> u.dst2(aimPos));

        int charges = chainLightning;
        float targetRangeSq = targetRange * targetRange;

        LightningData data = b.data instanceof LightningData ? (LightningData) b.data : null;
        if (data == null)
            return;

        data.targets.clear();
        data.holders.clear();

        for (int i = 0; i < Math.min(chainLightning, units.size); i++) {
            Unit unit = units.get(i);
            float dst = unit.dst(b);
            if (dst > range)
                break;
            float dst2 = unit.dst2(aimPos);
            if (dst2 > targetRangeSq)
                break;

            float distanceFactor = 1f - (dst / range) * distanceDamageFalloff;
            float finalDamage = damage * Math.max(distanceFactor, 0.1f);

            unit.damage(finalDamage);
            hitEffect.at(unit.x, unit.y);

            data.targets.add(new LightningTarget(unit.x, unit.y, true));
            data.holders.add(new LightningHolder(b.x, b.y, unit.x, unit.y, width, segmentLength, arc));

            charges--;
            if (charges <= 0)
                break;
        }

        if (charges > 0) {
            Seq<Building> buildings = new Seq<>();

            Geometry.circle(b.tileX(), b.tileY(), (int) (range / tilesize), (x, y) -> {
                Tile t = world.tile(x, y);
                if (t != null && t.build != null && t.build.team != b.team) {
                    if (t.build.dst2(aimPos) <= targetRangeSq && !buildings.contains(t.build)) {
                        buildings.add(t.build);
                    }
                }
            });

            buildings.sort(bld -> bld.dst2(aimPos));

            int builds = Math.min(charges, buildings.size);
            for (int i = 0; i < builds; i++) {
                Building target = buildings.get(i);
                float dst = target.dst(b);
                float distanceFactor = 1f - (dst / range) * distanceDamageFalloff;
                float finalDamage = damage * Math.max(distanceFactor, 0.1f);

                target.damage(finalDamage);
                hitEffect.at(target.x, target.y);

                data.targets.add(new LightningTarget(target.x, target.y, false));
                data.holders.add(new LightningHolder(b.x, b.y, target.x, target.y, width, segmentLength, arc));

                charges--;
                if (charges <= 0)
                    break;
            }
        }

        if (charges > 0) {
            for (int i = 0; i < charges; i++) {
                float endX, endY;

                if (focus >= 0.99f) {
                    endX = aimX;
                    endY = aimY;
                } else {
                    float spread = (1f - focus) * 60f;
                    endX = aimX + Mathf.range(spread);
                    endY = aimY + Mathf.range(spread);
                }

                float dst2 = Mathf.dst2(endX, endY, b.x, b.y);
                if (dst2 > range * range) {
                    Tmp.v1.set(endX - b.x, endY - b.y).setLength(range * 0.9f);
                    endX = b.x + Tmp.v1.x;
                    endY = b.y + Tmp.v1.y;
                }

                data.targets.add(new LightningTarget(endX, endY, false));
                data.holders.add(new LightningHolder(b.x, b.y, endX, endY, width * 0.6f, segmentLength, arc * 0.8f));
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        LightningData data = b.data instanceof LightningData ? (LightningData) b.data : null;
        if (data == null)
            return;

        for (LightningHolder holder : data.holders) {
            holder.start.set(b.x, b.y);
            drawLightningEffect(b.x, b.y, holder.end.x, holder.end.y, holder.width, holder.segLength, holder.arc);
        }
    }

    @Override
    public void drawLight(Bullet b) {
        LightningData data = b.data instanceof LightningData ? (LightningData) b.data : null;
        if (data == null)
            return;

        for (LightningTarget target : data.targets) {
            Drawf.light(b.x, b.y, target.x, target.y, width * 2f, lightningColor, 0.7f);
        }
    }

    private void drawLightningEffect(float x1, float y1, float x2, float y2, float width, float segLength, float arc) {
        // 替换为你的实际闪电特效，例如：
        // CruxFx.chainLightning.at(x1, y1, 0f, lightningColor, new LightningHolder(x1,
        // y1, x2, y2, width, segLength, arc));
    }

    public static class LightningData {
        public Seq<LightningTarget> targets = new Seq<>();
        public Seq<LightningHolder> holders = new Seq<>();
    }

    public static class LightningTarget {
        public float x, y;
        public boolean isUnit;

        public LightningTarget(float x, float y, boolean isUnit) {
            this.x = x;
            this.y = y;
            this.isUnit = isUnit;
        }
    }

    public static class LightningHolder {
        public Vec2 start, end;
        public float width, segLength, arc;

        public LightningHolder(float sx, float sy, float ex, float ey, float width, float segLength, float arc) {
            this.start = new Vec2(sx, sy);
            this.end = new Vec2(ex, ey);
            this.width = width;
            this.segLength = segLength;
            this.arc = arc;
        }
    }
}
