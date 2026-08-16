package crystal.aviation.blocks;

import arc.*;
import arc.graphics.*;
import arc.math.Mathf;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.ObjectMap;
import arc.util.*;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.UI;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.*;
import crystal.world.meta.CStat;
import mindustry.world.meta.*;

import crystal.aviation.*;
import crystal.aviation.input.SatelliteMissileInputHandler;
import crystal.aviation.ui.*;
import crystal.type.SatelliteMissile;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

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
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(Items.silicon, 100),
                new ItemStack(Items.titanium, 80),
                new ItemStack(Items.copper, 120)
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.satelliteLimit, SatelliteManager.maxSatellitesPerPlanet);
    }

    @Override
    public boolean canBreak(Tile tile) {
        return false;
    }

    public class SatelliteControlCenterBuild extends Building {
        /** 绑定的卫星ID */
        public int satelliteId = -1;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            Satellite initialSatellite = SatelliteManager.get(satelliteId);
            if (satelliteId < 0 || initialSatellite == null) {
                table.add("未绑定卫星").row();
                return;
            }

            table.clear();
            table.top();

            Table content = new Table();
            content.defaults().growX().left();
            content.margin(8f);

            content.update(() -> {
                content.clearChildren();
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null) {
                    content.add("[red]卫星已失效或不存在[]").row();
                    return;
                }

                content.add("[cyan]" + s.name + "[]  [#" + s.id + "]").padBottom(8f).row();
                content.image().growX().height(2f).color(Pal.accent).padBottom(8f).row();
                content.add("[lightgray]所属星球: []" + (s.planet != null ? s.planet.localizedName : "未知")).row();
                content.label(() -> {
                    Satellite currentSat = SatelliteManager.get(satelliteId);
                    if (currentSat == null)
                        return "[lightgray]绑定区块: []未知";
                    if (!currentSat.boundToSector || currentSat.targetSectorId < 0)
                        return "[lightgray]绑定区块: []未绑定";
                    Sector boundSector = currentSat.planet != null
                            ? currentSat.planet.sectors.find(se -> se.id == currentSat.targetSectorId)
                            : null;
                    return "[lightgray]绑定区块: [#" + currentSat.targetSectorId + " "
                            + (boundSector != null ? boundSector.name() : "未知") + "]";
                }).row();
                content.add("[lightgray]等级: []" + s.tier + " / " + Satellite.maxTier).row();
                content.add("[lightgray]生命值: []" + (int) s.health + " / " + (int) s.maxHealth).row();
                content.add("[lightgray]扩容仓: []" + s.expansionBeaconCount + " / " + s.expansionBeaconLimit).row();
                content.add("[lightgray]液体仓: []" + s.liquidTankCount + " / " + s.liquidTankLimit).row();
                content.add(new Bar(() -> {
                    Satellite currentSat = SatelliteManager.get(satelliteId);
                    int total = currentSat == null ? 0 : currentSat.items.total();
                    int unlocked = Math.max(1, Vars.content.items().count(i -> state.rules != null && i.unlocked()));
                    int cap = currentSat == null ? 0 : (int) (unlocked * currentSat.itemStorageCapacity);
                    return "物品: " + total + "/" + cap;
                }, () -> Pal.items, () -> {
                    Satellite currentSat = SatelliteManager.get(satelliteId);
                    if (currentSat == null || currentSat.itemStorageCapacity <= 0.001f)
                        return 0f;
                    int unlocked = Math.max(1, Vars.content.items().count(i -> state.rules != null && i.unlocked()));
                    return currentSat.items.total() / (unlocked * currentSat.itemStorageCapacity);
                })).growX().height(18f).padTop(4f).row();

                content.add("[accent]物品明细[]").padTop(6f).padBottom(2f).row();
                Table itemTable = new Table();
                itemTable.defaults().growX().left();
                boolean hasItems = false;
                for (Item item : Vars.content.items()) {
                    int amount = s.items.get(item);
                    if (amount > 0) {
                        itemTable.add("  " + item.localizedName + ": " + amount).color(item.color).row();
                        hasItems = true;
                    }
                }
                if (!hasItems) {
                    itemTable.add("  无物品").row();
                }
                content.add(itemTable).growX().row();

                content.add("[accent]注入配置[]").padTop(8f).padBottom(4f).row();
                if (s.injectItems.isEmpty()) {
                    content.add("  无注入配置").row();
                } else {
                    Table injectTable = new Table();
                    injectTable.defaults().left();
                    int i = 0;
                    for (var entry : s.injectItems) {
                        Item item = entry.key;
                        int perInject = entry.value;
                        if (item == null || perInject <= 0)
                            continue;
                        int rate = (int) (perInject * 60f / Satellite.injectInterval);
                        injectTable.image(item.uiIcon).size(16f).padRight(2f);
                        injectTable.add(item.localizedName + " " + UI.formatAmount(rate) + "/min").color(item.color)
                                .padRight(6f);
                        if (++i % 2 == 0)
                            injectTable.row();
                    }
                    content.add(injectTable).padLeft(10f).row();
                    if (!s.injectMode) {
                        content.add("  [gray]注入模式已关闭[]").row();
                    }
                }

                content.add("[accent]液体库存[]").padTop(8f).padBottom(4f).row();
                if (s.liquidStorage.isEmpty()) {
                    content.add("  无液体").row();
                } else {
                    for (var entry : s.liquidStorage) {
                        Liquid liquid = entry.key;
                        float amount = entry.value;
                        if (liquid == null || amount <= 0.001f)
                            continue;
                        content.add(new Bar(
                                () -> liquid.localizedName + ": " + Strings.fixed(s.liquidStorage.get(liquid, 0f), 1)
                                        + "/" + (int) s.liquidCapacity,
                                () -> liquid.color,
                                () -> s.liquidCapacity <= 0.001f ? 0f
                                        : s.liquidStorage.get(liquid, 0f) / s.liquidCapacity))
                                .growX().height(18f).padTop(2f).row();
                    }
                }

                // 各区块向本卫星发射资源速率
                ObjectMap<String, ObjectMap<Item, SatelliteSectorInfo.ExportStat>> itemExports = SatelliteSectorInfoManager
                        .getItemExportsToSatellite(s);
                ObjectMap<String, ObjectMap<Liquid, SatelliteSectorInfo.ExportStat>> liquidExports = SatelliteSectorInfoManager
                        .getLiquidExportsToSatellite(s);
                boolean hasExports = itemExports.size > 0 || liquidExports.size > 0;
                content.add("[accent]区块供应速率[]").padTop(8f).padBottom(4f).row();
                if (!hasExports) {
                    content.add("  暂无区块向本卫星发射资源").row();
                } else {
                    for (var entry : itemExports) {
                        Sector src = SatelliteSectorInfoManager.sectorFromKey(entry.key);
                        String srcName = src != null ? src.name() : entry.key;
                        content.add("  [lightgray]" + srcName + "[]").row();
                        Table rateTable = new Table();
                        rateTable.defaults().left();
                        int i = 0;
                        for (var item : Vars.content.items()) {
                            SatelliteSectorInfo.ExportStat stat = entry.value.get(item);
                            if (stat == null)
                                continue;
                            int rate = (int) (stat.mean * 60);
                            if (rate > 1) {
                                rateTable.image(item.uiIcon).size(16f).padRight(2f);
                                rateTable.add(UI.formatAmount(rate) + "/min").color(item.color).padRight(6f);
                                if (++i % 3 == 0)
                                    rateTable.row();
                            }
                        }
                        content.add(rateTable).padLeft(10f).row();
                    }
                    for (var entry : liquidExports) {
                        Sector src = SatelliteSectorInfoManager.sectorFromKey(entry.key);
                        String srcName = src != null ? src.name() : entry.key;
                        content.add("  [lightgray]" + srcName + " (液体)[]").row();
                        Table rateTable = new Table();
                        rateTable.defaults().left();
                        int i = 0;
                        for (var liquid : Vars.content.liquids()) {
                            SatelliteSectorInfo.ExportStat stat = entry.value.get(liquid);
                            if (stat == null)
                                continue;
                            int rate = (int) (stat.mean * 60);
                            if (rate > 1) {
                                rateTable.image(liquid.uiIcon).size(16f).padRight(2f);
                                rateTable.add(UI.formatAmount(rate) + "/min").color(liquid.color).padRight(6f);
                                if (++i % 3 == 0)
                                    rateTable.row();
                            }
                        }
                        content.add(rateTable).padLeft(10f).row();
                    }
                }

                content.add("[lightgray]太阳能: []" + Strings.fixed(s.totalSolarPower, 1) + " /s").row();
                content.add("[lightgray]轨道打击缩放加成: []" + Strings.fixed(s.strikeZoomBonus, 1)).row();

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
                                missileTable.add("  " + (m.name != null ? m.name : "未知导弹") + ": " + amount + " 发")
                                        .row();
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

            Table panel = new Table(Tex.pane2);
            panel.margin(8f);
            panel.add(pane).size(320f, 220f).padBottom(8f).row();

            // 科技感按钮样式：青色文字、扁平背景
            TextButton.TextButtonStyle techStyle = new TextButton.TextButtonStyle(Styles.flatt);
            techStyle.fontColor = Pal.accent;
            techStyle.downFontColor = Color.white;
            techStyle.overFontColor = Color.valueOf("aaffff");
            techStyle.disabledFontColor = Color.gray;

            Table buttons = new Table();
            buttons.defaults().growX().height(44f).pad(2f);

            var btnSelectSector = buttons.button("[accent]选择目标区块[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null)
                    new SatelliteSectorDialog(s).show();
            }).get();
            buttons.row();

            var btnUnbind = buttons.button("[accent]取消绑定区块[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null) {
                    s.unbindSector();
                    SatelliteManager.save();
                    ui.showInfoFade("已取消绑定区块");
                }
            }).get();
            buttons.row();

            var btnOrbitalStrike = buttons.button("[accent]轨道打击[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null)
                    SatelliteMissileInputHandler.enterForSatellite(s);
            }).get();
            buttons.row();

            var btnAutoAttack = buttons.button("[accent]自动攻击: 关[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null) {
                    s.autoAttackMode = !s.autoAttackMode;
                    SatelliteManager.save();
                    ui.showInfoFade("自动攻击: " + (s.autoAttackMode ? "开" : "关"));
                }
            }).get();
            buttons.row();

            var btnInject = buttons.button("[accent]注入模式: 关[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s != null) {
                    s.injectMode = !s.injectMode;
                    SatelliteManager.save();
                    ui.showInfoFade("注入模式: " + (s.injectMode ? "开" : "关"));
                }
            }).get();
            buttons.row();

            var btnSelectMissile = buttons.button("[accent]选择导弹[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null || s.missileModule == null)
                    return;
                BaseDialog dialog = new BaseDialog("选择卫星导弹");
                dialog.cont.defaults().pad(6f);
                for (SatelliteMissile missile : SatelliteMissile.map.values()) {
                    int amount = s.missileModule.get(missile);
                    dialog.cont.button(b -> {
                        b.left();
                        b.defaults().left();
                        b.add("[accent]" + missile.name + "[] [lightgray](" + amount + ")").growX().left().row();
                        missile.displayStats(b);
                    }, Styles.flatt, () -> {
                        s.selectedMissile = missile;
                        SatelliteManager.save();
                        dialog.hide();
                    }).size(320f, 110f).pad(4f).row();
                }
                dialog.addCloseButton();
                dialog.show();
            }).get();
            buttons.row();

            buttons.button("[accent]管理卫星[]", techStyle, () -> {
                new SatelliteAccessDialog().show();
            }).row();

            var btnTechTree = buttons.button("[accent]科技树[]", techStyle, () -> {
                crystal.CVars.cui.cresearch.show();
            }).get();
            buttons.row();

            var btnRetire = buttons.button("[accent]移除卫星[]", techStyle, () -> {
                if (SatelliteManager.currentSatelliteId >= 0) {
                    SatelliteManager.retire(SatelliteManager.currentSatelliteId);
                }
            }).get();
            buttons.row();

            var btnRename = buttons.button("[accent]修改卫星名字[]", techStyle, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null)
                    return;
                Vars.ui.showTextInput("", "请输入卫星名称", 16, s.name, false,
                        newName -> {
                            String trimName = newName == null ? "" : newName.trim();
                            if (trimName.isEmpty()) {
                                Vars.ui.showErrorMessage(Core.bundle.get("changeplayername.nonull"));
                                return;
                            }
                            SatelliteManager.rename(satelliteId, trimName);
                            Vars.ui.showInfo(Core.bundle.format("changeplayername.done", trimName));
                        }, () -> {
                        });
            }).get();
            buttons.row();

            var btnExit = buttons.button("[accent]退出卫星[]", techStyle, () -> {
                SatelliteAccessDialog.exitToSector();
            }).get();
            buttons.row();

            buttons.update(() -> {
                Satellite s = SatelliteManager.get(satelliteId);
                boolean hasSatellite = (s != null);
                boolean isCurrent = (SatelliteManager.currentSatelliteId >= 0);
                boolean bound = hasSatellite && s.boundToSector;

                btnSelectSector.visible = hasSatellite;
                btnUnbind.visible = bound;
                btnOrbitalStrike.visible = bound;
                btnAutoAttack.visible = bound;
                btnInject.visible = bound;
                btnAutoAttack.setText("[accent]自动攻击: " + (hasSatellite && s.autoAttackMode ? "开" : "关") + "[]");
                btnInject.setText("[accent]注入模式: " + (hasSatellite && s.injectMode ? "开" : "关") + "[]");
                String missileName = (hasSatellite && s.selectedMissile != null) ? s.selectedMissile.name : "未选择";
                btnSelectMissile.setText("[accent]导弹: " + missileName + "[]");
                btnTechTree.visible = isCurrent;
                btnRetire.visible = isCurrent;
                btnRename.visible = isCurrent;
                btnExit.visible = isCurrent;
            });

            ScrollPane buttonPane = new ScrollPane(buttons, Styles.defaultPane);
            buttonPane.setOverscroll(false, true);
            panel.add(buttonPane).size(320f, 220f).growX();
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
