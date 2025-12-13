package crystal.world.blocks.defence;

import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Polygon;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.world.blocks.defense.ForceProjector;

public class LaserResistantForceProjector extends ForceProjector {

    public float laserDamageMultiplier = 1.2f; // 激光对护盾的伤害倍率
    public Effect laserHitEffect = paramEffect; // 激光命中特效
    public Color laserHitColor = Color.orange; // 激光命中时护盾闪烁色

    public LaserResistantForceProjector(String name) {
        super(name);
    }

    public class LaserResistantForceBuild extends ForceBuild {
        @Override
        public void updateTile() {
            super.updateTile(); // 执行官方力墙逻辑（能量恢复、护盾破碎等）
            interceptLaserBullets(); // 新增：激光拦截逻辑
            interceptContinueLaserBullets();
        }

        private void interceptLaserBullets() {
            if (broken || realRadius() <= 0)
                return;

            float shieldX = this.x;
            float shieldY = this.y;
            int shieldSides = ((LaserResistantForceProjector) block).sides;
            float shieldRot = ((LaserResistantForceProjector) block).shieldRotation;
            float laserDamageMulti = ((LaserResistantForceProjector) block).laserDamageMultiplier;
            Effect laserHitEffect = ((LaserResistantForceProjector) block).laserHitEffect;
            Color laserHitColor = ((LaserResistantForceProjector) block).laserHitColor;

            // 遍历所有子弹，重点筛选 LaserBulletType（脉冲激光）
            Groups.bullet.each(bullet -> {
                // 筛选条件：
                // 1. 敌方子弹 2. 未被吸收 3. 是 LaserBulletType 4. 官方已计算完长度（fdata > 0，避免拦截未初始化的子弹）
                if (bullet.team() == team || bullet.absorbed() || !(bullet.type() instanceof LaserBulletType)
                        || bullet.fdata() <= 0) {
                    return;
                }

                LaserBulletType laserType = (LaserBulletType) bullet.type();
                // 3. 构建激光路径（官方已算好fdata，用这个长度作为原长度）
                float laserStartX = bullet.x();
                float laserStartY = bullet.y();
                float laserRot = bullet.rotation(); // LaserBulletType 的 rotation 是发射方向（vel=0）
                float officialLength = bullet.fdata(); // 官方在 init 中计算的长度

                // 计算激光原终点（基于官方长度）
                float laserEndX = laserStartX + Angles.trnsx(laserRot, officialLength);
                float laserEndY = laserStartY + Angles.trnsy(laserRot, officialLength);

                // 4. 检测激光路径与力墙正多边形是否相交
                Seq<Vec2> shieldVertices = getShieldPolygonVertices(shieldX, shieldY, realRadius(), shieldSides,
                        shieldRot);
                if (!Intersector.intersectSegmentPolygon(crystal.util.CTmp.v1.set(laserStartX, laserStartY),
                        crystal.util.CTmp.v2.set(laserEndX, laserEndY), getPolygon(shieldVertices))) {
                    // 无接触，跳过
                    return;
                }

                // 5. 计算激光与力墙的最近交点
                Vec2 intersection = Tmp.v1;
                /*
                 * if (!Intersector.nearestSegmentPoint(shieldVertices, laserStartX,
                 * laserStartY, laserEndX, laserEndY, intersection)) {
                 * return;
                 * }
                 */

                // 6. 修正激光长度：覆盖官方的 fdata，用“起点到交点”的距离
                float interceptedLength = Math.max(6f,
                        Mathf.dst(laserStartX, laserStartY, intersection.x, intersection.y));
                bullet.fdata(interceptedLength); // 覆盖官方值，draw 时会用新长度

                // 7. 力墙耐久消耗（与持续激光逻辑一致）
                float laserDamage = laserType.shieldDamage(bullet) * laserDamageMulti;
                this.buildup += laserDamage;
                this.hit = 1f; // 护盾闪烁反馈
                laserHitEffect.at(intersection.x, intersection.y, laserHitColor); // 命中特效
            });

        }

        private Polygon getPolygon(Seq<Vec2> vec2s) {
            Seq<Float> floats = new Seq<>();
            for (var v : vec2s) {
                floats.addAll(v.x, v.y);
            }
            float[] f = new float[floats.size];
            for (int i = 0; i < floats.size; i++) {
                f[i] = floats.get(i);
            }
            Polygon polygon = new Polygon(f);
            return polygon;
        }

        private void interceptContinueLaserBullets() {
            // 仅在护盾激活时执行（未破碎+有有效半径）
            if (broken || realRadius() <= 0)
                return;

            float shieldRadius = realRadius();
            float shieldX = this.x;
            float shieldY = this.y;
            int shieldSides = ((LaserResistantForceProjector) block).sides;
            float shieldRot = ((LaserResistantForceProjector) block).shieldRotation;
            float laserDamageMulti = ((LaserResistantForceProjector) block).laserDamageMultiplier;
            Effect laserHitEffect = ((LaserResistantForceProjector) block).laserHitEffect;
            Color laserHitColor = ((LaserResistantForceProjector) block).laserHitColor;

            // 1. 遍历所有子弹，筛选激光类型
            Groups.bullet.each(bullet -> {
                // 筛选条件：敌方+未吸收+激光类型（持续/脉冲激光）
                if (bullet.team() == team || bullet.absorbed() ||
                        !(bullet.type() instanceof ContinuousLaserBulletType
                                || bullet.type() instanceof LaserBulletType)) {
                    return;
                }

                // 2. 构建激光向量（起点=子弹位置，终点=起点+方向×长度）
                float laserStartX = bullet.x();
                float laserStartY = bullet.y();
                float laserRot = bullet.rotation(); // vel为0时返回rotation字段（发射方向）
                float laserLength = getLaserOriginalLength(bullet); // 获取激光原长度

                // 计算激光终点
                float laserEndX = laserStartX + Angles.trnsx(laserRot, laserLength);
                float laserEndY = laserStartY + Angles.trnsy(laserRot, laserLength);

                // 3. 检测激光线段是否与力墙正多边形接触
                Seq<Vec2> shieldVertices = getShieldPolygonVertices(shieldX, shieldY, shieldRadius, shieldSides,
                        shieldRot);
                if (!Intersector.isInPolygon(shieldVertices,
                        crystal.util.CTmp.v1.set(laserEndX - laserStartX, laserEndY - laserStartY))) {
                    // 无接触，跳过
                    return;
                }

                // 4. 计算激光与护盾的交点（最近交点）
                Vec2 intersection = Tmp.v1;
                /**
                 * if (!Intersector.nearestSegmentPoint(shieldVertices, laserStartX,
                 * laserStartY, laserEndX, laserEndY,
                 * intersection)) {
                 * return;
                 * }
                 */

                // 5. 重新计算激光长度（起点到交点的距离）
                float newLaserLength = Math.max(6f,
                        Mathf.dst(laserStartX, laserStartY, intersection.x, intersection.y));
                bullet.fdata(newLaserLength); // 修改子弹fdata，官方绘制时会用此长度

                // 6. 消耗力墙耐久+视觉反馈
                float laserDamage = bullet.type().shieldDamage(bullet) * laserDamageMulti;
                this.buildup += laserDamage;
                this.hit = 1f; // 护盾闪烁
                laserHitEffect.at(intersection.x, intersection.y, laserHitColor); // 交点处显示命中特效
            });
        }

        private float getLaserOriginalLength(Bullet bullet) {
            // 脉冲激光（LaserBulletType）：fdata存储碰撞后的长度，优先用fdata
            if (bullet.fdata() > 0) {
                return bullet.fdata();
            }
            // 持续激光（ContinuousLaserBulletType）：计算生命周期衰减后的长度
            if (bullet.type() instanceof ContinuousLaserBulletType continuousType) {
                float fout = Mathf.clamp(bullet.time() > bullet.lifetime() - continuousType.fadeTime
                        ? 1f - (bullet.time() - (bullet.lifetime() - continuousType.fadeTime)) / continuousType.fadeTime
                        : 1f);
                return continuousType.length * fout;
            }
            // 不是激光类
            return 0f;
        }

        private Seq<Vec2> getShieldPolygonVertices(float cx, float cy, float radius, int sides, float rotation) {
            Seq<Vec2> vertices = new Seq<>();
            for (int i = 0; i < sides; i++) {
                float angle = rotation + (i * 360f / sides);
                float x = cx + Angles.trnsx(angle, radius);
                float y = cy + Angles.trnsy(angle, radius);
                vertices.add(new Vec2(x, y));
            }
            return vertices;
        }
    }
}
