package crystal;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Log;
import crystal.game.UnitInfo;
import crystal.game.UnitInfoFileStorage;
import crystal.ui.TimeControl;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.mod.Mod;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import mindustry.ui.dialogs.BaseDialog;

public class Crystal extends Mod {
  public static BaseDialog welcomeDialog;
  public static final String scqq = "http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Rrju8RLWbsJstJ3rcJxWyrtop4u7uRb9&authKey=gdngZkPeYxZPhYTmjQUTjPos%2FJKckD02YSFnYLmdVojPZIzZw1T%2FbtubSoyuw2LA&noverify=0&group_code=756820891";
  static int timer = 0;
  Seq<SectorPreset> sectorpresets = Vars.content.getBy(ContentType.sector);
  Seq<Sector> sectors = new Seq<>();

  public Crystal() {
    Log.info("Start to Loaded Crystal Mod Constructor.");
    Events.on(ClientLoadEvent.class, e -> {
      constructor();
    });
  }

  @Override
  public void loadContent() {
    Log.info("Start to Load Contents");
    TimeControl.load();
    Log.info("hava loaded all");
  }

  public void constructor() {
    UnitInfoFileStorage.loadAll();
    loadlog();
    showwelcome();
  }

  public void loadlog() {
    for (var preset : UnitInfo.all) {
      if (preset != null)
        sectors.add(preset.getBoundSector());
    }
    Log.info("开始遍历sectors");
    for (var u : UnitInfo.all) {
      if (u != null)
        Log.info("id+" + u.id + "  sector " + u.getBoundSector());
    }
    Log.info("");
    for (var u : sectors) {
      Log.info("id+" + u.id + "  sector " + u + " " + u.name());
    }
  }

  @Override
  public void init() {
    CVars.cui.init();
    Events.run(Trigger.update, () -> {
      update();
    });
  }

  public void update() {
  }

  public void showwelcome() {
    welcomeDialog = new BaseDialog(Core.bundle.get("sc.welcome"));
    welcomeDialog.cont.image(Core.atlas.find("sc-crystal-core")).size(310f).pad(5.0f).row();
    welcomeDialog.cont.pane(t -> {
      t.add(Core.bundle.get("sc.text1")).row();
    }).row();
    welcomeDialog.addCloseButton();
    welcomeDialog.cont.pane((c) -> {
      c.button(Core.bundle.get("sc.qq"), () -> {
        if (!Core.app.openURI(scqq)) {
          Vars.ui.showErrorMessage("@linkfail");
          Core.app.setClipboardText(scqq);
        }
      }).color(Color.valueOf("#556352")).size(120.0f, 50.0f);
    }).pad(3f).row();
    welcomeDialog.show();
  }

  public void closeMod(String name) {
    Events.on(ClientLoadEvent.class, (e) -> {
      if (Vars.mods.getMod(name) != null)
        Vars.mods.removeMod(Vars.mods.getMod(name));
    });
  }
}
