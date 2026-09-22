package crystal;

import crystal.core.UI;
import crystal.util.PlotBundle;

public class CVars {
  public static int maxVersion = 160;
  public static boolean debug = false;
  public static boolean chooseNewRoad = false;
  public static UI cui = new UI();
  public static String modName = "crystal";
  public static String[] threats = new String[] { "low", "medium", "high", "extreme", "eradication", "lianyu", "diyu",
      "school", "daoshu" };
  public static PlotBundle plot;
  public static String playerName;

  // 修仙运行时状态已迁移至 crystal.core.CultivationState
  // 保留此注释方便 grep 定位
}
