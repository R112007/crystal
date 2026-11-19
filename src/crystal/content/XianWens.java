package crystal.content;

import crystal.magic.xianWen.CarryItems;

/**
 * 仙纹内容入口。
 */
public final class XianWens {
  public static CarryItems carryItems;

  public static void load() {
    carryItems = new CarryItems();
  }
}
