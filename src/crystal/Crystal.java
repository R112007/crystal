package crystal;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import crystal.content.CBlocks;
import crystal.content.CEnvironment;
import crystal.content.CIcons;
import crystal.content.CItems;
import crystal.content.CLoadouts;
import crystal.content.CPlanets;
import crystal.content.CUnitCommands;
import crystal.content.CUnits;
import crystal.content.CrystalTechTree;
import crystal.content.GongFas;
import crystal.content.LxMaps;
import crystal.content.MuchLoadUnit;
import crystal.content.SpecialUnits;
import crystal.content.hzr.HZRBlocks;
import crystal.core.Affection;
import crystal.core.CSettings;
import crystal.core.PlayerXiuWeiSystem;
import crystal.core.Storys;
import crystal.core.UnitInfoSystem;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.MultiStageMechUnit;
import crystal.entities.units.SummonUnit;
import crystal.game.MultiSectorWaveTrigger;
import crystal.game.CEventType.MapChangeEvent;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.gen.EntityRegistry;
import crystal.mod.ClassMapLoader;
import crystal.net.CCall;
import crystal.ui.CStyles;
import crystal.ui.Hints;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.MobileLaunchLoadoutDialog;
import crystal.util.DLog;
import crystal.world.blocks.payloads.UnitLaunchPayload;
import crystal.world.blocks.stroage.MoveBlockSystem;
import crystal.world.blocks.stroage.MoveCoreSystem;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.EntityMapping;
import mindustry.maps.Map;
import mindustry.mod.Mod;
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
    CCall.load();
    Affection.affection.load();
    GongFas.load();
    registerShenTongs();
    EntityRegistry.register();
    CStyles.load();
    CItems.load();
    CEnvironment.load();
    CUnitCommands.load();
    CUnits.load();
    SpecialUnits.load();
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
    MoveBlockSystem.init();
    MoveCoreSystem.init();
    Log.info("Have Loaded All Contents!");
  }

  public void constructor() {
    if (CVars.debug)
      loadlog();
    showwelcome();
    if (CSettings.instance.hasPlayerName()) {
      CVars.playerName = CSettings.instance.getPlayerName();
    }
    Hints.load();
    checkAndShowNameInputDialog();
    Storys.inst.init();
  }

  public void jsonRegister() {
    mindustry.io.JsonIO.json.setSerializer(
        crystal.ui.gal.DialogueHistory.class,
        new arc.util.serialization.Json.Serializer<crystal.ui.gal.DialogueHistory>() {
          @Override
          public void write(arc.util.serialization.Json json, crystal.ui.gal.DialogueHistory object, Class knownType) {
            object.write(json);
          }

          @Override
          public crystal.ui.gal.DialogueHistory read(arc.util.serialization.Json json,
              arc.util.serialization.JsonValue jsonData, Class type) {
            crystal.ui.gal.DialogueHistory h = new crystal.ui.gal.DialogueHistory();
            h.read(json, jsonData);
            return h;
          }
        });
  }

  public void registerShenTongs() {
    ShenTong.shengTongMap.clear();
    ShenTong.shengTongMap.put(0, new FaTianXiangDi(0, 0, 0, 0));
  }

  public void loadlog() {
    DLog.info("density = " + Core.graphics.getDensity());
    DLog.info("scl = " + Scl.scl());
    DLog.info("moblie : " + Vars.mobile);
  }

  @Override
  public void init() {
    ClassMapLoader.load();
    CVars.cui.init();
    UnitInfoSystem.init();
    UnitInfoSystem.loadUnitInfo();
    jsonRegister();
    MultiSectorWaveTrigger.get().init();
    UnitInfoSystem.checkAllSector();
    UnitInfoSystem.saveUnitInfo();
    PlayerXiuWeiSystem.init();
    Events.on(ClientLoadEvent.class, e -> {
      constructor();
    });
    MobileLaunchLoadoutDialog.init();
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
    updateSector();
    updateMap();
    FaTianXiangDi.faShens.update();
  }

  public void updateSector() {
    if (Vars.state.getSector() != null && Vars.state.getSector() != hereSector) {
      Events.fire(new SectorChangeEvent(hereSector, Vars.state.getSector()));
      hereSector = Vars.state.getSector();
    }
  }

  public void checkAndShowNameInputDialog() {
    CSettings settings = CSettings.instance;
    // 已有名字存档，直接同步到角色，不弹框
    if (settings.hasPlayerName()) {
      CVars.playerName = settings.getPlayerName();
      return;
    }

    // 弹出原生输入对话框，强制玩家输入名字
    Vars.ui.showTextInput(
        Core.bundle.get("inputplayername"),
        Core.bundle.get("inputplayername.content"),
        16, // 最大输入长度
        "", // 默认空值
        false, // 不允许纯空格
        // 确认回调：校验+保存
        inputName -> {
          String finalName = inputName == null ? "" : inputName.trim();
          // Java8兼容校验
          if (finalName.isEmpty()) {
            Vars.ui.showErrorMessage(Core.bundle.get("changeplayername.nonull"));
            // 校验失败，重新弹出对话框
            Core.app.post(this::checkAndShowNameInputDialog);
            return;
          }
          // 保存名字，自动同步到剧情角色
          settings.setPlayerName(finalName);
          CVars.playerName = finalName;
          Storys.inst.player.name = finalName;
          Storys.inst.initModels();
          // Vars.ui.showInfo(Core.bundle.format("welcome.playername", finalName));
          Core.app.exit();
        },
        // 取消回调：强制要求输入，不允许取消
        () -> {
          Vars.ui.showErrorMessage(Core.bundle.get("welcome.nonull"));
          Core.app.post(this::checkAndShowNameInputDialog);
        });
  }

  public void updateMap() {
    if (Vars.state.map != null && Vars.state.map != hereMap) {
      Events.fire(new MapChangeEvent(Vars.state.map));
      hereMap = Vars.state.map;
    }
  }

  public void replaceUI() {
    Events.on(ClientLoadEvent.class, (e) -> {
      // if (false)
      Events.run(Trigger.update, () -> {
        if (CVars.cui.cplanet.lockPlanetReplace)
          return;
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
      });
      // Vars.ui.planet = CVars.cui.cplanet;
      Events.run(Trigger.update, () -> {
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
    welcomeDialog = new BaseDialog(Core.bundle.get("crystal.title"));
    welcomeDialog.cont.image(Core.atlas.find("crystal-crystal-core")).size(310f).pad(5.0f).row();
    welcomeDialog.cont.pane(t -> {
      t.add(Core.bundle.get("crystal.welcome")).row();
    }).row();
    welcomeDialog.cont.pane(t -> {
      t.add("如果很多地方都变成了???xxx???(xxx是一些字母)的形式，\n去游戏设置 -> 语言 -> 切换成任意其他语言（如繁体中文）-> 重启 -> 切回简体中文即可。").row();
    }).row();
    welcomeDialog.addCloseButton();
    welcomeDialog.cont.pane((c) -> {
      c.button(Core.bundle.get("crystal.qq"), () -> {
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
