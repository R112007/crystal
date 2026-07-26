package crystal.aviation.blocks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.aviation.world.SatelliteMapData;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/**
 * 卫星扩容信标（UnitAssembler 式虚线框扩展）。
 *
 * 放置时会显示一个朝向可选的虚线框；虚线框必须至少覆盖一格当前卫星地图边界墙，
 * 否则无法放置。建筑会根据虚线框实际覆盖的墙数量动态计算物品消耗：
 * 总消耗 = baseItemCost × 覆盖墙格数。
 *
 * 当物品与电力（若配置了 powerUse）都满足后，自动把虚线框向外超出当前地图的
 * 部分扩展为新的可建造区域。
 */
public class SatelliteExpansionBeacon extends Block {

    /** 虚线框边长（格数），可调。 */
    public int areaSize = 7;

    /** 每覆盖一格墙的基础物品消耗。 */
    public ItemStack[] baseItemCost = new ItemStack[] {
            new ItemStack(mindustry.content.Items.silicon, 10),
            new ItemStack(mindustry.content.Items.titanium, 5)
    };

    /** 每秒电力消耗，0 表示不耗电。 */
    public float powerUse = 0f;

    public SatelliteExpansionBeacon(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        rotate = true;
        rotateDraw = false;
        hasItems = true;
        group = BlockGroup.logic;
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.titanium, 60),
                new ItemStack(mindustry.content.Items.metaglass, 40)
        });
    }

    @Override
    public void init() {
        // 根据最大可能消耗设置库存容量，确保能装下全部扩展材料。
        int maxWall = areaSize;
        int maxSingle = 0;
        for (ItemStack stack : baseItemCost) {
            maxSingle = Math.max(maxSingle, stack.amount * maxWall);
        }
        itemCapacity = Math.max(itemCapacity, maxSingle * 2 + 10);

        if (powerUse > 0f) {
            hasPower = true;
            consumePower(powerUse);
        }

        super.init();
    }

    /**
     * 计算虚线框覆盖的整数 tile 范围 [left, right] × [bottom, top]。
     * 以建筑为起点，向 rotation 方向延伸 areaSize 格，两侧各展开 areaSize/2 格。
     */
    public int[] getTileBounds(int tx, int ty, int rotation) {
        int half = areaSize / 2;
        int left, right, bottom, top;
        switch (rotation) {
            case 0: // 向右
                left = tx + 1;
                right = tx + areaSize;
                bottom = ty - half;
                top = ty + half;
                break;
            case 1: // 向上
                left = tx - half;
                right = tx + half;
                bottom = ty + 1;
                top = ty + areaSize;
                break;
            case 2: // 向左
                left = tx - areaSize;
                right = tx - 1;
                bottom = ty - half;
                top = ty + half;
                break;
            case 3: // 向下
                left = tx - half;
                right = tx + half;
                bottom = ty - areaSize;
                top = ty - 1;
                break;
            default:
                left = right = bottom = top = 0;
        }
        return new int[] { left, bottom, right, top };
    }

    /** 把 tile 范围转成世界坐标 Rect，用于 drawPlace / drawSelect。 */
    public Rect getRect(Rect rect, int tx, int ty, int rotation) {
        int[] b = getTileBounds(tx, ty, rotation);
        return rect.set(b[0] * tilesize, b[1] * tilesize,
                (b[2] - b[0] + 1) * tilesize, (b[3] - b[1] + 1) * tilesize);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Rect rect = getRect(Tmp.r1, x, y, rotation);
        Drawf.dashRect(valid ? Pal.accent : Pal.remove, rect);
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        if (SatelliteManager.currentSatelliteId < 0)
            return false;
        Satellite s = SatelliteManager.get(SatelliteManager.currentSatelliteId);
        if (s == null || s.mapData == null)
            return false;
        if (world == null || world.tiles == null)
            return false;

        int[] b = getTileBounds(tile.x, tile.y, rotation);
        return countWallTiles(b[0], b[1], b[2], b[3]) > 0;
    }

    /** 统计整数范围内覆盖的 Wall 类型方块数量。虚线框必须覆盖至少一格墙才能放置。 */
    public static int countWallTiles(int left, int bottom, int right, int top) {
        int count = 0;
        int w = world.tiles.width;
        int h = world.tiles.height;
        int x0 = Math.max(left, 0);
        int x1 = Math.min(right, w - 1);
        int y0 = Math.max(bottom, 0);
        int y1 = Math.min(top, h - 1);

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Tile t = world.tile(x, y);
                if (t != null && t.block() instanceof Wall)
                    count++;
            }
        }
        return count;
    }

    public class SatelliteExpansionBeaconBuild extends Building {
        public int satelliteId = -1;
        public boolean expanded = false;

        /** 当前帧计算出的覆盖墙数量。 */
        public transient int wallCount = 0;
        /** 当前帧计算出的动态消耗。 */
        public transient ItemStack[] currentCost = new ItemStack[0];

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile() {
            if (expanded || satelliteId < 0)
                return;
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null)
                return;

            recalculateCost();
            if (wallCount <= 0)
                return;

            // 检查物品
            boolean hasItems = true;
            for (ItemStack stack : currentCost) {
                if (items.get(stack.item) < stack.amount) {
                    hasItems = false;
                    break;
                }
            }

            // 检查电力（不耗电时默认满足）
            boolean hasPower = powerUse <= 0f || (power != null && power.status >= 0.999f);

            if (hasItems && hasPower) {
                for (ItemStack stack : currentCost) {
                    items.remove(stack.item, stack.amount);
                }
                expandMap(s);
                expanded = true;
            }
        }

        /** 根据建筑朝向与位置重新计算虚线框覆盖的墙数及对应消耗。 */
        void recalculateCost() {
            int[] b = getTileBounds(tile.x, tile.y, rotation);
            wallCount = countWallTiles(b[0], b[1], b[2], b[3]);

            currentCost = new ItemStack[baseItemCost.length];
            for (int i = 0; i < baseItemCost.length; i++) {
                currentCost[i] = new ItemStack(baseItemCost[i].item, baseItemCost[i].amount * wallCount);
            }
        }

        /** 根据虚线框覆盖的可建造边界墙执行定向扩展。 */
        void expandMap(Satellite s) {
            SatelliteMapData data = s.mapData;
            int[] b = getTileBounds(tile.x, tile.y, rotation);
            int left = b[0], bottom = b[1], right = b[2], top = b[3];

            // 新机制：实际地图尺寸不变，只扩大可建造范围。
            // 扩展量 = 虚线框超出当前可建造范围的部分。
            int expandLeft = Math.max(0, data.buildableLeft - left);
            int expandRight = Math.max(0, right - data.buildableRight);
            int expandBottom = Math.max(0, data.buildableBottom - bottom);
            int expandTop = Math.max(0, top - data.buildableTop);

            data.expand(expandLeft, expandRight, expandBottom, expandTop);
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Rect rect = getRect(Tmp.r1, tile.x, tile.y, rotation);
            Drawf.dashRect(Pal.accent, rect);
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(4f);
            table.add("虚线框覆盖墙格数: ").style(Styles.outlineLabel);
            table.add(String.valueOf(wallCount)).style(Styles.outlineLabel);
            table.row();

            if (currentCost.length == 0) {
                table.add("当前不覆盖任何地图墙").style(Styles.outlineLabel);
            } else {
                table.add("扩展所需物品:").style(Styles.outlineLabel);
                table.row();
                for (ItemStack stack : currentCost) {
                    int have = items.get(stack.item);
                    String color = have >= stack.amount ? "[lightgray]" : "[scarlet]";
                    table.add(color + stack.item.localizedName + ": " + have + "/" + stack.amount)
                            .style(Styles.outlineLabel);
                    table.row();
                }
            }

            if (powerUse > 0f) {
                table.row();
                float p = power != null ? power.status * powerUse : 0f;
                String color = p >= powerUse * 0.999f ? "[lightgray]" : "[scarlet]";
                table.add(color + "电力: " + (int) p + "/" + (int) powerUse + " /s")
                        .style(Styles.outlineLabel);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
            write.bool(expanded);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
            expanded = read.bool();
        }
    }
}
