package crystal;

import crystal.core.CultivationState;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import crystal.audio.CMusics;
import crystal.aviation.CrystalAviationSystemCore;
import crystal.aviation.SatelliteManager;
import crystal.content.CBlocks;
import crystal.content.CEnvironment;
import crystal.content.CIcons;
import crystal.content.CItems;
import crystal.content.CLoadouts;
import crystal.content.CPlanets;
import crystal.content.CUnitCommands;
import crystal.content.CUnits;
import crystal.content.CWeather;
import crystal.content.CrystalTechTree;
import crystal.content.FaBaos;
import crystal.content.GongFas;
import crystal.content.LxMaps;
import crystal.content.MuchLoadUnit;
import crystal.content.SpecialUnits;
import crystal.content.hzr.HZRBlocks;
import crystal.core.Affection;
import crystal.core.CSettings;
import crystal.core.FaBaoSystem;
import crystal.core.PlayerXiuWeiSystem;
import crystal.core.Storys;
import crystal.core.UnitInfoSystem;
import crystal.editor.MagicWaves;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.MultiStageMechUnit;
import crystal.entities.units.SummonUnit;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.game.MultiSectorWaveTrigger;
import crystal.game.CEventType.MapChangeEvent;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.gen.EntityRegistry;
import crystal.graphics.BlackHoleRenderer;
import crystal.mod.ClassMapLoader;
import crystal.net.CCall;
import crystal.ui.CStyles;
import crystal.ui.FaBaoHUD;
import crystal.ui.Hints;
import crystal.ui.dialogs.CPlanetDialog;
import crystal.ui.dialogs.MobileLaunchLoadoutDialog;
import crystal.util.DLog;
import crystal.util.PlotBundle;
import crystal.world.blocks.payloads.UnitLaunchPayload;
import crystal.world.blocks.stroage.MoveBlockSystem;
import crystal.world.blocks.stroage.MoveCoreSystem;
import crystal.world.time.TimeRewind;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.core.Version;
import mindustry.core.GameState.State;
import mindustry.editor.MapEditorDialog;
import mindustry.editor.MapInfoDialog;
import mindustry.entities.Units;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.EntityMapping;
import mindustry.maps.Map;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.PlanetDialog;

import static mindustry.Vars.*;

import java.lang.reflect.Field;

public class Crystal extends Mod {
  public static BaseDialog welcomeDialog;
  public static final String scqq = "http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Rrju8RLWbsJstJ3rcJxWyrtop4u7uRb9&authKey=gdngZkPeYxZPhYTmjQUTjPos%2FJKckD02YSFnYLmdVojPZIzZw1T%2FbtubSoyuw2LA&noverify=0&group_code=756820891";
  public static int timer = 0;
  public static Sector hereSector = null;
  public static Map hereMap = null;
  private TimeRewind timeRewind;
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
    CMusics.load();
    MagicWaves.init();
    CCall.load();
    Affection.affection.load();
    GongFas.load();
    FaBaos.load();
    EntityRegistry.register();
    CStyles.load();
    CItems.load();
    CEnvironment.load();
    CUnitCommands.load();
    CUnits.load();
    SpecialUnits.load();
    CBlocks.load();
    HZRBlocks.load();
    CrystalAviationSystemCore.loadAllContent();
    if (CVars.debug)
      Test.load();
    Test2.load();
    CLoadouts.load();
    CWeather.load();
    CPlanets.load();
    LxMaps.load();
    CrystalTechTree.load();
    try {
      MuchLoadUnit.load();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    MoveBlockSystem.init();
    MoveCoreSystem.init();
    Log.info("Have Loaded All Contents!");
  }

  public void constructor() {
    Vars.renderer.minZoom = 0.5f;
    Vars.renderer.maxZoom = 25f;
    if (CVars.debug)
      loadlog();
    showwelcome();
    if (CSettings.instance.hasPlayerName()) {
      CVars.playerName = CSettings.instance.getPlayerName();
    }
    // 【修复】plot 读取 bug：modDirectory 是 Vars.modDirectory（游戏 mods 文件夹），
    // child(modName) 指向的是 mods/crystal 这个条目——zip 安装时根本不存在（实际是 crystal.zip），
    // 文件夹安装时它是目录而非文件，PlotBundle.load 对一个目录调 reader() 直接失败，
    // 两种情况都会得到一个空 bundle，所有 key 返回 ???xxx???。
    // 正确做法：从 Vars.mods 取 LoadedMod.root（zip/文件夹安装都有效），交给 loadFromMod 找
    // plot/plot.properties。
    LoadedMod cmod = Vars.mods.getMod(CVars.modName);
    if (cmod == null) {
      // 按主类兜底，避免 mod.json 的 name 与 CVars.modName 不一致导致找不到
      cmod = Vars.mods.getMod(Crystal.class);
    }
    if (cmod != null) {
      Log.info("[Crystal] 找到 mod: name=@, root=@", cmod.name, cmod.root.absolutePath());
      CVars.plot = PlotBundle.loadFromMod(cmod.root);
    } else {
      Log.err("[Crystal] Vars.mods 中找不到 crystal mod，plot 文本将从游戏语言包兜底");
      CVars.plot = PlotBundle.loadFromMod(modDirectory.child(CVars.modName));
    }
    Log.info("[Crystal] plot bundle 加载完成: " + CVars.plot);
    Storys.inst.init();
    Hints.load();
    checkAndShowNameInputDialog();
    registerShenTongs();
    if (Version.build > CVars.maxVersion) {
      ui.showErrorMessage("你的游戏版本太高，要" + CVars.maxVersion + "才行");
    }
    checkGongFa();
  }

  // 【已删除】events()/increase()/lastUnit 整条单位属性加成管线：
  // 1) events() 从未被调用，是死代码；
  // 2) 首次 UnitChangeEvent 时 lastUnit 必为空，p.setType() 直接 NPE；
  // 3) w.weapon.bullet.damage *= f 篡改的是内容注册表里的共享 BulletType 单例，
  // 每次换单位复利叠加且影响敌方同类型单位，setType 不会重置它。

  public void checkGongFa() {
    if ((LxMaps.jianglindian.sector.info.wasCaptured
        || CultivationState.playerMagicPower >= JingJie.kaiqiao.amount - 0.1f)
        && !GongFas.taiXuanTianGong1.unlocked()) {
      GongFas.taiXuanTianGong1.unlock();
    }
  }

  public void registerShenTongs() {
    ShenTong.shengTongMap.clear();
    ShenTong.shengTongMap.put(0, new FaTianXiangDi(0, 0, 0, 0));
  }

  public void loadlog() {
  }

  @Override
  public void init() {
    ClassMapLoader.load();
    CVars.cui.init();
    // 尽早把原版 PlacementFragment 替换成带空指针防护的 SafePlacementFragment，
    // 并把旧实例隔离到 dummy parent，防止其残留的 WorldLoadEvent/UnlockEvent 监听崩溃
    SatelliteManager.ensureSafePlacementFragment();
    UnitInfoSystem.init();
    UnitInfoSystem.loadUnitInfo();
    MultiSectorWaveTrigger.get().init();
    UnitInfoSystem.checkAllSector();
    UnitInfoSystem.saveUnitInfo();
    BlackHoleRenderer.init();
    PlayerXiuWeiSystem.init();
    FaBaoSystem.init();
    FaBaoHUD.init();
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
    // MuchLoadUnit.addTab();
    timeRewind = new TimeRewind();
    // 在逻辑更新前驱动：正向时捕获快照，回溯时恢复旧快照
    Events.run(Trigger.beforeGameUpdate, () -> {
      if (timeRewind != null)
        timeRewind.update();
    });
  }

  public void update() {
    timer += Time.delta;
    UnitInfoSystem.update();
    // checkGongFa 原来只在 ClientLoadEvent 调一次，局内灵力达标永远不会触发解锁；
    // 改为每 120 tick 轮询一次（与 UnitInfoSystem 的节流约定一致）
    if (timer % 120 == 1) {
      checkGongFa();
    }
    updateSector();
    updateMap();
    FaTianXiangDi.faShens.update();
    if (timer % 60 == 0 && SatelliteManager.currentSatelliteId > -1) {
    }
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

  /**
   * 替换/包装 UI 组件。
   * 
   * 波次编辑器部分：
   * 不替换 WaveInfoDialog 实例（它在 MapInfoDialog 构造时已创建），
   * 而是获取已有实例并创建 ShenTongWaveDialog 包装器。
   * 包装器在 dialog.shown 回调中重建 UI，附加神通编辑功能。
   * 
   * 工作原理：
   * 1. WaveInfoDialog 构造器注册了 shown → setup() 回调
   * 2. 包装器额外注册 shown → rebuild() 回调（在 setup() 之后执行）
   * 3. rebuild() 将所有 SpawnGroup 转为 ShenTongSpawnGroup，重建 UI
   * 4. WaveInfoDialog 的 hidden → state.rules.spawns = groups 仍然有效
   * 因为通过反射修改的是同一个 groups 字段
   */
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
      replacePause();
    });
  }

  public void replacePause() {
    Events.run(Trigger.update, () -> {
      if (Vars.ui.paused.isShown()) {
        Vars.ui.paused.hide();
        if (!CVars.cui.cpaused.isShown()) {
          CVars.cui.cpaused.show();
        }
      }
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
