package crystal.content;

import crystal.magic.ShenWu;
import crystal.magic.shenWus.WuHua;

/** Registration point for all obtainable ShenWu and their skills. */
public final class ShenWus {
  public static ShenWu wuHua;

  private ShenWus() {
  }

  public static void load() {
    if (wuHua != null) {
      return;
    }
    wuHua = new WuHua();
  }

  public static ShenWu wuHua() {
    if (wuHua == null) {
      load();
    }
    return wuHua;
  }
}
