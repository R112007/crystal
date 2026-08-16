package crystal.aviation;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.serialization.Json;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.Liquid;
import mindustry.type.Planet;
import mindustry.type.Sector;

/**
 * 管理所有区块的卫星发射/注入统计，并负责后台模拟。
 *
 * 核心行为：
 * 1. 当玩家处于带 GroundLaunchPad 的区块时，真实发射会累加对应 SatelliteSectorInfo 的 counter。
 * 2. 每秒把 counter 移入滑动窗口得到 mean。
 * 3. 当区块未加载时，按 mean * 经过秒数 把物资/液体追加到目标卫星（或目标区块）。
 */
public class SatelliteSectorInfoManager {

    private static final String settingsKey = "crystal-aviation-sector-info-v2";
    /** 旧版 key，读取后删除 */
    private static final String legacySettingsPrefix = "crystal-aviation-sector-info-";
    private static final String legacyJsonKeysKey = "crystal-aviation-sector-info-keys";

    /** sector key -> info */
    private static final ObjectMap<String, SatelliteSectorInfo> infos = new ObjectMap<>();

    /** 统计刷新计时器（tick）：仅用于把 counter 移入滑动窗口，让 UI 数据平滑更新。 */
    private static float refreshTimer = 0f;
    private static final float refreshInterval = 60f; // 1 秒
    /** 后台模拟间隔（tick）：每 30 秒触发一次，与 Satellite.injectInterval 保持一致。 */
    private static final float backgroundInterval = 30f * 60f; // 30 秒
    /** 后台模拟计时器（tick）：使用 Time.delta，支持时间控制模组。 */
    private static float backgroundTimer = 0f;
    /** 是否有未保存的统计变更。 */
    private static boolean dirty = false;
    /** 自动保存计时器（tick）。 */
    private static float saveTimer = 0f;
    private static final float saveInterval = 5f * 60f; // 5 秒

    public static void init() {
        load();
        // 重置运行时计时器，避免旧存档/跨会话的静态变量导致模拟节奏异常
        refreshTimer = 0f;
        saveTimer = 0f;
        backgroundTimer = 0f;
        // 后台模拟统一由 30 秒计时器触发，避免 TurnEvent 每秒触发导致增量与显示速率不一致
        // 游戏主动写入存档时，确保统计也一并持久化
        arc.Events.on(EventType.SaveWriteEvent.class, e -> save());
    }

    /** 获取指定区块的统计信息；不存在则新建。 */
    public static SatelliteSectorInfo get(Sector sector) {
        String key = keyOf(sector);
        SatelliteSectorInfo info = infos.get(key);
        if (info == null) {
            info = new SatelliteSectorInfo(sector);
            infos.put(key, info);
        }
        return info;
    }

    /** 每帧调用：驱动统计滑动窗口刷新、后台模拟与周期性存档。 */
    public static void update() {
        if (Vars.state.isPaused())
            return;
        // 卫星地图可能不被 Vars.state.isGame() 识别，需要单独通过地图标签判断
        boolean inSatelliteMap = Vars.state.map != null
                && Vars.state.map.tags.get("crystal-aviation-satellite") != null;
        // 在普通星球区块、卫星地图等游戏状态中都需要刷新统计；仅在菜单/非游戏状态且非卫星地图时跳过
        if (!Vars.state.isGame() && !inSatelliteMap)
            return;

        // 只有在普通战斗中玩家主动暂停时才停止计时；卫星地图/星球视图本身 isPaused=true，
        // 但后台模拟仍需继续推进，因此不能单纯用 isPaused() 判断。
        boolean combatPaused = Vars.state.isPaused() && Vars.state.isGame() && !inSatelliteMap;
        if (combatPaused)
            return;

        refreshTimer += Time.delta;
        if (refreshTimer >= refreshInterval) {
            refreshTimer = 0f;
            // 只刷新当前所在区块的统计；未加载区块的统计保持冻结，
            // 避免每秒都向它们的滑动窗口里塞 0，导致后台模拟用的速率衰减到 0
            refreshCurrentSectorStats();
        }

        // 后台模拟使用游戏时间计时，自然支持时间控制模组
        backgroundTimer += Time.delta;
        if (backgroundTimer >= backgroundInterval) {
            float elapsedSeconds = backgroundTimer / 60f;
            backgroundTimer = 0f;
            runBackgroundSimulation(elapsedSeconds);
        }

        if (dirty) {
            saveTimer += Time.delta;
            if (saveTimer >= saveInterval) {
                saveTimer = 0f;
                save();
            }
        }
    }

    /** 强制运行一次后台模拟（由 30 秒计时器触发）。 */
    public static void runBackgroundSimulation(float elapsedSeconds) {
        // 卫星地图中也需要后台模拟，因此不能只用 isCampaign() 判断
        boolean inSatelliteMap = Vars.state.map != null
                && Vars.state.map.tags.get("crystal-aviation-satellite") != null;
        if (!Vars.state.isGame() && !inSatelliteMap)
            return;
        if (elapsedSeconds <= 0.001f)
            return;
        simulate((int) elapsedSeconds);
    }

    /** 记录一次物品发射：从 source 区块到 target 卫星。 */
    public static void recordItemLaunch(Sector source, Satellite target, Item item, int amount) {
        if (source == null || target == null || item == null || amount <= 0)
            return;
        get(source).handleItemLaunch(target.id, item, amount);
        dirty = true;
    }

    /** 批量记录物品发射。 */
    public static void recordItemLaunch(Sector source, Satellite target, mindustry.type.ItemStack[] stacks) {
        if (source == null || target == null || stacks == null)
            return;
        SatelliteSectorInfo info = get(source);
        for (mindustry.type.ItemStack stack : stacks) {
            if (stack != null && stack.item != null && stack.amount > 0) {
                info.handleItemLaunch(target.id, stack.item, stack.amount);
            }
        }
        dirty = true;
    }

    /** 记录一次液体发射：从 source 区块到 target 卫星。 */
    public static void recordLiquidLaunch(Sector source, Satellite target, Liquid liquid, float amount) {
        if (source == null || target == null || liquid == null || amount <= 0.001f)
            return;
        get(source).handleLiquidLaunch(target.id, liquid, amount);
        dirty = true;
    }

    /** 记录一次卫星向目标区块注入物品。 */
    public static void recordItemInject(Sector target, Satellite source, Item item, int amount) {
        if (target == null || source == null || item == null || amount <= 0)
            return;
        get(target).handleItemImport(source.id, item, amount);
        dirty = true;
    }

    /** 记录一次卫星向目标区块注入液体。 */
    public static void recordLiquidInject(Sector target, Satellite source, Liquid liquid, float amount) {
        if (target == null || source == null || liquid == null || amount <= 0.001f)
            return;
        get(target).handleLiquidImport(source.id, liquid, amount);
        dirty = true;
    }

    /** 刷新所有统计窗口。 */
    public static void refreshAllStats() {
        for (SatelliteSectorInfo info : infos.values()) {
            info.refreshStats();
        }
        // mean 值已更新，需要持久化
        dirty = true;
    }

    /** 只刷新玩家当前所在区块的统计窗口，避免未加载区块的 mean 因持续补 0 而衰减。 */
    private static void refreshCurrentSectorStats() {
        if (Vars.state.rules.sector == null)
            return;
        SatelliteSectorInfo info = infos.get(keyOf(Vars.state.rules.sector));
        if (info == null)
            return;
        info.refreshStats();
        dirty = true;
    }

    /**
     * 后台模拟：对未加载且未被攻击的区块，按已记录速率把资源加到目标卫星/区块。
     *
     * @param elapsedSeconds 距离上次模拟经过的秒数
     */
    public static void simulate(int elapsedSeconds) {
        if (elapsedSeconds <= 0)
            return;
        if (elapsedSeconds > 60) {
            // 防止长时间挂起后一次性模拟过多，造成资源暴涨
            elapsedSeconds = 60;
        }
        final float dt = elapsedSeconds;

        // 确定需要模拟的星球：普通战役取当前星球；卫星地图取当前卫星所属星球
        ObjectSet<Planet> planets = new ObjectSet<>();
        Planet currentPlanet = Vars.state.getPlanet();
        if (currentPlanet != null) {
            planets.add(currentPlanet);
        }
        int currentSatId = SatelliteManager.currentSatelliteId;
        // 卫星地图中 currentSatelliteId 可能因 isGame() 为 false 而未被恢复，直接从地图标签读取
        if (currentSatId < 0 && Vars.state.map != null) {
            String tag = Vars.state.map.tags.get("crystal-aviation-satellite");
            if (tag != null && !tag.isEmpty()) {
                try {
                    currentSatId = Integer.parseInt(tag);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (currentSatId >= 0) {
            Satellite currentSat = SatelliteManager.get(currentSatId);
            if (currentSat != null && currentSat.planet != null) {
                planets.add(currentSat.planet);
            }
        }

        if (planets.isEmpty())
            return;
        if (infos.isEmpty())
            return;

        // 卫星地图中当前卫星由前台 Satellite.updateInject 处理，后台模拟需跳过避免重复
        boolean inSatelliteMap = Vars.state.map != null
                && Vars.state.map.tags.get("crystal-aviation-satellite") != null;

        for (Planet planet : planets) {
            simulatePlanet(planet, dt);
            simulateSatelliteInject(planet, dt, currentSatId, inSatelliteMap);
        }
    }

    private static void simulatePlanet(Planet planet, float dt) {
        for (Sector sector : planet.sectors) {
            if (!sector.hasBase() || sector.isBeingPlayed() || sector.isAttacked())
                continue;

            SatelliteSectorInfo info = infos.get(keyOf(sector));
            if (info == null)
                continue;

            // 确保 sector.info 已加载；部分区块在卫星地图中可能尚未读取存档信息
            if (sector.info == null) {
                try {
                    sector.loadInfo();
                } catch (Throwable t) {
                    Log.warn("[SatelliteSectorInfoManager] 加载区块信息失败: @", keyOf(sector));
                }
            }

            // 物资发射到卫星：按统计速率直接追加到卫星，不再从区块核心扣除
            // 玩家可用物品源直接供应地面发射台，后台模拟不应依赖核心库存
            info.satelliteItemExports.each((satId, itemStats) -> {
                if (itemStats == null)
                    return;
                Satellite sat = SatelliteManager.get(satId);
                if (sat == null || sat.planet != sector.planet)
                    return;
                ItemSeq seq = new ItemSeq();
                itemStats.each((item, stat) -> {
                    if (item == null || stat == null || stat.mean <= 0.001f)
                        return;
                    int amount = (int) (stat.mean * dt);
                    if (amount <= 0)
                        return;
                    seq.add(item, amount);
                });
                if (seq.total > 0) {
                    sat.addItems(seq);
                }
            });

            // 液体发射到卫星：原版 SectorInfo 没有液体库存，后台不扣除来源区块
            info.satelliteLiquidExports.each((satId, liquidStats) -> {
                if (liquidStats == null)
                    return;
                Satellite sat = SatelliteManager.get(satId);
                if (sat == null || sat.planet != sector.planet)
                    return;
                liquidStats.each((liquid, stat) -> {
                    if (liquid == null || stat == null || stat.mean <= 0.001f)
                        return;
                    float amount = stat.mean * dt;
                    if (amount > 0.001f) {
                        sat.addLiquid(liquid, amount);
                    }
                });
            });

            // 卫星向本区块注入物资：改由 simulateSatelliteInject 统一按注入台配置处理，
            // 避免基于统计速率的注入与基于配置的后台注入重复扣减卫星库存。

            // 卫星向本区块注入液体：原版 Sector 没有液体存储，暂不做处理
        }

        SatelliteManager.save();
    }

    /**
     * 后台模拟卫星注入台：按配置直接向目标区块核心注入物品。
     * 当前正在访问的卫星地图由前台 Satellite.updateInject 处理，此处跳过避免重复。
     */
    private static void simulateSatelliteInject(Planet planet, float dt, int currentSatId, boolean inSatelliteMap) {
        for (Satellite sat : SatelliteManager.satellites.values()) {
            if (sat == null || sat.planet != planet)
                continue;
            if (sat.targetSectorId < 0 || !sat.injectMode)
                continue;
            if (inSatelliteMap && currentSatId == sat.id)
                continue;
            sat.simulateInject(dt);
        }
    }

    /** 用于持久化的简单记录结构（避免嵌套 ObjectMap 在 Json 序列化时丢失键值）。 */
    public static class StatRecord {
        public int satelliteId;
        public String contentName;
        public float mean;
        public int amount;

        public StatRecord() {
        }

        public StatRecord(int satelliteId, String contentName, float mean, int amount) {
            this.satelliteId = satelliteId;
            this.contentName = contentName;
            this.mean = mean;
            this.amount = amount;
        }
    }

    /** 用于持久化的单区块统计结构。 */
    public static class InfoData {
        public String planetName;
        public int sectorId;
        public StatRecord[] itemExports = new StatRecord[0];
        public StatRecord[] liquidExports = new StatRecord[0];
        public StatRecord[] itemImports = new StatRecord[0];
        public StatRecord[] liquidImports = new StatRecord[0];

        public InfoData() {
        }
    }

    /** 用于持久化的顶层结构。 */
    public static class SaveData {
        public String[] keys = new String[0];
        public InfoData[] infoList = new InfoData[0];

        public SaveData() {
        }
    }

    public static void load() {
        infos.clear();

        // 优先读取新版统一存储
        String jsonString = Core.settings.getString(settingsKey, "");
        if (jsonString != null && !jsonString.isEmpty()) {
            try {
                Json json = new Json();
                SaveData data = json.fromJson(SaveData.class, jsonString);
                if (data != null && data.keys != null && data.infoList != null) {
                    for (int i = 0; i < data.keys.length; i++) {
                        String key = data.keys[i];
                        InfoData idata = i < data.infoList.length ? data.infoList[i] : null;
                        if (idata == null)
                            continue;
                        SatelliteSectorInfo info = new SatelliteSectorInfo();
                        info.planetName = idata.planetName;
                        info.sectorId = idata.sectorId;
                        restoreRecords(idata.itemExports, info.satelliteItemExports, true);
                        restoreRecords(idata.liquidExports, info.satelliteLiquidExports, false);
                        restoreRecords(idata.itemImports, info.satelliteItemImports, true);
                        restoreRecords(idata.liquidImports, info.satelliteLiquidImports, false);
                        infos.put(key, info);
                    }
                }
            } catch (Throwable t) {
                Log.err("[SatelliteSectorInfoManager] 新版加载失败", t);
            }
        }

        // 旧版兼容：若新版无数据，尝试读取旧版分散存储
        if (infos.isEmpty()) {
            try {
                String[] keys = Core.settings.getJson(legacyJsonKeysKey, String[].class, () -> new String[0]);
                if (keys != null) {
                    for (String key : keys) {
                        if (key == null || key.isEmpty())
                            continue;
                        try {
                            SatelliteSectorInfo info = Core.settings.getJson(legacySettingsPrefix + key,
                                    SatelliteSectorInfo.class, SatelliteSectorInfo::new);
                            if (info != null) {
                                infos.put(key, info);
                            }
                        } catch (Throwable t) {
                            Log.warn("[SatelliteSectorInfoManager] 加载旧版区块卫星统计失败: @", key);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.err("[SatelliteSectorInfoManager] 旧版加载失败", t);
            }
        }
    }

    public static void save() {
        try {
            SaveData data = new SaveData();
            Seq<String> keyList = new Seq<>();
            Seq<InfoData> infoDataList = new Seq<>();
            for (var entry : infos) {
                String key = entry.key;
                SatelliteSectorInfo info = entry.value;
                if (info == null || !info.hasAnyStats())
                    continue;
                keyList.add(key);
                InfoData idata = new InfoData();
                idata.planetName = info.planetName;
                idata.sectorId = info.sectorId;
                idata.itemExports = collectRecords(info.satelliteItemExports, true);
                idata.liquidExports = collectRecords(info.satelliteLiquidExports, false);
                idata.itemImports = collectRecords(info.satelliteItemImports, true);
                idata.liquidImports = collectRecords(info.satelliteLiquidImports, false);
                infoDataList.add(idata);
            }
            data.keys = keyList.toArray(String.class);
            data.infoList = infoDataList.toArray(InfoData.class);

            Json json = new Json();
            String jsonString = json.toJson(data, SaveData.class);
            Core.settings.put(settingsKey, jsonString);

            // 旧版数据已迁移到新版，清理避免重复/冲突
            Core.settings.remove(legacyJsonKeysKey);

            Core.settings.forceSave();
            dirty = false;
            saveTimer = 0f;
        } catch (Throwable t) {
            Log.err("[SatelliteSectorInfoManager] save 失败", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> StatRecord[] collectRecords(
            ObjectMap<Integer, ObjectMap<T, SatelliteSectorInfo.ExportStat>> nested, boolean isItem) {
        Seq<StatRecord> list = new Seq<>();
        nested.each((satId, map) -> {
            if (map == null)
                return;
            map.each((content, stat) -> {
                if (content == null || stat == null || stat.mean <= 0.001f)
                    return;
                String name = isItem ? ((Item) content).name : ((Liquid) content).name;
                list.add(new StatRecord(satId, name, stat.mean, stat.amount));
            });
        });
        return list.toArray(StatRecord.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> void restoreRecords(StatRecord[] records,
            ObjectMap<Integer, ObjectMap<T, SatelliteSectorInfo.ExportStat>> nested,
            boolean isItem) {
        if (records == null)
            return;
        for (StatRecord r : records) {
            if (r == null || r.contentName == null || r.mean <= 0.001f)
                continue;
            T content;
            if (isItem) {
                Item item = mindustry.Vars.content.item(r.contentName);
                if (item == null)
                    continue;
                content = (T) item;
            } else {
                Liquid liquid = mindustry.Vars.content.liquid(r.contentName);
                if (liquid == null)
                    continue;
                content = (T) liquid;
            }
            SatelliteSectorInfo.ExportStat stat = nested.get(r.satelliteId, ObjectMap::new).get(content,
                    SatelliteSectorInfo.ExportStat::new);
            stat.mean = r.mean;
            stat.amount = r.amount;
            stat.loaded = false;
        }
    }

    /** 获取所有向指定卫星发射物品的统计：sector key -> item -> ExportStat。 */
    public static ObjectMap<String, ObjectMap<Item, SatelliteSectorInfo.ExportStat>> getItemExportsToSatellite(
            Satellite satellite) {
        ObjectMap<String, ObjectMap<Item, SatelliteSectorInfo.ExportStat>> result = new ObjectMap<>();
        if (satellite == null)
            return result;
        for (var entry : infos) {
            SatelliteSectorInfo info = entry.value;
            ObjectMap<Item, SatelliteSectorInfo.ExportStat> stats = info.satelliteItemExports.get(satellite.id);
            if (stats != null && !stats.isEmpty()) {
                result.put(entry.key, stats);
            }
        }
        return result;
    }

    /** 获取所有向指定卫星发射液体的统计：sector key -> liquid -> ExportStat。 */
    public static ObjectMap<String, ObjectMap<Liquid, SatelliteSectorInfo.ExportStat>> getLiquidExportsToSatellite(
            Satellite satellite) {
        ObjectMap<String, ObjectMap<Liquid, SatelliteSectorInfo.ExportStat>> result = new ObjectMap<>();
        if (satellite == null)
            return result;
        for (var entry : infos) {
            SatelliteSectorInfo info = entry.value;
            ObjectMap<Liquid, SatelliteSectorInfo.ExportStat> stats = info.satelliteLiquidExports.get(satellite.id);
            if (stats != null && !stats.isEmpty()) {
                result.put(entry.key, stats);
            }
        }
        return result;
    }

    /** 解析 sector key 为对应的 Sector，若星球/区块不存在则返回 null。 */
    public static @Nullable Sector sectorFromKey(String key) {
        if (key == null)
            return null;
        int dash = key.lastIndexOf('-');
        if (dash <= 0)
            return null;
        String planetName = key.substring(0, dash);
        int sectorId;
        try {
            sectorId = Integer.parseInt(key.substring(dash + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        Planet planet = Vars.content.planet(planetName);
        if (planet == null || planet.sectors == null || sectorId < 0 || sectorId >= planet.sectors.size)
            return null;
        return planet.sectors.get(sectorId);
    }

    private static String keyOf(Sector sector) {
        return sector.planet.name + "-" + sector.id;
    }
}
