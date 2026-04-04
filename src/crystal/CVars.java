package crystal;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Timer;
import crystal.core.UI;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.GenerateBuild;
import crystal.game.CEventType.MagicPowerChange;
import crystal.game.CEventType.XiuWeiChange;
import crystal.type.GongFa;
import mindustry.core.GameState;
import mindustry.game.EventType.StateChangeEvent;

public class CVars {
  public static boolean debug = true;
  public static boolean chooseNewRoad = false;
  public static UI cui = new UI();
  public static String modName = "crystal";
  public static String[] threats = new String[] { "low", "medium", "high", "extreme", "eradication", "lianyu", "diyu",
      "school", "daoshu" };

  public static void register() {
    Events.on(GenerateBuild.class, e -> {
      Timer.schedule(() -> {
        e.tile.setBlock(e.block, e.team, e.rotation);
      }, 0.2f);
    });
  }

  public static ObjectSet<GongFa> gongfaHave = new ObjectSet<>();
  /**
   * 【Seq1】历史到达过的所有境界
   * 特性：永久记录，境界倒退不会删除，重启游戏不丢失，按灵力从小到大自动排序
   * 示例：玩家到过开阳境，就算掉回凡境，列表里依然保留开阳境记录
   */
  public static Seq<JingJie> reachedJingJie = new Seq<>();
  /**
   * 【Seq2】当前路线下，从最低境界到当前境界的所有可用境界
   * 特性：动态更新，境界升级自动追加，境界倒退自动缩减，严格匹配当前新路/旧路路线
   * 示例：当前是开阳境（新路），列表就是新路从凡境到开阳境的全部境界；掉回凡境就只剩凡境
   */
  public static Seq<JingJie> currentAvailableJingJie = new Seq<>();

  public static XiuWei playerXiuWei = XiuWei.yong;
  public static JingJie playerJingJie = JingJie.fan;
  public static float playerMagicPower;
}
