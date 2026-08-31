package crystal.core;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import crystal.CVars;
import crystal.magic.ShenWu;
import crystal.magic.ShenWu.Skill;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;

/**
 * 神武系统：管理玩家已获得的神武、当前装备、技能等级。
 * 解锁/升级操作由科技树对话框调用本类方法完成。
 */
public class ShenWuSystem {

  /** 玩家已获得的神武（科技树只显示这些） */
  public static Seq<ShenWu> obtained = new Seq<>();
  /** 当前装备的神武（同一时间只能装备一把） */
  public static ShenWu equipped;

  private static boolean initialized = false;

  private static final String SAVE_KEY_OBTAINED = "crystal.shenwu.obtained";
  private static final String SAVE_KEY_EQUIPPED = "crystal.shenwu.equipped";
  private static final String SAVE_KEY_SKILL_LV = "crystal.shenwu.skillLv";

  public static void init() {
    if (initialized)
      return;
    initialized = true;

    Events.on(ClientLoadEvent.class, e -> load());
    Events.on(StateChangeEvent.class, e -> {
      if (!Vars.state.isGame())
        equipped = null;
    });
    Events.on(WorldLoadEvent.class, e -> {
      if (equipped != null)
        equipped.update(0);
    });

    // 每帧更新装备神武的技能冷却
    Events.run(Trigger.update, () -> {
      if (equipped != null && Vars.state.isPlaying())
        equipped.update(Time.delta);
    });
  }

  // ==================== 获得 / 装备 ====================

  /** 获得神武（剧情/掉落/合成等调用） */
  public static void obtain(ShenWu sw) {
    if (sw == null || obtained.contains(sw))
      return;
    obtained.add(sw);
    // 第一把自动装备
    if (equipped == null)
      equip(sw);
    save();
  }

  /** 装备神武 */
  public static boolean equip(ShenWu sw) {
    if (sw == null || !obtained.contains(sw))
      return false;
    equipped = sw;
    save();
    return true;
  }

  /** 卸下神武 */
  public static void unequip() {
    equipped = null;
    save();
  }

  // ==================== 技能解锁 / 升级 ====================

  /**
   * 解锁技能（科技树调用）。
   * 前置条件（Boolp）由调用方（科技树）检查，本方法只改状态。
   */
  public static boolean unlockSkill(Skill skill) {
    if (skill == null || skill.unlocked())
      return false;
    skill.level = 1;
    save();
    return true;
  }

  /**
   * 升级技能（科技树调用）。
   * 前置条件由调用方检查。
   */
  public static boolean upgradeSkill(Skill skill) {
    if (skill == null || !skill.unlocked() || skill.maxed())
      return false;
    skill.level++;
    skill.onLevelUp(skill.level);
    save();
    return true;
  }

  // ==================== 存档 / 读档 ====================

  public static void save() {
    // 存已获得神武名列表
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < obtained.size; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(obtained.get(i).name);
    }
    Core.settings.put(SAVE_KEY_OBTAINED, sb.toString());

    // 存当前装备
    if (equipped != null)
      Core.settings.put(SAVE_KEY_EQUIPPED, equipped.name);
    else
      Core.settings.remove(SAVE_KEY_EQUIPPED);

    // 存所有技能等级 "神武名:技能名=等级,..."
    StringBuilder lv = new StringBuilder();
    int c = 0;
    for (ShenWu sw : ShenWu.list) {
      for (Skill sk : sw.skills) {
        if (sk.level > 0) {
          if (c++ > 0)
            lv.append(",");
          lv.append(sw.name).append(":").append(sk.name).append("=").append(sk.level);
        }
      }
    }
    if (c > 0)
      Core.settings.put(SAVE_KEY_SKILL_LV, lv.toString());
    else
      Core.settings.remove(SAVE_KEY_SKILL_LV);

    Core.settings.manualSave();
  }

  public static void load() {
    obtained.clear();
    equipped = null;

    // 读已获得
    String obs = Core.settings.getString(SAVE_KEY_OBTAINED, "");
    if (!obs.isEmpty()) {
      for (String s : obs.split(",")) {
        ShenWu sw = ShenWu.all.get(s.trim());
        if (sw != null)
          obtained.add(sw);
      }
    }

    // 读装备
    String eq = Core.settings.getString(SAVE_KEY_EQUIPPED, "");
    if (!eq.isEmpty()) {
      ShenWu sw = ShenWu.all.get(eq.trim());
      if (sw != null && obtained.contains(sw))
        equipped = sw;
    }

    // 读技能等级
    String lvs = Core.settings.getString(SAVE_KEY_SKILL_LV, "");
    if (!lvs.isEmpty()) {
      for (String entry : lvs.split(",")) {
        String[] parts = entry.split("=");
        if (parts.length != 2)
          continue;
        String[] names = parts[0].split(":");
        if (names.length != 2)
          continue;
        int level = Integer.parseInt(parts[1].trim());
        ShenWu sw = ShenWu.all.get(names[0].trim());
        if (sw == null)
          continue;
        for (Skill sk : sw.skills) {
          if (sk.name.equals(names[1].trim())) {
            sk.level = level;
            break;
          }
        }
      }
    }
  }
}
