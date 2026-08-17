package crystal;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.core.UI;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.type.GongFa;

public class CVars {
  public static int maxVersion = 159;
  public static boolean debug = true;
  public static boolean chooseNewRoad = false;
  public static UI cui = new UI();
  public static String modName = "crystal";
  public static String[] threats = new String[] { "low", "medium", "high", "extreme", "eradication", "lianyu", "diyu",
      "school", "daoshu" };
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

  /** 当前待渡劫的目标境界 */
  public static @Nullable JingJie pendingDuJieJingJie;
  /** 是否处于渡劫中状态 */
  public static boolean isInDuJie = false;
}
