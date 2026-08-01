package crystal.aviation.ui;

import arc.*;
import arc.math.Mathf;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import crystal.aviation.*;
import crystal.aviation.world.*;

import static mindustry.Vars.*;

/**
 * 卫星系统调试面板。
 * 用于实时查看所有卫星状态、当前所在卫星、saveData 大小、地图尺寸，
 * 并提供强制保存/捕获/重载/进入等调试操作。
 */
public class SatelliteDebugDialog extends BaseDialog {
    private Table content;
    private ScrollPane pane;
    private float refreshInterval = 1f;
    private float timer = 0f;

    public SatelliteDebugDialog() {
        super("卫星系统调试面板");

        cont.clear();
        content = new Table();
        content.defaults().growX().left();
        content.margin(8f);

        pane = new ScrollPane(content, Styles.defaultPane);
        pane.setOverscroll(false, true);
        cont.add(pane).grow();

        buttons.button("@close", this::hide).size(120f, 50f);
        buttons.button("刷新", Icon.refresh, this::rebuild).size(120f, 50f);
        buttons.button("强制保存", Icon.save, this::forceSave).size(140f, 50f);

        shown(this::rebuild);

        // 打开后面板自动定时刷新
        update(() -> {
            timer += Time.delta;
            if (timer >= refreshInterval * 60f) {
                timer = 0f;
                if (isShown())
                    rebuild();
            }
        });
    }

    void rebuild() {
        content.clear();

        // ===== 运行时状态概览 =====
        content.add("=== 运行时状态 ===").color(Pal.accent).row();
        content.add("当前所在卫星 ID: " + SatelliteManager.currentSatelliteId).left().row();
        content.add("上一个区块: " + (SatelliteManager.lastSector != null
                ? SatelliteManager.lastSector.id + " (" + SatelliteManager.lastSector.name() + ")"
                : "无")).left().row();
        content.add("卫星总数: " + SatelliteManager.satellites.size).left().row();
        content.add("游戏状态: " + state.getState()).left().row();
        content.add("当前地图标签 crystal-aviation-satellite: " + state.map.tags.get("crystal-aviation-satellite", "无"))
                .left().row();
        content.row();

        // ===== 卫星列表 =====
        content.add("=== 卫星列表 ===").color(Pal.accent).row();
        if (SatelliteManager.satellites.isEmpty()) {
            content.add("暂无卫星").left().row();
        } else {
            for (Satellite s : SatelliteManager.satellites.values()) {
                content.table(Styles.grayPanel, t -> buildSatelliteCard(t, s)).growX().pad(4f).row();
            }
        }

        content.row();
        content.add("=== 全局操作 ===").color(Pal.accent).row();
        content.table(t -> {
            t.defaults().size(160f, 45f).pad(4f);
            t.button("从 settings 重载", Icon.download, this::reloadFromSettings)
                    .tooltip("重新从 Core.settings 读取卫星数据，会覆盖当前内存数据");
            t.button("重置运行时状态", Icon.cancel, () -> SatelliteManager.resetRuntimeState())
                    .tooltip("将 currentSatelliteId 设为 -1，不会捕获地图");
            t.button("捕获当前世界", Icon.edit, this::captureCurrentWorld).tooltip("把当前世界状态写入当前所在卫星的 saveData");
        }).left().row();

        // 滚动到顶部
        pane.setScrollY(0f);
    }

    void buildSatelliteCard(Table table, Satellite s) {
        table.left();
        table.defaults().growX().pad(2f);

        table.add("[#" + Pal.accent.toString() + "]卫星 #" + s.id + " - " + s.name + "[]").left();
        table.row();

        table.add("  所属星球: " + (s.planet != null ? s.planet.localizedName : "null") + " ("
                + (s.planet != null ? s.planet.name : "null") + ")").left();
        table.row();
        table.add("  轨道: 半径=" + Strings.fixed(s.orbitRadius, 2) + ", 角度="
                + Strings.fixed(s.orbitAngle * Mathf.radDeg, 1) + "°, 速度=" + Strings.fixed(s.orbitSpeed, 5)).left();
        table.row();
        table.add("  地图: " + s.mapData.width + "x" + s.mapData.height + ", saveData="
                + (s.mapData.saveData != null ? s.mapData.saveData.length : 0) + " bytes, center=(" + s.mapData.centerX
                + "," + s.mapData.centerY + ")").left();
        table.row();
        table.add("  可建造范围: [" + s.mapData.buildableLeft + "," + s.mapData.buildableRight + "] x ["
                + s.mapData.buildableBottom + "," + s.mapData.buildableTop + "]").left();
        table.row();
        table.add("  自定义地图: " + (s.mapData.hasCustomMap() ? s.mapData.customMapPath : "无")).left();
        table.row();
        table.add("  对接: master=" + s.dockMaster + ", docked=" + s.dockedSatellites.toString() + ", 等级=" + s.tier)
                .left();
        table.row();
        table.add("  移动: targetSector=" + s.targetSectorId + ", moving=" + s.moving + ", progress="
                + Strings.fixed(s.moveProgress, 2)).left();
        table.row();
        table.add("  太阳能=" + Strings.fixed(s.solarPower, 1) + ", 仓库条目=" + s.storage.size + ", 已扫描="
                + s.scannedSectors.size).left();
        table.row();
        table.add("  导弹库存=" + (s.missileModule != null ? s.missileModule.total() : 0)).left();
        table.row();

        table.table(bt -> {
            bt.defaults().size(100f, 40f).pad(4f);
            bt.button("进入", Icon.eye, () -> {
                hide();
                SatelliteManager.enterSatelliteMap(s);
            }).disabled(b -> SatelliteManager.currentSatelliteId == s.id);
            bt.button("保存", Icon.save, () -> {
                s.mapData.captureFromWorld();
                SatelliteManager.save();
                ui.showInfoFade("卫星 #" + s.id + " 已保存");
            }).disabled(b -> SatelliteManager.currentSatelliteId != s.id);
            bt.button("重载", Icon.upload, () -> {
                s.mapData.applyToWorld();
                ui.showInfoFade("卫星 #" + s.id + " 已重载到世界");
            }).disabled(b -> SatelliteManager.currentSatelliteId != s.id);
            bt.button("详情", Icon.info, () -> showSatelliteDetail(s));
        }).left().row();
    }

    void showSatelliteDetail(Satellite s) {
        BaseDialog detail = new BaseDialog("卫星 #" + s.id + " 详情");
        detail.cont.add("saveData 字节数组长度: " + (s.mapData.saveData != null ? s.mapData.saveData.length : 0)).left()
                .row();
        detail.cont.add("tiles 缓存: " + (s.mapData.hasTilesLoaded() ? "已加载" : "未加载")).left().row();
        detail.cont.add("buildings 缓存: " + s.mapData.buildingCacheSize()).left().row();
        detail.cont.row();
        detail.cont.add("轨道倾角: " + Strings.fixed(s.orbitTilt * Mathf.radDeg, 1) + "°").left().row();
        detail.cont.add("绑定到区块: " + s.boundToSector).left().row();
        detail.cont.add("视觉缩放: " + Strings.fixed(s.visualScale, 2)).left().row();
        detail.buttons.button("@close", detail::hide).size(120f, 50f);
        detail.show();
    }

    void forceSave() {
        try {
            SatelliteManager.save();
            Core.settings.manualSave();
            ui.showInfoFade("已强制保存卫星数据到 settings");
                    } catch (Throwable t) {
                        ui.showException("保存失败", t);
        }
    }

    void reloadFromSettings() {
        try {
            SatelliteManager.load();
            ui.showInfoFade("已从 settings 重载卫星数据");
                    } catch (Throwable t) {
                        ui.showException("重载失败", t);
        }
        rebuild();
    }

    void captureCurrentWorld() {
        if (SatelliteManager.currentSatelliteId < 0) {
            ui.showInfoFade("当前不在任何卫星地图中");
            return;
        }
        Satellite s = SatelliteManager.get(SatelliteManager.currentSatelliteId);
        if (s == null) {
            ui.showInfoFade("当前卫星不存在");
            return;
        }
        try {
            s.mapData.captureFromWorld();
            SatelliteManager.save();
            ui.showInfoFade("已捕获当前世界并保存 (卫星 #" + s.id + ")");
                    } catch (Throwable t) {
                        ui.showException("捕获失败", t);
        }
        rebuild();
    }
}
