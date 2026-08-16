package crystal.aviation.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.consumers.*;
import crystal.world.meta.CStat;
import mindustry.world.meta.*;

import crystal.aviation.*;
import crystal.aviation.entities.SatelliteLaunchVehicles;
import crystal.aviation.ui.*;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星发射台。
 * 获得足够物品后，玩家点击可输入卫星名称，播放发射特效并自毁，
 * 随后在所属星球轨道创建一颗新卫星实例。
 */
public class SatelliteLauncher extends Block {
    /** 发射所需物品 */
    public ItemStack[] launchCost = new ItemStack[] {
    };
    /** 发射特效 */
    public Effect launchEffect = Fx.launch;
    /** 发射持续时间（tick） */
    public float launchDuration = 120f;

    public SatelliteLauncher(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        itemCapacity = 400;
        buildCostMultiplier = 0.5f;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.launchCost, StatValues.items(launchCost));
        stats.add(Stat.launchTime, launchDuration / 60f, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理物品 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("items");
    }

    public class SatelliteLauncherBuild extends Building {
        /** 发射进度 */
        public float launchProgress = 0f;
        /** 是否正在发射 */
        public boolean launching = false;
        /** 已输入的卫星名称 */
        public String satelliteName = "";
        /** 本次发射使用的自定义地图文件 */
        public @Nullable arc.files.Fi selectedMapFile;
        /** 发射轨道半径（相对于星球半径的倍数） */
        public float launchOrbitRadius = -1f;
        /** 发射初始轨道角度（度） */
        public float launchOrbitAngleDeg = -1f;
        /** 发射完成后等待下一逻辑帧自毁，避免在 Android 渲染线程直接 kill 导致 tile 事件竞态崩溃 */
        public boolean pendingKill = false;
        /** 自毁后需要进入的卫星（延迟到 kill 完成后再跳转，避免竞态） */
        public @Nullable Satellite pendingEnterSatellite;

        @Override
        public void updateTile() {
            if (pendingKill) {
                pendingKill = false;
                if (!dead)
                    kill();
                // kill 完成后再跳转卫星地图，避免与 TilePreChange/MinimapRenderer 竞态
                if (pendingEnterSatellite != null) {
                    Satellite s = pendingEnterSatellite;
                    pendingEnterSatellite = null;
                    Core.app.post(() -> SatelliteManager.enterSatelliteMap(s));
                }
                return;
            }

            if (launching) {
                launchProgress += edelta();
                if (launchProgress >= launchDuration) {
                    finishLaunch();
                }
            }
        }

        @Override
        public void draw() {
            super.draw();
            // 绘制简单的发射进度指示
            if (launching) {
                float p = launchProgress / launchDuration;
                Draw.color(Color.valueOf("ffaa00"), p);
                Fill.circle(x, y, 8f + p * 16f);
                Draw.color();
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            if (launching)
                return;

            table.button(Icon.upOpen, Styles.cleari, () -> {
                // 检查是否满足物品
                if (!hasLaunchItems()) {
                    ui.showInfo("资源不足");
                    return;
                }
                // 检查星球卫星上限
                Planet planet = state.rules.sector != null ? state.rules.sector.planet : Planets.serpulo;
                if (!SatelliteManager.canLaunchOn(planet)) {
                    ui.showInfo("该星球卫星数量已达上限");
                    return;
                }
                // 弹出名称输入框，可选择自定义地图文件
                SatelliteNameDialog dialog = new SatelliteNameDialog(result -> {
                    if (result == null || result.name == null || result.name.isEmpty())
                        return;
                    this.satelliteName = result.name;
                    this.selectedMapFile = result.mapFile;
                    this.launchOrbitRadius = result.orbitRadius;
                    this.launchOrbitAngleDeg = result.orbitAngleDeg;
                    configure(result.name); // 只同步名称，地图文件与轨道参数保留在本地
                });
                dialog.show();
            }).size(40f).tooltip("发射卫星");
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar("发射进度",
                    Pal.power,
                    () -> launching ? launchProgress / launchDuration : 0f))
                    .height(18f).row();
            table.add(new Bar(() -> "物品准备: " + (items == null ? 0 : items.total()) + "/" + itemCapacity,
                    () -> Pal.items,
                    () -> items == null ? 0f : items.total() / (float) itemCapacity))
                    .growX().height(18f).row();
        }

        @Override
        public void configured(Unit builder, Object value) {
            if (value instanceof String name && !launching) {
                satelliteName = name;
                launching = true;
                launchProgress = 0f;
                launchEffect.at(x, y, rotation, Color.white, this);
                Effect.shake(6f, 60f, x, y);
            }
        }

        /** 是否已集齐发射所需物品 */
        public boolean hasLaunchItems() {
            for (ItemStack stack : launchCost) {
                if (items.get(stack.item) < stack.amount)
                    return false;
            }
            return true;
        }

        void finishLaunch() {
            launching = false;
            launchProgress = 0f;

            // 消耗物品
            for (ItemStack stack : launchCost) {
                items.remove(stack.item, stack.amount);
            }

            // 创建发射载体实体，实体升空 remove 后才会真正创建卫星
            Planet planet = state.rules.sector != null ? state.rules.sector.planet : Planets.serpulo;
            SatelliteLaunchVehicles.launch(
                    planet,
                    satelliteName.isEmpty() ? "Satellite" : satelliteName,
                    selectedMapFile,
                    launchOrbitRadius,
                    launchOrbitAngleDeg,
                    x, y, true);
            selectedMapFile = null;
            launchOrbitRadius = -1f;
            launchOrbitAngleDeg = -1f;

            // 自毁：标记为等待下一逻辑帧在 updateTile 中执行，避免在 Android 渲染线程直接 kill
            // 导致 TilePreChangeEvent / MinimapRenderer 的竞态 NullPointerException。
            pendingKill = true;
            Fx.rocketSmokeLarge.at(x, y);
            ui.showInfoFade("卫星发射载体已升空");
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(launchProgress);
            write.bool(launching);
            write.str(satelliteName);
            write.f(launchOrbitRadius);
            write.f(launchOrbitAngleDeg);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            launchProgress = read.f();
            launching = read.bool();
            satelliteName = read.str();
            if (revision >= 2) {
                launchOrbitRadius = read.f();
                launchOrbitAngleDeg = read.f();
            }
        }
    }
}
