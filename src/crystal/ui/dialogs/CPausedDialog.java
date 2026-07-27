package crystal.ui.dialogs;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.editor.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.*;
import crystal.aviation.SatelliteManager;

import static mindustry.Vars.*;

public class CPausedDialog extends BaseDialog {
    private MapProcessorsDialog processors = new MapProcessorsDialog();
    private SaveDialog save = new SaveDialog();
    private LoadDialog load = new LoadDialog();
    private CustomRulesDialog rulesDialog = new CustomRulesDialog();

    public CPausedDialog() {
        super("@menu");
        shouldPause = true;

        clearChildren();
        add(titleTable).growX().row();

        stack(cont, new Table(t -> {
            t.bottom().left();
            t.button(Icon.book, () -> {
                Rules toEdit = Vars.state.rules.copy();
                rulesDialog.show(toEdit, () -> state.rules.copy());
                rulesDialog.hidden(() -> {
                    // apply rule changes only once it is hidden
                    Vars.state.rules = toEdit;
                    Call.setRules(toEdit);
                });
            }).size(70f).tooltip("@customize")
                    .visible(() -> state.rules.allowEditRules && (net.server() || !net.active()));
        })).grow().row();

        shown(() -> {
            rebuild();

            // 打开暂停菜单时自动保存当前卫星地图
            if (SatelliteManager.currentSatelliteId >= 0) {
                SatelliteManager.onPauseDialogOpen();
            }

            if (state.isCampaign()) {
                state.getPlanet().saveStats();
            }
        });

        addCloseListener();
    }

    void rebuild() {
        cont.clear();

        update(() -> {
            if (state.isMenu() && isShown()) {
                hide();
            }
        });

        if (!mobile) {
            if (steam) {
                cont.check("@steam.friendsonly", !Core.settings.getBool("steampublichost2"), val -> {
                    Core.settings.put("steampublichost2", !val);
                    platform.updateLobby();
                }).colspan(2).left().with(c -> ui.addDescTooltip(c, "@steam.friendsonly.tooltip")).width(440f)
                        .visible(() -> net.server()).center().colspan(2).fillX().padBottom(10f).row();
            }

            float dw = 220f;
            cont.defaults().width(dw).height(55).pad(5f);

            boolean showObjective = state.rules.sector != null && state.rules.sector.preset != null
                    && state.rules.sector.preset.description != null;

            if (showObjective) {
                cont.button("@objective", Icon.info,
                        () -> ui.fullText.show("@objective",
                                state.rules.sector != null && state.rules.sector.preset != null
                                        ? state.rules.sector.preset.description
                                        : "oh dear"))
                        .padTop(-60f);
            }

            cont.button("@abandon", Icon.cancel, () -> ui.planet.abandonSectorConfirm(state.rules.sector, this::hide))
                    .padTop(-60f)
                    .colspan(showObjective ? 1 : 2).width(showObjective ? dw : dw * 2 + 10f)
                    .disabled(b -> net.client() || state.gameOver).visible(() -> state.rules.sector != null).row();

            cont.button("@back", Icon.left, this::hide).name("back");
            cont.button("@settings", Icon.settings, ui.settings::show).name("settings");

            if (!state.isCampaign() && !state.isEditor()) {
                cont.row();
                cont.button("@savegame", Icon.save, save::show);
                cont.button("@loadgame", Icon.upload, load::show).disabled(b -> net.active());
            }

            cont.row();

            // the button runs out of space when the editor button is added, so use the
            // mobile text
            cont.button(state.isEditor() ? "@hostserver.mobile" : "@hostserver", Icon.host, () -> {
                if (net.server() && steam) {
                    platform.inviteFriends();
                } else {
                    ui.host.show();
                }
            }).disabled(b -> !((steam && net.server()) || !net.active())).colspan(state.isEditor() ? 1 : 2)
                    .width(state.isEditor() ? dw : dw * 2 + 10f)
                    .update(e -> e.setText(net.server() && steam ? "@invitefriends"
                            : state.isEditor() ? "@hostserver.mobile" : "@hostserver"));

            if (state.isEditor()) {
                cont.button("@editor.worldprocessors", Icon.logic, () -> {
                    hide();
                    processors.show();
                });
            }

            cont.row();

            cont.button("@quit", Icon.exit, this::showQuitConfirm).colspan(2).width(dw + 10f)
                    .update(s -> s.setText(
                            control.saves.getCurrent() != null && control.saves.getCurrent().isAutosave() ? "@save.quit"
                                    : "@quit"));

        } else {
            cont.defaults().size(130f).pad(5);
            cont.buttonRow("@back", Icon.play, this::hide);
            cont.buttonRow("@settings", Icon.settings, ui.settings::show);

            if (!state.isCampaign() && !state.isEditor() && SatelliteManager.currentSatelliteId < 0) {
                cont.buttonRow("@save", Icon.save, save::show);

                cont.row();

                cont.buttonRow("@load", Icon.download, () -> {
                    if (net.active()) {
                        ui.database.show();
                    } else {
                        load.show();
                    }
                }).update(t -> {
                    Image image = (Image) t.getChildren().first();
                    image.setDrawable(net.active() ? Icon.book : Icon.download);
                    t.setText(net.active() ? "@database" : "@load");
                });
            } else if (state.isCampaign() && SatelliteManager.currentSatelliteId < 0) {
                cont.buttonRow("@research", Icon.tree, ui.research::show);

                cont.row();

                cont.buttonRow("@planetmap", Icon.map, () -> {
                    hide();
                    ui.planet.show();
                });
            } else if (SatelliteManager.currentSatelliteId >= 0) {
                // 在卫星地图中：显示返回行星地图按钮，点击时先保存卫星再返回
                cont.row();

                cont.buttonRow("@planetmap", Icon.map, () -> {
                    hide();
                    crystal.aviation.ui.SatelliteAccessDialog.exitToSector();
                });
            } else {
                cont.row();
            }

            cont.buttonRow("@hostserver.mobile", Icon.host, ui.host::show).disabled(b -> net.active());

            cont.buttonRow("@quit", Icon.exit, this::showQuitConfirm).update(s -> {
                s.setText(control.saves.getCurrent() != null && control.saves.getCurrent().isAutosave() ? "@save.quit"
                        : "@quit");
                s.getLabelCell().growX().wrap();
            });
        }
    }

    void showQuitConfirm() {
        Runnable quit = () -> {
            runExitSave();
            hide();
        };

        if (confirmExit) {
            ui.showConfirm("@confirm", "@quit.confirm", quit);
        } else {
            quit.run();
        }
    }

    public boolean checkPlaytest() {
        if (state.playtestingMap != null) {
            // no exit save here
            var testing = state.playtestingMap;
            logic.reset();
            ui.editor.resumeAfterPlaytest(testing);
            return true;
        }
        return false;
    }

    public void runExitSave() {
        runExitSave(true);
    }

    public void runExitSave(boolean save) {
        boolean wasClient = net.client();
        if (net.client())
            netClient.disconnectQuietly();

        if (state.isEditor() && !wasClient) {
            ui.editor.resumeEditing();
            return;
        } else if (checkPlaytest()) {
            return;
        }

        // 在重置世界前先保存卫星地图，否则 logic.reset() 会清空实体，导致保存空地图
        if (SatelliteManager.currentSatelliteId >= 0) {
            SatelliteManager.setExitingSatellite(true);
            crystal.aviation.Satellite current = SatelliteManager.get(SatelliteManager.currentSatelliteId);
            if (current != null) {
                current.mapData.captureFromWorld();
                SatelliteManager.save();
                Log.info("[CrystalAviation] Captured satellite @ before exit to menu.", current.id);
            }
            // 立即重置运行时状态，防止随后的 ResetEvent/StateChangeEvent 再次捕获已被清空的世界
            SatelliteManager.resetRuntimeState(false);
        }

        if (control.saves.getCurrent() == null || !control.saves.getCurrent().isAutosave() || wasClient
                || state.gameOver || disableSave) {
            logic.reset();
            SatelliteManager.setExitingSatellite(false);
            return;
        }

        if (save) {
            ui.loadAnd("@saving", () -> {
                try {
                    control.saves.getCurrent().save();
                } catch (Throwable e) {
                    Log.err(e);
                    ui.showException("[accent]" + Core.bundle.get("savefail"), e);
                }
                logic.reset();
                SatelliteManager.setExitingSatellite(false);
            });
        }
    }
}
