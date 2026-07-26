package crystal.aviation.world;

import arc.Core;
import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.storage.*;
import mindustry.graphics.g3d.*;

import crystal.aviation.*;
import crystal.aviation.blocks.*;

import java.io.*;

import static mindustry.Vars.*;

/**
 * 卫星地图数据。
 * 自 revision 7 起，持久化格式改为 Mindustry 原生 .msav 存档字节流（saveData），
 * 地形、建筑、单位、电力、液体等实体状态由 SaveIO 统一保存与恢复。
 * tiles/buildings 仅作为 transient 工作缓存，用于默认生成、自定义地图加载、合并与扩容。
 */
public class SatelliteMapData {
    public static final int defaultSize = 31;
    public static final int maxSize = 127;

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
    /** 地板方块名称 */
    public String floorName = CrystalAviationMod.defaultSatelliteFloor;
    /** 核心方块名称 */
    public String coreName = "core-shard";

    /** 卫星世界存档数据（SaveIO 格式的 .msav 字节流，包含地形与实体）。这是唯一的持久化地图状态。 */
    public byte[] saveData = new byte[0];

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

    /** 将可建造范围重置为整个地图（旧格式兼容/默认值）。 */
    public void resetBuildableBounds() {
        this.buildableLeft = 1;
        this.buildableRight = Math.max(1, width - 2);
        this.buildableBottom = 1;
        this.buildableTop = Math.max(1, height - 2);
    }

    public SatelliteMapData() {
    }

    /**
     * 生成默认卫星地图。
     * 实际地图尺寸固定为 maxSize，中间 defaultSize x defaultSize 区域为初始可建造范围，
     * 其余区域用墙围住。扩展时只需拆除/移动内边界墙，无需重载世界。
     */
    public void generateDefault() {
        Floor floor = (Floor) content.block(floorName);
        if (floor == null)
            floor = (Floor) content.block(CrystalAviationMod.defaultSatelliteFloor);
        if (floor == null)
            floor = (Floor) Blocks.metalFloor;

        Block core = content.block(coreName);
        if (core == null)
            core = Blocks.coreShard;

        Block wall = Blocks.scrapWall;

        // 使用最大尺寸作为实际地图尺寸
        this.width = maxSize;
        this.height = maxSize;
        this.centerX = width / 2;
        this.centerY = height / 2;

        // 初始可建造区域位于中心
        int half = defaultSize / 2;
        this.buildableLeft = centerX - half;
        this.buildableRight = centerX + half;
        this.buildableBottom = centerY - half;
        this.buildableTop = centerY + half;

        this.tiles = new TileEntry[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileEntry e = new TileEntry();
                e.floor = floor.name;
                e.block = Blocks.air.name;
                // 实际地图外边界放墙
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    e.block = wall.name;
                }
                // 初始可建造区域内边界放墙
                else if (x == buildableLeft - 1 || x == buildableRight + 1
                        || y == buildableBottom - 1 || y == buildableTop + 1) {
                    e.block = wall.name;
                }
                tiles[y][x] = e;
            }
        }

        placeBuildingLocal(centerX, centerY, core, Team.sharded, 0, null);
        placeBuildingLocal(centerX + 2, centerY, CrystalAviationMod.satelliteControlCenter, Team.sharded, 0, null);
        placeBuildingLocal(centerX - 2, centerY, CrystalAviationMod.satelliteDockingPort, Team.sharded, 0, null);
        placeBuildingLocal(centerX, centerY + 3, CrystalAviationMod.satelliteSolarArray, Team.sharded, 0, null);
        placeBuildingLocal(centerX, centerY - 3, CrystalAviationMod.satelliteSolarArray, Team.sharded, 0, null);
        placeBuildingLocal(centerX + 4, centerY + 2, CrystalAviationMod.satelliteResourceScanner, Team.sharded, 0,
                null);
        placeBuildingLocal(centerX - 4, centerY - 2, CrystalAviationMod.satelliteRelay, Team.sharded, 0, null);

        rebuildBuildings();
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
        Map map = MapIO.createMap(file, true);
        loadFromMap(map);
        setCustomMapFile(file);
    }

    /** 从 Map 对象加载数据到 transient tiles（不污染当前游戏世界）。 */
    public void loadFromMap(Map map) throws IOException {
        this.width = map.width;
        this.height = map.height;
        this.centerX = width / 2;
        this.centerY = height / 2;
        // 自定义地图：整张地图都可建造（留出外边界墙）
        this.buildableLeft = 1;
        this.buildableRight = Math.max(1, width - 2);
        this.buildableBottom = 1;
        this.buildableTop = Math.max(1, height - 2);
        this.tiles = new TileEntry[height][width];
        this.buildings.clear();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new TileEntry();
            }
        }

        CachedTile tile = new CachedTile();
        MapIO.loadMap(map, new WorldContext() {
            @Override
            public void resize(int width, int height) {
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
                int x = index % width;
                int y = index / width;
                tile.x = (short) x;
                tile.y = (short) y;
                return tile;
            }

            @Override
            public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                tile.x = (short) x;
                tile.y = (short) y;
                return tile;
            }

            @Override
            public void onReadTileData() {
                int x = tile.x;
                int y = tile.y;
                TileEntry e = tiles[y][x];
                e.floor = tile.floor().name;
                e.block = tile.block().name;
                e.team = (byte) tile.team().id;
                e.rotation = tile.build != null ? (byte) tile.build.rotation : 0;
                if (tile.build != null) {
                    BuildingEntry be = new BuildingEntry();
                    be.lx = (short) x;
                    be.ly = (short) y;
                    be.block = tile.block().name;
                    be.team = (byte) tile.team().id;
                    be.rotation = (byte) tile.build.rotation;
                    be.config = configToString(tile.build.config());
                    buildings.add(be);
                }
            }
        });

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
                    Log.err("[CrystalAviation] Failed to reload custom map '@', using default.", customMapPath);
                    Log.err(e);
                    generateDefault();
                }
            } else {
                Log.warn("[CrystalAviation] Custom map file missing '@', using default.", customMapPath);
                generateDefault();
            }
        } else {
            generateDefault();
        }
        return tiles;
    }

    /** 将 saveData 解码为 transient tiles，不加载到当前世界。 */
    private void decodeSaveData() {
        if (saveData == null || saveData.length == 0) {
            generateDefault();
            return;
        }
        try {
            // 保存当前可建造范围，避免 loadFromMap 将其重置为整张地图
            int oldLeft = buildableLeft;
            int oldRight = buildableRight;
            int oldBottom = buildableBottom;
            int oldTop = buildableTop;

            Fi temp = tempFile();
            temp.writeBytes(saveData);
            Map map = MapIO.createMap(temp, true);
            this.width = map.width;
            this.height = map.height;
            this.centerX = width / 2;
            this.centerY = height / 2;
            this.tiles = new TileEntry[height][width];
            this.buildings.clear();
            loadFromMap(map);

            // 恢复可建造范围（衰变机制：实际地图尺寸不变，只移动内边界墙）
            buildableLeft = Mathf.clamp(oldLeft, 1, width - 2);
            buildableRight = Mathf.clamp(oldRight, 1, width - 2);
            buildableBottom = Mathf.clamp(oldBottom, 1, height - 2);
            buildableTop = Mathf.clamp(oldTop, 1, height - 2);
        } catch (Exception e) {
            Log.err("[CrystalAviation] Failed to decode satellite saveData, using default.", e);
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
        Block core = content.block(coreName);
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
        e.config = configToString(config);
    }

    public void removeBuildingLocal(int lx, int ly) {
        if (tiles == null)
            return;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height)
            return;
        tiles[ly][lx].block = Blocks.air.name;
    }

    /** 将本地地图数据应用到当前世界（进入卫星时调用）。优先使用 saveData，否则回退到 transient tiles。 */
    public void applyToWorld() {
        if (world == null)
            return;
        try {
            if (saveData != null && saveData.length > 0) {
                Fi temp = tempFile();
                temp.writeBytes(saveData);
                SaveIO.load(temp);
                if (world.tiles != null) {
                    width = world.tiles.width;
                    height = world.tiles.height;
                }
            } else {
                ensureTiles();
                world.loadGenerator(width, height, genTiles -> {
                    Floor fallbackFloor = (Floor) Blocks.metalFloor;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            TileEntry e = tiles[y][x];
                            Block floor = content.block(e.floor);
                            if (!(floor instanceof Floor))
                                floor = fallbackFloor;
                            genTiles.set(x, y, new Tile(x, y, floor.asFloor(), Blocks.air, Blocks.air));

                            Block block = content.block(e.block);
                            if (block != null && block != Blocks.air) {
                                Tile tile = genTiles.getn(x, y);
                                tile.setBlock(block, Team.get(e.team), e.rotation);
                            }
                        }
                    }
                });
            }

            centerX = width / 2;
            centerY = height / 2;
            applySatelliteRules();
            setupBackgroundRules();
            rebindBuildings();
        } catch (Exception e) {
            Log.err("[CrystalAviation] Failed to apply satellite world", e);
        }
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

    /** 从当前世界读取完整状态（保存前调用），结果写入 saveData。 */
    public void captureFromWorld() {
        if (world == null || world.tiles == null)
            return;
        try {
            applySatelliteRules();
            if (satellite != null) {
                state.map.tags.put("name", satellite.name);
                state.map.tags.put("author", "Crystal Aviation");
            }

            Fi temp = tempFile();
            SaveIO.save(temp);
            saveData = temp.readBytes();
            width = world.tiles.width;
            height = world.tiles.height;
            centerX = width / 2;
            centerY = height / 2;

            // 清除 transient 缓存，下次从 saveData 重新解码
            tiles = null;
            buildings.clear();
        } catch (Exception e) {
            Log.err("[CrystalAviation] Failed to capture satellite world", e);
        }
    }

    public void rebindBuildings() {
        if (world == null || world.tiles == null)
            return;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile tile = world.tile(x, y);
                if (tile == null || tile.build == null)
                    continue;
                if (tile.block() instanceof SatelliteControlCenter) {
                    ((SatelliteControlCenter.SatelliteControlCenterBuild) tile.build).satelliteId = satellite.id;
                } else if (tile.block() instanceof SatelliteDockingPort) {
                    ((SatelliteDockingPort.SatelliteDockingPortBuild) tile.build).satelliteId = satellite.id;
                } else if (tile.block() instanceof SatelliteExpansionBeacon) {
                    ((SatelliteExpansionBeacon.SatelliteExpansionBeaconBuild) tile.build).satelliteId = satellite.id;
                } else if (tile.block() instanceof SatelliteSolarArray) {
                    ((SatelliteSolarArray.SatelliteSolarArrayBuild) tile.build).satelliteId = satellite.id;
                } else if (tile.block() instanceof SatelliteResourceScanner) {
                    ((SatelliteResourceScanner.SatelliteResourceScannerBuild) tile.build).satelliteId = satellite.id;
                } else if (tile.block() instanceof SatelliteRelay) {
                    ((SatelliteRelay.SatelliteRelayBuild) tile.build).satelliteId = satellite.id;
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

        Floor floor = (Floor) content.block(floorName);
        if (floor == null)
            floor = (Floor) Blocks.metalFloor;
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                TileEntry e = new TileEntry();
                e.floor = floor.name;
                e.block = Blocks.air.name;
                if (x == 0 || y == 0 || x == newWidth - 1 || y == newHeight - 1) {
                    e.block = Blocks.scrapWall.name;
                }
                this.tiles[y][x] = e;
            }
        }

        copyMap(oldTiles, this.tiles, 0, 0, oldW, oldH, 1, 1);
        copyMap(other.tiles, this.tiles, 0, 0, other.width, other.height,
                newWidth - other.width - 1, 1);

        rebuildBuildings();
        // 合并后实体坐标失效，清空 saveData，下次进入时以 tiles 重建世界
        saveData = new byte[0];
    }

    /**
     * 扩大可建造范围：不重新调整实际地图尺寸，只移动内边界墙。
     * 若玩家当前正处于该卫星世界中，会直接修改当前世界的墙并保存。
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

        updateBuildableWalls(oldLeft, oldRight, oldBottom, oldTop);
        rebuildBuildings();

        if (currentlyInside) {
            applyBuildableWallsToWorld(oldLeft, oldRight, oldBottom, oldTop);
            captureFromWorld();
        }

        // 注意：不要在这里清空 saveData。
        // 当在卫星世界内时，captureFromWorld() 已经把新状态写入 saveData；
        // 当不在世界内时，tiles 已更新，但缺少 saveData 序列化，建议进入卫星后保存。
        ui.showInfoFade("卫星可建造范围已扩展");
    }

    /** 在 tiles 缓存中移动内边界墙：清除旧边界墙，绘制新边界墙。 */
    private void updateBuildableWalls(int oldLeft, int oldRight, int oldBottom, int oldTop) {
        if (tiles == null)
            return;
        Block wallBlock = Blocks.scrapWall;
        String wallName = wallBlock.name;
        Floor floor = (Floor) content.block(floorName);
        if (floor == null)
            floor = (Floor) Blocks.metalFloor;
        String floorName = floor.name;

        // 清除旧内边界墙
        int oldWallLeft = oldLeft - 1;
        int oldWallRight = oldRight + 1;
        int oldWallBottom = oldBottom - 1;
        int oldWallTop = oldTop + 1;

        if (oldWallLeft >= 1 && oldWallRight <= width - 2 && oldWallBottom >= 1 && oldWallTop <= height - 2) {
            for (int x = oldWallLeft; x <= oldWallRight; x++) {
                tiles[oldWallBottom][x].block = Blocks.air.name;
                tiles[oldWallTop][x].block = Blocks.air.name;
            }
            for (int y = oldWallBottom; y <= oldWallTop; y++) {
                tiles[y][oldWallLeft].block = Blocks.air.name;
                tiles[y][oldWallRight].block = Blocks.air.name;
            }
        }

        // 绘制新内边界墙
        int wallLeft = buildableLeft - 1;
        int wallRight = buildableRight + 1;
        int wallBottom = buildableBottom - 1;
        int wallTop = buildableTop + 1;

        if (wallLeft >= 1 && wallRight <= width - 2 && wallBottom >= 1 && wallTop <= height - 2) {
            for (int x = wallLeft; x <= wallRight; x++) {
                tiles[wallBottom][x].block = wallName;
                tiles[wallTop][x].block = wallName;
            }
            for (int y = wallBottom; y <= wallTop; y++) {
                tiles[y][wallLeft].block = wallName;
                tiles[y][wallRight].block = wallName;
            }
        }
    }

    /** 将边界墙变更直接应用到当前运行中的世界，不重载整个地图。 */
    private void applyBuildableWallsToWorld(int oldLeft, int oldRight, int oldBottom, int oldTop) {
        if (world == null || world.tiles == null)
            return;
        Block wallBlock = Blocks.scrapWall;
        Floor floor = (Floor) content.block(floorName);
        if (floor == null)
            floor = (Floor) Blocks.metalFloor;

        // 清除旧内边界墙（不覆盖玩家建筑）
        int oldWallLeft = oldLeft - 1;
        int oldWallRight = oldRight + 1;
        int oldWallBottom = oldBottom - 1;
        int oldWallTop = oldTop + 1;

        for (int x = oldWallLeft; x <= oldWallRight; x++) {
            clearWallIfAny(x, oldWallBottom, floor);
            clearWallIfAny(x, oldWallTop, floor);
        }
        for (int y = oldWallBottom; y <= oldWallTop; y++) {
            clearWallIfAny(oldWallLeft, y, floor);
            clearWallIfAny(oldWallRight, y, floor);
        }

        // 绘制新内边界墙（不覆盖玩家建筑）
        int wallLeft = buildableLeft - 1;
        int wallRight = buildableRight + 1;
        int wallBottom = buildableBottom - 1;
        int wallTop = buildableTop + 1;

        for (int x = wallLeft; x <= wallRight; x++) {
            placeWallIfEmpty(x, wallBottom, wallBlock);
            placeWallIfEmpty(x, wallTop, wallBlock);
        }
        for (int y = wallBottom; y <= wallTop; y++) {
            placeWallIfEmpty(wallLeft, y, wallBlock);
            placeWallIfEmpty(wallRight, y, wallBlock);
        }
    }

    private void clearWallIfAny(int x, int y, Floor floor) {
        if (x < 0 || y < 0 || x >= width || y >= height)
            return;
        Tile t = world.tile(x, y);
        if (t == null)
            return;
        // 墙也是建筑，不能通过 build != null 过滤，否则永远清不掉墙
        if (t.block() instanceof StaticWall) {
            t.setBlock(Blocks.air);
            t.setFloor(floor);
        }
    }

    private void placeWallIfEmpty(int x, int y, Block wall) {
        if (x < 0 || y < 0 || x >= width || y >= height)
            return;
        Tile t = world.tile(x, y);
        if (t == null || t.build != null)
            return;
        if (t.block() == Blocks.air) {
            t.setBlock(wall);
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
        write.str(coreName);
        write.str(customMapPath);

        byte[] data = saveData != null ? saveData : new byte[0];
        write.i(data.length);
        for (byte b : data) {
            write.b(b);
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
        public String config = "";

        public TileEntry copy() {
            TileEntry e = new TileEntry();
            e.floor = floor;
            e.block = block;
            e.team = team;
            e.rotation = rotation;
            e.config = config;
            return e;
        }

        public void write(Writes write) {
            write.str(floor);
            write.str(block);
            write.b(team);
            write.b(rotation);
            write.str(config);
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
        public String config = "";

        public void write(Writes write) {
            write.s(lx);
            write.s(ly);
            write.str(block);
            write.b(team);
            write.b(rotation);
            write.str(config);
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
}
