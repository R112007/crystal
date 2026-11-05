package crystal.content;

import crystal.magic.Scroll;
import crystal.magic.ShenWu;

/** Scroll content. Each scroll owns exactly one skill unlock target. */
public final class Scrolls {
  public static Scroll coreUnit, wuHuaDash, wuHuaLianZhan, wuHuaHuaWu;

  private Scrolls() {
  }

  public static void load() {
    if (coreUnit != null) {
      return;
    }

    ShenWu wuHua = ShenWus.wuHua();
    coreUnit = new Scroll("coreUnit", "召唤卷轴", Skills.coreUnit(), "scroll-coreunit");
    wuHuaDash = new Scroll("wuHua-dash", "技能卷轴", wuHua.skills.get(0));
    wuHuaLianZhan = new Scroll("wuHua-lianZhan", "技能卷轴", wuHua.skills.get(1));
    wuHuaHuaWu = new Scroll("wuHua-huaWu", "技能卷轴", wuHua.skills.get(2));
  }
}
