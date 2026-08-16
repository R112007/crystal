package crystal.aviation;

import arc.Core;
import arc.files.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

import crystal.aviation.blocks.SatelliteExpansionBeacon;
import crystal.aviation.blocks.SatelliteLiquidTank;
import crystal.aviation.world.*;
import crystal.aviation.SatelliteSectorInfoManager;
import crystal.type.SatelliteMissile;
import crystal.world.modules.SatelliteMissleModule;
import static mindustry.Vars.*;

/**
 * 代表一颗在轨人造卫星。
 * 包含：显示名称、轨道参数、地图数据、对接关系、移动状态、生命值、模式、注入/接收/太阳能系统。
 */
public class Satellite {
    private static int idCounter = 0;

    /** 最大等级 */
    public static final int maxTier = 5;
    /** 注入物品到绑定区块的间隔（秒），后台机制每 30 秒触发一次 */
    public static final float injectInterval = 30f;
    /** 接收台请求物品/液体的间隔（秒） */
    public static final float requestInterval = 3f;

    /** 唯一标识 */
    public int id;
    /** 玩家输入的卫星名称 */
    public String name;
    /** 所属星球 */
    public Planet planet;
    /** 当前轨道角度（弧度） */
    public float orbitAngle;
    /** 轨道半径（相对于星球半径的倍数） */
    public float orbitRadius;
    /** 轨道倾角（用于3D视觉效果） */
    public float orbitTilt;
    /** 轨道角速度 */
    public float orbitSpeed;

    /** 卫星地图数据 */
    public SatelliteMapData mapData;
    /** 已对接的卫星ID */
    public IntSeq dockedSatellites = new IntSeq();
    /** 对接后作为主体的卫星ID（-1表示自己就是主体） */
    public int dockMaster = -1;

    /** 移动目标：目标Sector ID（星球区块索引），-1表示无 */
    public int targetSectorId = -1;
    /** 移动进度 0~1 */
    public float moveProgress = 0f;
    /** 移动总耗时（秒） */
    public float moveDuration = 60f;
    /** 移动起始角度 */
    public float moveFromAngle;
    /** 移动目标角度 */
    public float moveToAngle;
    /** 是否正在移动 */
    public boolean moving = false;
    /** 是否已绑定到某个区块（绑定后静止在区块上方） */
    public boolean boundToSector = false;

    /** 3D渲染辅助字段 */
    public transient float renderX, renderY, renderZ;
    public transient float visualScale = 1f;
    /** 太阳能板旋转角度 */
    public transient float spinAngle = 0f;

    /** 卫星资源仓库（与核心共用同一个 ItemModule 实例） */
    public ItemModule items = new ItemModule();
    /** 太阳能发电量（单位：能量/秒） */
    public float solarPower = 0f;
    /** 太阳能板总发电量（卫星地图上所有太阳能板之和） */
    public float totalSolarPower = 0f;
    /** 卫星电力储存（能量） */
    public float powerStorage = 0f;
    /** 卫星电力储存上限（能量） */
    public float powerCapacity = 5000f;
    /** 已扫描的 Sector ID */
    public IntSeq scannedSectors = new IntSeq();
    /** 卫星等级（1 + 升级次数 + 对接数量，上限 maxTier） */
    public int tier = 1;
    /** 通过升级建筑获得的等级加成 */
    public int upgradeTier = 0;
    /** 卫星导弹仓库 */
    public SatelliteMissleModule missileModule = new SatelliteMissleModule();
    /** 当前选中的自动攻击/轨道打击导弹类型 */
    public @Nullable SatelliteMissile selectedMissile;

    /** 生命值 */
    public float health = 1000f;
    /** 最大生命值 */
    public float maxHealth = 1000f;

    /** 自动攻击模式 */
    public boolean autoAttackMode = false;
    /** 自动攻击间隔（秒） */
    public float autoAttackInterval = 4f;
    /** 自动攻击计时器（秒） */
    public float autoAttackTimer = 0f;
    /** 自动攻击单次伤害 */
    public float autoAttackDamage = 150f;
    /** 注入模式（向绑定区块核心注入物品） */
    public boolean injectMode = false;

    /** 扩容仓上限（随等级提升） */
    public int expansionBeaconLimit = 1;
    /** 当前扩容仓数量 */
    public int expansionBeaconCount = 0;
    /** 液体仓上限（随等级提升） */
    public int liquidTankLimit = 1;
    /** 当前液体仓数量 */
    public int liquidTankCount = 0;
    /** 轨道打击缩放加成（随等级提升） */
    public float strikeZoomBonus = 0f;

    /** 液体仓单座容量（旧版兼容字段，现在由建筑决定） */
    public float liquidCapacityPerTank = 1000f;
    /** 每种液体的独立容量上限（所有液体仓容量之和） */
    public float liquidCapacity = 0f;
    /** 卫星液体仓库（液体 -> 数量） */
    public ObjectMap<Liquid, Float> liquidStorage = new ObjectMap<>();

    /** 基础物品仓库容量 */
    public static final float baseItemStorageCapacity = 6000f;
    /** 当前物品仓库总容量（基础 + 扩容仓增量） */
    public float itemStorageCapacity = baseItemStorageCapacity;

    /** 注入物品统计：物品种类 -> 每次注入数量 */
    public ObjectMap<Item, Integer> injectItems = new ObjectMap<>();
    /** 注入台配置：建筑ID -> 配置（用于持久化汇总） */
    public ObjectMap<Integer, InjectorConfig> injectorConfigs = new ObjectMap<>();
    /** 注入计时器（秒） */
    public float injectTimer = 0f;
    /** 接收台请求计时器（秒） */
    public float requestTimer = 0f;

    /** 注入台单个配置。 */
    public static class InjectorConfig {
        public Item item;
        public int amount;

        public InjectorConfig(Item item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    public Satellite() {
        this.id = ++idCounter;
    }

    public Satellite(Planet planet, String name) {
        this(planet, name, null, -1f, -1f);
    }

    public Satellite(Planet planet, String name, @Nullable Fi mapFile) {
        this(planet, name, mapFile, -1f, -1f);
    }

    /**
     * 创建卫星。
     * 
     * @param orbitRadius   轨道半径（相对于星球半径的倍数），<=0 时使用随机默认值
     * @param orbitAngleDeg 初始轨道角度（度），<0 时使用随机默认值
     */
    public Satellite(Planet planet, String name, @Nullable Fi mapFile, float orbitRadius, float orbitAngleDeg) {
        this();
        this.planet = planet;
        this.name = name;
        if (orbitAngleDeg >= 0f) {
            this.orbitAngle = orbitAngleDeg * Mathf.degRad;
        } else {
            this.orbitAngle = Mathf.random(360f) * Mathf.degRad;
        }
        if (orbitRadius > 0f) {
            this.orbitRadius = orbitRadius;
        } else {
            this.orbitRadius = 2.2f + Mathf.random(0.8f);
        }
        this.orbitTilt = Mathf.random(-15f, 15f) * Mathf.degRad;
        this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        this.mapData = new SatelliteMapData(this);
        if (mapFile != null && mapFile.exists()) {
            try {
                mapData.loadFromMapFile(mapFile);
            } catch (Exception e) {
                mapData.generateDefault();
            }
        } else {
            mapData.generateDefault();
        }
        recalcStats();
    }

    public float injectProgress() {
        return injectTimer / injectInterval;
    }

    /** 根据 upgradeTier 与对接数量重新计算等级、生命、上限等 */
    public void recalcStats() {
        tier = Math.min(maxTier, 1 + upgradeTier + dockedSatellites.size);
        visualScale = 1f + 0.25f * upgradeTier + 0.3f * dockedSatellites.size;
        maxHealth = 1000f + (tier - 1) * 500f;
        if (health > maxHealth)
            health = maxHealth;

        expansionBeaconLimit = tier;
        liquidTankLimit = tier;
        strikeZoomBonus = (tier - 1) * 0.5f;
        powerCapacity = 5000f + (tier - 1) * 2500f;
        if (powerStorage > powerCapacity)
            powerStorage = powerCapacity;
    }

    /** 更新轨道位置、移动、自动攻击、注入 */
    public void update() {
        if (Vars.state.isPaused())
            return;
        if (health <= 0) {
            SatelliteManager.retire(this.id);
        }
        if (moving) {
            moveProgress += Time.delta / (moveDuration * 60f);
            if (moveProgress >= 1f) {
                moveProgress = 1f;
                orbitAngle = moveToAngle;
                moving = false;
                if (!boundToSector) {
                    targetSectorId = -1;
                }
            } else {
                orbitAngle = Mathf.lerp(moveFromAngle, moveToAngle, moveProgress);
            }
        } else {
            orbitAngle += orbitSpeed * Time.delta;
        }

        spinAngle += orbitSpeed * Time.delta * 4f;

        float r = planet.radius * orbitRadius;
        fallbackRenderPosition(r);

        recalcStats();

        // 将本帧太阳能板发电量累加到卫星电力仓库
        if (totalSolarPower > 0f) {
            powerStorage = Math.min(powerCapacity, powerStorage + totalSolarPower * Time.delta / 60f);
        }
        totalSolarPower = 0f;

        // 自动攻击：寻找敌方目标发射导弹/光束
        if (autoAttackMode && boundToSector && targetSectorId >= 0) {
            updateAutoAttack();
        }

        // 注入逻辑
        if (injectMode && boundToSector && targetSectorId >= 0) {
            updateInject();
        }
    }

    private void fallbackRenderPosition(float r) {
        renderX = planet.position.x + Mathf.cos(orbitAngle) * r;
        renderY = planet.position.y + Mathf.sin(orbitAngle) * r * Mathf.cos(orbitTilt);
        renderZ = planet.position.z + Mathf.sin(orbitAngle) * r * Mathf.sin(orbitTilt);
    }

    /** 自动攻击逻辑：寻找绑定区块内的敌方目标并发射导弹。 */
    private void updateAutoAttack() {
        autoAttackTimer += Time.delta / 60f;
        if (autoAttackTimer < autoAttackInterval)
            return;
        autoAttackTimer = 0f;

        // 只有在玩家处于绑定区块时才进行实际攻击
        if (state.rules.sector == null || state.rules.sector.id != targetSectorId)
            return;
        if (state.rules.sector.planet != planet)
            return;
        if (selectedMissile == null || missileModule == null || !missileModule.has(selectedMissile, 1))
            return;

        // 寻找敌方目标
        Team enemyTeam = state.rules.waveTeam;
        mindustry.gen.Unit target = mindustry.entities.Units.closestEnemy(
                Team.sharded, world.unitWidth() / 2f, world.unitHeight() / 2f,
                Math.max(world.unitWidth(), world.unitHeight()), u -> true);

        if (target != null && target.isValid()) {
            launchAutoAttackMissile(target.x, target.y);
            return;
        }

        // 没有单位时寻找敌方建筑
        mindustry.gen.Building build = findEnemyBuilding();
        if (build != null && build.isValid()) {
            launchAutoAttackMissile(build.x, build.y);
        }
    }

    /** 从相机两侧向目标发射一枚自动攻击导弹，并扣除库存。 */
    private void launchAutoAttackMissile(float tx, float ty) {
        if (selectedMissile == null || missileModule == null || !missileModule.has(selectedMissile, 1))
            return;

        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        boolean left = Mathf.randomBoolean();
        float sx = left ? cx - (Core.camera.width / 2f + 8f) : cx + (Core.camera.width / 2f + 8f);
        float sy = cy + Mathf.random(-24f, 24f);

        missileModule.remove(selectedMissile, 1);
        selectedMissile.create(mindustry.Vars.player.unit(), mindustry.Vars.player.team(), sx, sy, tx, ty);
    }

    /** 查找一个敌方建筑作为自动攻击目标。 */
    private mindustry.gen.Building findEnemyBuilding() {
        Team enemyTeam = state.rules.waveTeam;
        for (int y = 0; y < world.tiles.height; y++) {
            for (int x = 0; x < world.tiles.width; x++) {
                mindustry.world.Tile tile = world.tile(x, y);
                if (tile == null || tile.build == null)
                    continue;
                if (tile.build.team == enemyTeam) {
                    return tile.build;
                }
            }
        }
        return null;
    }

    /** 向绑定区块核心注入物品（前台，每帧调用） */
    private void updateInject() {
        injectTimer += Time.delta / 60f;
        refreshInjectItems();
        if (injectItems.isEmpty())
            return;

        int injections = (int) (injectTimer / injectInterval);
        if (injections <= 0)
            return;
        injectTimer %= injectInterval;

        injectBatch(injections);
    }

    /**
     * 后台模拟调用：按经过秒数向绑定区块核心注入物品。
     * 与前台 updateInject 共享 injectTimer，避免重进地图后节奏错乱。
     */
    public void simulateInject(float elapsedSeconds) {
        if (!injectMode || targetSectorId < 0 || items == null)
            return;

        refreshInjectItems();
        if (injectItems.isEmpty())
            return;

        float totalTime = injectTimer + elapsedSeconds;
        int injections = (int) (totalTime / injectInterval);
        if (injections <= 0)
            return;
        injectTimer = totalTime % injectInterval;

        injectBatch(injections);
    }

    /** 执行多次注入，每次按 injectItems 中配置的数量。 */
    private void injectBatch(int injections) {
        Sector sector = findSector(targetSectorId);
        if (sector == null || items == null)
            return;

        try {
            for (var entry : injectItems) {
                Item item = entry.key;
                int amountPerInject = entry.value;
                if (item == null || amountPerInject <= 0)
                    continue;

                int totalAmount = amountPerInject * injections;
                int available = items.get(item);
                int actual = Math.min(totalAmount, available);
                if (actual <= 0)
                    continue;

                int accepted = 0;
                boolean inSatelliteMap = SatelliteManager.currentSatelliteId >= 0;
                if (sector.isBeingPlayed() && !inSatelliteMap && state.rules.defaultTeam.core() != null) {
                    ItemModule storage = state.rules.defaultTeam.items();
                    int cap = state.rules.defaultTeam.core().storageCapacity;
                    if (storage != null) {
                        accepted = Math.min(actual, Math.max(0, cap - storage.get(item)));
                    }
                } else if (sector.hasBase() && sector.info != null && sector.info.items != null) {
                    accepted = Math.min(actual,
                            Math.max(0, sector.info.storageCapacity - sector.info.items.get(item)));
                }

                if (accepted > 0) {
                    ItemSeq seq = new ItemSeq();
                    seq.set(item, accepted);
                    sector.addItems(seq);
                    items.remove(item, accepted);
                    SatelliteSectorInfoManager.recordItemInject(sector, this, item, accepted);
                    if (sector.hasBase() && sector.info != null) {
                        sector.saveInfo();
                    }
                }
            }
        } catch (Throwable t) {
            Log.warn("[Satellite] 向区块注入物品时发生异常: @", t.getMessage());
        }
    }

    /** 根据注入台配置刷新 injectItems 汇总。 */
    public void refreshInjectItems() {
        injectItems.clear();
        for (var entry : injectorConfigs) {
            InjectorConfig config = entry.value;
            if (config == null || config.item == null || config.amount <= 0)
                continue;
            injectItems.put(config.item, injectItems.get(config.item, 0) + config.amount);
        }
    }

    /** 注册或更新注入台配置。 */
    public void setInjectorConfig(int buildId, Item item, int amount) {
        if (item == null || amount <= 0) {
            injectorConfigs.remove(buildId);
        } else {
            injectorConfigs.put(buildId, new InjectorConfig(item, amount));
        }
    }

    /** 移除注入台配置。 */
    public void removeInjectorConfig(int buildId) {
        injectorConfigs.remove(buildId);
    }

    /** 受到伤害 */
    public void damage(float amount) {
        health -= amount;
        if (health < 0f)
            health = 0f;
    }

    /** 恢复生命 */
    public void heal(float amount) {
        health = Math.min(maxHealth, health + amount);
    }

    /** 增加太阳能板发电量 */
    public void addSolarPower(float amount) {
        totalSolarPower += amount;
    }

    /** 减少太阳能板发电量 */
    public void removeSolarPower(float amount) {
        totalSolarPower = Math.max(0f, totalSolarPower - amount);
    }

    /** 增加扩容仓，capacity 为建筑决定的容量增量。 */
    public boolean addExpansionBeacon(float capacity) {
        if (expansionBeaconCount >= expansionBeaconLimit)
            return false;
        expansionBeaconCount++;
        itemStorageCapacity += capacity;
        refreshCoreCapacity();
        return true;
    }

    /** 减少扩容仓，capacity 为建筑决定的容量增量。 */
    public void removeExpansionBeacon(float capacity) {
        if (expansionBeaconCount <= 0)
            return;
        expansionBeaconCount = Math.max(0, expansionBeaconCount - 1);
        itemStorageCapacity = Math.max(baseItemStorageCapacity, itemStorageCapacity - capacity);
        refreshCoreCapacity();
    }

    /** 触发卫星核心重新计算容量（基于当前 itemStorageCapacity）。 */
    public void refreshCoreCapacity() {
        CoreBlock.CoreBuild core = findCoreBuild();
        if (core != null) {
            core.onProximityUpdate();
        }
    }

    /** 根据当前世界中实际存在的扩容仓/液体仓数量，重新计算容量与计数（并默认裁剪液体）。 */
    public void recalcStorageCapacityFromWorld() {
        recalcStorageCapacityFromWorld(null, true);
    }

    /**
     * 根据当前世界中实际存在的扩容仓/液体仓数量，重新计算容量与计数。
     *
     * @param clamp 是否在计算完成后根据当前 liquidCapacity 裁剪液体储量。
     *              地图加载过程中创建建筑时应传 false，防止液体被未加载完成的建筑数量误截断。
     */
    public void recalcStorageCapacityFromWorld(boolean clamp) {
        recalcStorageCapacityFromWorld(null, clamp);
    }

    /**
     * 根据当前世界中实际存在的扩容仓/液体仓数量，重新计算容量与计数。
     *
     * @param ignore 要忽略的建筑（用于 onRemoved 时排除即将被移除的建筑自身）。
     */
    public void recalcStorageCapacityFromWorld(@Nullable Building ignore) {
        recalcStorageCapacityFromWorld(ignore, ignore == null);
    }

    /**
     * 根据当前世界中实际存在的扩容仓/液体仓数量，重新计算容量与计数。
     *
     * @param ignore 要忽略的建筑（用于 onRemoved 时排除即将被移除的建筑自身）。
     * @param clamp  是否在计算完成后根据当前 liquidCapacity 裁剪液体储量。
     */
    private void recalcStorageCapacityFromWorld(@Nullable Building ignore, boolean clamp) {
        if (world == null || world.tiles == null)
            return;

        expansionBeaconCount = 0;
        liquidTankCount = 0;
        itemStorageCapacity = baseItemStorageCapacity;
        liquidCapacity = 0f;

        for (Building b : Groups.build) {
            if (b == null || !b.isValid() || b == ignore)
                continue;
            Block block = b.block;
            if (block instanceof SatelliteExpansionBeacon) {
                expansionBeaconCount++;
                itemStorageCapacity += ((SatelliteExpansionBeacon) block).itemCapacityIncrease;
            } else if (block instanceof SatelliteLiquidTank) {
                liquidTankCount++;
                liquidCapacity += ((SatelliteLiquidTank) block).capacityPerTank;
            }
        }

        if (clamp) {
            clampLiquidStorage();
        }
    }

    /** 查找当前卫星地图中的核心建筑。 */
    public @Nullable CoreBlock.CoreBuild findCoreBuild() {
        Block coreBlock = CrystalAviationSystemCore.spaceCore;
        if (coreBlock == null || world == null || world.tiles == null)
            return null;
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Tile tile = world.tile(x, y);
                if (tile != null && tile.block() == coreBlock && tile.build instanceof CoreBlock.CoreBuild) {
                    return (CoreBlock.CoreBuild) tile.build;
                }
            }
        }
        return null;
    }

    /** 把当前卫星的 ItemModule 绑定到核心建筑，使双方共用同一实例。 */
    public void bindCoreItems() {
        CoreBlock.CoreBuild core = findCoreBuild();
        if (core == null) {
            return;
        }
        if (core.items == items) {
            return;
        }
        // 旧存档兼容：只有卫星 items 为空时才把核心物品合并进来，且以卫星 items 为准，
        // 绝不能用核心存档里的旧数据覆盖发射台新送到卫星的物品。
        if ((items == null || items.total() == 0) && core.items != null && core.items.total() > 0) {
            if (items == null) {
                items = new ItemModule();
            }
            items.set(core.items);
        }
        core.items = items;
    }

    /** 向卫星仓库添加一批物品，按每种物品的独立容量上限自动截断（参考 Sector.addItems）。 */
    public void addItems(ItemSeq seq) {
        if (seq == null) {
            return;
        }
        if (items == null) {
            items = new ItemModule();
        }
        seq.each((item, amount) -> {
            if (item == null || amount <= 0)
                return;
            int space = Math.max(0, (int) itemStorageCapacity - items.get(item));
            int add = Math.min(space, amount);
            if (add > 0) {
                items.add(item, add);
            }
        });
    }

    /** 增加液体仓，capacity 为建筑决定的液体容量增量。 */
    public boolean addLiquidTank(float capacity) {
        if (liquidTankCount >= liquidTankLimit)
            return false;
        liquidTankCount++;
        liquidCapacity += capacity;
        return true;
    }

    /** 减少液体仓，capacity 为建筑决定的液体容量增量。 */
    public void removeLiquidTank(float capacity) {
        if (liquidTankCount <= 0)
            return;
        liquidTankCount = Math.max(0, liquidTankCount - 1);
        liquidCapacity = Math.max(0f, liquidCapacity - capacity);
        clampLiquidStorage();
    }

    /** 限制每种液体储量不超过当前单种容量上限，并清空无效条目。 */
    public void clampLiquidStorage() {
        if (liquidStorage.isEmpty())
            return;
        var it = liquidStorage.iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.value <= 0.001f) {
                it.remove();
            } else if (e.value > liquidCapacity) {
                e.value = liquidCapacity;
            }
        }
    }

    /** 向卫星添加液体，每种液体有独立容量上限，返回实际添加量。 */
    public float addLiquid(Liquid liquid, float amount) {
        if (liquid == null || amount <= 0f)
            return 0f;
        float current = liquidStorage.get(liquid, 0f);
        float space = Math.max(0f, liquidCapacity - current);
        float actual = Math.min(amount, space);
        if (actual > 0f) {
            liquidStorage.put(liquid, current + actual);
        }
        return actual;
    }

    /** 从卫星移除液体，返回实际移除量。 */
    public float removeLiquid(Liquid liquid, float amount) {
        if (liquid == null || amount <= 0f)
            return 0f;
        float current = liquidStorage.get(liquid, 0f);
        float actual = Math.min(amount, current);
        if (actual > 0f) {
            liquidStorage.put(liquid, current - actual);
        }
        return actual;
    }

    /** 获取某种液体储量。 */
    public float getLiquid(Liquid liquid) {
        return liquid == null ? 0f : liquidStorage.get(liquid, 0f);
    }

    /** 获取当前液体总储量。 */
    public float totalLiquid() {
        float total = 0f;
        for (var e : liquidStorage)
            total += e.value;
        return total;
    }

    /** 注册/更新注入台的需求 */
    public void setInjector(Item item, int amount) {
        if (item == null || amount <= 0) {
            if (item != null)
                injectItems.remove(item);
            return;
        }
        injectItems.put(item, amount);
    }

    /** 移除注入台需求 */
    public void removeInjector(Item item) {
        if (item != null)
            injectItems.remove(item);
    }

    /** 根据 ID 查找所属星球上的区块 */
    private @Nullable Sector findSector(int id) {
        if (planet == null || planet.sectors == null)
            return null;
        for (Sector sec : planet.sectors) {
            if (sec.id == id)
                return sec;
        }
        return null;
    }

    /** 开始移动到目标角度 */
    public void startMove(float targetAngle, float duration) {
        this.moveFromAngle = orbitAngle;
        this.moveToAngle = targetAngle;
        this.moveDuration = duration;
        this.moveProgress = 0f;
        this.moving = true;
        if (boundToSector && orbitSpeed == 0f) {
            this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        }
    }

    /** 绑定到指定区块 */
    public void bindToSector(int sectorId) {
        this.targetSectorId = sectorId;
        this.boundToSector = true;
    }

    /** 解除区块绑定 */
    public void unbindSector() {
        this.boundToSector = false;
        if (this.orbitSpeed == 0f) {
            this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        }
    }

    /** 对接另一颗卫星 */
    public void dockWith(Satellite other) {
        if (other == null || other.id == this.id)
            return;
        if (dockedSatellites.contains(other.id))
            return;

        dockedSatellites.add(other.id);
        other.dockedSatellites.add(this.id);

        Satellite master = this.id < other.id ? this : other;
        Satellite slave = this.id < other.id ? other : this;
        slave.dockMaster = master.id;

        master.mapData.mergeFrom(slave.mapData);
        if (slave.missileModule != null) {
            for (SatelliteMissile missile : SatelliteMissile.map.values()) {
                int amount = slave.missileModule.get(missile);
                if (amount > 0) {
                    master.missileModule.add(missile, amount);
                }
            }
        }
        master.recalcStats();
        other.recalcStats();
    }

    /** 通过升级建筑提升卫星等级 */
    public boolean upgrade() {
        if (tier >= maxTier)
            return false;
        upgradeTier++;
        recalcStats();
        return true;
    }

    public boolean isDockMaster() {
        return dockMaster == -1;
    }

    public void rename(String newName) {
        if (newName != null && !newName.trim().isEmpty()) {
            this.name = newName.trim();
        }
    }

    /** 解除与所有其他卫星的对接关系 */
    public void undockAll() {
        dockedSatellites.clear();
        dockMaster = -1;
        recalcStats();
    }

    public void write(Writes write) {
        write.i(id);
        write.str(name);
        write.str(planet.name);
        write.f(orbitAngle);
        write.f(orbitRadius);
        write.f(orbitTilt);
        write.f(orbitSpeed);
        mapData.write(write);
        write.i(dockMaster);
        write.i(dockedSatellites.size);
        for (int i = 0; i < dockedSatellites.size; i++)
            write.i(dockedSatellites.get(i));
        write.i(targetSectorId);
        write.f(moveProgress);
        write.f(moveDuration);
        write.f(moveFromAngle);
        write.f(moveToAngle);
        write.bool(moving);
        write.bool(boundToSector);

        // 扩展数据
        write.f(solarPower);
        write.f(totalSolarPower);
        write.i(tier);
        write.i(upgradeTier);
        // 物品仓库（revision >= 17，改为 ItemModule）
        items.write(write);
        write.i(scannedSectors.size);
        for (int i = 0; i < scannedSectors.size; i++)
            write.i(scannedSectors.get(i));

        // 卫星导弹仓库
        if (missileModule != null) {
            missileModule.write(write);
        } else {
            write.i(0);
            write.i(0);
        }

        // 生命值与模式（revision >= 12）
        write.f(health);
        write.f(maxHealth);
        write.f(powerStorage);
        write.f(powerCapacity);
        write.bool(autoAttackMode);
        write.bool(injectMode);
        write.i(expansionBeaconCount);
        write.i(liquidTankCount);
        write.f(injectTimer);
        write.f(requestTimer);
        write.f(autoAttackTimer);
        write.i(injectItems.size);
        for (var entry : injectItems) {
            write.str(entry.key.name);
            write.i(entry.value);
        }

        // 液体仓库（revision >= 12）
        write.f(liquidCapacity);
        // 物品仓库总容量（revision >= 16）
        write.f(itemStorageCapacity);
        write.i(liquidStorage.size);
        for (var entry : liquidStorage) {
            write.str(entry.key.name);
            write.f(entry.value);
        }

        // 注入台配置（revision >= 12）
        write.i(injectorConfigs.size);
        for (var entry : injectorConfigs) {
            write.i(entry.key);
            InjectorConfig config = entry.value;
            write.str(config.item == null ? "" : config.item.name);
            write.i(config.amount);
        }

        // 当前选中的导弹（revision >= 14）
        write.str(selectedMissile == null ? "" : selectedMissile.name);
    }

    public void read(Reads read, byte revision) {
        id = read.i();
        name = read.str();
        String planetName = read.str();
        planet = mindustry.Vars.content.planet(planetName);
        if (planet == null)
            planet = mindustry.content.Planets.serpulo;
        orbitAngle = read.f();
        orbitRadius = read.f();
        orbitTilt = read.f();
        orbitSpeed = read.f();
        mapData = new SatelliteMapData(this);
        mapData.read(read, revision);
        dockMaster = read.i();
        int dockCount = read.i();
        dockedSatellites.clear();
        for (int i = 0; i < dockCount; i++)
            dockedSatellites.add(read.i());
        targetSectorId = read.i();
        moveProgress = read.f();
        moveDuration = read.f();
        moveFromAngle = read.f();
        moveToAngle = read.f();
        moving = read.bool();
        boundToSector = revision >= 3 && read.bool();

        if (revision >= 2) {
            solarPower = read.f();
            if (revision >= 12) {
                totalSolarPower = read.f();
            } else {
                totalSolarPower = solarPower;
            }
            tier = read.i();
            if (revision >= 11) {
                upgradeTier = read.i();
            } else {
                upgradeTier = Math.max(0, tier - 1 - dockedSatellites.size);
            }
            // 物品仓库：revision >= 17 使用 ItemModule；旧版为 IntIntMap
            if (items == null)
                items = new ItemModule();
            if (revision >= 17) {
                items.read(read, false);
            } else {
                items.clear();
                int storageSize = read.i();
                for (int i = 0; i < storageSize; i++) {
                    int itemId = read.i();
                    int amount = read.i();
                    Item item = content.item(itemId);
                    if (item != null) {
                        items.set(item, amount);
                    }
                }
            }
            scannedSectors.clear();
            int scanSize = read.i();
            for (int i = 0; i < scanSize; i++)
                scannedSectors.add(read.i());

            if (missileModule == null)
                missileModule = new SatelliteMissleModule();
            if (revision >= 10) {
                missileModule.read(read);
            } else {
                missileModule.clear();
            }
        }

        if (revision >= 12) {
            health = read.f();
            maxHealth = read.f();
            if (revision >= 13) {
                powerStorage = read.f();
                powerCapacity = read.f();
            } else {
                powerStorage = 0f;
                powerCapacity = 5000f;
            }
            autoAttackMode = read.bool();
            injectMode = read.bool();
            expansionBeaconCount = read.i();
            liquidTankCount = read.i();
            injectTimer = read.f();
            requestTimer = read.f();
            autoAttackTimer = read.f();
            int injectSize = read.i();
            injectItems.clear();
            for (int i = 0; i < injectSize; i++) {
                String itemName = read.str();
                int amount = read.i();
                Item item = mindustry.Vars.content.item(itemName);
                if (item != null)
                    injectItems.put(item, amount);
            }

            // 液体仓库（revision >= 12）
            liquidCapacity = read.f();
            // 物品仓库总容量（revision >= 16）
            if (revision >= 16) {
                itemStorageCapacity = read.f();
            } else {
                itemStorageCapacity = baseItemStorageCapacity + expansionBeaconCount * 1000f;
            }
            int liquidSize = read.i();
            liquidStorage.clear();
            for (int i = 0; i < liquidSize; i++) {
                String liquidName = read.str();
                float amount = read.f();
                Liquid liquid = mindustry.Vars.content.liquid(liquidName);
                if (liquid != null)
                    liquidStorage.put(liquid, amount);
            }

            // 注入台配置（revision >= 12）
            int injectorSize = read.i();
            injectorConfigs.clear();
            for (int i = 0; i < injectorSize; i++) {
                int buildId = read.i();
                String itemName = read.str();
                int amount = read.i();
                Item item = mindustry.Vars.content.item(itemName);
                if (item != null)
                    injectorConfigs.put(buildId, new InjectorConfig(item, amount));
            }

            // 当前选中的导弹（revision >= 14）
            if (revision >= 14) {
                String missileName = read.str();
                selectedMissile = null;
                if (!missileName.isEmpty()) {
                    for (SatelliteMissile m : SatelliteMissile.map.values()) {
                        if (m.name.equals(missileName)) {
                            selectedMissile = m;
                            break;
                        }
                    }
                }
            } else {
                selectedMissile = null;
            }
        } else {
            health = maxHealth = 1000f;
            autoAttackMode = false;
            injectMode = false;
            expansionBeaconCount = 0;
            liquidTankCount = 0;
            injectTimer = 0f;
            requestTimer = 0f;
            autoAttackTimer = 0f;
            injectItems.clear();
            liquidCapacity = 0f;
            liquidStorage.clear();
            itemStorageCapacity = baseItemStorageCapacity;
        }

        recalcStats();
        idCounter = Math.max(idCounter, id);
    }

    @Override
    public String toString() {
        return "Satellite#" + id + "(" + name + ")";
    }
}
