package crystal.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.DuJieEndEvent;
import crystal.game.CEventType.DuJieStartEvent;
import crystal.game.CEventType.GongFaBuQuanEvent;
import crystal.game.CEventType.JingJieRecalc;
import crystal.game.CEventType.MagicPowerChange;
import crystal.game.CEventType.XiuWeiRecalc;
import crystal.type.GongFa;
import crystal.ui.dialogs.XiuWeiDialog;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static crystal.CVars.debug;
import static mindustry.Vars.*;

public class PlayerXiuWeiSystem {
  public static float height = 0;

  private static final long TOAST_COOLDOWN_MS = 3000;
  private static long lastToastTimestamp = 0;
  // 渡劫提示 toast 独立冷却：原来与功法不足 toast 共用 lastToastTimestamp，
  // 会导致功法提示把渡劫确认弹窗吞掉 3 秒
  private static long lastDuJieToastTimestamp = 0;
  // 灵力落盘节流：灵力变动只标脏，最多每 5 秒真正写一次盘（原来每杀一个单位就 manualSave 一次）
  private static final long POWER_FLUSH_INTERVAL_MS = 5000;
  private static boolean powerDirty = false;
  private static long lastPowerFlushMs = 0;
  private static final long DUJIE_CONFIRM_COOLDOWN_MS = 60 * 1000;
  private static long lastDuJieConfirmTimestamp = 0;
  private static boolean initialized = false;
  private static final String SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie_ordinal";
  private static final String SAVE_KEY_AVAILABLE_JINGJIE = "crystal.availableJingJie_ordinal";
  private static final String OLD_SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie";
  private static final String SAVE_KEY_PENDING_DUJIE = "crystal.pendingDuJieJingJieOrdinal";
  private static final String SAVE_KEY_COMPLETED_DUJIE = "crystal.completedDuJieJingJies";
  private static final String SAVE_KEY_DUJIE_KILLS = "crystal.duJieKillCount";
  /** 渡劫期间的敌方击杀计数（shentu 等击杀类渡劫条件用），重启后从存档恢复 */
  private static int duJieKillCount = 0;

  /** 可动用灵力自然恢复：每秒恢复到 playerMagicPower 上限 */
  private static final float MAGIC_REGEN_PER_SECOND = 0.1f;

  /** 当前渡劫期间已击杀的敌方单位数 */
  public static int getDuJieKillCount() {
    return duJieKillCount;
  }

  /**
   * 检查境界是否可通过渡劫门槛
   * 修复：已完成渡劫的境界直接放行，未完成的拦截
   */
  private static boolean canPassDuJie(JingJie jingJie) {
    if (!jingJie.needDuJie || jingJie.duJieCondition == null)
      return true;
    return CultivationState.completedDuJieJingJies.contains(jingJie);
  }

  /**
   * 进度归零到凡人：境界、修为、灵力、可用境界、渡劫状态。
   * duJieFail() 与 clear() 共用，不再各自维护一份重置逻辑。
   */
  private static void resetProgressToFan() {
    CultivationState.playerMagicPower = 0f;
    CultivationState.availableMagicPower = 0f;
    CultivationState.playerJingJie = JingJie.fan;
    CultivationState.playerXiuWei = XiuWei.yong;
    CultivationState.currentAvailableJingJie.clear();
    CultivationState.currentAvailableJingJie.add(JingJie.fan);
    CultivationState.pendingDuJieJingJie = null;
    CultivationState.isInDuJie = false;
    duJieKillCount = 0;
  }

  /**
   * 执行渡劫失败惩罚。
   * 与 clear() 的分工（现在显式固定）：失败只重置当前进度，
   * 保留已习得功法、历史境界与已完成渡劫记录；clear() 是调试用的全清硬重置。
   */
  public static void duJieFail() {
    JingJie target = CultivationState.pendingDuJieJingJie;

    resetProgressToFan();

    savePower();
    saveCurrentAvailableJingJie();
    saveDuJieState();
    Events.fire(new JingJieRecalc(0f));
    Events.fire(new XiuWeiRecalc(CultivationState.playerJingJie));

    if (target != null) {
      Events.fire(new DuJieEndEvent(target, false));
    }

    Vars.ui.hudfrag.showToast(Icon.cancel, Core.bundle.get("dujie.fail"));
    DLog.info("渡劫失败执行：已重置当前境界与灵力");
  }

  public static void addButton(XiuWei xiuWei, float h) {
    if (CVars.debug)
      Vars.ui.hudGroup.fill(null, table -> {
        table.table(null, t -> {
          t.button(xiuWei.str,
              () -> {
                CultivationState.playerXiuWei = xiuWei;
              });
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
      CultivationState.playerMagicPower = Math.max(0, Core.settings.getFloat("crystal.magicpower", 0f));
      DLog.info("playerMagicPower" + CultivationState.playerMagicPower);
      CultivationState.availableMagicPower = Math.max(0, Core.settings.getFloat("crystal.availableMagicPower", 0f));
      if (CultivationState.reachedJingJie.isEmpty()) {
        CultivationState.reachedJingJie.add(JingJie.fan);
        saveReachedJingJie();
      }
      if (CultivationState.currentAvailableJingJie.isEmpty()) {
        CultivationState.currentAvailableJingJie.add(JingJie.fan);
        saveCurrentAvailableJingJie();
      }
      if (CultivationState.playerJingJie == null)
        CultivationState.playerJingJie = JingJie.fan;
      if (CultivationState.playerXiuWei == null)
        CultivationState.playerXiuWei = XiuWei.yong;

      Events.run(Trigger.update, PlayerXiuWeiSystem::tickDuJieCheck);
      Events.run(Trigger.update, PlayerXiuWeiSystem::regenMagicPower);

      Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
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

      addButton(XiuWei.fan, -60);
      addButton(XiuWei.shen, 0);
      addButton(XiuWei.dijun, 60);

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
      // 此处原本重复 fire 了一次 JingJieChange（上方已 fire 过），删除
    });

    // 监听渡劫开始事件
    Events.on(DuJieStartEvent.class, e -> {
      if (CultivationState.isInDuJie || e.targetJingJie == null)
        return;
      if (!e.targetJingJie.needDuJie || e.targetJingJie.duJieCondition == null)
        return;
      // 已渡劫成功的直接跳过
      if (CultivationState.completedDuJieJingJies.contains(e.targetJingJie))
        return;

      CultivationState.pendingDuJieJingJie = e.targetJingJie;
      CultivationState.isInDuJie = true;
      duJieKillCount = 0; // 新一轮渡劫，击杀数清零
      saveDuJieState();

      Vars.ui.hudfrag.showToast(Icon.defense, Core.bundle.format("dujie.start", e.targetJingJie.str));
      DLog.info("进入渡劫状态：" + e.targetJingJie.str + "，目标：" + e.targetJingJie.duJieCondition.str);
    });

    // 渡劫击杀计数：渡劫期间敌方单位死亡累计（供击杀类渡劫条件判定）
    Events.on(UnitDestroyEvent.class, e -> {
      if (CultivationState.isInDuJie && Vars.player != null && e.unit != null && e.unit.team != Vars.player.team()) {
        duJieKillCount++;
      }
    });

    Events.on(JingJieRecalc.class, e -> {
      float currentMagic = e.magicPower;
      JingJie currentJingJie = CultivationState.playerJingJie;
      JingJie finalTargetJingJie = JingJie.getMin();
      boolean isNewRoad = CVars.chooseNewRoad;
      JingJie blockJingJie = null;
      JingJie needDuJieJingJie = null;

      for (JingJie jingJie : JingJie.all) {
        if (jingJie.hasMirror && jingJie.newRoad != isNewRoad)
          continue;
        if (currentMagic < jingJie.amount)
          break;

        if (!CultivationState.gongfaHave.contains(jingJie.gongFa)) {
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
          CultivationState.playerMagicPower = targetPower;
          savePower();
          long currentTime = Time.millis();
          if (currentTime - lastToastTimestamp >= TOAST_COOLDOWN_MS) {
            Events.fire(new GongFaBuQuanEvent(blockJingJie, blockJingJie.gongFa));
            lastToastTimestamp = currentTime;
          }
          Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
          return;
        }
      }

      // 渡劫拦截：锁定灵力 + 弹出确认
      if (needDuJieJingJie != null && !CultivationState.isInDuJie) {
        float targetPower = Math.max(0, needDuJieJingJie.amount - 0.1f);
        if (currentMagic > targetPower) {
          CultivationState.playerMagicPower = targetPower;
          savePower();
        }

        long currentTime = Time.millis();
        if (currentTime - lastDuJieToastTimestamp >= TOAST_COOLDOWN_MS) {
          Vars.ui.hudfrag.showToast(Icon.warning, Core.bundle.format("dujie.need", needDuJieJingJie.str));
          lastDuJieToastTimestamp = currentTime;
        }
        // 弹窗有自己的 60s 冷却（showDuJieConfirm 内），不再受 toast 冷却门控
        showDuJieConfirm(needDuJieJingJie);

        Events.fire(new XiuWeiRecalc(CultivationState.playerJingJie));
        return;
      }

      // 境界更新
      boolean isLevelUp = finalTargetJingJie.amount > currentJingJie.amount;
      boolean isLevelDown = finalTargetJingJie.amount < currentJingJie.amount;
      CultivationState.playerJingJie = finalTargetJingJie;
      updateCurrentAvailableJingJie();

      if (isLevelUp) {
        updateReachedJingJie(finalTargetJingJie);
        Vars.ui.hudfrag.showToast(Icon.up, Core.bundle.get("xiuweitupo") + finalTargetJingJie.str);
      } else if (isLevelDown) {
        Vars.ui.hudfrag.showToast(Icon.down, Core.bundle.get("xiuweidieluo") + finalTargetJingJie.str);
      }

      Events.fire(new XiuWeiRecalc(CultivationState.playerJingJie));
      DLog.info("当前境界更新为：" + finalTargetJingJie.str + "，当前灵力：" + CultivationState.playerMagicPower);
    });

    Events.on(XiuWeiRecalc.class, e -> {
      if (JingJie.fajing.contains(e.jingJie)) {
        CultivationState.playerXiuWei = XiuWei.fan;
      } else if (JingJie.shenjing.contains(e.jingJie)) {
        CultivationState.playerXiuWei = XiuWei.shen;
      } else if (JingJie.shengjing.contains(e.jingJie)) {
        CultivationState.playerXiuWei = XiuWei.sheng;
      } else if (JingJie.xianjing.contains(e.jingJie)) {
        CultivationState.playerXiuWei = XiuWei.xian;
      } else if (JingJie.dijing.contains(e.jingJie)) {
        CultivationState.playerXiuWei = XiuWei.dijun;
      } else {
        CultivationState.playerXiuWei = XiuWei.yong;
      }
    });

    Events.on(MagicPowerChange.class, e -> {
      // 修复：复合赋值不要再嵌进表达式（原来 x = max(0, x += amt) 一次表达式写字段两次）
      CultivationState.playerMagicPower = Math.max(0, CultivationState.playerMagicPower + e.amount);
      Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
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
        flushPowerIfNeeded(true);
      }
    });
  }

  /** 每帧检查渡劫条件，失败优先 */
  private static void tickDuJieCheck() {
    // 灵力落盘节流：脏且超过间隔才真正写盘
    flushPowerIfNeeded(false);
    if (!CultivationState.isInDuJie || CultivationState.pendingDuJieJingJie == null)
      return;
    var cond = CultivationState.pendingDuJieJingJie.duJieCondition;
    if (cond == null)
      return;

    // 先检查失败条件
    if (cond.fail.get()) {
      duJieFail();
      return;
    }

    // 再检查成功条件
    if (cond.success.get()) {
      JingJie target = CultivationState.pendingDuJieJingJie;
      // 标记该境界渡劫永久完成
      CultivationState.completedDuJieJingJies.add(target);
      CultivationState.isInDuJie = false;
      CultivationState.pendingDuJieJingJie = null;
      saveDuJieState();

      Events.fire(new DuJieEndEvent(target, true));
      Vars.ui.hudfrag.showToast(Icon.ok, Core.bundle.format("dujie.success", target.str));
      DLog.info("渡劫成功：" + target.str);

      // 触发境界突破
      Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
    }
  }

  // ========== 渡劫状态持久化（含已完成记录） ==========
  private static void saveDuJieState() {
    try {
      // 保存已完成渡劫的境界
      if (CultivationState.completedDuJieJingJies.isEmpty()) {
        Core.settings.remove(SAVE_KEY_COMPLETED_DUJIE);
      } else {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (JingJie j : CultivationState.completedDuJieJingJies) {
          if (i > 0)
            sb.append(",");
          sb.append(j.name()); // 存枚举名，不存 ordinal（防枚举插入导致存档错位）
          i++;
        }
        Core.settings.put(SAVE_KEY_COMPLETED_DUJIE, sb.toString());
      }

      // 保存待渡劫境界与渡劫击杀数
      if (CultivationState.pendingDuJieJingJie != null) {
        Core.settings.put(SAVE_KEY_PENDING_DUJIE, CultivationState.pendingDuJieJingJie.name());
        Core.settings.put(SAVE_KEY_DUJIE_KILLS, duJieKillCount);
      } else {
        Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
        Core.settings.remove(SAVE_KEY_DUJIE_KILLS);
        duJieKillCount = 0;
      }

      Core.settings.manualSave();
      DLog.info("渡劫状态已保存，已完成：" + CultivationState.completedDuJieJingJies.size + "个，待渡劫：" + CultivationState.pendingDuJieJingJie);
    } catch (Exception e) {
      DLog.err("渡劫状态保存失败", e);
    }
  }

  private static void loadDuJieState() {
    try {
      // 加载已完成渡劫的境界
      CultivationState.completedDuJieJingJies.clear();
      String savedCompleted = Core.settings.getString(SAVE_KEY_COMPLETED_DUJIE, "");
      if (!isBlank(savedCompleted)) {
        for (String s : savedCompleted.split(",")) {
          JingJie j = parseJingJie(s); // 兼容旧 ordinal 与新 name 两种格式
          if (j != null && j.needDuJie) {
            CultivationState.completedDuJieJingJies.add(j);
          }
        }
      }

      // 加载待渡劫境界。不能用 getString：旧档这个 key 存的是 int（JSON 读回变成 Float），
      // Arc 的 getString 是硬 (String) 强转，会直接 ClassCastException 把整个渡劫状态清掉
      Object pendingObj = Core.settings.get(SAVE_KEY_PENDING_DUJIE, null);
      String pendingStr = pendingObj == null ? "" : String.valueOf(pendingObj);
      if (!isBlank(pendingStr)) {
        JingJie pendingJingJie = parseJingJie(pendingStr);
        if (pendingJingJie != null && pendingJingJie.needDuJie && pendingJingJie.duJieCondition != null
            && !CultivationState.completedDuJieJingJies.contains(pendingJingJie)) {
          CultivationState.pendingDuJieJingJie = pendingJingJie;
          CultivationState.isInDuJie = true;
          // 恢复渡劫击杀数。不能用 getInt：settings 经 JSON 读写后 int 变 Float，
          // Arc 的 getInt 硬强转 (int) 会 ClassCastException。用 Number 兼容两种
          Object killObj = Core.settings.get(SAVE_KEY_DUJIE_KILLS, null);
          duJieKillCount = killObj instanceof Number ? ((Number) killObj).intValue() : 0;
          DLog.info("已恢复渡劫状态：" + pendingJingJie.str + "，击杀数：" + duJieKillCount);
        } else {
          Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
          Core.settings.remove(SAVE_KEY_DUJIE_KILLS);
          CultivationState.pendingDuJieJingJie = null;
          CultivationState.isInDuJie = false;
          duJieKillCount = 0;
        }
      } else {
        CultivationState.pendingDuJieJingJie = null;
        CultivationState.isInDuJie = false;
        duJieKillCount = 0;
      }

      DLog.info("渡劫状态加载完成，已完成渡劫：" + CultivationState.completedDuJieJingJies.size + "个");
    } catch (Exception e) {
      DLog.err("渡劫状态加载失败，已重置", e);
      CultivationState.completedDuJieJingJies.clear();
      CultivationState.pendingDuJieJingJie = null;
      CultivationState.isInDuJie = false;
      Core.settings.remove(SAVE_KEY_COMPLETED_DUJIE);
      Core.settings.remove(SAVE_KEY_PENDING_DUJIE);
    }
  }

  private static void regenMagicPower() {
    if (Vars.state.isPlaying() && Vars.state.isCampaign()) {
      CultivationState.availableMagicPower = Math.min(
          CultivationState.playerMagicPower,
          CultivationState.availableMagicPower + MAGIC_REGEN_PER_SECOND * Time.delta / 60f);
    }
  }

  public static void savePower() {
    Core.settings.put("crystal.magicpower", CultivationState.playerMagicPower);
    Core.settings.put("crystal.availableMagicPower", CultivationState.availableMagicPower);
    powerDirty = true;
  }

  /**
   * 灵力落盘节流：原来每次灵力变动都 manualSave 同步写盘，杀一个单位就写一次，
   * 一波团战几十次磁盘 IO。现在只标脏，最多每 5 秒写一次，回主菜单时强制写。
   * 代价：崩溃/杀进程最多丢失 5 秒内的灵力。
   */
  private static void flushPowerIfNeeded(boolean force) {
    if (!powerDirty)
      return;
    long now = Time.millis();
    if (force || now - lastPowerFlushMs >= POWER_FLUSH_INTERVAL_MS) {
      Core.settings.manualSave();
      powerDirty = false;
      lastPowerFlushMs = now;
    }
  }

  private static void showDuJieConfirm(JingJie targetJingJie) {
    long currentTime = Time.millis();
    if (CultivationState.isInDuJie
        || targetJingJie == CultivationState.pendingDuJieJingJie
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
    CultivationState.isInDuJie = false;
    CultivationState.pendingDuJieJingJie = null;
    saveDuJieState();

    Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
  }

  /** 调试用全清硬重置：进度归零 + 清空历史境界/渡劫记录 + 锁全部功法 */
  public static void clear() {
    resetProgressToFan();
    // 原来的 clear() 清完 currentAvailableJingJie 没有补回 fan（resetProgressToFan 已统一处理），
    // 这里额外清空失败惩罚会保留的部分
    CultivationState.reachedJingJie.clear();
    CultivationState.reachedJingJie.add(JingJie.fan);
    CultivationState.completedDuJieJingJies.clear();
    // none 是"凡人"境界绑定的占位功法（GongFas.load() 每次都解锁它），不是玩家要习得的功法：
    // 把它一起锁掉，功法门槛会永远停在"凡人缺功法"，而凡人被 getMin() 豁免 →
    // 灵力再高、功法再全，境界也永远升不上去。这里跳过它，并在最后兜底解锁。
    for (var g : GongFa.gongFas.values()) {
      if (g != GongFas.none)
        g.lock();
    }
    // 兜底：正常情况下 none 一直是解锁的，这里只在它真被锁过时补回来（unlock() 会写盘+发事件，能省就省）
    if (!GongFas.none.unlocked())
      GongFas.none.unlock();
    saveReachedJingJie();
    saveCurrentAvailableJingJie();
    savePower();
    saveDuJieState();
    Events.fire(new JingJieRecalc(0f));
  }

  /**
   * 按枚举名解析境界，兼容旧的 ordinal 存档格式。
   * 注意：存档必须用 name 而不是 ordinal——在枚举中间插一个新境界会让所有
   * ordinal 存档静默错位（渡过的劫变成别人的劫），name 格式不受枚举变更影响。
   */
  private static @Nullable JingJie parseJingJie(String s) {
    if (isBlank(s))
      return null;
    s = s.trim();
    try {
      // 旧格式：ordinal 数字（JSON 读回可能是 "3.0" 这种浮点形式，用 Float 解析兼容）
      return JingJie.getByOrdinal((int) Float.parseFloat(s));
    } catch (NumberFormatException e) {
      // 新格式：枚举名
      try {
        return JingJie.valueOf(s);
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }

  private static String serializeJingJieList(Seq<JingJie> list) {
    if (list == null || list.isEmpty()) {
      return JingJie.getMin().name();
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(list.get(i).name());
    }
    return sb.toString();
  }

  private static Seq<JingJie> deserializeJingJieList(String str) {
    Seq<JingJie> result = new Seq<>();
    if (isBlank(str)) {
      result.add(JingJie.getMin());
      return result;
    }
    for (String token : str.split(",")) {
      JingJie jingJie = parseJingJie(token);
      if (jingJie != null && !result.contains(jingJie)) {
        result.add(jingJie);
      }
    }
    if (result.isEmpty())
      result.add(JingJie.getMin());
    result.sort(j -> j.amount);
    return result;
  }

  private static void loadReachedJingJie() {
    CultivationState.reachedJingJie.clear();
    try {
      String savedOrdinalStr = Core.settings.getString(SAVE_KEY_REACHED_JINGJIE, "");
      if (!isBlank(savedOrdinalStr)) {
        CultivationState.reachedJingJie = deserializeJingJieList(savedOrdinalStr);
        DLog.info("历史境界加载完成（序号格式）："
            + CultivationState.reachedJingJie.toString(",", j -> j.str));
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
            if (!CultivationState.reachedJingJie.contains(jingJie)) {
              CultivationState.reachedJingJie.add(jingJie);
            }
          } catch (Exception ex) {
            DLog.warn("旧存档兼容：无效的境界名称：" + trimName);
          }
        }
        CultivationState.reachedJingJie.sort(j -> j.amount);
        saveReachedJingJie();
        DLog.info("旧存档兼容完成，已转换为序号格式："
            + CultivationState.reachedJingJie.toString(",", j -> j.str));
        return;
      }
      CultivationState.reachedJingJie.add(JingJie.getMin());
      DLog.info("无历史境界存档，已初始化默认值");
    } catch (Exception e) {
      DLog.err("历史境界加载失败，已重置为默认值", e);
      CultivationState.reachedJingJie.clear();
      CultivationState.reachedJingJie.add(JingJie.getMin());
    }
  }

  private static void saveReachedJingJie() {
    try {
      if (CultivationState.reachedJingJie.isEmpty()) {
        Core.settings.remove(SAVE_KEY_REACHED_JINGJIE);
      } else {
        Core.settings.put(SAVE_KEY_REACHED_JINGJIE, serializeJingJieList(CultivationState.reachedJingJie));
      }
      Core.settings.remove(OLD_SAVE_KEY_REACHED_JINGJIE);
      Core.settings.manualSave();
      DLog.info("历史境界已保存：" + CultivationState.reachedJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("历史境界保存失败", e);
    }
  }

  private static void loadCurrentAvailableJingJie() {
    CultivationState.currentAvailableJingJie.clear();
    try {
      String savedOrdinalStr = Core.settings.getString(SAVE_KEY_AVAILABLE_JINGJIE, "");
      CultivationState.currentAvailableJingJie = deserializeJingJieList(savedOrdinalStr);
      DLog.info("当前可用境界加载完成："
          + CultivationState.currentAvailableJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("当前可用境界加载失败，已重置为默认值", e);
      CultivationState.currentAvailableJingJie.clear();
      CultivationState.currentAvailableJingJie.add(JingJie.getMin());
    }
  }

  private static void saveCurrentAvailableJingJie() {
    try {
      if (CultivationState.currentAvailableJingJie.isEmpty()) {
        Core.settings.remove(SAVE_KEY_AVAILABLE_JINGJIE);
      } else {
        Core.settings.put(SAVE_KEY_AVAILABLE_JINGJIE,
            serializeJingJieList(CultivationState.currentAvailableJingJie));
      }
      Core.settings.manualSave();
      DLog.info("当前可用境界已保存："
          + CultivationState.currentAvailableJingJie.toString(",", j -> j.str));
    } catch (Exception e) {
      DLog.err("当前可用境界保存失败", e);
    }
  }

  private static void updateCurrentAvailableJingJie() {
    CultivationState.currentAvailableJingJie.clear();
    JingJie currentJingJie = CultivationState.playerJingJie;
    boolean isNewRoad = CVars.chooseNewRoad;
    for (JingJie jingJie : JingJie.all) {
      if (jingJie.hasMirror && jingJie.newRoad != isNewRoad)
        continue;
      if (jingJie.amount <= currentJingJie.amount) {
        CultivationState.currentAvailableJingJie.add(jingJie);
      }
    }
    CultivationState.currentAvailableJingJie.sort(j -> j.amount);
    saveCurrentAvailableJingJie();
  }

  private static void updateReachedJingJie(JingJie newJingJie) {
    boolean hasNew = false;
    for (JingJie jingJie : JingJie.all) {
      if ((!jingJie.hasMirror || jingJie.newRoad == CVars.chooseNewRoad)
          && jingJie.amount <= newJingJie.amount
          && !CultivationState.reachedJingJie.contains(jingJie)) {
        CultivationState.reachedJingJie.add(jingJie);
        hasNew = true;
      }
    }
    if (hasNew) {
      CultivationState.reachedJingJie.sort(j -> j.amount);
      saveReachedJingJie();
      DLog.info("玩家解锁历史境界，当前已解锁："
          + CultivationState.reachedJingJie.toString(",", j -> j.str));
    }
  }

  /**
   * 修为面板（保留方法名供 HUD 按钮调用）。
   * v2：委托给卡片式布局的 XiuWeiDialog（灵力进度条、功法图标卡片、法宝/神武预留槽位）
   */
  public static void showMagic() {
    new XiuWeiDialog().show();
  }

  public static JingJie getNextJingJie() {
    JingJie[] t = CVars.chooseNewRoad ? JingJie.xinLu : JingJie.jiuLu;
    for (int i = 0; i < t.length; i++) {
      if (t[i] == CultivationState.playerJingJie) {
        return i >= t.length - 1 ? t[t.length - 1] : t[i + 1];
      }
    }
    // 当前境界不在本路线（切换新旧路后出现）：原来的实现 index 保持 0，
    // 会静默返回第二个境界（如 5 万灵力的玩家显示"下一境界：开窍"）。
    // 改为按灵力量在路线数组里找第一个更高的境界
    for (JingJie j : t) {
      if (j.amount > CultivationState.playerJingJie.amount)
        return j;
    }
    return t[t.length - 1];
  }

  public static boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }
}
