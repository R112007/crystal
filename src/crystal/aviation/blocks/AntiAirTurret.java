package crystal.aviation.blocks;

import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Tmp;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 高射炮。
 *
 * 敌方建筑（非玩家队伍）会攻击绑定到当前区块的卫星，每次命中造成固定伤害。
 * 玩家队伍建造时不会攻击自己的卫星。
 */
public class AntiAirTurret extends Block {

    /** 单次射击伤害。 */
    public float damage = 50f;
    /** 射击间隔（秒）。 */
    public float reloadTime = 2f;
    /** 射程半径（世界单位），覆盖整张地图。 */
    public float range = 2000f;
    /** 每次射击消耗的电力（每秒），0 表示不耗电。 */
    public float powerUse = 3f;
    /** 射击特效。 */
    public Effect shootEffect = Fx.shootBig;

    public AntiAirTurret(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        targetable = true;
        hasPower = true;
        consumesPower = true;
        requirements(Category.turret, new ItemStack[] {
                new ItemStack(mindustry.content.Items.copper, 120),
                new ItemStack(mindustry.content.Items.lead, 100),
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.titanium, 60)
        });
        if (powerUse > 0f) {
            consumePower(powerUse);
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.damage, damage);
        stats.add(Stat.reload, 60f / reloadTime, StatUnit.perSecond);
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.powerUse, powerUse * 60f, StatUnit.powerSecond);
    }

    public class AntiAirTurretBuild extends Building {
        /** 装填计时器（秒） */
        public float reload = 0f;
        /** 目标卫星 */
        public transient Satellite target;

        @Override
        public void updateTile() {
            // 玩家队伍不攻击自己的卫星
            if (team == Team.sharded)
                return;

            reload -= Time.delta / 60f;
            if (reload < 0f)
                reload = 0f;

            target = findTargetSatellite();
            if (target == null)
                return;

            // 需要电力且未充满时不射击
            if (powerUse > 0f && (power == null || power.status < 0.999f))
                return;

            if (reload <= 0f) {
                shoot(target);
                reload = reloadTime;
            }
        }

        /** 查找绑定到当前区块的玩家卫星。 */
        Satellite findTargetSatellite() {
            if (state.rules.sector == null)
                return null;
            mindustry.type.Sector sector = state.rules.sector;
            for (Satellite s : SatelliteManager.satellites.values()) {
                if (s.planet == sector.planet && s.boundToSector && s.targetSectorId == sector.id) {
                    return s;
                }
            }
            return null;
        }

        void shoot(Satellite s) {
            if (s == null)
                return;
            s.damage(damage);
            SatelliteManager.save();

            // 特效：向天空发射一束激光
            float angle = s.orbitAngle;
            float r = s.planet.radius * s.orbitRadius;
            Vec2 skyPos = Tmp.v1.set(
                    s.planet.position.x + Mathf.cos(angle) * r,
                    s.planet.position.y + Mathf.sin(angle) * r * Mathf.cos(s.orbitTilt));

            if (shootEffect != null) {
                shootEffect.at(x, y, angle(x, y, skyPos.x, skyPos.y));
            }
            Fx.hitBulletBig.at(skyPos.x, skyPos.y, Pal.remove);

            // 卫星生命值为 0 时触发坠毁提示（由其他逻辑处理实体移除）
            if (s.health <= 0f) {
                Fx.reactorExplosion.at(skyPos.x, skyPos.y);
            }
        }

        float angle(float x1, float y1, float x2, float y2) {
            return Mathf.angle(x2 - x1, y2 - y1);
        }

        @Override
        public void draw() {
            super.draw();
            if (target != null && team != Team.sharded) {
                // 绘制瞄准线指向天空中的卫星
                float angle = target.orbitAngle;
                float r = target.planet.radius * target.orbitRadius;
                float sx = target.planet.position.x + Mathf.cos(angle) * r;
                float sy = target.planet.position.y + Mathf.sin(angle) * r * Mathf.cos(target.orbitTilt);

                Draw.color(Pal.remove);
                Draw.alpha(0.4f);
                Drawf.line(Pal.remove, y, sx, sy, 0.4f);
                Draw.color();
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar(() -> "装填: " + Mathf.round((1f - reload / reloadTime) * 100f) + "%",
                    () -> Pal.ammo, () -> 1f - reload / reloadTime))
                    .growX().height(18f).row();
        }
    }
}
