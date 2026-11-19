package crystal.magic.xianWen;

import crystal.game.YiCarryLogic;
import crystal.magic.XianWen;
import mindustry.type.ItemStack;

public class CarryItems extends XianWen {
  public CarryItems() {
    super("carryItems", "安亦雨·搬运");
    loadCost = 1;
    unlocked = false;
  }

  /** 请求安亦雨把一组物品送到指定坐标。 */
  public void carryTo(float x, float y, ItemStack[] items) {
    YiCarryLogic.carryItems(x, y, items);
  }

}
