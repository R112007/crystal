package crystal.aviation.entities;

import arc.Core;
import arc.files.Fi;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Nullable;
import arc.util.Time;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.gen.SatelliteLaunchVehicle;
import crystal.gen.SatelliteLaunchVehiclec;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import mindustry.content.Fx;
import mindustry.gen.Drawc;
import mindustry.gen.Timedc;
import mindustry.graphics.Layer;
import mindustry.type.Planet;

/**
 * 卫星发射载体实体。
 *
 * 仿照 LaunchPad 的 LaunchPayload，从地面卫星发射台升起，
 * 到达生命周期并被 remove 后，在轨道上创建对应的卫星实例。
 */
public class SatelliteLaunchVehicles {

    @EntityDef(value = { SatelliteLaunchVehiclec.class })
    @EntityComponent
    public static abstract class SatelliteLaunchVehicleComp implements Drawc, Timedc, SatelliteLaunchVehiclec {
        @Import
        public float x, y;
        @Import
        public float lifetime;
        @Import
        public float time;

        /** 卫星名称 */
        public String satelliteName = "Satellite";
        /** 所属星球 */
        public Planet planet;
        /** 自定义地图文件 */
        public @Nullable Fi mapFile;
        /** 轨道半径 */
        public float orbitRadius = -1f;
        /** 轨道角度 */
        public float orbitAngleDeg = -1f;
        /** 发射来源建筑坐标，用于进入卫星地图时定位 */
        public float launchX, launchY;
        /** 是否在创建后自动进入卫星地图 */
        public boolean autoEnter = true;

        /** 上升速度（世界单位/秒） */
        public float riseSpeed = 10f;

        @Override
        public void update() {
            y += riseSpeed * Time.delta;
        }

        @Override
        public void remove() {
            createSatellite();
        }

        public void createSatellite() {
            if (planet == null) {
                planet = mindustry.content.Planets.serpulo;
            }
            Satellite satellite = SatelliteManager.launch(
                    planet,
                    satelliteName,
                    mapFile,
                    orbitRadius,
                    orbitAngleDeg);

            if (satellite != null && autoEnter) {
                // 延迟进入卫星地图，避免与当前实体的 remove 产生竞态
                Core.app.post(() -> {
                    SatelliteManager.enterSatelliteMap(satellite);
                });
            }

            Fx.launchPod.at(x, y);
        }

        @Override
        public void draw() {
            float progress = Mathf.clamp(time / lifetime, 0f, 1f);
            float scale = 1f - progress * 0.5f;
            float alpha = 1f - progress;

            Draw.z(Layer.flyingUnit + 3f);
            Draw.color(mindustry.graphics.Pal.accent);
            Draw.alpha(alpha);
            Draw.rect("launch-pod", x, y, 18f * scale, 26f * scale, 0f);
            Draw.color();
            Draw.z(0f);
        }
    }

    /** 从地面发射台发射一颗新卫星。 */
    public static void launch(Planet planet, String name, Fi mapFile,
            float orbitRadius, float orbitAngleDeg,
            float x, float y, boolean autoEnter) {
        SatelliteLaunchVehicle vehicle = SatelliteLaunchVehicle.create();
        vehicle.set(x, y);
        vehicle.lifetime = 150f;
        vehicle.planet = planet;
        vehicle.satelliteName = name;
        vehicle.mapFile = mapFile;
        vehicle.orbitRadius = orbitRadius;
        vehicle.orbitAngleDeg = orbitAngleDeg;
        vehicle.launchX = x;
        vehicle.launchY = y;
        vehicle.autoEnter = autoEnter;
        vehicle.add();
        Fx.launch.at(x, y);
    }
}
