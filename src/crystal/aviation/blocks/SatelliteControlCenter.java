package crystal.aviation.blocks;

import arc.*;
import arc.math.Mathf;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import crystal.aviation.*;
import crystal.aviation.input.SatelliteMissileInputHandler;
import crystal.aviation.ui.*;
import crystal.type.SatelliteMissile;

import static mindustry.Vars.*;

/**
 * 卫星控制中心。
 * 放置在卫星地图上，用于查看卫星信息并执行各种操作。
 */
public class SatelliteControlCenter extends Block {
    public SatelliteControlCenter(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 100),
                new ItemStack(mindustry.content.Items.titanium, 80),
                new ItemStack(mindustry.content.Items.copper, 120)
        });
    }

    public class SatelliteControlCenterBuild extends Building {
        /** 绑定的卫星ID */
        public int satelliteId = -1;

        @Override
        public void created() {
            super.created();
            // 在卫星地图中新建造时绑定当前卫星
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            // 1. 初始状态检查（仅在打开面板的瞬间执行一次）
            Satellite initialSatellite = SatelliteManager.get(satelliteId);
            if (satelliteId < 0 || initialSatellite == null) {
                table.add("未绑定卫星").row();
                return;
            }

            // 2. 初始化主面板
            table.clear();
            table.top();

            Table content = new Table();
            content.defaults().growX().left();
            content.margin(8f);

            // 3. 卫星基本信息与导弹库存：动态更新
            content.update(() -> {
                content.clearChildren();

                // 每一帧都重新获取最新的卫星对象
                Satellite s = SatelliteManager.get(satelliteId);

                if (s == null) {
                    content.add("[red]卫星已失效或不存在[]").row();
                    return;
                }

                // 卫星基本信息
                content.add("[accent]" + s.name + "[]  #" + s.id).padBottom(4f).row();
                content.add("所属星球: " + (s.planet != null ? s.planet.localizedName : "未知")).row();
                content.label(() -> {
                    Satellite currentSat = SatelliteManager.get(satelliteId);
                    if (currentSat == null)
                        return "绑定区块: 未知";
                    if (!currentSat.boundToSector || currentSat.targetSectorId < 0)
                        return "绑定区块: 未绑定";
                    Sector boundSector = currentSat.planet != null
                            ? currentSat.planet.sectors.find(se -> se.id == currentSat.targetSectorId)
                            : null;
                    return "绑定区块: #" + currentSat.targetSectorId + " "
                            + (boundSector != null ? boundSector.preset.localizedName : "未知");
                }).row();
                content.add("轨道半径: " + Strings.fixed(s.orbitRadius, 2)).row();
                content.add("轨道角度: " + Strings.fixed(s.orbitAngle * Mathf.radDeg, 1) + "°").row();
                content.add("太阳能: " + Strings.fixed(s.solarPower, 1)).row();
                content.add("等级: " + s.tier).row();
                content.add("对接卫星: " + s.dockedSatellites.size + " 颗").row();

                // 导弹库存
                content.row();
                content.add("[accent]导弹库存[]").padTop(8f).padBottom(4f).row();
                Table missileTable = new Table();
                missileTable.defaults().growX().left();
                missileTable.update(() -> {
                    missileTable.clearChildren();
                    Satellite currentSat = SatelliteManager.get(satelliteId);
                    if (currentSat != null && currentSat.missileModule != null
                            && currentSat.missileModule.total() > 0 && SatelliteMissile.map != null) {
                        for (SatelliteMissile m : SatelliteMissile.map.values()) {
                            if (m == null)
                                continue;
                            int amount = currentSat.missileModule.get(m);
                            if (amount > 0) {
                                missileTable.add("  " + (m.name != null ? m.name : "未知导弹") + ": " + amount + " 发").row();
                            }
                        }
                    } else {
                        missileTable.add("  无导弹库存").row();
                    }
                });
                content.add(missileTable).growX().row();
            });

            ScrollPane pane = new ScrollPane(content, Styles.defaultPane);
            pane.setOverscroll(false, true);

            // 使用深色背景包裹整个面板
            Table panel = new Table(Tex.pane2);
            panel.margin(10f);
            panel.add(pane).size(260f, 220f).padBottom(8f).row();

            // 4. 操作按钮区域：只创建一次，状态动态更新
            Table buttons = new Table();
            buttons.defaults().growX().height(42f).pad(2f);

            // 创建按钮（只执行一次）
            var btnSelectSector = buttons.button("选择目标区块", Styles.flatt, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null)
                    new SatelliteSectorDialog(s).show();
            }).get();

            var btnOrbitalStrike = buttons.button("轨道打击", Styles.flatt, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null)
                    SatelliteMissileInputHandler.enterForSatellite(s);
            }).get();

            buttons.button("管理卫星", Styles.flatt, () -> {
                new SatelliteAccessDialog().show();
            }).row();

            var btnRetire = buttons.button("移除卫星", Styles.flatt, () -> {
                if (SatelliteManager.currentSatelliteId >= 0) {
                    SatelliteManager.retire(SatelliteManager.currentSatelliteId);
                }
            }).get();

            var btnRename = buttons.button("修改卫星名字", Styles.flatt, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null)
                    return;

                Vars.ui.showTextInput(
                        "",
                        "请输入卫星名称",
                        16,
                        s.name,
                        false,
                        newName -> {
                            String trimName = newName == null ? "" : newName.trim();
                            if (trimName.isEmpty()) {
                                Vars.ui.showErrorMessage(Core.bundle.get("changeplayername.nonull"));
                                return;
                            }
                            SatelliteManager.rename(satelliteId, trimName);
                            Vars.ui.showInfo(Core.bundle.format("changeplayername.done", trimName));
                        },
                        () -> {
                        });
            }).get();

            var btnExit = buttons.button("退出卫星", Styles.flatt, () -> {
                SatelliteAccessDialog.exitToSector();
            }).get();

            // 动态控制按钮的可见性（每一帧检查，直接赋值字段）
            buttons.update(() -> {
                Satellite s = SatelliteManager.get(satelliteId);
                boolean hasSatellite = (s != null);
                boolean isCurrent = (SatelliteManager.currentSatelliteId >= 0);

                btnSelectSector.visible = hasSatellite;
                btnOrbitalStrike.visible = hasSatellite && s.boundToSector;
                btnRetire.visible = isCurrent;
                btnRename.visible = isCurrent;
                btnExit.visible = isCurrent;
            });

            panel.add(buttons).growX();
            table.add(panel);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
        }
    }
}
