package crystal.aviation;

import arc.math.Mathf;
import arc.math.WindowedMean;
import arc.struct.ObjectMap;
import arc.util.Nullable;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.Sector;

/**
 * 记录某个星球区块与卫星之间的物资/液体发射与注入统计。
 *
 * 仿照原版 SectorInfo 的 ExportStat 机制：实际发射/注入时累加 counter，
 * 每秒把 counter 移入滑动窗口并计算 mean，后台未加载区块时按 mean * 秒数
 * 继续向目标卫星（或目标区块）累加资源。
 */
public class SatelliteSectorInfo {

    /** 滑动窗口大小（样本数） */
    private static final int valueWindow = 60;

    public String planetName;
    public int sectorId;

    /**
     * 从本区块发射到卫星的物资统计：卫星ID -> 物品 -> ExportStat。
     * 由于 GroundLaunchPad 可接收任意物品并分别指定目标卫星，按卫星分组。
     */
    public ObjectMap<Integer, ObjectMap<Item, ExportStat>> satelliteItemExports = new ObjectMap<>();
    /**
     * 从本区块发射到卫星的液体统计：卫星ID -> 液体 -> ExportStat。
     */
    public ObjectMap<Integer, ObjectMap<Liquid, ExportStat>> satelliteLiquidExports = new ObjectMap<>();

    /**
     * 卫星向本区块注入的物资统计：卫星ID -> 物品 -> ExportStat。
     */
    public ObjectMap<Integer, ObjectMap<Item, ExportStat>> satelliteItemImports = new ObjectMap<>();
    /**
     * 卫星向本区块注入的液体统计：卫星ID -> 液体 -> ExportStat。
     */
    public ObjectMap<Integer, ObjectMap<Liquid, ExportStat>> satelliteLiquidImports = new ObjectMap<>();

    public SatelliteSectorInfo() {
    }

    public SatelliteSectorInfo(Sector sector) {
        this.planetName = sector.planet.name;
        this.sectorId = sector.id;
    }

    /** 获取当前区块；若星球已卸载则可能返回 null。 */
    public @Nullable Sector getSector() {
        if (planetName == null)
            return null;
        mindustry.type.Planet planet = mindustry.Vars.content.planet(planetName);
        if (planet == null || planet.sectors == null || sectorId < 0 || sectorId >= planet.sectors.size)
            return null;
        return planet.sectors.get(sectorId);
    }

    /** 记录一次向指定卫星发射的物品。 */
    public void handleItemLaunch(int satelliteId, Item item, int amount) {
        if (item == null || amount <= 0)
            return;
        satelliteItemExports.get(satelliteId, ObjectMap::new).get(item, ExportStat::new).counter += amount;
    }

    /** 记录一次向指定卫星发射的液体。 */
    public void handleLiquidLaunch(int satelliteId, Liquid liquid, float amount) {
        if (liquid == null || amount <= 0.001f)
            return;
        satelliteLiquidExports.get(satelliteId, ObjectMap::new).get(liquid, ExportStat::new).counter += amount;
    }

    /** 记录一次卫星向本区块注入的物品。 */
    public void handleItemImport(int satelliteId, Item item, int amount) {
        if (item == null || amount <= 0)
            return;
        satelliteItemImports.get(satelliteId, ObjectMap::new).get(item, ExportStat::new).counter += amount;
    }

    /** 记录一次卫星向本区块注入的液体。 */
    public void handleLiquidImport(int satelliteId, Liquid liquid, float amount) {
        if (liquid == null || amount <= 0.001f)
            return;
        satelliteLiquidImports.get(satelliteId, ObjectMap::new).get(liquid, ExportStat::new).counter += amount;
    }

    /** 把当前 counter 移入滑动窗口并重新计算 mean。 */
    public void refreshStats() {
        satelliteItemExports.each((satId, map) -> refreshMap(map));
        satelliteLiquidExports.each((satId, map) -> refreshMap(map));
        satelliteItemImports.each((satId, map) -> refreshMap(map));
        satelliteLiquidImports.each((satId, map) -> refreshMap(map));
    }

    private <T> void refreshMap(ObjectMap<T, ExportStat> map) {
        map.each((key, stat) -> {
            stat.updateMean();
        });
    }

    /** 是否有任何非零的卫星发射/注入统计。 */
    public boolean hasAnyStats() {
        return anyMap2(satelliteItemExports) || anyMap2(satelliteLiquidExports)
                || anyMap2(satelliteItemImports) || anyMap2(satelliteLiquidImports);
    }

    /** 是否有任何非零的卫星向本区块注入统计（用于星球面板顶部提示）。 */
    public boolean hasAnyImports() {
        return anyMap2(satelliteItemImports) || anyMap2(satelliteLiquidImports);
    }

    private <T> boolean anyMap1(ObjectMap<T, ExportStat> map) {
        if (map == null || map.size == 0)
            return false;
        boolean[] result = { false };
        map.each((k, s) -> {
            if (s.mean > 0.001f || s.counter > 0.001f)
                result[0] = true;
        });
        return result[0];
    }

    private <K> boolean anyMap2(ObjectMap<Integer, ObjectMap<K, ExportStat>> nested) {
        if (nested == null || nested.size == 0)
            return false;
        boolean[] result = { false };
        nested.each((id, map) -> {
            if (anyMap1(map))
                result[0] = true;
        });
        return result[0];
    }

    /** 单个物资/液体的统计单元，与原版 SectorInfo.ExportStat 结构一致。 */
    public static class ExportStat {
        public transient float counter;
        public transient WindowedMean means = new WindowedMean(valueWindow);
        public transient boolean loaded;

        /** 平均每秒数量 */
        public float mean;
        /** 最近一次实际数量（用于显示） */
        public int amount;

        public void updateMean() {
            if (means == null) {
                means = new WindowedMean(valueWindow);
            }
            if (!loaded) {
                means.fill(mean);
                loaded = true;
            }
            means.add(Math.max(counter, 0));
            amount = (int) Math.max(counter, 0);
            counter = 0;
            mean = means.rawMean();
        }

        @Override
        public String toString() {
            return Mathf.round(mean * 60) + "/min";
        }
    }
}
