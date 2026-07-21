package crystal.type.weapons;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import crystal.entities.DirectedLightning;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.Weapon;

/**
 * 持续定向闪电武器 - 发射指向目标的定向闪电。
 */
// TODO stat显示
public class ContinuousLightningWeapon extends Weapon {

    // ========== 高光圆参数 ==========
    /** 高光圆的基础半径 */
    public float glowRadius = 12f;
    /** 高光圆半径变化的幅度（实际半径 = base ± mag） */
    public float glowRadiusMag = 6f;
    /** 高光圆脉冲周期，单位：tick（越小变化越快） */
    public float glowPulsePeriod = 30f;
    /** 高光圆外圈颜色 */
    public Color glowColor = Color.valueOf("ffaa44");
    /** 高光圆内圈/核心颜色 */
    public Color glowColorInner = Color.valueOf("ffffaa");
    /** 高光圆不透明度 0~1 */
    public float glowAlpha = 0.7f;

    // ========== 定向闪电参数 ==========
    /** 每次update产生的闪电数量 */
    public int lightningCount = 2;
    /** 闪电基础长度（段数） */
    public int lightningLength = 16;
    /** 闪电长度随机增量 */
    public int lightningLengthRand = 6;
    /** 闪电伤害 */
    public float lightningDamage = 10f;
    /** 闪电颜色 */
    public Color lightningColor = Pal.surge;
    /** 闪电产生间隔，单位：tick */
    public float lightningInterval = 5f;
    /** 闪电使用的子弹类型（影响伤害倍率等，null则使用默认） */
    public @Nullable BulletType lightningBulletType = null;

    // ========== 定向参数 ==========
    /** 最大射程（决定目标点距离） */
    public float lightningRange = 200f;
    /** 是否使用 mount.aimX/aimY 作为目标点 */
    public boolean useMountAim = true;
    /** 目标点随机偏移（增加视觉效果） */
    public float aimJitter = 8f;
    /** 强制到达目标点的阈值段数 */
    public int forceReachThreshold = 3;
    /** 向目标点拉拢强度 */
    public float targetPullStrength = 0.4f;
    /** 最大角度偏移（相对于目标方向） */
    public float maxAngleOffset = 35f;
    /** 每步长度 */
    public float stepLengthMin = 14f, stepLengthMax = 22f;
    /** 额外转折次数 */
    public int maxBends = 6;
    // 武器前方有效扇形总角度，左右各60°，总共120°，按需修改
    public float frontSectorAngle = 30f;
    // ========== 内部状态 ==========
    /** 每个mount的独立计时器偏移，避免多mount冲突 */
    private float[] mountTimeOffsets;
    private float timer = 0f;

    public ContinuousLightningWeapon(String name) {
        super(name);
        initDefaults();
    }

    public ContinuousLightningWeapon() {
        this("");
    }

    private void initDefaults() {
        // 使用空子弹，不依赖子弹的持续机制
        bullet = new EmptyBulletType() {
            {
                damage = 0;
                hitEffect = despawnEffect = shootEffect = chargeEffect = Fx.none;
                lifetime = 0;
                hitSize = 0;
                hitShake = 0;
                drawSize = 0;
                collidesAir = collidesGround = true;
            }
        };

        // 持续射击模式 - 强制 mount.shoot = true
        alwaysShooting = true;
        shoot = new ShootPattern();

        // 最小预热（避免刚瞄准就产生效果）
        minWarmup = 0.1f;
        shootWarmupSpeed = 0.2f;

        // 不需要后坐力
        recoil = 0f;

        // 音效
        shootSound = Sounds.shootArc;

        // 关闭默认效果
        ejectEffect = Fx.none;
        shootStatus = StatusEffects.none;
    }

    @Override
    public float range() {
        return lightningRange;
    }

    /**
     * 获取或创建 mount 的独立时间偏移
     */
    private float getMountOffset(Unit unit, int mountIndex) {
        if (mountTimeOffsets == null) {
            mountTimeOffsets = new float[unit.mounts.length];
            for (int i = 0; i < mountTimeOffsets.length; i++) {
                // 每个 mount 有独立的随机偏移，避免同步闪烁
                mountTimeOffsets[i] = Mathf.random(1000f);
            }
        }
        return mountTimeOffsets[mountIndex];
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        timer += Time.delta;
        super.update(unit, mount);

        // 1. 正确计算炮口世界坐标
        float unitRotation = unit.rotation - 90f;
        float weaponRot = this.rotate ? (unitRotation + mount.rotation) : (unitRotation + baseRotation);
        // 武器挂载点
        float mountX = unit.x + Angles.trnsx(unitRotation, x, y);
        float mountY = unit.y + Angles.trnsy(unitRotation, x, y);
        // 炮口发射点（闪电固定起点）
        float muzzleX = mountX + Angles.trnsx(weaponRot, shootX, shootY);
        float muzzleY = mountY + Angles.trnsy(weaponRot, shootX, shootY);

        // 2. 获取目标方向角
        float aimAngle;
        if (mount.target != null) {
            aimAngle = Angles.angle(muzzleX, muzzleY, mount.target.x(), mount.target.y());
        } else if (useMountAim && mount.aimX != 0 && mount.aimY != 0) {
            aimAngle = Angles.angle(muzzleX, muzzleY, mount.aimX, mount.aimY);
        } else {
            aimAngle = weaponRot;
        }

        // 3. 使用 Angles.within 判断是否在前方扇形内
        // frontSectorAngle = 单侧允许角度，总扇形 = 2 * frontSectorAngle
        boolean targetInFront = Angles.within(aimAngle, weaponRot + 90, frontSectorAngle);
        if (!targetInFront) {
            // 目标不在武器前方，直接终止，不生成闪电
            return;
        }

        // 4. 满足射击条件才生成闪电
        if (mount.shoot && unit.canShoot() && mount.warmup >= minWarmup && timer >= reload) {
            createDirectedLightningAtMuzzle(unit, mount, muzzleX, muzzleY, weaponRot);
            timer = 0f;
        }
    }

    /**
     * 在指定位置创建定向闪电，闪电会趋向目标点
     * 
     * @param unit           拥有者单位
     * @param mount          武器挂载点
     * @param x              起点X（炮口世界坐标）
     * @param y              起点Y（炮口世界坐标）
     * @param weaponRotation 武器当前旋转角度
     */
    private void createDirectedLightningAtMuzzle(Unit unit, WeaponMount mount, float x, float y, float weaponRotation) {
        // 原始目标坐标
        float targetX, targetY;
        if (useMountAim && mount.aimX != 0 && mount.aimY != 0) {
            targetX = mount.aimX + Mathf.range(aimJitter);
            targetY = mount.aimY + Mathf.range(aimJitter);
        } else {
            targetX = x + Angles.trnsx(weaponRotation, lightningRange);
            targetY = y + Angles.trnsy(weaponRotation, lightningRange);
        }

        // ========== 新增：超出射程则截断目标点到最大范围 ==========
        float dist = Mathf.dst(x, y, targetX, targetY);
        if (dist > lightningRange) {
            float angleToTarget = Angles.angle(x, y, targetX, targetY);
            targetX = x + Angles.trnsx(angleToTarget, lightningRange);
            targetY = y + Angles.trnsy(angleToTarget, lightningRange);
        }
        // ======================================================

        BulletType hitType = lightningBulletType != null ? lightningBulletType : bullet;
        for (int i = 0; i < lightningCount; i++) {
            Bullet dummy = Bullet.create();
            dummy.type = hitType;
            dummy.owner = unit;
            dummy.team = unit.team;
            dummy.damage = lightningDamage;
            dummy.set(x, y);
            int length = lightningLength + Mathf.random(lightningLengthRand);
            DirectedLightning.create(
                    dummy,
                    lightningColor,
                    lightningDamage,
                    x, y,
                    targetX, targetY, // 已限制距离的目标
                    weaponRotation + 90f,
                    length,
                    maxAngleOffset,
                    targetPullStrength,
                    stepLengthMin,
                    stepLengthMax,
                    maxBends,
                    forceReachThreshold,
                    aimJitter);
            dummy.absorbed = true;
            dummy.remove();
        }
    }

    @Override
    public void draw(Unit unit, WeaponMount mount) {
        // === 先绘制武器本体（父类绘制武器贴图、后坐力等） ===
        super.draw(unit, mount);
        float unitRotation = unit.rotation - 90f;
        float weaponRot = this.rotate ? (unitRotation + mount.rotation) : (unitRotation + baseRotation);
        // 武器挂载点
        float mountX = unit.x + Angles.trnsx(unitRotation, x, y);
        float mountY = unit.y + Angles.trnsy(unitRotation, x, y);
        // 炮口发射点（闪电固定起点）
        float muzzleX = mountX + Angles.trnsx(weaponRot, shootX, shootY);
        float muzzleY = mountY + Angles.trnsy(weaponRot, shootX, shootY);

        // 2. 获取目标方向角
        float aimAngle;
        if (mount.target != null) {
            aimAngle = Angles.angle(muzzleX, muzzleY, mount.target.x(), mount.target.y());
        } else if (useMountAim && mount.aimX != 0 && mount.aimY != 0) {
            aimAngle = Angles.angle(muzzleX, muzzleY, mount.aimX, mount.aimY);
        } else {
            aimAngle = weaponRot;
        }

        // 3. 使用 Angles.within 判断是否在前方扇形内
        // frontSectorAngle = 单侧允许角度，总扇形 = 2 * frontSectorAngle
        boolean targetInFront = Angles.within(aimAngle, weaponRot + 90, frontSectorAngle);

        // === 当武器正在射击且预热足够时绘制高光圆 ===
        if (mount.shoot && unit.canShoot() && mount.warmup >= minWarmup && targetInFront) {
            drawGlowCircle(unit, mount);
        }
    }

    /**
     * 在炮口位置绘制随时间均匀变大变小的高光圆
     */
    private void drawGlowCircle(Unit unit, WeaponMount mount) {
        // 计算炮口世界坐标（与 update 中相同的计算逻辑）
        float rotation = unit.rotation - 90f;
        float weaponRotation = rotation + (this.rotate ? mount.rotation : baseRotation);

        float mountX = unit.x + Angles.trnsx(rotation, x, y);
        float mountY = unit.y + Angles.trnsy(rotation, x, y);

        float bulletX = mountX + Angles.trnsx(weaponRotation, shootX, shootY);
        float bulletY = mountY + Angles.trnsy(weaponRotation, shootX, shootY);

        // 找到 mount 索引用于获取独立偏移
        int mountIndex = -1;
        for (int i = 0; i < unit.mounts.length; i++) {
            if (unit.mounts[i] == mount) {
                mountIndex = i;
                break;
            }
        }
        if (mountIndex < 0)
            mountIndex = 0;

        // 计算脉冲半径
        // 使用正弦函数实现严格的均匀周期变化
        float offset = getMountOffset(unit, mountIndex);
        float time = Time.time + offset;
        float pulseProgress = time / glowPulsePeriod * Mathf.PI2;
        float radiusScale = Mathf.sin(pulseProgress); // -1 ~ 1
        float currentRadius = Math.max(glowRadius + radiusScale * glowRadiusMag, 0.5f);

        // 计算透明度（随 warmup 渐入，避免突兀出现）
        float alpha = glowAlpha * mount.warmup;

        // 保存当前绘制层级
        float z = Draw.z();

        // ===== 第1层：外圈光晕（最大的半透明圆） =====
        Draw.z(z + 0.001f);
        Draw.color(glowColor, alpha * 0.25f);
        Fill.circle(bulletX, bulletY, currentRadius * 2.0f);

        // ===== 第2层：中间过渡圆 =====
        Draw.z(z + 0.002f);
        Draw.color(glowColor, alpha * 0.45f);
        Fill.circle(bulletX, bulletY, currentRadius * 1.3f);

        // ===== 第3层：主高光圆（内圈亮色） =====
        Draw.z(z + 0.003f);
        Draw.color(glowColorInner, alpha * 0.8f);
        Fill.circle(bulletX, bulletY, currentRadius * 0.7f);

        // ===== 第4层：核心亮点 =====
        Draw.z(z + 0.004f);
        Draw.color(Color.white, alpha * 0.95f);
        Fill.circle(bulletX, bulletY, currentRadius * 0.3f);

        // ===== 第5层：外圈轮廓线 =====
        Draw.z(z + 0.0035f);
        Draw.color(glowColor, alpha * 0.7f);
        Lines.stroke(1.5f);
        Lines.circle(bulletX, bulletY, currentRadius);
        Lines.stroke(1f);

        // ===== 光源效果 =====
        Drawf.light(bulletX, bulletY, currentRadius * 4f, glowColor, alpha * 0.5f);

        // 恢复绘制状态
        Draw.reset();
        Draw.z(z);
    }
}
