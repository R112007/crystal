package crystal.entities.bullet;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;

/**
 * 持续闪电子弹：子弹存活期间每隔一段时间向周围随机方向召唤闪电。
 *
 * 可用于：
 * - 速度为 0 的“闪电领域/雷云”类技能
 * - 缓慢飞行的“电球”，沿途不断放电
 * - 塔防炮塔的持续压制弹道
 */
public class ContinuousLightningBulletType extends BulletType {

    /** 每次闪电造成的伤害 */
    public float lightningDamage = 12f;
    /** 闪电视觉长度（格数） */
    public int lightningLength = 14;
    /** 闪电射程 / 索敌半径 */
    public float lightningRange = 90f;
    /** 每次放电之间的间隔（秒） */
    public float lightningInterval = 0.15f;
    /** 每次放电同时发出几条闪电 */
    public int boltsPerStrike = 6;
    /** 是否开启主动索敌；false 时闪电完全随机 360° 发射，沿途造成伤害 */
    public boolean seekTarget = false;
    /** 单条闪电最多连锁几个目标（仅在 seekTarget=true 时生效） */
    public int targetsPerStrike = 2;
    /** 闪电散开角度：>=360 表示完全随机；<360 表示在 bullet.rotation 两侧对称散开 */
    public float boltAngleSpread = 360f;
    /** 同一目标能否被重复电击（仅在 seekTarget=true 时生效） */
    public boolean repeatTarget = false;
    /** 闪电颜色 */
    public Color lightningColor = Color.valueOf("a9d8ff");

    public ContinuousLightningBulletType(float speed, float damage) {
        super(speed, damage);

        // 默认把子弹本体伤害关掉，只靠闪电输出
        // 若需要本体也撞人造成伤害，可保留 >0
        this.damage = damage;

        lifetime = 120f;
        despawnEffect = Fx.none;
        hitEffect = Fx.hitLancer;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        trailEffect = Fx.none;

        // 该子弹对空对地都生效
        collides = false; // 不直接碰撞，由闪电沿途造成伤害
        collidesTiles = false;
        collidesAir = true;
        collidesGround = true;
        hittable = false;
        absorbable = false;
        reflectable = false;
    }

    @Override
    public void update(Bullet b) {
        super.update(b);

        // b.time 是子弹已存活时间（秒），b.fdata 用来记录上次放电时间
        if (b.time - b.fdata >= lightningInterval) {
            b.fdata = b.time;
            strike(b);
        }
    }

    /** 以子弹位置为中心，同时释放多道闪电。 */
    protected void strike(Bullet b) {
        for (int bolt = 0; bolt < boltsPerStrike; bolt++) {
            if (seekTarget) {
                strikeBoltTargeted(b);
            } else {
                strikeBoltRandom(b);
            }
        }
    }

    /** 完全随机 360° 发射一道闪电（或对 rotation 做对称散开）。 */
    protected void strikeBoltRandom(Bullet b) {
        float angle = boltAngleSpread >= 360f
                ? Mathf.random(360f)
                : b.rotation() + Mathf.range(boltAngleSpread / 2f);

        Lightning.create(b.team, lightningColor, lightningDamage, b.x, b.y, angle, lightningLength);
    }

    /** 主动索敌并连锁攻击。 */
    protected void strikeBoltTargeted(Bullet b) {
        float x = b.x, y = b.y;

        for (int chain = 0; chain < targetsPerStrike; chain++) {
            Teamc target = Units.closestTarget(
                    b.team,
                    x, y,
                    lightningRange,
                    u -> u.checkTarget(collidesAir, collidesGround),
                    building -> collidesGround);

            if (target == null) {
                // 没目标时补一道随机闪电，避免空放太单调
                strikeBoltRandom(b);
                break;
            }

            float tx = target.x();
            float ty = target.y();
            float angle = Angles.angle(x, y, tx, ty);

            Lightning.create(b.team, lightningColor, lightningDamage, x, y, angle, lightningLength);

            if (hitEffect != null) {
                hitEffect.at(tx, ty, angle, lightningColor);
            }

            if (!repeatTarget) {
                x = tx;
                y = ty;
            }
        }
    }

    /** 链式设置：闪电伤害 */
    public ContinuousLightningBulletType lightningDamage(float v) {
        this.lightningDamage = v;
        return this;
    }

    /** 链式设置：闪电长度 */
    public ContinuousLightningBulletType lightningLength(int v) {
        this.lightningLength = v;
        return this;
    }

    /** 链式设置：索敌/伤害范围 */
    public ContinuousLightningBulletType lightningRange(float v) {
        this.lightningRange = v;
        return this;
    }

    /** 链式设置：放电间隔（秒） */
    public ContinuousLightningBulletType lightningInterval(float v) {
        this.lightningInterval = v;
        return this;
    }

    /** 链式设置：每次放电同时发出的闪电数量 */
    public ContinuousLightningBulletType bolts(int v) {
        this.boltsPerStrike = v;
        return this;
    }

    /** 链式设置：是否主动索敌（默认 false，即 360° 随机） */
    public ContinuousLightningBulletType seekTarget(boolean v) {
        this.seekTarget = v;
        return this;
    }

    /** 链式设置：每条闪电最多连锁目标数 */
    public ContinuousLightningBulletType targets(int v) {
        this.targetsPerStrike = v;
        return this;
    }

    /**
     * 链式设置：闪电散开角度。
     * 360 或以上 = 完全随机；小于 360 = 以 bullet.rotation 为中心对称散开。
     */
    public ContinuousLightningBulletType spread(float v) {
        this.boltAngleSpread = v;
        return this;
    }

    /** 链式设置：闪电颜色 */
    public ContinuousLightningBulletType color(Color c) {
        this.lightningColor = c;
        return this;
    }

    public ContinuousLightningBulletType lifetime(float l) {
        this.lifetime = l;
        return this;
    }
}
