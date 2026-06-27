package crystal.core;

import arc.Core;
import crystal.content.WorldStuffs;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.CResearchDialog;
import crystal.ui.dialogs.GongFaDialog;
import crystal.ui.dialogs.WorldStuffDialog;
import crystal.ui.gal.GalgameDialogueManager;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

import static crystal.CVars.debug;

public class UI {
  public CResearchDialog cresearch;
  public CPlanetDialog cplanet;
  public WorldStuffDialog stuff;
  public GongFaDialog gongFa;
  float height = 100;

  public void init() {
    WorldStuffs.load();
    cresearch = new CResearchDialog();
    cplanet = new CPlanetDialog();
    stuff = new WorldStuffDialog();
    gongFa = new GongFaDialog();
    Vars.ui.menufrag.addButton(Core.bundle.get("showgongfas"), Icon.bookOpen, () -> gongFa.show());
    Vars.ui.paused.shown(() -> {
      Vars.ui.paused.cont.row();
      Vars.ui.paused.cont.button(
          Core.bundle.get("showgongfas"),
          Icon.bookOpen,
          gongFa::show).size(Vars.mobile ? 130f : 220f, Vars.mobile ? 130f : 55f).pad(5f).colspan(2);
      Vars.ui.paused.cont.button(
          "历史记录",
          Icon.book,
          GalgameDialogueManager.instance.historyUI::show).size(Vars.mobile ? 130f : 220f, Vars.mobile ? 130f : 55f)
          .pad(5f).colspan(20);
    });

    if (debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("调试面板", Styles.flatt, () -> {
            GalgameDialogueManager.instance.debugDialog.show();
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(100, height);
        });
      });

    if (debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("对话", () -> {
            GalgameDialogueManager.instance.getModule("main").resetProgress();
            GalgameDialogueManager.instance.playModule("main");
          }).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          table.translation.set(0, height);
        });
      });
  }
}
