package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
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
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static crystal.content.GongFas.*;

public class PlayerXiuWeiSystem {
  public static float height = 0;
  // 新路专属功法集合
  private static final ObjectSet<GongFa> NEW_ROAD_GONGFAS = ObjectSet.with(guHuang, guZun);
  // 旧路镜像功法集合
  private static final ObjectSet<GongFa> OLD_ROAD_MIRROR_GONGFAS = ObjectSet.with(xinHuang, xinZun);
  // 提示冷却时间（毫秒），3秒内不会重复弹出功法不足提示，可自行调整
  private static final long TOAST_COOLDOWN_MS = 3000;
  // 记录上次弹出提示的时间戳
  private static long lastToastTimestamp = 0;

  public static void addbutton(float amount, float h) {
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
      // 加载新路开关状态
      CVars.chooseNewRoad = Core.settings.getBool("crystal.chooseNewRoad", false);
      // 新增：启动时加载玩家历史到达过的境界
      loadReachedJingJie();
      CVars.playerMagicPower = Core.settings.getFloat("crystal.magicpower", 0f);
      CVars.playerMagicPower = Math.max(0, CVars.playerMagicPower);
      DLog.info("playerMagicPower" + CVars.playerMagicPower);
      Events.fire(new JingJieChange(CVars.playerMagicPower));
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
    });
    Events.on(JingJieChange.class, e -> {
      float currentMagic = e.amount;
      JingJie targetJingJie = null;
      boolean isNewRoad = CVars.chooseNewRoad;

      // ========== 第一步：匹配当前灵力对应的最高境界，严格路线隔离 ==========
      if (isNewRoad) {
        // 新路模式：仅匹配新路专属功法的境界，完全忽略旧路
        for (JingJie jingJie : JingJie.all) {
          if (jingJie.newRoad
              && NEW_ROAD_GONGFAS.contains(jingJie.gongFa)
              && currentMagic >= jingJie.amount) {
            targetJingJie = jingJie;
          }
        }
      } else {
        // 旧路模式：仅匹配非新路的旧路境界，完全忽略新路
        for (JingJie jingJie : JingJie.all) {
          if (!jingJie.newRoad && currentMagic >= jingJie.amount) {
            targetJingJie = jingJie;
          }
        }
      }

      // 兜底：灵力为0/无匹配时，默认最低凡境
      if (targetJingJie == null) {
        targetJingJie = JingJie.fan;
      }

      // ========== 第二步：功法校验与境界更新 ==========
      if (CVars.gongfaHave.contains(targetJingJie.gongFa)) {
        // 拥有对应功法：正常更新境界
        boolean isLevelUp = targetJingJie.amount > CVars.playerJingJie.amount;
        CVars.playerJingJie = targetJingJie;

        // 自动更新两个核心Seq
        updateCurrentAvailableJingJie();
        // 仅突破新境界时，更新历史到达记录
        if (isLevelUp) {
          updateReachedJingJie(targetJingJie);
        }

        // 触发修为变更事件
        Events.fire(new XiuWeiChange(CVars.playerJingJie));
        DLog.info("当前境界更新为：" + targetJingJie.str);

      } else {
        // 无对应功法：强制拦截，灵力回退
        CVars.playerMagicPower = Math.max(0, targetJingJie.amount - 1);
        savePower();

        long currentTime = Time.millis();
        if (currentTime - lastToastTimestamp >= TOAST_COOLDOWN_MS) {
          Events.fire(new GongFaBuQuanEvent(targetJingJie, targetJingJie.gongFa));
          // 更新上次弹出时间戳
          lastToastTimestamp = currentTime;
        }
      }
    });
    Events.on(XiuWeiChange.class, e -> {
      if (JingJie.fajing.contains(e.jingJie)) {
        CVars.playerXiuWei = XiuWei.fan;
      } else if (JingJie.shengjing.contains(e.jingJie)) {
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
      }
    });
  }

  public static void savePower() {
    Core.settings.put("crystal.magicpower", CVars.playerMagicPower);
    Core.settings.manualSave();
  }

  public static void setChooseNewRoad(boolean enable) {
    if (CVars.chooseNewRoad == enable)
      return;
    CVars.chooseNewRoad = enable;
    Core.settings.put("crystal.chooseNewRoad", enable);
    Core.settings.manualSave();
    Events.fire(new JingJieChange(CVars.playerMagicPower));
  }

  public static void clear() {
    CVars.playerMagicPower = 0;
    CVars.playerXiuWei = XiuWei.yong;
    CVars.playerJingJie = JingJie.fan;
    CVars.reachedJingJie.clear();
    CVars.currentAvailableJingJie.clear();
    saveReachedJingJie();
    savePower();
    Events.fire(new JingJieChange(0f));
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
      int i = 0;
      for (GongFa g : CVars.gongfaHave) {
        t.add(g.localizedName);
        i++;
        if (i == 5) {
          t.row();
          i = 0;
        }
      }
      t.row();
      t.add(Core.bundle.get("reachedjingjie"));
      i = 0;
      for (JingJie j : CVars.reachedJingJie) {
        t.add(j.str);
        i++;
        if (i == 5) {
          t.row();
          i = 0;
        }
      }
      t.row();
      t.add(Core.bundle.get("currentavailablejingjie"));
      i = 0;
      for (JingJie j : CVars.currentAvailableJingJie) {
        t.add(j.str);
        i++;
        if (i == 5) {
          t.row();
          i = 0;
        }
      }
    });
    xiuweiDialog.addCloseButton();
    xiuweiDialog.show();
  }

  public static boolean isBlank(String str) {
    return str == null || str.matches("//s*");
  }

  private static void loadReachedJingJie() {
    CVars.reachedJingJie.clear();
    String savedNames = Core.settings.getString("crystal.reachedJingJie", "");
    DLog.info("savedNames" + savedNames);
    if (isBlank(savedNames))
      return;

    for (String name : savedNames.split(",")) {
      try {
        JingJie jingJie = JingJie.valueOf(name.trim());
        if (!CVars.reachedJingJie.contains(jingJie)) {
          CVars.reachedJingJie.add(jingJie);
        }
      } catch (Exception ex) {
        DLog.warn("加载历史境界失败，无效的境界名称：" + name);
      }
    }
    // 按灵力从小到大自动排序
    CVars.reachedJingJie.sort(j -> j.amount);
  }

  /** 保存玩家历史到达过的境界到本地，解锁新境界时自动调用 */
  private static void saveReachedJingJie() {
    if (CVars.reachedJingJie.isEmpty()) {
      Core.settings.remove("crystal.reachedJingJie");
    } else {
      // 把境界枚举名称序列化为逗号分隔的字符串
      String names = CVars.reachedJingJie.toString(",", j -> j.name());
      Core.settings.put("crystal.reachedJingJie", names);
    }
    Core.settings.manualSave();
  }

  // ========== 新增：境界列表更新方法 ==========
  /** 更新【当前可用境界列表】，每次境界变化/路线切换时自动调用 */
  private static void updateCurrentAvailableJingJie() {
    CVars.currentAvailableJingJie.clear();
    JingJie currentJingJie = CVars.playerJingJie;
    boolean isNewRoad = CVars.chooseNewRoad;

    // 严格匹配当前路线，筛选出灵力≤当前境界的所有境界
    for (JingJie jingJie : JingJie.all) {
      // 路线隔离：新路只加新路境界，旧路只加旧路境界
      if (isNewRoad != jingJie.newRoad)
        continue;
      // 只保留当前境界及以下的
      if (jingJie.amount <= currentJingJie.amount) {
        CVars.currentAvailableJingJie.add(jingJie);
      }
    }
    // 按灵力从小到大排序
    CVars.currentAvailableJingJie.sort(j -> j.amount);
  }

  /** 更新【历史到达过的境界】，仅突破新境界时调用 */
  private static void updateReachedJingJie(JingJie newJingJie) {
    // 仅当该境界从未到达过时，才加入历史记录
    if (!CVars.reachedJingJie.contains(newJingJie)) {
      CVars.reachedJingJie.add(newJingJie);
      CVars.reachedJingJie.sort(j -> j.amount);
      // 自动保存到本地
      saveReachedJingJie();
      DLog.info("玩家解锁新历史境界：" + newJingJie.str);
    }
  }
}
