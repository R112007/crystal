package crystal.core;

import arc.Core;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.struct.ObjectMap;
import crystal.CVars;
import crystal.content.WorldStuffs;
import crystal.game.UnitInfo;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.CResearchDialog;
import crystal.ui.dialogs.WorldStuffDialog;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.meta.StatValues;

public class UI {
  public CResearchDialog cresearch;
  public CPlanetDialog cplanet;
  public WorldStuffDialog stuff;

  public void init() {
    WorldStuffs.load();
    cresearch = new CResearchDialog();
    cplanet = new CPlanetDialog();
    stuff = new WorldStuffDialog();
    // if (CVars.debug)
    // addButton();
    // addInfoButton();
  }

  public void addInfoButton() {
    Vars.ui.paused.shown(() -> {
      Vars.ui.paused.cont.row();
      Vars.ui.paused.cont.buttonRow("显示单位", Icon.planet, () -> showUnitInfoStats());
    });
  }

  void showUnitInfoStats() {
    BaseDialog dialog = new BaseDialog(/* sector.name() + */Core.bundle.get("unitinfo.title"));
    // var info = UnitInfo.get(sector);
    dialog.cont.pane(c -> {
      for (UnitInfo info : UnitInfo.all) {
        if (info != null) {
          if (info.getBoundSector().planet == Vars.state.getPlanet()) {
            c.add(info.getBoundSector().name());
            c.row();
            c.defaults().padBottom(5);
            c.add(Core.bundle.get("unitinfo.possessed")).left().row();
            c.table(t -> {
              int i;
              if (!info.possessed.isEmpty()) {
                i = 0;
                info.possessed.sort();
                for (var stack : info.possessed) {
                  if (stack.amount != 0) {
                    t.add(StatValues.stack(stack.unit, stack.amount));
                    i++;
                    if (i % 4 == 0) {
                      t.row();
                    }
                  }
                }
              }
            });
            c.row();
          }
        }
      }
    });
    dialog.addCloseButton();
    dialog.show();
  }
}
