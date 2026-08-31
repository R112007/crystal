package crystal.test;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.weapons.*;
import mindustry.world.meta.BlockFlag;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static arc.math.Angles.*;
import static mindustry.Vars.*;

public class CataclysmUnit {

    // 特效绘制用的临时变量（Fx.java 同款）
    public static final Rand rand = new Rand();
    public static final Vec2 v = new Vec2();

    public static UnitType cataclysm;

    // ==================== 自定义华丽特效 ====================
    public static Effect cataclysmMuzzleFlash, // 炮口闪光：白色核心 + 红色光晕 + 放射火花
            cataclysmHitExplosion, // 小型命中：多层环 + 飞溅火花
            cataclysmHitExplosionLarge, // 大型命中：冲击波 + 旋转碎片 + 十字光芒
            cataclysmShootRing, // 射击扩散环：能量环 + 旋转射线
            cataclysmChargeSwirl, // 充能漩涡：螺旋收缩能量粒子
            cataclysmTrailSpark, // 子弹尾迹火花
            cataclysmDeathExplosion; // 死亡大爆炸

    public static void load() {

        // ========== 特效定义 ==========

        cataclysmMuzzleFlash = new Effect(18f, e -> {
            color(Color.white, Pal.remove, e.fin());
            alpha(e.fout());
            Fill.circle(e.x, e.y, e.fin() * 14f);

            color(Pal.remove, Color.orange, e.fin());
            alpha(e.fout() * 0.6f);
            Fill.circle(e.x, e.y, e.fin() * 26f);

            rand.setSeed(e.id);
            for (int i = 0; i < 8; i++) {
                float angle = e.rotation + rand.random(-40f, 40f) + i * 45f;
                float len = e.fin() * rand.random(12f, 35f);
                v.trns(angle, len);
                color(Color.white, Pal.remove, e.fin());
                alpha(e.fout());
                Fill.circle(e.x + v.x, e.y + v.y, e.fout() * 3.5f);
            }
        });

        cataclysmHitExplosion = new Effect(35f, 55f, e -> {
            color(Pal.remove, Color.orange, e.fin());
            stroke(e.fout() * 2.5f);
            circle(e.x, e.y, e.fin() * 38f);

            color(Color.white, Pal.remove, e.fin());
            stroke(e.fout() * 1.5f);
            circle(e.x, e.y, e.fin() * 24f);

            rand.setSeed(e.id);
            for (int i = 0; i < 12; i++) {
                float angle = rand.random(360f);
                float len = rand.random(15f, 45f) * e.fin();
                v.trns(angle, len);
                color(Pal.remove, Color.darkGray, e.fin());
                Fill.circle(e.x + v.x, e.y + v.y, e.fout() * 3f);
            }

            color(Color.white);
            alpha(e.fout());
            Fill.circle(e.x, e.y, e.fout() * 7f);
        });

        cataclysmHitExplosionLarge = new Effect(55f, 90f, e -> {
            color(Pal.remove, Color.orange, e.fin());
            stroke(e.fout() * 3.5f);
            circle(e.x, e.y, e.fin() * 60f);

            color(Color.orange, Color.yellow, e.fin());
            stroke(e.fout() * 2.5f);
            circle(e.x, e.y, e.fin() * 45f);

            color(Color.white, Pal.remove, e.fin());
            stroke(e.fout() * 2f);
            circle(e.x, e.y, e.fin() * 28f);

            rand.setSeed(e.id);
            for (int i = 0; i < 20; i++) {
                float angle = rand.random(360f) + e.fin() * 120f;
                float len = rand.random(25f, 70f) * e.fin();
                v.trns(angle, len);
                color(Pal.remove, Color.darkGray, e.fin());
                Fill.circle(e.x + v.x, e.y + v.y, e.fout() * 4f);
            }

            color(Color.white);
            alpha(e.fout());
            Fill.circle(e.x, e.y, e.fout() * 12f);

            color(Color.white, e.fout() * 0.4f);
            for (int i = 0; i < 4; i++) {
                float angle = i * 90f + e.fin() * 45f;
                lineAngle(e.x, e.y, angle, e.fin() * 18f);
            }
        });

        cataclysmShootRing = new Effect(30f, 55f, e -> {
            color(Pal.remove, Color.orange, e.fin());
            stroke(e.fout() * 2.5f);
            circle(e.x, e.y, e.fin() * 45f);

            color(Color.white, e.fout());
            stroke(e.fout() * 1f);
            circle(e.x, e.y, e.fin() * 28f);

            for (int i = 0; i < 8; i++) {
                float angle = e.rotation + i * 45f + e.fin() * 160f;
                v.trns(angle, e.fin() * 40f);
                color(Pal.remove, e.fout());
                lineAngle(e.x + v.x, e.y + v.y, angle + 90f, e.fin() * 14f);
            }
        });

        cataclysmChargeSwirl = new Effect(75f, 100f, e -> {
            color(Pal.remove, Color.orange, e.fin());
            for (int i = 0; i < 5; i++) {
                float angle = e.rotation + i * 72f + e.fin() * 300f;
                float radius = (1f - e.fin()) * 40f;
                v.trns(angle, radius);
                Fill.circle(e.x + v.x, e.y + v.y, e.fin() * 7f);
            }

            color(Color.white, Pal.remove, e.fin());
            for (int i = 0; i < 4; i++) {
                float angle = e.rotation + i * 90f - e.fin() * 220f;
                float radius = (1f - e.fin()) * 25f;
                v.trns(angle, radius);
                Fill.circle(e.x + v.x, e.y + v.y, e.fin() * 5f);
            }

            color(Color.white);
            alpha(e.fout());
            Fill.circle(e.x, e.y, e.fin() * 12f);
        });

        cataclysmTrailSpark = new Effect(25f, e -> {
            color(Pal.remove, Color.orange, e.fin());
            Fill.circle(e.x, e.y, e.fout() * 3f);
            v.trns(e.rotation + 180f, e.fin() * 10f);
            color(Color.orange, e.fout() * 0.5f);
            Fill.circle(e.x + v.x, e.y + v.y, e.fout() * 2.5f);
        });

        cataclysmDeathExplosion = new Effect(90f, 140f, e -> {
            color(Pal.remove, Color.darkGray, e.fin());
            stroke(e.fout() * 5f);
            circle(e.x, e.y, e.fin() * 90f);

            color(Color.orange, Color.yellow, e.fin());
            stroke(e.fout() * 3f);
            circle(e.x, e.y, e.fin() * 70f);

            color(Color.white, Pal.remove, e.fin());
            stroke(e.fout() * 2f);
            circle(e.x, e.y, e.fin() * 45f);

            rand.setSeed(e.id);
            for (int i = 0; i < 30; i++) {
                float angle = rand.random(360f);
                float len = rand.random(35f, 100f) * e.fin();
                v.trns(angle, len);
                color(Pal.remove, Color.darkGray, e.fin());
                Fill.circle(e.x + v.x, e.y + v.y, e.fout() * 5f);
            }

            color(Color.white);
            alpha(e.fout());
            Fill.circle(e.x, e.y, e.fout() * 18f);
        });

        // ========== 单位定义：灾变级重型飞行炮艇 ==========
        cataclysm = new UnitType("lutian") {
            {

                // === 飞行基础 ===
                flying = true;
                lowAltitude = true;

                // === 机动属性（重型，慢速但稳定）===
                speed = 0.38f;
                accel = 0.02f;
                drag = 0.04f;
                rotateSpeed = 0.9f;

                // === 生存属性（飞行堡垒级别）===
                health = 30000;
                armor = 18f;
                hitSize = 65f;

                // === 引擎（主引擎在后方）===
                engineOffset = 68f;
                engineSize = 27f;
                engineColor = Color.valueOf("#FF9166FF");
                setEnginesMirror(new UnitEngine(32f, -80.5f, 17, 0));
                // === 目标优先级 ===
                targetFlags = new BlockFlag[] {
                        BlockFlag.reactor,
                        BlockFlag.battery,
                        BlockFlag.core,
                        null
                };

                // === 声音 ===
                loopSound = Sounds.loopHover;

                // === 绘制 ===
                drawShields = true;

                // 能力1：力场护盾 —— 周期性生成防护罩吸收伤害
                abilities.add(new ForceFieldAbility(200f, 10f, 35000f, 60f * 25));

                // 能力2：过载光环 —— 周期性给附近友军施加过载（射速+移动速度提升）
                abilities.add(new StatusFieldAbility(StatusEffects.overdrive, 60f * 6, 60f * 10f, 100f));

                // 能力3：能量场 —— 对周围敌人持续造成伤害并吸血回复自身
                abilities.add(new EnergyFieldAbility(50f, 70f, 100f) {
                    {
                        status = StatusEffects.sapped;
                        statusDuration = 60f * 4f;
                        maxTargets = 20;
                        healPercent = 4f;
                        effectRadius = 7f;
                        sectors = 6;
                        rotateSpeed = 2f;
                        color = Pal.remove;
                    }
                });

                // 小武器1：最前方外侧 (38.5, 72.75)，mirror=true → 自动复制到 (-38.5, 72.75)
                weapons.add(new Weapon("crystal-lutian-w1") {
                    {
                        x = 38.5f;
                        y = 72.75f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 9f;
                        reload = 6f;
                        recoil = 0.8f;
                        shootY = 4f;
                        ejectEffect = Fx.casing1;
                        shootSound = Sounds.shoot;
                        shadow = 5f;

                        bullet = new BasicBulletType(8f, 18) {
                            {
                                width = 5f;
                                height = 8f;
                                lifetime = 32f;
                                splashDamage = 8f;
                                splashDamageRadius = 14f;

                                trailColor = Pal.remove;
                                trailWidth = 1.5f;
                                trailLength = 5;

                                shootEffect = cataclysmMuzzleFlash;
                                hitEffect = cataclysmHitExplosion;
                                despawnEffect = cataclysmHitExplosion;
                                smokeEffect = Fx.shootSmallSmoke;
                            }
                        };
                    }
                });

                // 小武器2：前中部 (30.5, 27.75)，mirror=true → 自动复制到 (-30.5, 27.75)
                weapons.add(new Weapon("crystal-lutian-w1") {
                    {
                        x = 30.5f;
                        y = 27.75f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 9f;
                        reload = 7f;
                        recoil = 0.8f;
                        shootY = 4f;
                        ejectEffect = Fx.casing1;
                        shootSound = Sounds.shoot;
                        shadow = 5f;

                        bullet = new BasicBulletType(7.5f, 20) {
                            {
                                width = 5.5f;
                                height = 9f;
                                lifetime = 35f;
                                splashDamage = 10f;
                                splashDamageRadius = 16f;

                                trailColor = Pal.remove;
                                trailWidth = 1.8f;
                                trailLength = 6;

                                shootEffect = cataclysmMuzzleFlash;
                                hitEffect = cataclysmHitExplosion;
                                despawnEffect = cataclysmHitExplosion;
                                smokeEffect = Fx.shootSmallSmoke;
                            }
                        };
                    }
                });

                // 小武器3：外侧中部 (53.5, 7)，mirror=true → 自动复制到 (-53.5, 7)
                weapons.add(new Weapon("crystal-lutian-w1") {
                    {
                        x = 53.5f;
                        y = 7f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 9f;
                        reload = 8f;
                        recoil = 0.8f;
                        shootY = 4f;
                        ejectEffect = Fx.casing1;
                        shootSound = Sounds.shoot;
                        shadow = 5f;

                        bullet = new BasicBulletType(7f, 22) {
                            {
                                width = 6f;
                                height = 10f;
                                lifetime = 38f;
                                splashDamage = 12f;
                                splashDamageRadius = 18f;

                                trailColor = Pal.remove;
                                trailWidth = 2f;
                                trailLength = 7;

                                shootEffect = cataclysmMuzzleFlash;
                                hitEffect = cataclysmHitExplosion;
                                despawnEffect = cataclysmHitExplosion;
                                smokeEffect = Fx.shootSmallSmoke;
                            }
                        };
                    }
                });

                // ===== 中武器 x2 定义 → 镜像后 x4（中距离曲射压制）=====

                // 中武器1：中部 (34.5, -4)，mirror=true → 自动复制到 (-34.5, -4)
                weapons.add(new Weapon("crystal-lutian-w2") {
                    {
                        x = 34.5f;
                        y = -4f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 4f;
                        reload = 26f;
                        recoil = 2.5f;
                        shootY = 8f;
                        shake = 2f;
                        ejectEffect = Fx.casing2;
                        shootSound = Sounds.explosionAfflict;
                        shadow = 8f;

                        bullet = new ArtilleryBulletType(4.5f, 55) {
                            {
                                width = 10f;
                                height = 14f;
                                lifetime = 80f;
                                splashDamage = 50f;
                                splashDamageRadius = 30f;

                                collidesTiles = true;
                                collidesGround = true;

                                trailColor = Color.valueOf("ff6b35");
                                trailWidth = 2.5f;
                                trailLength = 10;

                                shootEffect = cataclysmShootRing;
                                hitEffect = cataclysmHitExplosionLarge;
                                despawnEffect = cataclysmHitExplosionLarge;
                                smokeEffect = Fx.shootBigSmoke;
                            }
                        };
                    }
                });

                // 中武器2：最后方 (64, -68.75)，mirror=true → 自动复制到 (-64, -68.75)
                weapons.add(new Weapon("crystal-lutian-w2") {
                    {
                        x = 64f;
                        y = -68.75f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 4f;
                        reload = 32f;
                        recoil = 3f;
                        shootY = 8f;
                        shake = 2.5f;
                        ejectEffect = Fx.casing2;
                        shootSound = Sounds.explosionAfflict;
                        shadow = 8f;

                        bullet = new ArtilleryBulletType(4f, 70) {
                            {
                                width = 11f;
                                height = 15f;
                                lifetime = 85f;
                                splashDamage = 60f;
                                splashDamageRadius = 36f;

                                collidesTiles = true;
                                collidesGround = true;

                                trailColor = Color.valueOf("ff6b35");
                                trailWidth = 3f;
                                trailLength = 12;

                                shootEffect = cataclysmShootRing;
                                hitEffect = cataclysmHitExplosionLarge;
                                despawnEffect = cataclysmHitExplosionLarge;
                                smokeEffect = Fx.shootBigSmoke;
                            }
                        };
                    }
                });

                // ===== 大武器 x2 定义 → 镜像后 x3（重型直射打击）=====

                // 大武器1：中心背部主炮 (0, -21.75)，mirror=false → 只有1门
                weapons.add(new Weapon("crystal-lutian-w3") {
                    {
                        x = 0f;
                        y = -21.75f;
                        mirror = false;
                        rotate = true;
                        rotateSpeed = 1.5f;
                        top = false;
                        reload = 150f;
                        recoil = 6f;
                        shake = 7f;
                        shootY = 12f;
                        shootSound = Sounds.shootCorvus;
                        shadow = 18f;

                        chargeSound = Sounds.chargeCorvus;
                        shoot.firstShotDelay = cataclysmChargeSwirl.lifetime;
                        parentizeEffects = true;

                        bullet = new LaserBulletType(450f) {
                            {
                                length = 320f;
                                width = 70f;
                                lifetime = 65f;

                                lightningSpacing = 30f;
                                lightningLength = 6;
                                lightningDelay = 1.1f;
                                lightningLengthRand = 15;
                                lightningDamage = 60;
                                lightningAngleRand = 40f;
                                largeHit = true;

                                lightColor = lightningColor = Pal.remove;
                                colors = new Color[] {
                                        Pal.remove.cpy().a(0.35f),
                                        Pal.remove.cpy().a(0.7f),
                                        Pal.remove,
                                        Color.white
                                };

                                chargeEffect = cataclysmChargeSwirl;
                                shootEffect = Fx.shockwave;
                                hitEffect = cataclysmHitExplosionLarge;
                                despawnEffect = cataclysmHitExplosionLarge;

                                pierce = true;
                                pierceBuilding = true;
                                pierceCap = 5;
                            }
                        };
                    }
                });

                // 大武器2：侧舷重炮 (39.5, -50)，mirror=true → 自动复制到 (-39.5, -50)
                weapons.add(new Weapon("crystal-lutian-w3") {
                    {
                        x = 39.5f;
                        y = -50f;
                        mirror = true;
                        rotate = true;
                        rotateSpeed = 2f;
                        reload = 95f;
                        recoil = 4.5f;
                        shake = 4.5f;
                        shootY = 10f;
                        ejectEffect = Fx.casing3;
                        shootSound = Sounds.shootSmite;
                        shadow = 14f;

                        bullet = new BasicBulletType(8.5f, 200) {
                            {
                                width = 15f;
                                height = 24f;
                                lifetime = 48f;

                                pierce = true;
                                pierceBuilding = true;
                                pierceCap = 4;

                                splashDamage = 90f;
                                splashDamageRadius = 30f;

                                trailColor = Pal.remove;
                                trailWidth = 4f;
                                trailLength = 18;

                                shootEffect = cataclysmShootRing;
                                hitEffect = cataclysmHitExplosionLarge;
                                despawnEffect = cataclysmHitExplosionLarge;
                                smokeEffect = Fx.shootBigSmoke2;
                            }
                        };
                    }
                });

            }
        };
    }
}
