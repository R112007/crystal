package crystal;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.core.UI;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.type.GongFa;
import crystal.util.PlotBundle;

public class CVars {
  public static int maxVersion = 159;
  public static boolean debug = false;
  public static boolean chooseNewRoad = false;
  public static UI cui = new UI();
  public static String modName = "crystal";
  public static String[] threats = new String[] { "low", "medium", "high", "extreme", "eradication", "lianyu", "diyu",
      "school", "daoshu" };
  public static PlotBundle plot;
  public static String playerName;
  public static ObjectSet<GongFa> gongfaHave = new ObjectSet<>();

  /** 历史到达过的所有境界 */
  public static Seq<JingJie> reachedJingJie = new Seq<>();
  /** 当前路线下可用的全部境界 */
  public static Seq<JingJie> currentAvailableJingJie = new Seq<>();
  /** 已成功渡劫的境界（永久记录，重启不丢失） */
  public static ObjectSet<JingJie> completedDuJieJingJies = new ObjectSet<>();

  public static XiuWei playerXiuWei = XiuWei.yong;
  public static JingJie playerJingJie = JingJie.fan;
  public static float playerMagicPower;
  /** 可动用灵力（神武/技能消耗这个，战斗中自然恢复到 playerMagicPower 上限） */
  public static float availableMagicPower;

  /** 当前待渡劫的目标境界 */
  public static @Nullable JingJie pendingDuJieJingJie;
  /** 是否处于渡劫中状态 */
  public static boolean isInDuJie = false;

  /** 神武（占位列表：神武系统实装后往里放，修为面板的神武槽位从这里读取展示） */
  public static Seq<String> shenwuHave = new Seq<>();
  // 法宝已实装：持有数由 crystal.core.FaBaoSystem.counts 管理，不再用占位列表
}
