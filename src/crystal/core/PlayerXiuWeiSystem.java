package crystal.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Time;
import crystal.CVars;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.DuJieEndEvent;
import crystal.game.CEventType.DuJieStartEvent;
import crystal.game.CEventType.GongFaBuQuanEvent;
import crystal.game.CEventType.JingJieChange;
import crystal.game.CEventType.MagicPowerChange;
import crystal.game.CEventType.XiuWeiChange;
import crystal.type.GongFa;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static crystal.CVars.debug;
import static mindustry.Vars.*;

public class PlayerXiuWeiSystem {
  public static float height = 0;

  private static final long TOAST_COOLDOWN_MS = 3000;
  private static long lastToastTimestamp = 0;
  private static final long DUJIE_CONFIRM_COOLDOWN_MS = 60 * 1000;
  private static long lastDuJieConfirmTimestamp = 0;
  private static boolean initialized = false;
  private static final String SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie_ordinal";
  private static final String SAVE_KEY_AVAILABLE_JINGJIE = "crystal.availableJingJie_ordinal";
  private static final String OLD_SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie";
  private static final String SAVE_KEY_PENDING_DUJIE = "crystal.pendingDuJieJingJieOrdinal";
  private static final String SAVE_KEY_COMPLETED_DUJIE = "crystal.completedDuJieJingJies";

  /**
   * 检查境界是否可通过渡劫门槛
   * 修复：已完成渡劫的境界直接放行，未完成的拦截
   */
  private static boolean canPassDuJie(JingJie jingJie) {
    if (!jingJie.needDuJie || jingJie.duJieCondition == null)
      return true;
    return CVars.completedDuJieJingJies.contains(jingJie);
  }

  /** 执行渡劫失败惩罚 */
  public static void duJieFail() {
    JingJie target = CVars.pendingDuJieJingJie;

    CVars.playerMagicPower = 0f;
    CVars.playerJingJie = JingJie.fan;
    CVars.playerXiuWei = XiuWei.yong;
    CVars.currentAvailableJingJie.clear();
    CVars.currentAvailableJingJie.add(JingJie.fan);
    CVars.pendingDuJieJingJie = null;
    CVars.isInDuJie = false;

    savePower();
    saveCurrentAvailableJingJie();
    saveDuJieState();
    Events.fire(new JingJieChange(0f));
    Events.fire(new XiuWeiChange(CVars.playerJingJie));

    if (target != null) {
      Events.fire(new DuJieEndEvent(target, false));
    }

    Vars.ui.hudfrag.showToast(Icon.cancel, Core.bundle.get("dujie.fail"));
    DLog.info("渡劫失败执行：已重置当前境界与灵力");
  }

  public static void addButton(float amount, float h) {
    if (CVars.debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button("加" + Core.bundle.get("stat.xiuwei") + amount,
              () -> Events.fire(new MagicPowerChange(amount))).size(100, 70);
        }).size(100, 70);
        table.center().left().update(() -> {
          height = Core.settings.getBool("showXiuWei") ? h : 10000;
          table.translation.set(0, height);
        });
      });
  }

  public static void init() {
    if (initialized)
      return;
    initialized = true;
    Events.on(ClientLoadEvent.class, e -> {
      CVars.chooseNewRoad = Core.settings.getBool("crystal.chooseNewRoad", false);
      loadReachedJingJie();
      loadDuJieState();
      CVars.playerMagicPower = Math.max(0, Core.settings.getFloat("crystal.magicpower", 0f));
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

      Events.run(Trigger.update, PlayerXiuWeiSystem::tickDuJieCheck);

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
          } else {
            height = 10000;
          }
          table.translation.set(0, height);
        });
      });

      addButton(-100, -60);
      addButton(-10, 0);
      addButton(-1, 60);
      addButton(1, 120);
      addButton(10, 180);
      addButton(100, 240);

      if (debug)
        Vars.ui.hudGroup.fill(null, table -> {
          table.table(null, t -> {
            t.button("重置" + Core.bundle.get("stat.xiuwei"), () -> clear()).size(100, 80);
          }).size(100, 100);
          table.center().left().update(() -> {
            height = Core.settings.getBool("showXiuWei") ? -120 : 10000;
            table.translation.set(0, height);
          });
        });

      Events.fire(new JingJieChange(CVars.playerMagicPower));
    });

    // 监听渡劫开始事件
    Events.on(DuJieStartEvent.class, e -> {
      if (CVars.isInDuJie || e.targetJingJie == null)
        return;
      if (!e.targetJingJie.needDuJie || e.targetJingJie.duJieCondition == null)
        return;
      // 已渡劫成功的直接跳过
      if (CVars.completedDuJieJingJies.contains(e.targetJingJie))
        return;

      CVars.pendingDuJieJingJie = e.targetJingJie;
      CVars.isInDuJie = true;
      saveDuJieState();

      Vars.ui.hudfrag.showToast(Icon.defense, Core.bundle.format("dujie.start", e.targetJingJie.str));
      DLog.info("进入渡劫状态：" + e.targetJingJie.str + "，目标：" + e.targetJingJie.duJieCondition.str);
    });

    Events.on(JingJieChange.class, e -> {
      float currentMagic = e.amount;
      JingJie currentJingJie = CVars.playerJingJie;
      JingJie finalTargetJingJie = JingJie.getMin();
      boolean isNewRoad = CVars.chooseNewRoad;
      JingJie blockJingJie = null;
      JingJie needDuJieJingJie = null;

      for (JingJie jingJie : JingJie.all) {
        if (jingJie.hasMirror && jingJie.newRoad != isNewRoad)
          continue;
        if (currentMagic < jingJie.amount)
          break;

        if (!CVars.gongfaHave.contains(jingJie.gongFa)) {
          blockJingJie = jingJie;
          break;
        }

        if (!canPassDuJie(jingJie)) {
          needDuJieJingJie = jingJie;
          break;
        }

        finalTargetJingJie = jingJie;
      }

      // 功法不足拦截
      if (blockJingJie != null && blockJingJie != JingJie.getMin()) {
        float targetPower = Math.max(0, blockJingJie.amount - 0.1f);
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

      // 渡劫拦截：锁定灵力 + 弹出确认
      if (needDuJieJingJie != null && !CVars.isInDuJie) {
        float targetPower = Math.max(0, needDuJieJingJie.amount - 0.1f);
        if (currentMagic > targetPower) {
          CVars.playerMagicPower = targetPower;
          savePower();
        }

        long currentTime = Time.millis();
        if (currentTime - lastToastTimestamp >= TOAST_COOLDOWN_MS) {
          Vars.ui.hudfrag.showToast(Icon.warning, Core.bundle.format("dujie.need", needDuJieJingJie.str));
          lastToastTimestamp = currentTime;
          showDuJieConfirm(needDuJieJingJie);
        }

        Events.fire(new XiuWeiChange(CVars.playerJingJie));
        return;
      }

      // 境界更新
      boolean isLevelUp = finalTargetJingJie.amount > currentJingJie.amount;
      boolean isLevelDown = finalTargetJingJie.amount < currentJingJie.amount;
      CVars.playerJingJie = finalTargetJingJie;
      updateCurrentAvailableJingJie();

      if (isLevelUp) {
        updateReachedJingJie(finalTargetJingJie);
        Vars.ui.hudfrag.showToast(Icon.up, Core.bundle.get("xiuweitupo") + finalTargetJingJie.str);
      } else if (isLevelDown) {
        Vars.ui.hudfrag.showToast(Icon.down, Core.bundle.get("xiuweidieluo") + finalTargetJingJie.str);
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
      } else {
        CVars.playerXiuWei = XiuWei.yong;
      }
    });

    Events.on(MagicPowerChange.class, e -> {
      CVars.playerMagicPower = Math.max(0, CVars.playerMagicPower += e.amount);
      Events.fire(new JingJieChange(CVars.playerMagicPower));
      savePower();
    });

    Events.on(GongFaBuQuanEvent.class, e -> {
      String message = Core.bundle.get("gongfabuquan") + e.gongFa.localizedName + ","
          + Core.bundle.get("fail-upgrade") + e.jingJie.str;
      if (Core.settings.getBool("showgongfabuquan", true)) {
        Vars.ui.hudfrag.showToast(Icon.cancel, message);
      }
    });

    Events.on(StateChangeEvent.class, event -> {
      if (event.to == State.menu) {
        savePower();
        saveReachedJingJie();
        saveCurrentAvailableJingJie();
        saveDuJieState();
      }
    });
  }

  /** 每帧检查渡劫条件，失败优先 */
  private static void tickDuJieCheck() {
    if (!CVars.isInDuJie || CVars.pendingDuJieJingJie == null)
      return;
    var cond = CVars.pendingDuJieJingJie.duJieCondition;
    if (cond == null)
      return;

    // 先检查失败条件
    if (cond.fail.get()) {
      duJieFail();
      return;
    }

    // 再检查成功条件
    if (cond.success.get()) {
      JingJie target = CVars.pendingDuJieJingJie;
      // 标记该境界渡劫永久完成
      CVars.completedDuJieJingJies.add(target);
      CVars.isInDuJie = false;
      CVars.pendingDuJieJingJie = null;
      saveDuJieState();

      Events.fire(new DuJieEndEvent(target, true));
      Vars.ui.hudfrag.showToast(Icon.ok, Core.bundle.format("dujie.success", target.str));
      DLog.info("渡劫成功：" + target.str);

      // 触发境界突破
      Events.fire(new JingJieChange(CVars.playerMagicPower));
    }
  }

  // ========== 渡劫状态持久化（含已完成记录） ==========
  private static void saveDuJieState() {
    try {
      // 保存已完成渡劫的境界
      if (CVars.completedDuJieJingJies.isEmpty()) {
        Core.settings.remove(SAVE_KEY_COMPLETED_DUJIE);
      } else {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (JingJie j : CVars.completedDuJieJingJies) {
          if (i > 0)
            sb.append(",");
          sb.append(j.ordinal());
          i++;
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
      DLog.info("渡劫状态已保存，已完成：" + CVars.completedDuJieJingJies.size + "个，待渡劫：" + CVars.pendingDuJieJingJie);
    } catch (Exception e) {
      DLog.err("渡劫状态保存失败", e);
    }
  }

  private static void loadDuJieState() {
    try {
      // 加载已完成渡劫的境界
      CVars.completedDuJieJingJies.clear();
      String savedCompleted = Core.settings.getString(SAVE_KEY_COMPLETED_DUJIE, "");
      if (!isBlank(savedCompleted)) {
        for (String s : savedCompleted.split(",")) {
          try {
            int ord = Integer.parseInt(s.trim());
            JingJie j = JingJie.getByOrdinal(ord);
            if (j != null && j.needDuJie) {
              CVars.completedDuJieJingJies.add(j);
            }
          } catch (NumberFormatException ignored) {
          }
        }
      }

      // 加载待渡劫境界
      int pendingOrdinal = Core.settings.getInt(SAVE_KEY_PENDING_DUJIE, -1);
      if (pendingOrdinal != -1) {
        JingJie pendingJingJie = JingJie.getByOrdinal(pendingOrdinal);
        if (pendingJingJie != null && pendingJingJie.needDuJie && pendingJingJie.duJieCondition != null
            && !CVars.completedDuJieJingJies.contains(pendingJingJie)) {
          CVars.pendingDuJieJingJie = pendingJingJie;
          CVars.isInDuJie = true;
          DLog.info("已恢复渡劫状态：" + pendingJingJie.str);
        } else {
          Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
          CVars.pendingDuJieJingJie = null;
          CVars.isInDuJie = false;
        }
      } else {
        CVars.pendingDuJieJingJie = null;
        CVars.isInDuJie = false;
      }

      DLog.info("渡劫状态加载完成，已完成渡劫：" + CVars.completedDuJieJingJies.size + "个");
    } catch (Exception e) {
      DLog.err("渡劫状态加载失败，已重置", e);
      CVars.completedDuJieJingJies.clear();
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
    if (CVars.isInDuJie
        || targetJingJie == CVars.pendingDuJieJingJie
        || currentTime - lastDuJieConfirmTimestamp < DUJIE_CONFIRM_COOLDOWN_MS) {
      return;
    }
    lastDuJieConfirmTimestamp = currentTime;

    BaseDialog dujieDialog = new BaseDialog(
        Core.bundle.format("dujie.title", targetJingJie.str), Styles.fullDialog);
    dujieDialog.cont.add(Core.bundle.format("dujie.desc", targetJingJie.str))
        .wrap().width(400f).pad(20f);
    dujieDialog.cont.row();

    // 显示渡劫目标
    if (targetJingJie.duJieCondition != null) {
      dujieDialog.cont.add("渡劫目标：" + targetJingJie.duJieCondition.str)
          .color(Color.gold).wrap().width(400f).pad(10f);
      dujieDialog.cont.row();
    }

    dujieDialog.cont.add(Core.bundle.get("dujie.warn"))
        .color(Color.scarlet).wrap().width(400f).pad(10f);
    dujieDialog.cont.row();
    dujieDialog.cont.button(Core.bundle.get("dujie.confirm"), Styles.flatTogglet, () -> {
      Events.fire(new DuJieStartEvent(targetJingJie));
      dujieDialog.hide();
    }).size(200f, 60f).pad(10f);
    dujieDialog.cont.button(Core.bundle.get("cancel"), Styles.flatTogglet, dujieDialog::hide)
        .size(200f, 60f).pad(10f);
    dujieDialog.show();
  }

  public static void setChooseNewRoad(boolean enable) {
    if (CVars.chooseNewRoad == enable)
      return;
    CVars.chooseNewRoad = enable;
    Core.settings.put("crystal.chooseNewRoad", enable);
    Core.settings.manualSave();

    // 切换路线重置进行中的渡劫状态，已完成记录保留
    CVars.isInDuJie = false;
    CVars.pendingDuJieJingJie = null;
    saveDuJieState();

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
    CVars.completedDuJieJingJies.clear();
    CVars.pendingDuJieJingJie = null;

    saveReachedJingJie();
    saveCurrentAvailableJingJie();
    savePower();
    saveDuJieState();
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
    for (String ordinalStr : str.split(",")) {
      try {
        int ordinal = Integer.parseInt(ordinalStr.trim());
        JingJie jingJie = JingJie.getByOrdinal(ordinal);
        if (jingJie != null && !result.contains(jingJie)) {
          result.add(jingJie);
        }
      } catch (NumberFormatException ignored) {
      }
    }
    if (result.isEmpty())
      result.add(JingJie.getMin());
    result.sort(j -> j.amount);
    return result;
  }

  private static void loadReachedJingJie() {
    CVars.reachedJingJie.clear();
    try {
      String savedOrdinalStr = Core.settings.getString(SAVE_KEY_REACHED_JINGJIE, "");
      if (!isBlank(savedOrdinalStr)) {
        CVars.reachedJingJie = deserializeJingJieList(savedOrdinalStr);
        DLog.info("历史境界加载完成（序号格式）："
            + CVars.reachedJingJie.toString(",", j -> j.str));
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
        DLog.info("旧存档兼容完成，已转换为序号格式："
            + CVars.reachedJingJie.toString(",", j -> j.str));
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
      DLog.info("当前可用境界加载完成："
          + CVars.currentAvailableJingJie.toString(",", j -> j.str));
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
        Core.settings.put(SAVE_KEY_AVAILABLE_JINGJIE,
            serializeJingJieList(CVars.currentAvailableJingJie));
      }
      Core.settings.manualSave();
      DLog.info("当前可用境界已保存："
          + CVars.currentAvailableJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("当前可用境界保存失败", e);
    }
  }

  private static void updateCurrentAvailableJingJie() {
    CVars.currentAvailableJingJie.clear();
    JingJie currentJingJie = CVars.playerJingJie;
    boolean isNewRoad = CVars.chooseNewRoad;
    for (JingJie jingJie : JingJie.all) {
      if (jingJie.hasMirror && jingJie.newRoad != isNewRoad)
        continue;
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
      DLog.info("玩家解锁历史境界，当前已解锁："
          + CVars.reachedJingJie.toString(",", j -> j.str));
    }
  }

  /** 修为面板，修复重复调用 */
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

      // 渡劫目标显示
      if (CVars.isInDuJie && CVars.pendingDuJieJingJie != null
          && CVars.pendingDuJieJingJie.duJieCondition != null) {
        t.add("[渡] 当前目标：" + CVars.pendingDuJieJingJie.duJieCondition.str)
            .color(Color.scarlet).wrap().width(400f);
      } else {
        JingJie next = getNextJingJie();
        if (next.needDuJie && next.duJieCondition != null) {
          t.add("[渡] 下一境界目标：" + next.duJieCondition.str)
              .color(Color.gold).wrap().width(400f);
        }
      }
      t.row();

      JingJie next = getNextJingJie();
      t.add(Core.bundle.get("nextjingjie") + CVars.playerMagicPower
          + "/" + next.amount + " " + next.str);
    });
    xiuweiDialog.addCloseButton();
    xiuweiDialog.show();
  }

  /** 已修复数组越界 */
  public static JingJie getNextJingJie() {
    JingJie[] t = CVars.chooseNewRoad ? JingJie.xinLu : JingJie.jiuLu;
    int index = 0;
    for (int i = 0; i < t.length; i++) {
      if (t[i] == CVars.playerJingJie) {
        index = i;
        break;
      }
    }
    if (index >= t.length - 1)
      return t[t.length - 1];
    return t[index + 1];
  }

  public static boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }
}
