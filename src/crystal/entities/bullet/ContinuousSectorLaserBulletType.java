package crystal.entities.bullet;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * 持续扇形激光子弹。
 * 继承 ContinuousBulletType，持续照射一个扇形区域，
 * 每 {@link ContinuousBulletType#damageInterval} tick 对区域内敌人和建筑造成伤害。
 */
public class ContinuousSectorLaserBulletType extends ContinuousBulletType {

    /** 扇形总夹角（度），以子弹朝向为中心。 */
    public float sectorAngle = 45f;
    /** 扇形绘制分段数，越高越圆滑。 */
    public int divisions = 40;
    /** 淡出时间（tick）。 */
    public float fadeTime = 16f;
    /** 是否跟随射击者当前瞄准方向旋转。 */
    public boolean rotateToAim = true;

    /** 激光颜色，从外到内。 */
    public Color[] colors = {
            Color.valueOf("ec745855"),
            Color.valueOf("ec7458aa"),
            Color.valueOf("ff9c5a"),
            Color.white
    };

    public ContinuousSectorLaserBulletType(float damage) {
        this.damage = damage;
    }

    public ContinuousSectorLaserBulletType() {
    }

    {
        length = 160f;
        shake = 1f;
        largeHit = true;
        hitEffect = Fx.hitBeam;
        hitSize = 4;
        drawSize = 420f;
        lifetime = 16f;
        hitColor = colors[colors.length - 1];
        lightColor = colors[colors.length - 1];
        lightOpacity = 0.7f;
        incendAmount = 1;
        incendSpread = 5;
        incendChance = 0.4f;
    }

    @Override
    public void update(Bullet b) {
        // 同步位置到 owner，使扇形跟随单位/建筑移动
        // 注意：这会忽略武器的 x/y 偏移，适用于 x=y=0 的武器
        if (b.owner instanceof Unit u) {
            b.x = u.x;
            b.y = u.y;
        } else if (b.owner instanceof Building build) {
            b.x = build.x;
            b.y = build.y;
        }

        // 同步旋转到射击者瞄准方向
        if (rotateToAim && b.shooter instanceof Unit u && (u.aimX != -1f || u.aimY != -1f)) {
            b.rotation(u.angleTo(u.aimX, u.aimY));
        } else if (rotateToAim && b.aimX != -1f && b.aimY != -1f) {
            b.rotation(b.angleTo(b.aimX, b.aimY));
        }

        super.update(b);
    }

    @Override
    public float currentLength(Bullet b) {
        float fout = Mathf.clamp(b.time > b.lifetime - fadeTime
                ? 1f - (b.time - (b.lifetime - fadeTime)) / fadeTime
                : 1f);
        return Damage.findLength(b, length * fout, laserAbsorb, pierceCap);
    }

    @Override
    public void applyDamage(Bullet b) {
        float realLength = currentLength(b);
        float damage = b.damage;

        if (timescaleDamage && b.owner instanceof Building build) {
            b.damage *= build.timeScale();
        }

        applySectorDamage(b, realLength);

        b.damage = damage;
    }

    /** 对扇形区域内的单位和建筑造成伤害。 */
    protected void applySectorDamage(Bullet b, float realLength) {
        float rot = b.rotation();
        float half = sectorAngle / 2f;

        // ----- 单位 -----
        Tmp.r1.setCentered(b.x, b.y, realLength * 2f);
        Units.nearbyEnemies(b.team, Tmp.r1, u -> {
            if (!u.checkTarget(collidesAir, collidesGround) || !u.hittable())
                return;

            float dst = b.dst(u);
            if (dst > realLength + u.hitSize() / 2f)
                return;

            float ang = b.angleTo(u);
            if (!Angles.within(ang, rot, half))
                return;

            u.collision(b, u.x, u.y);
            b.collision(u, u.x, u.y);
        });

        // ----- 建筑 -----
        indexer.eachBlock(null, b.x, b.y, realLength, build -> {
            if (build.dead())
                return false;

            if (build.team == b.team) {
                return heals() && testCollision(b, build)
                        && Angles.within(b.angleTo(build), rot, half);
            }

            return build.collide(b)
                    && Angles.within(b.angleTo(build), rot, half);
        }, build -> {
            if (build.team == b.team) {
                hitTile(b, build, build.x, build.y, build.health, false);
            } else {
                float health = build.health;

                build.collision(b);
                if (build.collide(b)) {
                    hit(b, build.x, build.y);
                }

                if (testCollision(b, build)) {
                    hitTile(b, build, build.x, build.y, health, false);
                }
            }
        });
    }

    @Override
    public void draw(Bullet b) {
        float realLength = currentLength(b);
        float rot = b.rotation();
        float half = sectorAngle / 2f;
        float fraction = sectorAngle / 360f;
        float start = rot - half;

        float fin = Mathf.curve(b.fin(), 0f, 0.2f);
        float fout = Mathf.clamp(b.time > b.lifetime - fadeTime
                ? 1f - (b.time - (b.lifetime - fadeTime)) / fadeTime
                : 1f);
        float alpha = fin * fout;

        Draw.z(layer);

        // === 扇形主体：用 lerp 在 colors 之间插值，实现径向渐变 ===
        int layers = 20;
        for (int i = 0; i < layers; i++) {
            float layerFin = i / (float) (layers - 1);

            // 在 colors 数组中 lerp 取色
            float colorPos = layerFin * (colors.length - 1);
            int idx = (int) colorPos;
            float t = colorPos - idx;

            Color col;
            if (idx >= colors.length - 1) {
                col = colors[colors.length - 1];
            } else {
                col = Tmp.c1.set(colors[idx]).lerp(colors[idx + 1], t);
            }

            // 远处更浅：lerp 到透明色
            Color fadeColor = Tmp.c2.set(col).a(0f);
            col.lerp(fadeColor, layerFin * 0.7f); // 0.7f 控制远处淡化程度，可调

            float radius = realLength * Mathf.lerp(1f, 0.15f, layerFin);
            Draw.color(Tmp.c1.set(col).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)).a(col.a * alpha));
            Fill.arc(b.x, b.y, radius, fraction, start, divisions);
        }

        // 扇形边缘高光
        Draw.color(Tmp.c1.set(colors[colors.length - 1]).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)).a(alpha));
        Lines.stroke(1.5f * alpha);
        Lines.arc(b.x, b.y, realLength, fraction, start, divisions);

        // 扇形边界线（两条半径）
        for (int s : Mathf.signs) {
            float a = rot + half * s;
            float x2 = b.x + Angles.trnsx(a, realLength);
            float y2 = b.y + Angles.trnsy(a, realLength);
            Lines.line(b.x, b.y, x2, y2);
        }

        Draw.reset();

        // 扇形区域光效
        Drawf.light(b.x, b.y, realLength * 1.2f, lightColor, lightOpacity * alpha);
    }

    @Override
    public void drawLight(Bullet b) {
        // 光效已在 draw() 中绘制，避免默认圆形光叠加
    }
}
