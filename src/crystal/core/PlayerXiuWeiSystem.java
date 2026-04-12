package crystal.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.GongFaBuQuanEvent;
import crystal.game.CEventType.JingJieChange;
import crystal.game.CEventType.MagicPowerChange;
import crystal.game.CEventType.XiuWeiChange;
import crystal.type.GongFa;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.gen.Icon;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static crystal.content.GongFas.*;
import static mindustry.Vars.*;

public class PlayerXiuWeiSystem {
  public static float height = 0;
  private static final ObjectSet<GongFa> NEW_ROAD_GONGFAS = ObjectSet.with(guHuang, guZun);
  private static final ObjectSet<GongFa> OLD_ROAD_MIRROR_GONGFAS = ObjectSet.with(xinHuang, xinZun);
  private static final long TOAST_COOLDOWN_MS = 3000;
  private static long lastToastTimestamp = 0;

  private static final long DUJIE_CONFIRM_COOLDOWN_MS = 60 * 1000; // 1分钟冷却
  private static long lastDuJieConfirmTimestamp = 0;

  private static final String SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie_ordinal";
  private static final String SAVE_KEY_AVAILABLE_JINGJIE = "crystal.availableJingJie_ordinal";
  private static final String OLD_SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie";
  private static final String SAVE_KEY_COMPLETED_DUJIE = "crystal.completedDuJieSectors";
  private static final String SAVE_KEY_PENDING_DUJIE = "crystal.pendingDuJieJingJieOrdinal";

  private static void duJieFail() {
    // 仅重置核心惩罚项
    CVars.playerMagicPower = 0f; // 灵力清空
    CVars.playerJingJie = JingJie.fan; // 当前境界跌回凡境
    CVars.playerXiuWei = XiuWei.yong; // 修为等级同步重置
    // 当前可用境界重置为仅凡境（不影响历史到达记录）
    CVars.currentAvailableJingJie.clear();
    CVars.currentAvailableJingJie.add(JingJie.fan);
    CVars.completedDuJiePresets.clear();
    CVars.pendingDuJieJingJie = null;
    for (var s : JingJie.shenNewRoad) {
      if (s.duJiePreset != null)
        if (s.duJiePreset.sector.save != null && s.duJiePreset.sector.hasBase() && s.duJiePreset.sector.isCaptured()) {
          abandonSectorConfirm(s.duJiePreset.sector, null);
        }
    }
    CVars.isInDuJie = false;
    savePower();
    // 存档同步与状态更新
    saveCurrentAvailableJingJie();
    // 触发事件同步UI与全局状态
    Events.fire(new JingJieChange(0f));
    Events.fire(new XiuWeiChange(CVars.playerJingJie));

    DLog.info("渡劫失败执行：已重置当前境界与灵力，功法、历史境界记录全部保留");
  }

  public static void abandonSectorConfirm(Sector sector, Runnable listener) {
    if (listener != null)
      listener.run();

    if (sector.isBeingPlayed()) {
      Time.runTask(7f, () -> {
        // force game over in a more dramatic fashion
        for (var core : player.team().cores().copy()) {
          core.kill();
        }
      });
    } else {
      sector.info.items.clear();
      sector.info.hasCore = false;
      sector.info.production.clear();
      sector.saveInfo();
    }
    CVars.cui.cplanet.updateSelected();
    CVars.cui.cplanet.rebuildList();
  }

  public static void addbutton(float amount, float h) {
    if (CVars.debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("加" + Core.bundle.get("stat.xiuwei") + amount, () -> Events.fire(new MagicPowerChange(amount)))
              .size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          if (Core.settings.getBool("showXiuWei")) {
            height = h;
          } else
            height = 10000;
          table.translation.set(0, height);
        });
      });
  }

  public static void init() {
    Events.on(ClientLoadEvent.class, e -> {
      CVars.chooseNewRoad = Core.settings.getBool("crystal.chooseNewRoad", false);
      loadReachedJingJie();
      loadDuJieState();
      CVars.playerMagicPower = Core.settings.getFloat("crystal.magicpower", 0f);
      CVars.playerMagicPower = Math.max(0, CVars.playerMagicPower);
      DLog.info("playerMagicPower" + CVars.playerMagicPower);
      if (CVars.reachedJingJie.isEmpty()) {
        CVars.reachedJingJie.add(JingJie.fan);
        saveReachedJingJie();
      }
      if (CVars.currentAvailableJingJie.isEmpty()) {
        CVars.currentAvailableJingJie.add(JingJie.fan);
        saveCurrentAvailableJingJie();
      }
      if (CVars.playerJingJie == null)
        CVars.playerJingJie = JingJie.fan;
      if (CVars.playerXiuWei == null)
        CVars.playerXiuWei = XiuWei.yong;
      Events.fire(new JingJieChange(CVars.playerMagicPower));
      DLog.info("新路: " + CVars.chooseNewRoad);
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button(Core.bundle.get("stat.xiuwei"), () -> showMagic()).size(100, 80);
        }).size(100, 100);
        table.center().right().update(() -> {
          if (Core.settings.getBool("showXiuWei")) {
            height = 0;
            Vars.control.input.uiGroup.getChildren().each(element -> {
              height += element.visible ? element.getPrefHeight() : 0;
            });
          } else
            height = 10000;
          table.translation.set(0, height);
        });
      });
      addbutton(-100, -60);
      addbutton(-10, 0);
      addbutton(-1, 60);
      addbutton(1, 120);
      addbutton(10, 180);
      addbutton(100, 240);
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("重置" + Core.bundle.get("stat.xiuwei"), () -> clear()).size(100, 80);
        }).size(100, 100);
        table.center().left().update(() -> {
          if (Core.settings.getBool("showXiuWei")) {
            height = -120;
          } else
            height = 10000;
          table.translation.set(0, height);
        });
      });
      Events.fire(new JingJieChange(CVars.playerMagicPower));
    });
    Events.on(JingJieChange.class, e -> {
      float currentMagic = e.amount;
      JingJie currentJingJie = CVars.playerJingJie;
      JingJie finalTargetJingJie = JingJie.getMin();
      boolean isNewRoad = CVars.chooseNewRoad;
      JingJie blockJingJie = null;
      // 新增：待渡劫的境界标记
      JingJie needDuJieJingJie = null;

      for (JingJie jingJie : JingJie.all) {
        // 原有路线过滤逻辑不变
        if (jingJie.hasMirror && jingJie.newRoad != isNewRoad) {
          continue;
        }
        if (currentMagic < jingJie.amount) {
          break;
        }
        // 原有功法检查逻辑不变
        if (!CVars.gongfaHave.contains(jingJie.gongFa)) {
          blockJingJie = jingJie;
          break;
        }
        // ========== 新增：渡劫检查核心逻辑 ==========
        // 如果该境界需要渡劫，且未完成渡劫，拦截升级
        // 所有条件满足，更新目标境界
        if (jingJie.needDuJie && !CVars.completedDuJiePresets.contains(jingJie.duJiePreset)) {
          needDuJieJingJie = jingJie;
          break;
        }
        finalTargetJingJie = jingJie;
      }

      // 原有功法不足的拦截逻辑不变
      if (blockJingJie != null && blockJingJie != JingJie.getMin()) {
        float targetPower = Math.max(0, blockJingJie.amount - 1);
        if (currentMagic > targetPower) {
          CVars.playerMagicPower = targetPower;
          savePower();
          long currentTime = Time.millis();
          if (currentTime - lastToastTimestamp >= TOAST_COOLDOWN_MS) {
            Events.fire(new GongFaBuQuanEvent(blockJingJie, blockJingJie.gongFa));
            lastToastTimestamp = currentTime;
          }
          Events.fire(new JingJieChange(CVars.playerMagicPower));
          return;
        }
      }

      // ========== 新增：渡劫拦截处理 ==========
      if (needDuJieJingJie != null) {
        // 锁定灵力，不让玩家超过渡劫境界的阈值
        float targetPower = Math.max(0, needDuJieJingJie.amount - 1);
        if (currentMagic > targetPower) {
          CVars.playerMagicPower = targetPower;
          savePower();
        }
        // 弹出渡劫提示，仅触发一次
        long currentTime = Time.millis();
        if (currentTime - lastToastTimestamp >= TOAST_COOLDOWN_MS) {
          // 提示玩家需要渡劫
          Vars.ui.hudfrag.showToast(Icon.warning, Core.bundle.format("dujie.need", needDuJieJingJie.str));
          lastToastTimestamp = currentTime;
          // 触发渡劫确认弹窗
          showDuJieConfirm(needDuJieJingJie);
        }
        // 不更新境界，直接返回
        Events.fire(new XiuWeiChange(CVars.playerJingJie));
        return;
      }

      // 原有境界升级逻辑不变
      boolean isLevelUp = finalTargetJingJie.amount > currentJingJie.amount;
      boolean isLevelDown = finalTargetJingJie.amount < currentJingJie.amount;
      CVars.playerJingJie = finalTargetJingJie;
      updateCurrentAvailableJingJie();
      if (isLevelUp) {
        updateReachedJingJie(finalTargetJingJie);
        Vars.ui.hudfrag.showToast(Icon.up, "境界突破！当前境界：" + finalTargetJingJie.str);
      } else if (isLevelDown) {
        Vars.ui.hudfrag.showToast(Icon.down, "境界跌落！当前境界：" + finalTargetJingJie.str);
      }
      Events.fire(new XiuWeiChange(CVars.playerJingJie));
      DLog.info("当前境界更新为：" + finalTargetJingJie.str + "，当前灵力：" + CVars.playerMagicPower);
    });
    Events.on(XiuWeiChange.class, e -> {
      if (JingJie.fajing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.fan;
      } else if (JingJie.shenjing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.shen;
      } else if (JingJie.shengjing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.sheng;
      } else if (JingJie.xianjing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.xian;
      } else if (JingJie.dijing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.dijun;
      } else
        CVars.playerXiuWei = XiuWei.yong;
    });
    Events.on(MagicPowerChange.class, e -> {
      CVars.playerMagicPower = Math.max(0, CVars.playerMagicPower += e.amount);
      Events.fire(new JingJieChange(CVars.playerMagicPower));
      savePower();
    });
    Events.on(GongFaBuQuanEvent.class, e -> {
      String message = Core.bundle.get("gongfabuquan") + e.gongFa.localizedName + "," + Core.bundle.get("fail-upgrade")
          + e.jingJie.str;
      Vars.ui.hudfrag.showToast(Icon.cancel, message);
    });
    Events.on(StateChangeEvent.class, event -> {
      if (event.to == State.menu) {
        savePower();
        saveReachedJingJie();
        saveCurrentAvailableJingJie();
      }
    });
    Events.on(SectorCaptureEvent.class, e -> {
      if (!Vars.state.isCampaign())
        return;
      // 非渡劫中状态直接跳过
      if (!CVars.isInDuJie || CVars.pendingDuJieJingJie == null)
        return;

      JingJie duJieTarget = CVars.pendingDuJieJingJie;
      Sector duJieSector = duJieTarget.duJiePreset.sector;

      // 【严格校验】必须是绑定的渡劫区块，且已被捕获（complete）
      if (Vars.state.rules.sector == null
          || Vars.state.rules.sector.preset != duJieTarget.duJiePreset
          || e.sector != duJieSector
          || !e.sector.isCaptured()) {
        return;
      }

      // 渡劫成功：记录完成状态
      CVars.completedDuJiePresets.add(duJieTarget.duJiePreset);
      Vars.ui.hudfrag.showToast(Icon.ok, Core.bundle.format("dujie.success", duJieTarget.str));
      DLog.info("渡劫成功：区块已完成，解锁" + duJieTarget.str + "境界突破权限");

      // 重置渡劫状态
      CVars.pendingDuJieJingJie = null;
      CVars.isInDuJie = false;
      saveDuJieState();

      // 触发境界自动突破
      Time.run(2f, () -> Events.fire(new JingJieChange(CVars.playerMagicPower)));
    });
    Events.on(GameOverEvent.class, e -> {
      if (!Vars.state.isCampaign())
        return;
      if (!CVars.isInDuJie || CVars.pendingDuJieJingJie == null)
        return;
      JingJie duJieTarget = CVars.pendingDuJieJingJie;
      SectorPreset bindPreset = duJieTarget.duJiePreset;

      // 2. 【严格校验】必须是在渡劫绑定的区块里失败，避免其他场景误触发
      if (bindPreset == null || Vars.state.rules.sector == null || Vars.state.rules.sector.preset != bindPreset)
        return;
      // 执行渡劫失败惩罚
      duJieFail();
      Vars.ui.hudfrag.showToast(Icon.cancel, Core.bundle.get("dujie.fail"));
      DLog.info("渡劫失败：我方核心全毁，已执行全量重置");

      // 重置渡劫状态
      CVars.pendingDuJieJingJie = null;
      CVars.isInDuJie = false;
      saveDuJieState();
    });
  }

  private static void saveDuJieState() {
    try {
      // 保存已完成的渡劫记录，用name序列化
      if (CVars.completedDuJiePresets.isEmpty()) {
        Core.settings.remove(SAVE_KEY_COMPLETED_DUJIE);
      } else {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (SectorPreset preset : CVars.completedDuJiePresets) {
          if (index > 0)
            sb.append(",");
          sb.append(preset.name);
          index++;
        }
        Core.settings.put(SAVE_KEY_COMPLETED_DUJIE, sb.toString());
      }
      // 保存待渡劫境界
      if (CVars.pendingDuJieJingJie != null) {
        Core.settings.put(SAVE_KEY_PENDING_DUJIE, CVars.pendingDuJieJingJie.ordinal());
      } else {
        Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
      }
      Core.settings.manualSave();
      DLog.info("渡劫状态已保存，已完成渡劫：" + CVars.completedDuJiePresets + "，待渡劫境界：" + CVars.pendingDuJieJingJie);
    } catch (Exception e) {
      DLog.err("渡劫状态保存失败", e);
    }
  }

  /**
   * 从本地存档加载渡劫状态
   */
  private static void loadDuJieState() {
    try {
      // 加载已完成的渡劫记录
      CVars.completedDuJiePresets.clear();
      String savedSectors = Core.settings.getString(SAVE_KEY_COMPLETED_DUJIE, "");
      if (!isBlank(savedSectors)) {
        String[] presetNames = savedSectors.split(",");
        for (String name : presetNames) {
          String trimName = name.trim();
          if (!isBlank(trimName)) {
            SectorPreset preset = (SectorPreset) Vars.content.getByName(ContentType.sector, trimName);
            if (preset != null) {
              CVars.completedDuJiePresets.add(preset);
            }
          }
        }
      }
      // 加载待渡劫境界
      int pendingOrdinal = Core.settings.getInt(SAVE_KEY_PENDING_DUJIE, -1);
      if (pendingOrdinal != -1) {
        JingJie pendingJingJie = JingJie.getByOrdinal(pendingOrdinal);
        if (pendingJingJie != null && pendingJingJie.needDuJie) {
          CVars.pendingDuJieJingJie = pendingJingJie;
          CVars.isInDuJie = true;
          DLog.info("已恢复待渡劫境界：" + pendingJingJie.str);
        } else {
          Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
          CVars.pendingDuJieJingJie = null;
          CVars.isInDuJie = false;
        }
      } else {
        CVars.pendingDuJieJingJie = null;
        CVars.isInDuJie = false;
      }
      DLog.info("渡劫状态加载完成，已完成渡劫：" + CVars.completedDuJiePresets);
    } catch (Exception e) {
      DLog.err("渡劫状态加载失败，已重置", e);
      CVars.completedDuJiePresets.clear();
      CVars.pendingDuJieJingJie = null;
      CVars.isInDuJie = false;
      Core.settings.remove(SAVE_KEY_COMPLETED_DUJIE);
      Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
    }
  }

  public static void savePower() {
    Core.settings.put("crystal.magicpower", CVars.playerMagicPower);
    Core.settings.manualSave();
  }

  private static void showDuJieConfirm(JingJie targetJingJie) {
    long currentTime = Time.millis();
    // 【新增】冷却时间、重复弹窗、渡劫中状态校验
    if (CVars.isInDuJie
        || targetJingJie == CVars.pendingDuJieJingJie
        || currentTime - lastDuJieConfirmTimestamp < DUJIE_CONFIRM_COOLDOWN_MS) {
      return;
    }
    // 更新最后一次弹窗时间
    lastDuJieConfirmTimestamp = currentTime;

    // 原有弹窗逻辑保持不变
    BaseDialog dujieDialog = new BaseDialog(Core.bundle.format("dujie.title", targetJingJie.str), Styles.fullDialog);
    dujieDialog.cont.add(Core.bundle.format("dujie.desc", targetJingJie.str)).wrap().width(400f).pad(20f);
    dujieDialog.cont.row();
    dujieDialog.cont.add(Core.bundle.get("dujie.warn")).color(Color.scarlet).wrap().width(400f).pad(10f);
    dujieDialog.cont.row();

    dujieDialog.cont.button(Core.bundle.get("dujie.confirm"), Styles.flatTogglet, () -> {
      startDuJie(targetJingJie);
      dujieDialog.hide();
    }).size(200f, 60f).pad(10f);

    dujieDialog.cont.button(Core.bundle.get("cancel"), Styles.flatTogglet, dujieDialog::hide).size(200f, 60f).pad(10f);
    dujieDialog.show();
  }

  private static void startDuJie(JingJie targetJingJie) {
    // 空值保护，直接判断绑定的SectorPreset
    if (targetJingJie == null || !targetJingJie.needDuJie || targetJingJie.duJiePreset == null) {
      Vars.ui.hudfrag.showToast(Icon.cancel, Core.bundle.get("dujie.sector.notfound"));
      return;
    }
    // 无需运行时查找，直接使用枚举绑定的实例
    SectorPreset dujiePreset = targetJingJie.duJiePreset;

    // 后续原有逻辑保持不变
    CVars.pendingDuJieJingJie = targetJingJie;
    CVars.isInDuJie = true;
    saveDuJieState();

    if (Vars.state.isGame()) {
      Vars.control.exit();
    }
    CVars.cui.cplanet.show();
    CVars.cui.cplanet.viewPlanet(dujiePreset.sector.planet, false);
    CVars.cui.cplanet.selectSector(dujiePreset.sector);
    Vars.ui.hudfrag.showToast(Icon.defense, Core.bundle.format("dujie.start", targetJingJie.str));
    DLog.info(
        "开始渡劫：" + targetJingJie.str + "，对应Sector：" + dujiePreset.name + "，所属星球：" + dujiePreset.sector.planet.name);
  }

  public static void setChooseNewRoad(boolean enable) {
    if (CVars.chooseNewRoad == enable)
      return;
    CVars.chooseNewRoad = enable;
    Core.settings.put("crystal.chooseNewRoad", enable);
    Core.settings.manualSave();
    // 切换路线后，强制重新匹配境界
    Events.fire(new JingJieChange(CVars.playerMagicPower));
  }

  public static void clear() {
    CVars.playerMagicPower = 0;
    CVars.playerXiuWei = XiuWei.yong;
    CVars.playerJingJie = JingJie.fan;
    CVars.reachedJingJie.clear();
    CVars.isInDuJie = false;
    CVars.reachedJingJie.add(JingJie.fan);
    CVars.currentAvailableJingJie.clear();
    CVars.completedDuJiePresets.clear();
    CVars.pendingDuJieJingJie = null;
    for (var s : JingJie.shenNewRoad) {
      if (s.duJiePreset != null)
        if (s.duJiePreset.sector.save != null && s.duJiePreset.sector.hasBase() && s.duJiePreset.sector.isCaptured()) {
          abandonSectorConfirm(s.duJiePreset.sector, null);
        }
    }
    CVars.completedDuJiePresets.clear(); // 同步修改
    saveReachedJingJie();
    saveCurrentAvailableJingJie();
    savePower();
    saveDuJieState(); // 同步保存渡劫状态
    Events.fire(new JingJieChange(0f));
  }

  private static String serializeJingJieList(Seq<JingJie> list) {
    if (list == null || list.isEmpty()) {
      return String.valueOf(JingJie.getMin().ordinal());
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(list.get(i).ordinal());
    }
    return sb.toString();
  }

  private static Seq<JingJie> deserializeJingJieList(String str) {
    Seq<JingJie> result = new Seq<>();
    if (isBlank(str)) {
      result.add(JingJie.getMin());
      return result;
    }
    String[] ordinalStrs = str.split(",");
    for (String ordinalStr : ordinalStrs) {
      try {
        int ordinal = Integer.parseInt(ordinalStr.trim());
        JingJie jingJie = JingJie.getByOrdinal(ordinal);
        if (jingJie != null && !result.contains(jingJie)) {
          result.add(jingJie);
        }
      } catch (NumberFormatException ignored) {
      }
    }
    if (result.isEmpty()) {
      result.add(JingJie.getMin());
    }
    result.sort(j -> j.amount);
    return result;
  }

  private static void loadReachedJingJie() {
    CVars.reachedJingJie.clear();
    try {
      String savedOrdinalStr = Core.settings.getString(SAVE_KEY_REACHED_JINGJIE, "");
      if (!isBlank(savedOrdinalStr)) {
        CVars.reachedJingJie = deserializeJingJieList(savedOrdinalStr);
        DLog.info("历史境界加载完成（序号格式）：" + CVars.reachedJingJie.toString(",", j -> j.str));
        return;
      }
      String savedNameStr = Core.settings.getString(OLD_SAVE_KEY_REACHED_JINGJIE, "");
      if (!isBlank(savedNameStr)) {
        for (String name : savedNameStr.split(",")) {
          String trimName = name.trim();
          if (isBlank(trimName))
            continue;
          try {
            JingJie jingJie = JingJie.valueOf(trimName);
            if (!CVars.reachedJingJie.contains(jingJie)) {
              CVars.reachedJingJie.add(jingJie);
            }
          } catch (Exception ex) {
            DLog.warn("旧存档兼容：无效的境界名称：" + trimName);
          }
        }
        CVars.reachedJingJie.sort(j -> j.amount);
        saveReachedJingJie();
        DLog.info("旧存档兼容完成，已转换为序号格式：" + CVars.reachedJingJie.toString(",", j -> j.str));
        return;
      }
      CVars.reachedJingJie.add(JingJie.getMin());
      DLog.info("无历史境界存档，已初始化默认值");
    } catch (Exception e) {
      DLog.err("历史境界加载失败，已重置为默认值", e);
      CVars.reachedJingJie.clear();
      CVars.reachedJingJie.add(JingJie.getMin());
    }
  }

  private static void saveReachedJingJie() {
    try {
      if (CVars.reachedJingJie.isEmpty()) {
        Core.settings.remove(SAVE_KEY_REACHED_JINGJIE);
      } else {
        Core.settings.put(SAVE_KEY_REACHED_JINGJIE, serializeJingJieList(CVars.reachedJingJie));
      }
      Core.settings.remove(OLD_SAVE_KEY_REACHED_JINGJIE);
      Core.settings.manualSave();
      DLog.info("历史境界已保存：" + CVars.reachedJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("历史境界保存失败", e);
    }
  }

  private static void loadCurrentAvailableJingJie() {
    CVars.currentAvailableJingJie.clear();
    try {
      String savedOrdinalStr = Core.settings.getString(SAVE_KEY_AVAILABLE_JINGJIE, "");
      CVars.currentAvailableJingJie = deserializeJingJieList(savedOrdinalStr);
      DLog.info("当前可用境界加载完成：" + CVars.currentAvailableJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("当前可用境界加载失败，已重置为默认值", e);
      CVars.currentAvailableJingJie.clear();
      CVars.currentAvailableJingJie.add(JingJie.getMin());
    }
  }

  private static void saveCurrentAvailableJingJie() {
    try {
      if (CVars.currentAvailableJingJie.isEmpty()) {
        Core.settings.remove(SAVE_KEY_AVAILABLE_JINGJIE);
      } else {
        Core.settings.put(SAVE_KEY_AVAILABLE_JINGJIE, serializeJingJieList(CVars.currentAvailableJingJie));
      }
      Core.settings.manualSave();
      DLog.info("当前可用境界已保存：" + CVars.currentAvailableJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("当前可用境界保存失败", e);
    }
  }

  private static void updateCurrentAvailableJingJie() {
    CVars.currentAvailableJingJie.clear();
    JingJie currentJingJie = CVars.playerJingJie;
    boolean isNewRoad = CVars.chooseNewRoad;
    for (JingJie jingJie : JingJie.all) {
      if (jingJie.hasMirror && jingJie.newRoad != isNewRoad) {
        continue;
      }
      if (jingJie.amount <= currentJingJie.amount) {
        CVars.currentAvailableJingJie.add(jingJie);
      }
    }
    CVars.currentAvailableJingJie.sort(j -> j.amount);
    saveCurrentAvailableJingJie();
  }

  private static void updateReachedJingJie(JingJie newJingJie) {
    boolean hasNew = false;
    for (JingJie jingJie : JingJie.all) {
      if ((!jingJie.hasMirror || jingJie.newRoad == CVars.chooseNewRoad)
          && jingJie.amount <= newJingJie.amount
          && !CVars.reachedJingJie.contains(jingJie)) {
        CVars.reachedJingJie.add(jingJie);
        hasNew = true;
      }
    }

    if (hasNew) {
      CVars.reachedJingJie.sort(j -> j.amount);
      saveReachedJingJie();
      DLog.info("玩家解锁历史境界，当前已解锁：" + CVars.reachedJingJie.toString(",", j -> j.str));
    }
  }

  public static void showMagic() {
    BaseDialog xiuweiDialog = new BaseDialog("", Styles.fullDialog);
    xiuweiDialog.cont.pane(t -> {
      t.add(Core.bundle.get("stat.xiuwei") + ":" + CVars.playerXiuWei.str);
      t.row();
      t.add(Core.bundle.get("stat.jingjie") + ":" + CVars.playerJingJie.str);
      t.row();
      t.add(Core.bundle.get("stat.magicpower") + ":" + CVars.playerMagicPower);
      t.row();
      t.add(Core.bundle.get("gongfahave"));
      Seq<GongFa> tmp = CVars.gongfaHave.toSeq().sort(j -> j.id);
      int i = 0;
      for (GongFa g : tmp) {
        t.add(g.localizedName + " ");
        i++;
        if (i == 5) {
          t.row();
          i = 0;
        }
      }
      t.row();
      t.add(Core.bundle.get("nextjingjie") + CVars.playerMagicPower + "/" + getNextJngJie().amount + " "
          + getNextJngJie().str);
    });
    xiuweiDialog.addCloseButton();
    xiuweiDialog.show();
  }

  public static JingJie getNextJngJie() {
    JingJie[] t = CVars.chooseNewRoad ? JingJie.xinLu : JingJie.jiuLu;
    int index = 0;
    for (int i = 0; i < t.length; i++) {
      if (t[i] == CVars.playerJingJie) {
        index = i;
        break;
      }
    }
    return t[index + 1];
  }

  public static boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }
}
