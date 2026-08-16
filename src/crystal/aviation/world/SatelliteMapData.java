package crystal.aviation.world;

import arc.Core;
import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.Nullable;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.modules.*;
import mindustry.graphics.g3d.*;

import crystal.aviation.*;
import crystal.aviation.world.*;
import crystal.aviation.blocks.*;
import crystal.aviation.blocks.SatelliteUpgradeCenter;

import java.io.*;
import java.util.zip.InflaterInputStream;

import static mindustry.Vars.*;

/**
 * 卫星地图数据。
 * 自 revision 9 起，地图扩展机制改为“衰变式”地板扩展：
 * 实际地图尺寸固定为 maxSize，中心区域为可建造地板，外部为 void（EmptyFloor）。
 * 扩展时把指定区域内的 void 地板转换为可建造地板，不再使用墙边界。
 * 持久化仍使用 Mindustry 原生 .msav 存档字节流（saveData）。
 */
public class SatelliteMapData {
    /** 初始可建造区域边长。 */
    public static final int defaultSize = 31;
    /** 卫星地图总尺寸（最大可扩展范围）。已按需求改为 400×400。 */
    public static final int maxSize = 400;

    public transient Satellite satellite;

    /** 自定义地图文件（玩家上传的 .msav），transient */
    public transient @Nullable Fi customMapFile;
    /** 自定义地图文件绝对路径，用于序列化 */
    public String customMapPath = "";

    public int width = defaultSize, height = defaultSize;
    /** 中心偏移，使 (0,0) 对应地图中心 */
    public int centerX, centerY;
    /** 当前可建造区域左边界（包含） */
    public int buildableLeft;
    /** 当前可建造区域右边界（包含） */
    public int buildableRight;
    /** 当前可建造区域下边界（包含） */
    public int buildableBottom;
    /** 当前可建造区域上边界（包含） */
    public int buildableTop;
    /** 可建造地板方块名称 */
    public String floorName = CrystalAviationSystemCore.defaultSatelliteFloor;
    /** 外部 void/未探索地板方块名称 */
    public String voidFloorName = Blocks.empty.name;
    /** 核心方块名称 */
    public String coreName = "space-core";

    /** 卫星世界存档数据（SaveIO 格式的 .msav 字节流，包含地形与实体）。这是唯一的持久化地图状态。 */
    public byte[] saveData = new byte[0];

    /**
     * 建筑模块快照（items/liquids/power），键为 tile pos。
     * 作为 SaveIO 建筑模块数据的冗余备份，确保卫星地图读档时模块不丢失。
     */
    private ObjectMap<Integer, ModuleSnapshot> moduleSnapshots = new ObjectMap<>();

    /** transient 工作缓存：由 saveData 解码或默认生成得到，不参与序列化。 */
    private transient TileEntry[][] tiles;
    /** transient 工作缓存：由 tiles 重建。 */
    private transient Seq<BuildingEntry> buildings = new Seq<>();

    /** 调试：tiles 缓存是否已加载 */
    public boolean hasTilesLoaded() {
        return tiles != null;
    }

    /** 调试：buildings 缓存数量 */
    public int buildingCacheSize() {
        return buildings.size;
    }

    public SatelliteMapData(Satellite satellite) {
        this.satellite = satellite;
        this.centerX = width / 2;
        this.centerY = height / 2;
        resetBuildableBounds();
    }

    /** 将可建造范围重置为默认中心区域。 */
    public void resetBuildableBounds() {
        int half = defaultSize / 2;
        this.buildableLeft = Mathf.clamp(centerX - half, 1, width - 2);
        this.buildableRight = Mathf.clamp(centerX + half, 1, width - 2);
        this.buildableBottom = Mathf.clamp(centerY - half, 1, height - 2);
        this.buildableTop = Mathf.clamp(centerY + half, 1, height - 2);
    }

    public SatelliteMapData() {
    }

    /**
     * 生成默认卫星地图。
     * 实际地图尺寸固定为 maxSize，中心 defaultSize x defaultSize 区域为初始可建造地板，
     * 其余区域为 void 地板。扩展时把 void 地板转换为可建造地板，无需重载世界。
     */
    public void generateDefault() {
        // 强制使用 spaceFloor 作为可建造地板，忽略存档中可能存在的旧 floorName
        Floor buildableFloor = resolveSpaceFloor();
        Floor voidFloor = resolveVoidFloor();

        Block core = CrystalAviationSystemCore.spaceCore;
        if (core == null)
            core = content.block(coreName);
        if (core == null)
            core = Blocks.coreShard;

        // 使用最大尺寸作为实际地图尺寸
        this.width = maxSize;
        this.height = maxSize;
        this.centerX = width / 2;
        this.centerY = height / 2;

        // 初始可建造区域位于中心
        resetBuildableBounds();

        this.tiles = new TileEntry[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileEntry e = new TileEntry();
                boolean inside = x >= buildableLeft && x <= buildableRight
                                 && y >= buildableBottom && y <= buildableTop;
                e.floor = inside ? buildableFloor.name : voidFloor.name;
                e.block = Blocks.air.name;
                tiles[y][x] = e;
            }
        }

        // spaceCore 为 4x4，satelliteControlCenter 为 4x4，
        // 在核心右侧紧邻放置一个卫星控制中心，其余建筑不生成。
        placeBuildingLocal(centerX, centerY, core, Team.sharded, 0, null);
        placeBuildingLocal(centerX + 4, centerY, CrystalAviationSystemCore.satelliteControlCenter, Team.sharded, 0,
                           null);

        rebuildBuildings();
    }

    /** 获取当前注册的可建造地板，优先使用 spaceCore 中定义的 spaceFloor。 */
    private Floor resolveSpaceFloor() {
        Floor floor = CrystalAviationSystemCore.spaceFloor != null ? (Floor) CrystalAviationSystemCore.spaceFloor
                      : null;
        if (floor == null)
            floor = (Floor) content.block(CrystalAviationSystemCore.defaultSatelliteFloor);
        if (floor == null)
            floor = (Floor) content.block(floorName);
        if (floor == null)
            floor = (Floor) Blocks.metalFloor;
        return floor;
    }

    /** 获取外部 void 地板。 */
    private Floor resolveVoidFloor() {
        Floor floor = (Floor) content.block(voidFloorName);
        if (floor == null)
            floor = (Floor) Blocks.empty;
        return floor;
    }

    /**
     * 将 transient tiles 中的旧可建造地板替换为 spaceFloor，并把可建造范围外的非 void 地板还原为 void。
     * 用于兼容旧存档中保存的 metal-floor 等旧地板，确保玩家进入卫星地图时看到的是 spaceFloor。
     * 注意：不再根据 buildableBounds 把 void 强制转为 spaceFloor，否则会无限扩大可建造区域。
     */
    public void migrateToSpaceFloor() {
        if (tiles == null)
            return;
        Floor spaceFloor = resolveSpaceFloor();
        Floor voidFloor = resolveVoidFloor();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileEntry e = tiles[y][x];
                Block floor = content.block(e.floor);
                // 已经是 spaceFloor 或 voidFloor 的无需处理
                if (floor == spaceFloor || floor == voidFloor)
                    continue;
                boolean inside = x >= buildableLeft && x <= buildableRight
                                 && y >= buildableBottom && y <= buildableTop;
                if (inside) {
                    e.floor = spaceFloor.name;
                } else {
                    e.floor = voidFloor.name;
                }
            }
        }
        floorName = spaceFloor.name;
    }

    /** 是否使用了自定义地图文件 */
    public boolean hasCustomMap() {
        return customMapFile != null && customMapFile.exists();
    }

    /** 设置自定义地图文件引用 */
    public void setCustomMapFile(@Nullable Fi file) {
        this.customMapFile = file;
        this.customMapPath = file != null ? file.absolutePath() : "";
    }

    /** 从地图文件加载卫星地图数据到 transient tiles，不生成 saveData。 */
    public void loadFromMapFile(Fi file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("Map file not found: " + file);
        }
        loadFromMapData(file);
        setCustomMapFile(file);
    }

    /** 从 Map 对象加载数据到 transient tiles（不污染当前游戏世界）。 */
    public void loadFromMap(Map map) throws IOException {
        if (map == null || map.file == null || !map.file.exists()) {
            throw new IOException("Map or map file is null/missing: " + map);
        }
        loadFromMapData(map.file);
    }

    /**
     * 手动解析 .msav 的 map region，不调用 MapIO.loadMap/SaveIO.load，避免 logic.reset()
     * 或修改全局 state。解析结果写入 transient tiles。
     */
    private void loadFromMapData(Fi file) throws IOException {
        // 在解析过程中先用足够大的占位尺寸创建 tiles；resize 回调会回填真实宽高
        this.tiles = new TileEntry[maxSize][maxSize];
        this.buildings.clear();
        for (int y = 0; y < maxSize; y++) {
            for (int x = 0; x < maxSize; x++) {
                tiles[y][x] = new TileEntry();
            }
        }

        // 每个 tile 使用独立的 DecodeTile，避免复用导致 floor/overlay 数据串扰
        DecodeTile[][] decodeTiles = new DecodeTile[maxSize][maxSize];
        DecodeTile[] current = new DecodeTile[1];
        int[] parsedSize = new int[2];

        try (InputStream is = new InflaterInputStream(file.read(bufferSize));
                    CounterInputStream counter = new CounterInputStream(is);
                    DataInputStream stream = new DataInputStream(counter)) {

            SaveIO.readHeader(stream);
            int version = stream.readInt();
            SaveVersion ver = SaveIO.getSaveWriter(version);
            if (ver == null)
                throw new IOException("Unknown save version: " + version);

            // 跳过 meta、patches（版本 >= 12）、content，直接读取 map region
            ver.skipChunk(stream);
            if (version >= 12)
                ver.skipChunk(stream);
            ver.skipChunk(stream);

            WorldContext ctx = new WorldContext() {
                @Override
                public void resize(int width, int height) {
                    parsedSize[0] = width;
                    parsedSize[1] = height;
                    SatelliteMapData.this.width = width;
                    SatelliteMapData.this.height = height;
                    SatelliteMapData.this.centerX = width / 2;
                    SatelliteMapData.this.centerY = height / 2;
                }

                @Override
                public void begin() {
                }

                @Override
                public void end() {
                }

                @Override
                public boolean isGenerating() {
                    return false;
                }

                @Override
                public void onReadBuilding() {
                    // 只有中心格会触发，此时建筑实体数据已读取完毕
                    DecodeTile t = current[0];
                    if (t == null || t.build == null)
                        return;
                    int x = t.x;
                    int y = t.y;
                    TileEntry e = tiles[y][x];
                    // 必须同步写入 TileEntry，否则 rebuildBuildings（从 tiles 遍历）会漏掉该建筑
                    e.block = t.block().name;
                    e.team = (byte) t.team().id;
                    e.rotation = (byte) t.build.rotation;
                    // 保存原始配置对象，便于后续重新应用到世界
                    e.config = t.build.config();
                    // 在 SaveIO.load 失败后的回退路径中，保留建筑 items/liquids/power
                    e.itemBytes = writeModule(t.build.items);
                    e.liquidBytes = writeModule(t.build.liquids);
                    e.powerBytes = writeModule(t.build.power);

                    BuildingEntry be = new BuildingEntry();
                    be.lx = (short) x;
                    be.ly = (short) y;
                    be.block = e.block;
                    be.team = e.team;
                    be.rotation = e.rotation;
                    be.config = e.config;
                    buildings.add(be);
                }

                @Override
                public Sector getSector() {
                    return null;
                }

                @Override
                public boolean isMap() {
                    return false;
                }

                @Override
                public Tile tile(int index) {
                    int x = index % parsedSize[0];
                    int y = index / parsedSize[0];
                    DecodeTile t = decodeTiles[y][x];
                    if (t == null) {
                        t = new DecodeTile(x, y, 0, 0);
                        decodeTiles[y][x] = t;
                    }
                    current[0] = t;
                    return t;
                }

                @Override
                public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                    DecodeTile t = new DecodeTile(x, y, floorID, overlayID);
                    decodeTiles[y][x] = t;
                    current[0] = t;

                    // 立即记录地板信息；onReadTileData 只在有额外数据时才调用，
                    // 对于普通 tile 不会触发，因此必须在这里捕获 floor。
                    TileEntry e = tiles[y][x];
                    Block floor = content.block(floorID);
                    if (floor instanceof Floor) {
                        e.floor = floor.name;
                    } else {
                        Floor fallback = (Floor) content.block(floorName);
                        e.floor = fallback != null ? fallback.name : CrystalAviationSystemCore.defaultSatelliteFloor;
                    }
                    return t;
                }

                @Override
                public void onReadTileData() {
                    DecodeTile t = current[0];
                    if (t == null)
                        return;
                    int x = t.x;
                    int y = t.y;
                    TileEntry e = tiles[y][x];
                    e.floor = t.floor().name;
                    // 非中心格的多格建筑在存档中 block 为 air，只有中心格会保存实际 block
                    e.block = t.block().name;
                }
            };

            ver.readRegion("map", stream, counter, in -> ver.readMap(in, ctx));
        } catch (Exception e) {
            throw new IOException("Failed to read map region from " + file, e);
        }

        // 自定义地图：整张地图都可建造（留出外边界一圈 void 作为保护）
        this.buildableLeft = 1;
        this.buildableRight = Math.max(1, width - 2);
        this.buildableBottom = 1;
        this.buildableTop = Math.max(1, height - 2);

        ensureHasCore();
        rebuildBuildings();
    }

    /** 确保 transient tiles 已就绪。优先从 saveData 解码，其次自定义地图，最后默认生成。 */
    private TileEntry[][] ensureTiles() {
        if (tiles != null)
            return tiles;

        if (saveData != null && saveData.length > 0) {
            decodeSaveData();
        } else if (customMapPath != null && !customMapPath.isEmpty()) {
            customMapFile = Core.files.absolute(customMapPath);
            if (customMapFile.exists()) {
                try {
                    loadFromMapFile(customMapFile);
                } catch (Exception e) {
                    generateDefault();
                }
            } else {
                generateDefault();
            }
        } else {
            generateDefault();
        }
        // 强制把可建造区域地板替换为 spaceFloor（兼容旧存档）
        migrateToSpaceFloor();
        return tiles;
    }

    /** 将 saveData 解码为 transient tiles，不加载到当前世界。 */
    private void decodeSaveData() {
        if (saveData == null || saveData.length == 0) {
            generateDefault();
            return;
        }
        try {
            // 保存当前可建造范围，避免 loadFromMapData 将其重置为整张地图
            int oldLeft = buildableLeft;
            int oldRight = buildableRight;
            int oldBottom = buildableBottom;
            int oldTop = buildableTop;

            Fi temp = tempFile();
            temp.writeBytes(saveData);
            loadFromMapData(temp);

            // 恢复可建造范围
            buildableLeft = Mathf.clamp(oldLeft, 1, width - 2);
            buildableRight = Mathf.clamp(oldRight, 1, width - 2);
            buildableBottom = Mathf.clamp(oldBottom, 1, height - 2);
            buildableTop = Mathf.clamp(oldTop, 1, height - 2);

        } catch (Exception e) {
            saveData = new byte[0];
            generateDefault();
        }
    }

    /** 确保地图中至少有一个核心 */
    private void ensureHasCore() {
        if (tiles == null)
            return;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Block b = content.block(tiles[y][x].block);
                if (b instanceof CoreBlock) {
                    return;
                }
            }
        }
        int cx = width / 2, cy = height / 2;
        Block core = CrystalAviationSystemCore.spaceCore;
        if (core == null)
            core = content.block(coreName);
        if (core == null)
            core = Blocks.coreShard;
        placeBuildingLocal(cx, cy, core, Team.sharded, 0, null);
    }

    public int worldX(int localX) {
        return localX - centerX;
    }

    public int worldY(int localY) {
        return localY - centerY;
    }

    public int localX(int worldX) {
        return worldX + centerX;
    }

    public int localY(int worldY) {
        return worldY + centerY;
    }

    public void placeBuildingLocal(int lx, int ly, Block block, Team team, int rotation, Object config) {
        if (tiles == null)
            return;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height)
            return;
        TileEntry e = tiles[ly][lx];
        e.block = block.name;
        e.team = (byte) team.id;
        e.rotation = (byte) rotation;
        e.config = config;
    }

    public void removeBuildingLocal(int lx, int ly) {
        if (tiles == null)
            return;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height)
            return;
        tiles[ly][lx].block = Blocks.air.name;
    }

    /**
     * 将本地地图数据应用到当前世界（进入卫星时调用）。
     * 若存在 saveData（Mindustry 原生 .msav 字节流），优先直接调用 SaveIO.load 完整加载，
     * 这样可以正确恢复单位、传送带物品、核心库存等全部世界状态。
     * 若 saveData 为空/损坏，则回退到 transient tiles 重建世界。
     */
    public void applyToWorld() {
        if (world == null)
            return;
        int targetId = SatelliteManager.currentSatelliteId;
        try {
            boolean hadSaveData = saveData != null && saveData.length > 0;

            if (hadSaveData) {
                Exception loadError = null;
                try {
                    Fi temp = tempFile();
                    temp.writeBytes(saveData);

                    // SaveIO.load 会重置 state.map，导致卫星标签丢失并触发污染回退。
                    // 先保存标签，加载后立即恢复。
                    StringMap preservedTags = (state.map != null && state.map.tags != null)
                                              ? new StringMap(state.map.tags)
                                              : new StringMap();

                    SaveIO.load(temp);

                    if (state.map != null) {
                        state.map.tags.putAll(preservedTags);
                        // 防御：SaveIO.load 创建的 state.map 可能只含 name/width/height，
                        // 显式补回卫星标签，避免后续校验失败导致不必要的 tiles 回退。
                        state.map.tags.put("crystal-aviation-satellite", String.valueOf(targetId));
                    }

                    // 校验：加载的存档必须是当前卫星的卫星地图。tag 不匹配说明数据被覆盖，必须回退。
                    // sector 非 null 时强制清空，避免把卫星地图误判为战役区块。
                    String loadedTag = state.map != null ? state.map.tags.get("crystal-aviation-satellite") : null;
                    if (loadedTag == null || !loadedTag.equals(String.valueOf(targetId))) {
                        throw new IOException("Satellite saveData tag mismatch (tag='" + loadedTag
                                              + "', expected='" + targetId + "')");
                    }
                    if (state.rules.sector != null) {
                        state.rules.sector = null;
                    }

                    width = world.tiles.width;
                    height = world.tiles.height;
                    centerX = width / 2;
                    centerY = height / 2;

                    // 强制替换存档中的旧地板为 spaceFloor
                    replaceWorldFloorsWithSpaceFloor();

                    // 加载后再次兜底：移除任何残留的扩展块，防止脏存档重进后自动扩展
                    removeMapExpandersFromWorld();

                    restoreSatelliteMapTags(targetId);
                    applySatelliteRules();
                    setupBackgroundRules();
                    rebindBuildings();


                    restoreModuleSnapshots();

                    return;
                } catch (Exception e) {
                    loadError = e;
                    // 加载失败时保留 saveData 以便调试，并继续走 tiles 回退
                }
            }

            // 1. 确保 transient tiles 已就绪（saveData -> 自定义地图 -> 默认生成）
            ensureTiles();

            // 2. 验证 saveData 是否成功解码；失败则清空，走默认 tiles
            if (hadSaveData && (tiles == null || tiles.length == 0 || tiles[0].length == 0)) {
                saveData = new byte[0];
                ensureTiles();
            }

            if (tiles == null) {
                throw new IOException("tiles is null after ensureTiles");
            }

            // 3. 从 tiles 重建世界；若原本没有 saveData，生成后捕获为 saveData
            applyFallbackToWorld(targetId, !hadSaveData);

            centerX = width / 2;
            centerY = height / 2;
            restoreSatelliteMapTags(targetId);
            applySatelliteRules();
            setupBackgroundRules();

            // 兜底：从 tiles 回退重建时也可能残留扩展块，加载后统一移除
            removeMapExpandersFromWorld();

            rebindBuildings();


            restoreModuleSnapshots();

        } catch (Throwable t) {
            // ignored
        }
    }

    /**
     * 使用 transient tiles 生成世界。
     *
     * @param targetId     当前卫星 ID，仅用于日志
     * @param captureAfter 为 true 时，在生成结束后调用 captureFromWorld() 把世界保存为 saveData；
     *                     为 false 时说明本次是从已有 saveData 解码而来，无需重复捕获。
     */
    private void applyFallbackToWorld(int targetId, boolean captureAfter) {
        ensureTiles();
        world.loadGenerator(width, height, genTiles -> {
            Floor buildableFloor = resolveSpaceFloor();
            Floor voidFloor = resolveVoidFloor();

            // 第一步：铺设全部地板，避免多格建筑 setBlock 时相邻 tile 尚未初始化。
            // 直接根据 tiles 缓存中的实际 floor 生成，而不是根据 buildableBounds，
            // 避免 bounds 大于实际已转换区域时把 void 错误填充为可建造地板。
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    TileEntry e = tiles[y][x];
                    Block floorBlock = content.block(e.floor);
                    Floor targetFloor;
                    if (floorBlock == buildableFloor) {
                        targetFloor = buildableFloor;
                    } else if (floorBlock == voidFloor) {
                        targetFloor = voidFloor;
                    } else {
                        // 未知/旧地板：根据是否在 bounds 内回退判断
                        boolean inside = x >= buildableLeft && x <= buildableRight
                                         && y >= buildableBottom && y <= buildableTop;
                        targetFloor = inside ? buildableFloor : voidFloor;
                    }

                    genTiles.set(x, y, new Tile(x, y, targetFloor, Blocks.air, Blocks.air));
                }
            }

            // 第二步：放置建筑；多格建筑只需中心格调用 setBlock
            int placed = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    TileEntry e = tiles[y][x];
                    Block block = content.block(e.block);
                    if (block != null && block != Blocks.air) {
                        Tile tile = genTiles.getn(x, y);
                        if (tile.block() != block) {
                            tile.setBlock(block, Team.get(e.team), e.rotation);
                            placed++;
                        }
                        // 恢复建筑配置；block 只接受其 configurations 中注册的类型，不匹配的 String 会被忽略
                        if (tile.build != null && e.config != null) {
                            try {
                                tile.build.configured(null, e.config);
                            } catch (Throwable t) {
                                // ignored
                            }
                        }
                    }
                }
            }
        });

        // 第三步：从 tiles 缓存恢复建筑 items/liquids/power（SaveIO.load 失败后的回退路径）
        int restoredItems = 0, restoredLiquids = 0, restoredPower = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileEntry e = tiles[y][x];
                if (e == null)
                    continue;
                Tile tile = world.tile(x, y);
                if (tile == null || tile.build == null)
                    continue;
                if (e.itemBytes != null) {
                    readModule(tile.build.items, e.itemBytes);
                    restoredItems++;
                }
                if (e.liquidBytes != null) {
                    readModule(tile.build.liquids, e.liquidBytes);
                    restoredLiquids++;
                }
                if (e.powerBytes != null) {
                    readModule(tile.build.power, e.powerBytes);
                    restoredPower++;
                }
            }
        }

        // 只有从 tiles（无 saveData）回退生成时，才需要捕获为 saveData；否则已有 saveData 无需重复写入
        if (captureAfter) {
            captureFromWorld();
            SatelliteManager.save();
        }
    }

    /**
     * 将当前已加载世界中的旧可建造地板替换为 spaceFloor，并把可建造范围外的非 void 地板还原为 void。
     * 注意：不再根据 buildableBounds 把 void 强制转为 spaceFloor，否则当 bounds 大于实际已转换区域时，
     * 每次加载都会把 bounds 内的 void 自动扩展为可建造地板。
     */
    private void replaceWorldFloorsWithSpaceFloor() {
        if (world == null || world.tiles == null)
            return;
        Floor spaceFloor = resolveSpaceFloor();
        Floor voidFloor = resolveVoidFloor();
        for (int y = 0; y < world.tiles.height; y++) {
            for (int x = 0; x < world.tiles.width; x++) {
                Tile t = world.tile(x, y);
                if (t == null)
                    continue;
                boolean inside = x >= buildableLeft && x <= buildableRight
                                 && y >= buildableBottom && y <= buildableTop;
                // 只替换旧的/非标准的可建造地板；保留 void，避免自动扩展
                if (inside && t.floor() != spaceFloor && t.floor() != voidFloor) {
                    t.setFloor(spaceFloor);
                } else if (!inside && t.floor() != voidFloor) {
                    t.setFloor(voidFloor);
                }
            }
        }
    }

    /** 强制移除当前世界中所有残留的 SatelliteMapExpander 建筑，防止脏存档导致重进后自动扩展。 */
    private void removeMapExpandersFromWorld() {
        removeMapExpandersFromWorld(false);
    }

    /**
     * 移除当前世界中的 SatelliteMapExpander 建筑。
     *
     * @param onlyFinished 为 true 时只移除已经扩展完成的（expanded=true），
     *                     用于保存时避免误删正在充能的扩展块；
     *                     为 false 时移除所有残留扩展块，用于加载后的兜底清理。
     */
    private void removeMapExpandersFromWorld(boolean onlyFinished) {
        if (world == null || world.tiles == null)
            return;
        int removed = 0;
        for (int y = 0; y < world.tiles.height; y++) {
            for (int x = 0; x < world.tiles.width; x++) {
                Tile t = world.tile(x, y);
                if (t == null || !(t.block() instanceof SatelliteMapExpander))
                    continue;
                if (onlyFinished) {
                    if (!(t.build instanceof SatelliteMapExpander.SatelliteMapExpanderBuild build) || !build.expanded)
                        continue;
                }
                t.setBlock(Blocks.air);
                removed++;
            }
        }
    }

    /** 恢复 state.map 上的卫星标签与尺寸，确保 onWorldLoaded 与后续存档能识别当前卫星。 */
    private void restoreSatelliteMapTags(int targetId) {
        if (state.map == null)
            return;
        Satellite s = SatelliteManager.get(targetId);
        if (s != null) {
            state.map.tags.put("name", s.name);
            state.map.tags.put("author", "Crystal Aviation");
        }
        state.map.tags.put("crystal-aviation-satellite", String.valueOf(targetId));
        if (SatelliteManager.lastSector != null) {
            state.map.tags.put("crystal-aviation-last-sector",
                               SatelliteManager.lastSector.planet.name + ":" + SatelliteManager.lastSector.id);
        }
        state.map.width = width;
        state.map.height = height;
    }

    /** 配置卫星地图的 Rules：无波次、黑雾边界、云层关闭。星球背景单独设置。 */
    private void applySatelliteRules() {
        state.rules.waves = false;
        state.rules.pvp = false;
        state.rules.editor = false;
        state.rules.backgroundTexture = null;
        state.rules.borderDarkness = true;
        state.rules.cloudColor.a = 0f;
        state.rules.sector = null;
    }

    /** 配置卫星地图的 Rules：以中心天体作为背景，外缘黑雾过渡。 */
    private void setupBackgroundRules() {
        if (satellite == null || satellite.planet == null)
            return;

        state.rules.backgroundTexture = null;
        state.rules.borderDarkness = true;

        PlanetParams params = new PlanetParams();
        params.planet = satellite.planet;
        params.camPos.set(0.3f, 0.5f, 3.5f);
        params.camDir.set(0f, 0f, -1f);
        params.camUp.set(0f, 1f, 0f);
        params.zoom = 0.85f;
        params.drawSkybox = true;
        params.drawUi = false;
        state.rules.planetBackground = params;

        state.rules.cloudColor.a = 0f;
    }

    /** 统计当前世界中建筑数量以及带有 items/liquids/power 模块的建筑数量，用于调试。 */
    private String countBuildingsWithModules() {
        if (world == null || world.tiles == null)
            return "world not ready";
        int total = 0, withItems = 0, withLiquids = 0, withPower = 0;
        for (int y = 0; y < world.tiles.height; y++) {
            for (int x = 0; x < world.tiles.width; x++) {
                Tile t = world.tile(x, y);
                if (t == null || t.build == null)
                    continue;
                total++;
                if (t.build.items != null && t.build.items.total() > 0)
                    withItems++;
                if (t.build.liquids != null && t.build.liquids.currentAmount() > 0.001f)
                    withLiquids++;
                if (t.build.power != null && t.build.power.status > 0.001f)
                    withPower++;
            }
        }
        return "total=" + total + ", withItems=" + withItems + ", withLiquids=" + withLiquids + ", withPower="
               + withPower;
    }

    /** 从当前世界捕获所有建筑的 items/liquids/power 模块快照。 */
    private void captureModuleSnapshots() {
        if (world == null || world.tiles == null)
            return;
        moduleSnapshots.clear();
        int captured = 0;
        int itemCount = 0, liquidCount = 0, powerCount = 0;
        for (int y = 0; y < world.tiles.height; y++) {
            for (int x = 0; x < world.tiles.width; x++) {
                Tile t = world.tile(x, y);
                if (t == null || t.build == null)
                    continue;
                byte[] itemBytes = writeModule(t.build.items);
                byte[] liquidBytes = writeModule(t.build.liquids);
                byte[] powerBytes = writeModule(t.build.power);
                if (itemBytes != null || liquidBytes != null || powerBytes != null) {
                    ModuleSnapshot snap = new ModuleSnapshot();
                    snap.itemBytes = itemBytes;
                    snap.liquidBytes = liquidBytes;
                    snap.powerBytes = powerBytes;
                    moduleSnapshots.put(t.pos(), snap);
                    captured++;
                    if (itemBytes != null)
                        itemCount++;
                    if (liquidBytes != null)
                        liquidCount++;
                    if (powerBytes != null)
                        powerCount++;
                }
            }
        }
    }

    /** 将模块快照恢复到当前已加载的世界。 */
    private void restoreModuleSnapshots() {
        if (moduleSnapshots == null || moduleSnapshots.size == 0)
            return;
        int restored = 0;
        int itemCount = 0, liquidCount = 0, powerCount = 0;
        int skippedCore = 0;
        for (var entry : moduleSnapshots.entries()) {
            Tile t = world.tile(entry.key);
            if (t == null || t.build == null)
                continue;
            ModuleSnapshot snap = entry.value;

            // 卫星核心 items 与卫星 items 共用同一对象，快照是存档时的旧值；
            // 若从快照恢复，会把发射台在两次存档之间送到卫星的物品覆盖掉。
            boolean isCore = t.build instanceof SatelliteCoreBlock.SatelliteCoreBuild;
            if (snap.itemBytes != null) {
                if (isCore) {
                    skippedCore++;
                } else {
                    readModule(t.build.items, snap.itemBytes);
                    itemCount++;
                }
            }
            if (snap.liquidBytes != null) {
                readModule(t.build.liquids, snap.liquidBytes);
                liquidCount++;
            }
            if (snap.powerBytes != null) {
                readModule(t.build.power, snap.powerBytes);
                powerCount++;
            }
            restored++;
        }
    }

    /** 将 ItemModule 序列化为字节数组；为空或 null 时返回 null。 */
    private static @Nullable byte[] writeModule(@Nullable ItemModule items) {
        if (items == null || items.total() <= 0)
            return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            items.write(new Writes(dos));
            dos.close();
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 将 LiquidModule 序列化为字节数组；为空或 null 时返回 null。 */
    private static @Nullable byte[] writeModule(@Nullable LiquidModule liquids) {
        if (liquids == null || liquids.currentAmount() <= 0.001f)
            return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            liquids.write(new Writes(dos));
            dos.close();
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 将 PowerModule 序列化为字节数组；为空或 null 时返回 null。 */
    private static @Nullable byte[] writeModule(@Nullable PowerModule power) {
        if (power == null || power.status <= 0.001f)
            return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            power.write(new Writes(dos));
            dos.close();
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从字节数组恢复 ItemModule；bytes 为 null 时不操作。 */
    private static void readModule(@Nullable ItemModule items, @Nullable byte[] bytes) {
        if (items == null || bytes == null || bytes.length == 0)
            return;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            items.read(new Reads(dis), false);
            dis.close();
        } catch (Throwable t) {
            // ignored
        }
    }

    /** 从字节数组恢复 LiquidModule；bytes 为 null 时不操作。 */
    private static void readModule(@Nullable LiquidModule liquids, @Nullable byte[] bytes) {
        if (liquids == null || bytes == null || bytes.length == 0)
            return;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            liquids.read(new Reads(dis), false);
            dis.close();
        } catch (Throwable t) {
            // ignored
        }
    }

    /** 从字节数组恢复 PowerModule；bytes 为 null 时不操作。 */
    private static void readModule(@Nullable PowerModule power, @Nullable byte[] bytes) {
        if (power == null || bytes == null || bytes.length == 0)
            return;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            power.read(new Reads(dis));
            dis.close();
        } catch (Throwable t) {
            // ignored
        }
    }

    /** 将当前世界读取完整状态（保存前调用），结果写入 saveData。 */
    public void captureFromWorld() {
        if (world == null || world.tiles == null || SatelliteManager.isCapturingWorld()
                || SatelliteManager.isExitingSatellite())
            return;
        // 防御：菜单/重置状态下世界可能已被清空，不应覆盖有效存档
        if (state != null && state.isMenu()) {
            return;
        }
        SatelliteManager.setCapturingWorld(true);
        try {
            applySatelliteRules();
            // 保存前移除已经扩展完成但尚未消失的残留扩展块，避免脏数据被写入 saveData
            removeMapExpandersFromWorld(true);
            // 同时更新 state.map.tags 与 extraTags，确保运行时标签和存档 meta 都正确
            StringMap tags = state.map != null && state.map.tags != null ? state.map.tags : new StringMap();
            if (satellite != null) {
                tags.put("name", satellite.name);
                tags.put("author", "Crystal Aviation");
                tags.put("crystal-aviation-satellite", String.valueOf(satellite.id));
                if (SatelliteManager.lastSector != null) {
                    tags.put("crystal-aviation-last-sector",
                             SatelliteManager.lastSector.planet.name + ":" + SatelliteManager.lastSector.id);
                }
            }
            if (state.map != null && world.tiles != null) {
                state.map.width = world.tiles.width;
                state.map.height = world.tiles.height;
            }

            Fi temp = tempFile();
            // 使用 extraTags 把卫星标签写入 .msav meta，确保读档时 state.map 能带标签
            SaveOptions options = new SaveOptions();
            options.extraTags = tags;
            String beforeSaveInfo = countBuildingsWithModules();

            SaveIO.save(temp, options);
            saveData = temp.readBytes();
            width = world.tiles.width;
            height = world.tiles.height;
            centerX = width / 2;
            centerY = height / 2;

            String afterSaveInfo = countBuildingsWithModules();

            // 显式捕获建筑模块快照，作为 SaveIO 模块数据的冗余备份
            captureModuleSnapshots();

            // 清除 transient 缓存，下次从 saveData 重新解码
            tiles = null;
            buildings.clear();

        } catch (Throwable t) {
            // ignored
        } finally {
            SatelliteManager.setCapturingWorld(false);
        }
    }

    public void rebindBuildings() {
        if (world == null || world.tiles == null)
            return;

        // 直接根据当前世界中实际存在的建筑数量重新计算容量与计数
        if (satellite != null) {
            satellite.recalcStorageCapacityFromWorld();
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile tile = world.tile(x, y);
                if (tile == null || tile.build == null)
                    continue;
                Block block = tile.block();
                if (block instanceof SatelliteControlCenter) {
                    ((SatelliteControlCenter.SatelliteControlCenterBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteExpansionBeacon) {
                    ((SatelliteExpansionBeacon.SatelliteExpansionBeaconBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteSolarArray) {
                    ((SatelliteSolarArray.SatelliteSolarArrayBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteUpgradeCenter) {
                    ((SatelliteUpgradeCenter.SatelliteUpgradeCenterBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteLiquidTank) {
                    ((SatelliteLiquidTank.SatelliteLiquidTankBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteInjector) {
                    ((SatelliteInjector.SatelliteInjectorBuild) tile.build).satelliteId = satellite.id;
                } else if (block instanceof SatelliteMapExpander) {
                    ((SatelliteMapExpander.SatelliteMapExpanderBuild) tile.build).satelliteId = satellite.id;
                }
            }
        }
    }

    /** 合并另一颗卫星的地图到当前地图。仅在 transient tiles 上操作，并清空 saveData 使其在下次进入时生效。 */
    public void mergeFrom(SatelliteMapData other) {
        ensureTiles();
        other.ensureTiles();

        int newWidth = Math.max(this.width, other.width + 4);
        int newHeight = Math.max(this.height, other.height + 4);
        if (newWidth > maxSize)
            newWidth = maxSize;
        if (newHeight > maxSize)
            newHeight = maxSize;

        TileEntry[][] oldTiles = this.tiles;
        int oldW = this.width, oldH = this.height;

        this.width = newWidth;
        this.height = newHeight;
        this.centerX = newWidth / 2;
        this.centerY = newHeight / 2;
        this.tiles = new TileEntry[newHeight][newWidth];

        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;
        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                TileEntry e = new TileEntry();
                e.floor = voidFloor.name;
                e.block = Blocks.air.name;
                this.tiles[y][x] = e;
            }
        }

        copyMap(oldTiles, this.tiles, 0, 0, oldW, oldH, 1, 1);
        copyMap(other.tiles, this.tiles, 0, 0, other.width, other.height,
                newWidth - other.width - 1, 1);

        // 根据实际地板恢复可建造范围
        recalcBuildableBounds();
        rebuildBuildings();
        // 合并后实体坐标失效，清空 saveData，下次进入时以 tiles 重建世界
        saveData = new byte[0];
    }

    /**
     * 扩大可建造范围：把新区域中的 void 地板转换为可建造地板。
     * 若玩家当前正处于该卫星世界中，会直接修改当前世界并保存。
     */
    public void expand(int expandLeft, int expandRight, int expandBottom, int expandTop) {
        boolean currentlyInside = SatelliteManager.currentSatelliteId >= 0
                                  && satellite != null
                                  && SatelliteManager.currentSatelliteId == satellite.id;

        if (currentlyInside) {
            // 先保存当前世界到 saveData，确保扩展基于最新状态
            captureFromWorld();
        }

        ensureTiles();
        int oldLeft = buildableLeft;
        int oldRight = buildableRight;
        int oldBottom = buildableBottom;
        int oldTop = buildableTop;

        int newLeft = Mathf.clamp(buildableLeft - expandLeft, 1, width - 2);
        int newRight = Mathf.clamp(buildableRight + expandRight, 1, width - 2);
        int newBottom = Mathf.clamp(buildableBottom - expandBottom, 1, height - 2);
        int newTop = Mathf.clamp(buildableTop + expandTop, 1, height - 2);

        if (newLeft == oldLeft && newRight == oldRight && newBottom == oldBottom && newTop == oldTop)
            return;

        buildableLeft = newLeft;
        buildableRight = newRight;
        buildableBottom = newBottom;
        buildableTop = newTop;

        // 把新增的条带区域中的 void 地板转换为可建造地板
        convertNewStripsToBuildable(oldLeft, oldRight, oldBottom, oldTop);

        if (currentlyInside) {
            applyBuildableAreaToWorld(oldLeft, oldRight, oldBottom, oldTop);
            captureFromWorld();
            SatelliteManager.save();
        } else {
            // 不在世界内时，tiles 已更新，清除 saveData 使下次进入时重新生成
            saveData = new byte[0];
        }

        ui.showInfoFade("卫星可建造范围已扩展");
    }

    /**
     * 把指定矩形区域内的 void 地板转换为可建造地板，并扩展可建造范围以包含该区域。
     * 返回实际转换的 void 格数。
     */
    public int expandArea(int left, int bottom, int right, int top) {
        boolean currentlyInside = SatelliteManager.currentSatelliteId >= 0
                                  && satellite != null
                                  && SatelliteManager.currentSatelliteId == satellite.id;

        if (currentlyInside) {
            captureFromWorld();
        }

        ensureTiles();

        // 限制在地图有效范围内（保留外边界一圈 void）
        left = Mathf.clamp(left, 1, width - 2);
        right = Mathf.clamp(right, 1, width - 2);
        bottom = Mathf.clamp(bottom, 1, height - 2);
        top = Mathf.clamp(top, 1, height - 2);

        int oldLeft = buildableLeft, oldRight = buildableRight;
        int oldBottom = buildableBottom, oldTop = buildableTop;

        // 扩展可建造范围以包含目标区域
        buildableLeft = Math.min(buildableLeft, left);
        buildableRight = Math.max(buildableRight, right);
        buildableBottom = Math.min(buildableBottom, bottom);
        buildableTop = Math.max(buildableTop, top);

        int converted = convertVoidToBuildable(left, bottom, right, top);

        if (currentlyInside) {
            // 只把虚线框内的 void 地板应用到当前世界，不要把新旧可建造范围之间的整块区域都转换
            applyAreaToWorld(left, bottom, right, top);
            captureFromWorld();
            SatelliteManager.save();
        } else if (converted > 0) {
            saveData = new byte[0];
        }

        return converted;
    }

    /** 统计指定矩形区域内的 void 地板格数。 */
    public int countVoidTiles(int left, int bottom, int right, int top) {
        boolean currentlyInside = SatelliteManager.currentSatelliteId >= 0
                                  && satellite != null
                                  && SatelliteManager.currentSatelliteId == satellite.id;

        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        int count = 0;
        if (currentlyInside && world != null && world.tiles != null) {
            for (int y = bottom; y <= top; y++) {
                for (int x = left; x <= right; x++) {
                    if (x < 0 || y < 0 || x >= world.tiles.width || y >= world.tiles.height)
                        continue;
                    Tile t = world.tile(x, y);
                    if (t != null && t.floor() == voidFloor)
                        count++;
                }
            }
        } else {
            ensureTiles();
            for (int y = bottom; y <= top; y++) {
                for (int x = left; x <= right; x++) {
                    if (x < 0 || y < 0 || x >= width || y >= height)
                        continue;
                    TileEntry e = tiles[y][x];
                    Block floor = content.block(e.floor);
                    if (floor == voidFloor)
                        count++;
                }
            }
        }
        return count;
    }

    /** 把新增可建造区域中的 void 地板条带转换为可建造地板。 */
    private void convertNewStripsToBuildable(int oldLeft, int oldRight, int oldBottom, int oldTop) {
        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;
        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        for (int y = buildableBottom; y <= buildableTop; y++) {
            for (int x = buildableLeft; x <= buildableRight; x++) {
                if (x < oldLeft || x > oldRight || y < oldBottom || y > oldTop) {
                    TileEntry e = tiles[y][x];
                    Block floor = content.block(e.floor);
                    if (floor == voidFloor) {
                        e.floor = buildableFloor.name;
                    }
                }
            }
        }
    }

    /** 把指定矩形区域内的 void 地板转换为可建造地板，返回转换格数。 */
    private int convertVoidToBuildable(int left, int bottom, int right, int top) {
        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;
        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        int converted = 0;
        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                if (x < 0 || y < 0 || x >= width || y >= height)
                    continue;
                TileEntry e = tiles[y][x];
                Block floor = content.block(e.floor);
                if (floor == voidFloor) {
                    e.floor = buildableFloor.name;
                    converted++;
                }
            }
        }
        return converted;
    }

    /** 将可建造区域变更直接应用到当前运行中的世界，不重载整个地图。 */
    private void applyBuildableAreaToWorld(int oldLeft, int oldRight, int oldBottom, int oldTop) {
        if (world == null || world.tiles == null)
            return;
        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;
        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        for (int y = buildableBottom; y <= buildableTop; y++) {
            for (int x = buildableLeft; x <= buildableRight; x++) {
                if (x < oldLeft || x > oldRight || y < oldBottom || y > oldTop) {
                    Tile t = world.tile(x, y);
                    if (t != null && t.floor() == voidFloor) {
                        t.setFloor(buildableFloor);
                    }
                }
            }
        }
    }

    /** 仅把指定矩形区域内的 void 地板转换为可建造地板，用于扩展信标的虚线框区域。 */
    private void applyAreaToWorld(int left, int bottom, int right, int top) {
        if (world == null || world.tiles == null)
            return;
        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;
        Floor voidFloor = (Floor) content.block(voidFloorName);
        if (voidFloor == null)
            voidFloor = (Floor) Blocks.empty;

        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                if (x < 0 || y < 0 || x >= world.tiles.width || y >= world.tiles.height)
                    continue;
                Tile t = world.tile(x, y);
                if (t != null && t.floor() == voidFloor) {
                    t.setFloor(buildableFloor);
                }
            }
        }
    }

    /** 根据当前 tiles 中的 buildableFloor 分布重新计算可建造范围。 */
    private void recalcBuildableBounds() {
        Floor buildableFloor = (Floor) content.block(floorName);
        if (buildableFloor == null)
            buildableFloor = (Floor) Blocks.metalFloor;

        int minX = width - 1, maxX = 0, minY = height - 1, maxY = 0;
        boolean found = false;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                Block floor = content.block(tiles[y][x].floor);
                if (floor == buildableFloor) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    found = true;
                }
            }
        }
        if (found) {
            buildableLeft = minX;
            buildableRight = maxX;
            buildableBottom = minY;
            buildableTop = maxY;
        } else {
            resetBuildableBounds();
        }
    }

    private void copyMap(TileEntry[][] src, TileEntry[][] dst, int srcX, int srcY, int srcW, int srcH, int dstOffX,
                         int dstOffY) {
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int dx = dstOffX + x;
                int dy = dstOffY + y;
                if (dy >= 0 && dy < dst.length && dx >= 0 && dx < dst[0].length) {
                    dst[dy][dx] = src[srcY + y][srcX + x].copy();
                }
            }
        }
    }

    private void rebuildBuildings() {
        buildings.clear();
        if (tiles == null)
            return;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileEntry e = tiles[y][x];
                if (e.block != null && !e.block.equals(Blocks.air.name)) {
                    BuildingEntry be = new BuildingEntry();
                    be.lx = (short) x;
                    be.ly = (short) y;
                    be.block = e.block;
                    be.team = e.team;
                    be.rotation = e.rotation;
                    be.config = e.config;
                    buildings.add(be);
                }
            }
        }
    }

    /** 把任意配置对象归一化：null 返回空字符串，仅用于日志或显示。 */
    String configToString(Object config) {
        if (config == null)
            return "";
        return config.toString();
    }

    private Fi tempFile() {
        String name = satellite != null ? "ca-satellite-" + satellite.id + ".msav" : "ca-satellite-temp.msav";
        return Core.files.local(name);
    }

    public void write(Writes write) {
        write.s(width);
        write.s(height);
        write.s(buildableLeft);
        write.s(buildableRight);
        write.s(buildableBottom);
        write.s(buildableTop);
        write.str(floorName);
        write.str(voidFloorName);
        write.str(coreName);
        write.str(customMapPath);

        byte[] data = saveData != null ? saveData : new byte[0];
        write.i(data.length);
        for (byte b : data) {
            write.b(b);
        }

        // revision 15+: 建筑模块快照
        write.i(moduleSnapshots != null ? moduleSnapshots.size : 0);
        if (moduleSnapshots != null) {
            for (var entry : moduleSnapshots.entries()) {
                write.i(entry.key);
                entry.value.write(write);
            }
        }
    }

    public void read(Reads read, byte revision) {
        width = read.s();
        height = read.s();
        if (revision >= 8) {
            buildableLeft = read.s();
            buildableRight = read.s();
            buildableBottom = read.s();
            buildableTop = read.s();
        } else {
            resetBuildableBounds();
        }
        floorName = read.str();
        // 强制把卫星地图地板更新为 spaceFloor，忽略旧存档中的 metal-floor 等旧值
        Floor spaceFloor = resolveSpaceFloor();
        if (spaceFloor != null) {
            floorName = spaceFloor.name;
        }
        if (revision >= 9) {
            voidFloorName = read.str();
        } else {
            voidFloorName = Blocks.empty.name;
        }
        coreName = read.str();
        if (revision >= 5) {
            customMapPath = read.str();
            if (customMapPath != null && !customMapPath.isEmpty()) {
                customMapFile = Core.files.absolute(customMapPath);
            }
        } else {
            customMapPath = "";
        }
        centerX = width / 2;
        centerY = height / 2;

        if (revision >= 7) {
            // 新格式：直接读取 .msav 字节流
            int len = read.i();
            saveData = new byte[len];
            for (int i = 0; i < len; i++) {
                saveData[i] = read.b();
            }
            tiles = null;
            buildings.clear();

            // revision 15+: 建筑模块快照
            moduleSnapshots.clear();
            if (revision >= 15) {
                int snapCount = read.i();
                for (int i = 0; i < snapCount; i++) {
                    int pos = read.i();
                    ModuleSnapshot snap = new ModuleSnapshot();
                    snap.read(read);
                    moduleSnapshots.put(pos, snap);
                }
            }
        } else {
            // 旧格式：读取自定义 tiles/buildings/unitData，并保留为 transient tiles 供回退
            saveData = new byte[0];
            tiles = new TileEntry[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    TileEntry e = new TileEntry();
                    e.read(read, revision);
                    tiles[y][x] = e;
                }
            }
            int count = read.i();
            buildings.clear();
            for (int i = 0; i < count; i++) {
                BuildingEntry b = new BuildingEntry();
                b.read(read, revision);
                buildings.add(b);
            }
            if (revision >= 6) {
                int unitLen = read.i();
                // 旧单位数据不再需要，直接跳过
                for (int i = 0; i < unitLen; i++)
                    read.b();
            }
            rebuildBuildings();
        }
    }

    public static class TileEntry {
        public String floor = Blocks.stone.name;
        public String block = Blocks.air.name;
        public byte team = 0;
        public byte rotation = 0;
        /** 建筑配置对象；从 .msav 解码得到的是原始对象，旧格式读取为 String。 */
        public Object config;
        /**
         * 建筑模块序列化数据（items/liquids/power）。
         * 仅用于 SaveIO.load 失败后的 tiles 回退路径；saveData 字节流本身已包含这些信息。
         * 不参与旧格式 write/read（旧格式不再作为权威数据源）。
         */
        public @Nullable byte[] itemBytes, liquidBytes, powerBytes;

        public TileEntry copy() {
            TileEntry e = new TileEntry();
            e.floor = floor;
            e.block = block;
            e.team = team;
            e.rotation = rotation;
            e.config = config;
            e.itemBytes = itemBytes != null ? java.util.Arrays.copyOf(itemBytes, itemBytes.length) : null;
            e.liquidBytes = liquidBytes != null ? java.util.Arrays.copyOf(liquidBytes, liquidBytes.length) : null;
            e.powerBytes = powerBytes != null ? java.util.Arrays.copyOf(powerBytes, powerBytes.length) : null;
            return e;
        }

        public void write(Writes write) {
            write.str(floor);
            write.str(block);
            write.b(team);
            write.b(rotation);
            // 旧格式仅做文本兼容；新格式走 saveData 字节流，不再调用此处
            write.str(config == null ? "" : config.toString());
        }

        public void read(Reads read, byte revision) {
            floor = read.str();
            block = read.str();
            team = read.b();
            rotation = read.b();
            config = read.str();
        }
    }

    public static class BuildingEntry {
        public short lx, ly;
        public String block = Blocks.air.name;
        public byte team = 0;
        public byte rotation = 0;
        /** 建筑配置对象；旧格式读取为 String。 */
        public Object config;

        public void write(Writes write) {
            write.s(lx);
            write.s(ly);
            write.str(block);
            write.b(team);
            write.b(rotation);
            write.str(config == null ? "" : config.toString());
        }

        public void read(Reads read, byte revision) {
            lx = read.s();
            ly = read.s();
            block = read.str();
            team = read.b();
            rotation = read.b();
            config = read.str();
        }
    }

    /** 建筑模块快照：保存单个建筑的 items/liquids/power 序列化数据。 */
    public static class ModuleSnapshot {
        public @Nullable byte[] itemBytes;
        public @Nullable byte[] liquidBytes;
        public @Nullable byte[] powerBytes;

        public void write(Writes write) {
            writeByteArray(write, itemBytes);
            writeByteArray(write, liquidBytes);
            writeByteArray(write, powerBytes);
        }

        public void read(Reads read) {
            itemBytes = readByteArray(read);
            liquidBytes = readByteArray(read);
            powerBytes = readByteArray(read);
        }

        private static void writeByteArray(Writes write, @Nullable byte[] bytes) {
            if (bytes == null) {
                write.i(-1);
            } else {
                write.i(bytes.length);
                for (byte b : bytes) {
                    write.b(b);
                }
            }
        }

        private static @Nullable byte[] readByteArray(Reads read) {
            int len = read.i();
            if (len < 0) {
                return null;
            }
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                bytes[i] = read.b();
            }
            return bytes;
        }
    }
}
