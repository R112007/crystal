package crystal;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import crystal.content.CBlocks;
import crystal.content.CIcons;
import crystal.content.CItems;
import crystal.content.CPlanets;
import crystal.content.CTechTree;
import crystal.content.CUnits;
import crystal.core.UnitInfoSystem;
import crystal.game.UnitInfo;
import crystal.ui.TimeControl;
import crystal.world.blocks.environment.DamageFloor;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.mod.Mod;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;

public class Crystal extends Mod {
  public static BaseDialog welcomeDialog;
  public static final String scqq = "http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Rrju8RLWbsJstJ3rcJxWyrtop4u7uRb9&authKey=gdngZkPeYxZPhYTmjQUTjPos%2FJKckD02YSFnYLmdVojPZIzZw1T%2FbtubSoyuw2LA&noverify=0&group_code=756820891";
  public static int timer = 0;
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
    CItems.load();
    CUnits.load();
    CBlocks.load();
    if (CVars.debug)
      Test.load();
    CPlanets.load();
    CTechTree.load();
    // TimeControl.load();
    Log.info("Have Loaded All Contents!");
  }

  public void constructor() {
    /*
     * UnitInfo.loadArray();
     * try {
     * for (var u : UnitInfo.all) {
     * if (u != null)
     * Log.info("id+" + u.id + "  sector " + u.getBoundSector());
     * }
     * } catch (Exception e) {
     * Log.err("loadarray出错", e);
     * }
     */
    // UnitInfoFileStorage.loadAll();
    if (CVars.debug)
      loadlog();
    UnitInfoSystem.loadUnitInfo();
    showwelcome();
    if (CVars.debug)
      Log.info("运行checkallsector");
    UnitInfoSystem.checkAllSector();
    UnitInfoSystem.saveUnitInfo();
    if (CVars.debug)
      Log.info("运行checkallsector结束");
    if (CVars.debug)
      test();
  }

  public void loadlog() {
    for (var preset : UnitInfo.all) {
      if (preset != null)
        sectors.add(preset.getBoundSector());
    }
    Log.info("");
    for (var u : sectors) {
      Log.info("id+" + u.id + "  sector " + u + " " + u.name());
    }
  }

  @Override
  public void init() {
    CVars.cui.init();
    CIcons.load();
    replaceUI();
    Events.run(Trigger.update, () -> {
      update();
    });
    UnitInfoSystem.init();
  }

  public void update() {
    timer++;
    UnitInfoSystem.update();
    DamageFloor.update();
  }

  public void replaceUI() {
    Events.on(ClientLoadEvent.class, (e) -> {
      Events.run(Trigger.update, () -> {
        if (Vars.ui.planet.isShown()) {
          Vars.ui.planet.hide();
          if (!CVars.cui.cplanet.isShown()) {
            CVars.cui.cplanet.show();
          }
        }
        if (Vars.ui.research.isShown()) {
          Vars.ui.research.hide();
          if (!CVars.cui.cresearch.isShown()) {
            CVars.cui.cresearch.show();
          }
        }
      });
    });
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

  public void test() {
    Seq<Vec2> t = new Seq<>();
    t.addAll(new Vec2(1, 1), new Vec2(1, 2));
    boolean b1 = Intersector.isInPolygon(t, new Vec2(1, 1));
    Log.info("boolean1" + b1);
    boolean b2 = Intersector.isInPolygon(t, new Vec2(-1, -1));
    Log.info("boolean2" + b2);
  }

  public void closeMod(String name) {
    Events.on(ClientLoadEvent.class, (e) -> {
      if (Vars.mods.getMod(name) != null)
        Vars.mods.removeMod(Vars.mods.getMod(name));
    });
  }
}
