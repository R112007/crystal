package crystal;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import crystal.content.CBlocks;
import crystal.content.CEnvironment;
import crystal.content.CIcons;
import crystal.content.CItems;
import crystal.content.CLoadouts;
import crystal.content.CPlanets;
import crystal.content.CUnits;
import crystal.content.CrystalTechTree;
import crystal.content.GongFas;
import crystal.content.LxMaps;
import crystal.content.MuchLoadUnit;
import crystal.content.hzr.HZRBlocks;
import crystal.core.CSettings;
import crystal.core.PlayerXiuWeiSystem;
import crystal.core.UnitInfoSystem;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.MultiStageMechUnit;
import crystal.entities.units.SummonUnit;
import crystal.game.UnitInfo;
import crystal.game.CEventType.MapChangeEvent;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.gen.EntityRegistry;
import crystal.ui.CStyles;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.util.DLog;
import crystal.world.blocks.environment.DamageFloor;
import crystal.world.blocks.payloads.UnitLaunchPayload;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.SectorPresets;
import mindustry.game.Objectives;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.EntityMapping;
import mindustry.maps.Map;
import mindustry.mod.Mod;
import mindustry.type.ItemStack;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.PlanetDialog;

import static mindustry.Vars.*;

public class Crystal extends Mod {
  public static BaseDialog welcomeDialog;
  public static final String scqq = "http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Rrju8RLWbsJstJ3rcJxWyrtop4u7uRb9&authKey=gdngZkPeYxZPhYTmjQUTjPos%2FJKckD02YSFnYLmdVojPZIzZw1T%2FbtubSoyuw2LA&noverify=0&group_code=756820891";
  public static int timer = 0;
  public static Sector hereSector = null;
  public static Map hereMap = null;
  Seq<Sector> sectors = new Seq<>();
  static {
    registerEntity();
  }

  public Crystal() {
    Log.info("Start to Loaded Crystal Mod Constructor.");
  }

  @Override
  public void loadContent() {
    Log.info("Start to Load Contents");
    registerShenTongs();
    EntityRegistry.register();
    CStyles.load();
    CItems.load();
    CEnvironment.load();
    CUnits.load();
    CBlocks.load();
    HZRBlocks.load();
    if (CVars.debug)
      Test.load();
    CLoadouts.load();
    CPlanets.load();
    try {
      MuchLoadUnit.load();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    LxMaps.load();
    CrystalTechTree.load();
    Log.info("Have Loaded All Contents!");
  }

  public void constructor() {
    if (CVars.debug)
      loadlog();
    showwelcome();
  }

  public void registerShenTongs() {
    ShenTong.shengTongMap.clear();
    ShenTong.shengTongMap.put(0, new FaTianXiangDi(0, 0, 0, 0));
  }

  public void loadlog() {
  }

  @Override
  public void init() {
    UnitInfoSystem.init();
    UnitInfoSystem.loadUnitInfo();
    if (CVars.debug)
      Log.info("运行checkallsector");
    UnitInfoSystem.checkAllSector();
    UnitInfoSystem.saveUnitInfo();
    if (CVars.debug)
      Log.info("运行checkallsector结束");
    PlayerXiuWeiSystem.init();
    Events.on(ClientLoadEvent.class, e -> {
      constructor();
    });
    CVars.cui.init();
    CIcons.load();
    CSettings.load();
    replaceUI();
    Events.run(Trigger.update, () -> {
      update();
    });
    SummonUnit.init();
  }

  public void update() {
    timer += Time.delta;
    UnitInfoSystem.update();
    DamageFloor.update();
    updateSector();
    updateMap();
    FaTianXiangDi.faShens.update();
  }

  public void updateSector() {
    if (Vars.state.getSector() != null && Vars.state.getSector() != hereSector) {
      Events.fire(new SectorChangeEvent(Vars.state.getSector()));
      hereSector = Vars.state.getSector();
    }
  }

  public void updateMap() {
    if (Vars.state.map != null && Vars.state.map != hereMap) {
      Events.fire(new MapChangeEvent(Vars.state.map));
      hereMap = Vars.state.map;
    }
  }

  public void replaceUI() {
    Events.on(ClientLoadEvent.class, (e) -> {
      Events.run(Trigger.update, () -> {
        if (!CVars.cui.cplanet.isShown() && Vars.ui.planet.isShown())
          Vars.ui.planet.hide();
        if (Vars.ui.planet.isShown() && Vars.ui.planet.mode == PlanetDialog.Mode.look) {
          DLog.info("planet为look模式");
          Vars.ui.planet.hide();
          if (!CVars.cui.cplanet.isShown()) {
            CVars.cui.cplanet.mode = CPlanetDialog.Mode.look;
            CVars.cui.cplanet.show();
          }
        } else if (Vars.ui.planet.isShown() && Vars.ui.planet.mode == PlanetDialog.Mode.select) {
          DLog.info("planet为select模式");
          Vars.ui.planet.hide();
          if (!CVars.cui.cplanet.isShown()) {
            CVars.cui.cplanet.showSelect(state.rules.sector, other -> {
              if (state.isCampaign() && other.planet == state.rules.sector.planet) {
                var prev = state.rules.sector.info.destination;
                state.rules.sector.info.destination = other;
                if (prev != null) {
                  prev.info.refreshImportRates(state.getPlanet());
                }
              }
            });
          }
        } else if (Vars.ui.planet.isShown() && Vars.ui.planet.mode == PlanetDialog.Mode.planetLaunch) {
          DLog.info("planet为planetlaunch模式");
          Vars.ui.planet.hide();
          if (!CVars.cui.cplanet.isShown()) {
            CVars.cui.cplanet.mode = CPlanetDialog.Mode.planetLaunch;
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

  public static void registerEntity() {
    EntityMapping.idMap[51] = MultiStageMechUnit::create;
    EntityMapping.nameMap.put("crystal-multistagemechunit", EntityMapping.idMap[51]);
    EntityMapping.idMap[53] = UnitLaunchPayload::create;
    EntityMapping.nameMap.put("crystal-unitLaunchPayload", EntityMapping.idMap[53]);
  }

  public void closeMod(String name) {
    Events.on(ClientLoadEvent.class, (e) -> {
      if (Vars.mods.getMod(name) != null)
        Vars.mods.removeMod(Vars.mods.getMod(name));
    });
  }
}
