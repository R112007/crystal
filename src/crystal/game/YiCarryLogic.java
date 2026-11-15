package crystal.game;

import arc.Events;
import crystal.gen.YiCarry;
import crystal.game.CEventType.*;
import mindustry.type.ItemStack;

public class YiCarryLogic {
    private static boolean initialized;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Events.on(YiCarryItemsEvent.class, YiCarryLogic::spawn);
    }

    public static void carryItems(float x, float y, ItemStack[] items) {
        Events.fire(new YiCarryItemsEvent(x, y, items));
    }

    private static void spawn(YiCarryItemsEvent event) {
        if (event == null || !Float.isFinite(event.x) || !Float.isFinite(event.y)
                || event.items == null || event.items.length == 0) {
            return;
        }

        YiCarry carry = YiCarry.create();
        carry.setup(event.x, event.y, event.items);
        if (carry.items.length == 0) {
            return;
        }
        carry.add();
    }
}
