package crystal.aviation;

import arc.struct.Seq;
import arc.util.Nullable;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.Planet;
import mindustry.type.UnitType;
import mindustry.world.Block;

import static mindustry.Vars.*;

/**
 * 卫星内容过滤工具。
 * 当玩家位于某颗卫星上时，物品/液体/单位/建筑等应与卫星绑定的星球保持一致。
 */
public class SatelliteContentFilter {

    /** 获取当前应作为过滤依据的星球：优先卫星绑定星球，不在卫星时返回 null。 */
    public static @Nullable Planet currentPlanet() {
        if (SatelliteManager.currentSatelliteId >= 0) {
            Satellite sat = SatelliteManager.get(SatelliteManager.currentSatelliteId);
            if (sat != null && sat.planet != null)
                return sat.planet;
        }
        return null;
    }

    /** 当前是否处于卫星地图中。 */
    public static boolean onSatellite() {
        return currentPlanet() != null;
    }

    /** 物品是否允许出现在当前卫星（不在卫星时永远返回 true）。 */
    public static boolean allowed(Item item) {
        Planet p = currentPlanet();
        if (p == null)
            return true;
        // 同步原版 ItemSelection.buildTable 的过滤规则：解锁、当前星球、非隐藏
        return item.unlockedNow() && item.isOnPlanet(p) && !item.isHidden();
    }

    /** 液体是否允许出现在当前卫星（不在卫星时永远返回 true）。 */
    public static boolean allowed(Liquid liquid) {
        Planet p = currentPlanet();
        if (p == null)
            return true;
        return liquid.unlockedNow() && liquid.isOnPlanet(p) && !liquid.isHidden();
    }

    /** 单位是否允许出现在当前卫星（不在卫星时永远返回 true）。 */
    public static boolean allowed(UnitType unit) {
        Planet p = currentPlanet();
        if (p == null)
            return true;
        return unit.unlockedNow() && unit.isOnPlanet(p) && !unit.isHidden();
    }

    /** 建筑是否允许出现在当前卫星（不在卫星时永远返回 true）。 */
    public static boolean allowed(Block block) {
        Planet p = currentPlanet();
        if (p == null)
            return true;

        if (!block.unlockedNow() || block.isHidden())
            return false;

        // 优先使用 UnlockableContent 的 shownPlanets
        if (block.isOnPlanet(p))
            return true;

        // 兼容旧逻辑：通过科技树节点判断建筑所属星球
        if (block.techNode != null) {
            if (block.techNode.planet == p)
                return true;
            if (block.techNode.rootNode != null && block.techNode.rootNode.planet == p)
                return true;
        }

        return false;
    }

    /** 返回当前卫星允许显示的物品列表（不在卫星时返回全部）。 */
    public static Seq<Item> items() {
        return content.items().select(SatelliteContentFilter::allowed);
    }

    /** 返回当前卫星允许显示的液体列表（不在卫星时返回全部）。 */
    public static Seq<Liquid> liquids() {
        return content.liquids().select(SatelliteContentFilter::allowed);
    }

    /** 返回当前卫星允许显示的单位列表（不在卫星时返回全部）。 */
    public static Seq<UnitType> units() {
        return content.units().select(SatelliteContentFilter::allowed);
    }

    /** 返回当前卫星允许显示的建筑列表（不在卫星时返回全部）。 */
    public static Seq<Block> blocks() {
        return content.blocks().select(SatelliteContentFilter::allowed);
    }

    private static final mindustry.type.Item[] itemArray = new mindustry.type.Item[0];
    private static final mindustry.type.Liquid[] liquidArray = new mindustry.type.Liquid[0];
    private static final mindustry.type.UnitType[] unitArray = new mindustry.type.UnitType[0];

    /** 返回当前卫星允许显示的物品数组（供 ItemSelection.buildTable 等使用）。 */
    public static Item[] itemArray() {
        return items().toArray(Item.class);
    }

    /** 返回当前卫星允许显示的液体数组。 */
    public static Liquid[] liquidArray() {
        return liquids().toArray(Liquid.class);
    }

    /** 返回当前卫星允许显示的单位数组。 */
    public static UnitType[] unitArray() {
        return units().toArray(UnitType.class);
    }
}
