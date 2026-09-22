package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.type.GongFa;
import mindustry.game.EventType.ClientLoadEvent;

/**
 * 修仙系统运行时状态集中管理。
 * 所有字段均为玩家维度，需要持久化。不要放配置常量。
 *
 * 从 CVars 迁移而来，目的是将修仙状态与通用配置分离，
 * 避免"万能静态类"反模式，降低各子系统耦合。
 *
 * 存档 Key 保持与 PlayerXiuWeiSystem 旧版一致，确保老档兼容。
 */
public class CultivationState {

  // ==================== 修为与境界 ====================
  public static XiuWei playerXiuWei = XiuWei.yong;
  public static JingJie playerJingJie = JingJie.fan;

  // ==================== 灵力 ====================
  /** 玩家总灵力上限（修为面板显示这个） */
  public static float playerMagicPower;
  /** 可动用灵力（神武/技能/法宝消耗这个，战斗中自然恢复到 playerMagicPower 上限） */
  public static float availableMagicPower;

  // ==================== 渡劫 ====================
  /** 当前待渡劫的目标境界 */
  public static @Nullable JingJie pendingDuJieJingJie;
  /** 是否处于渡劫中状态 */
  public static boolean isInDuJie = false;

  // ==================== 进度与解锁 ====================
  /** 已解锁持有的功法集合 */
  public static ObjectSet<GongFa> gongfaHave = new ObjectSet<>();
  /** 历史到达过的所有境界 */
  public static Seq<JingJie> reachedJingJie = new Seq<>();
  /** 当前路线下可用的全部境界 */
  public static Seq<JingJie> currentAvailableJingJie = new Seq<>();
  /** 已成功渡劫的境界（永久记录，重启不丢失） */
  public static ObjectSet<JingJie> completedDuJieJingJies = new ObjectSet<>();

  // ==================== 神武（占位） ====================
  /** 神武占位列表：神武系统实装后往里放，修为面板的神武槽位从这里读取展示 */
  public static Seq<String> shenwuHave = new Seq<>();

  // ==================== 存档 Key（与 PlayerXiuWeiSystem 旧版保持一致） ====================
  public static final String SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie_ordinal";
  public static final String SAVE_KEY_AVAILABLE_JINGJIE = "crystal.availableJingJie_ordinal";
  public static final String SAVE_KEY_COMPLETED_DUJIE = "crystal.completedDuJieJingJies";
  public static final String SAVE_KEY_PENDING_DUJIE = "crystal.pendingDuJieJingJieOrdinal";
  public static final String SAVE_KEY_DUJIE_KILLS = "crystal.duJieKillCount";
  public static final String SAVE_KEY_MAGIC_POWER = "crystal.magicpower";
  public static final String SAVE_KEY_AVAILABLE_POWER = "crystal.availableMagicPower";
  public static final String SAVE_KEY_CHOOSE_NEW_ROAD = "crystal.chooseNewRoad";
  /** 旧版 reachedJingJie key，保留用于兼容旧档 */
  public static final String OLD_SAVE_KEY_REACHED_JINGJIE = "crystal.reachedJingJie";

  static {
    Events.on(ClientLoadEvent.class, e -> load());
  }

  /** 保存全部修仙状态到 Core.settings（供外部一键调用） */
  public static void save() {
    try {
      Core.settings.put(SAVE_KEY_MAGIC_POWER, playerMagicPower);
      Core.settings.put(SAVE_KEY_AVAILABLE_POWER, availableMagicPower);
      Core.settings.manualSave();
    } catch (Exception ex) {
      // 静默失败
    }
  }

  /** 从 Core.settings 恢复灵力等基础字段（境界/渡劫由 PlayerXiuWeiSystem 精细处理） */
  public static void load() {
    try {
      playerMagicPower = Math.max(0, Core.settings.getFloat(SAVE_KEY_MAGIC_POWER, 0f));
      availableMagicPower = Math.max(0, Core.settings.getFloat(SAVE_KEY_AVAILABLE_POWER, 0f));
    } catch (Exception ex) {
      playerMagicPower = 0f;
      availableMagicPower = 0f;
    }
  }
}
