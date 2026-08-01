package crystal.aviation.ui;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import crystal.CVars;
import mindustry.gen.Icon;
import mindustry.type.Sector;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import crystal.aviation.*;
import crystal.aviation.input.SatelliteMissileInputHandler;
import crystal.aviation.world.*;

import static mindustry.Vars.*;

/**
 * 卫星总览对话框：列出所有卫星并允许进入某颗卫星的内部地图。
 */
public class SatelliteAccessDialog extends BaseDialog {
    public SatelliteAccessDialog() {
        super("卫星总览");

        cont.clear();
        Table table = new Table();
        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);

        Seq<Satellite> list = SatelliteManager.satellites.values().toSeq();
        if (list.isEmpty()) {
            table.add("暂无卫星");
        } else {
            for (Satellite s : list) {
                table.table(t -> {
                    t.add(s.name).padRight(20f);
                    t.add("[" + (s.planet != null ? s.planet.localizedName : "未知") + "]").padRight(20f);
                    t.button("进入", Styles.cleart, () -> {
                                                enterSatellite(s);
                        hide();
                    }).size(100f, 40f);
                }).pad(4f).row();
            }
        }

        cont.add(pane).size(420f, 320f);
        buttons.button("@close", this::hide).size(120f, 50f);
        if (crystal.CVars.debug) {
            buttons.button("调试", Icon.admin, () -> new SatelliteDebugDialog().show()).size(120f, 50f);
        }
    }

    void enterSatellite(Satellite s) {
        if (SatelliteManager.enterSatelliteMap(s)) {
            ui.showInfoFade("已进入卫星 \"" + s.name + "\"");
        }
    }

    /** 从卫星地图返回星球界面（不再直接进入区块）。 */
    public static void exitToSector() {
        SatelliteMissileInputHandler.resets();
        Satellite current = SatelliteManager.get(SatelliteManager.currentSatelliteId);
        if (current != null) {
            // 先保存卫星数据；此时 exitingSatellite 仍为 false，captureFromWorld 才能正常执行
            current.mapData.captureFromWorld();
            // 立即持久化到 settings，防止后续加载普通区块存档时旧数据覆盖
            SatelliteManager.save();
        }

        // 标记正在退出，防止后续 ResetEvent/StateChangeEvent 误清理状态
        SatelliteManager.setExitingSatellite(true);
        SatelliteManager.restoreDefaultPlacementFragment();
        SatelliteManager.currentSatelliteId = -1;
        SatelliteManager.lastSector = null;
        SatelliteManager.setExitingSatellite(false);

        // 显示星球界面，并聚焦到当前卫星视角
        ui.planet.show();
        Core.app.post(() -> {
            CVars.cui.cplanet.lookAt(current);
        });
    }
}
