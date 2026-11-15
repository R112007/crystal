package crystal.content;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import crystal.game.YiCarryLogic;
import crystal.magic.XianWen;
import mindustry.type.ItemStack;

/**
 * 仙纹内容入口。
 *
 * <p>首个仙纹 {@code carryItems} 只负责请求搬运；实际的世界动画、投箱和
 * 玩家接近后的物流由 {@link YiCarryLogic} 与 YiCarry 实体处理。</p>
 */
public final class XianWens {
  public static CarryItems carryItems;

  private XianWens() {
  }

  public static void load() {
    if (carryItems != null) {
      return;
    }
    carryItems = new CarryItems();
  }

  public static final class CarryItems extends XianWen {
    CarryItems() {
      super("carryItems", "安亦雨·搬运");
      loadCost = 1;
      unlocked = false;
    }

    /** 请求安亦雨把一组物品送到指定坐标。 */
    public void carryTo(float x, float y, ItemStack[] items) {
      YiCarryLogic.carryItems(x, y, items);
    }

    @Override
    public TextureRegion uiIcon() {
      TextureRegion yiIcon = Core.atlas == null ? null : Core.atlas.find("crystal-yi-normal");
      return yiIcon != null && yiIcon.found() ? yiIcon : super.uiIcon();
    }

    @Override
    public boolean usingFallbackIcon() {
      TextureRegion yiIcon = Core.atlas == null ? null : Core.atlas.find("crystal-yi-normal");
      return yiIcon == null || !yiIcon.found();
    }
  }
}
