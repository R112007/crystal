package crystal.aviation.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.aviation.world.SatelliteMapData;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockGroup;
import crystal.world.meta.CStat;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星地图扩展块。
 *
 * 放置时会显示一个朝向可选的虚线框；虚线框必须至少覆盖一格当前卫星地图的 void 地板，
 * 且不能与核心接触。建筑会根据虚线框实际覆盖的 void 格数动态计算物品消耗：
 * 总消耗 = baseItemCost × void 格数。
 *
 * 当物品与电力（若配置了 powerUse）都满足后，自动把虚线框内的 void 地板转换为可建造地板，
 * 并扩展卫星可建造范围；随后该建筑自毁。
 */
public class SatelliteMapExpander extends Block {

    /** 虚线框边长（格数），可调。 */
    public int areaSize = 7;

    /** 每转换一格 void 地板的基础物品消耗。 */
    public ItemStack[] baseItemCost = new ItemStack[] {
            new ItemStack(mindustry.content.Items.silicon, 10),
            new ItemStack(mindustry.content.Items.titanium, 5)
    };

    /** 每秒电力消耗，0 表示不耗电。 */
    public float powerUse = 0f;

    public SatelliteMapExpander(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        rotate = true;
        rotateDraw = false;
        hasItems = true;
        group = BlockGroup.logic;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.titanium, 60),
                new ItemStack(mindustry.content.Items.metaglass, 40)
        });
        consumeItems(baseItemCost);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.expansionArea, areaSize * areaSize, StatUnit.blocksSquared);
        stats.add(CStat.expansionCost, StatValues.items(baseItemCost));
        if (powerUse > 0f) {
            stats.add(Stat.powerUse, powerUse * 60f, StatUnit.powerSecond);
        }
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理物品 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("items");
    }

    @Override
    public void init() {
        // 根据最大可能消耗设置库存容量，确保能装下全部扩展材料。
        int maxVoid = areaSize * areaSize;
        int maxSingle = 0;
        for (ItemStack stack : baseItemCost) {
            maxSingle = Math.max(maxSingle, stack.amount * maxVoid);
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

        int[] b = getTileBounds(tile.x, tile.y, rotation);
        if (s.mapData.countVoidTiles(b[0], b[1], b[2], b[3]) <= 0)
            return false;

        // 不能与核心接触
        return !touchesCore(s, b[0], b[1], b[2], b[3]);
    }

    /** 判断给定矩形区域是否与已有核心建筑接触或重叠。 */
    boolean touchesCore(Satellite s, int left, int bottom, int right, int top) {
        if (world == null || world.tiles == null)
            return false;
        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                if (x < 0 || y < 0 || x >= world.tiles.width || y >= world.tiles.height)
                    continue;
                Tile t = world.tile(x, y);
                if (t == null || t.build == null)
                    continue;
                if (t.block() instanceof CoreBlock)
                    return true;
                // 同时检查周围 8 格是否有核心
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0)
                            continue;
                        Tile nt = world.tile(x + dx, y + dy);
                        if (nt != null && nt.block() instanceof CoreBlock)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    public class SatelliteMapExpanderBuild extends Building {
        public int satelliteId = -1;
        public boolean expanded = false;

        /** 当前帧计算出的覆盖 void 格数量。 */
        public transient int voidCount = 0;
        /** 当前帧计算出的动态消耗。 */
        public transient ItemStack[] currentCost = new ItemStack[0];

        /** 是否正在蓄力扩展（特效阶段）。 */
        public boolean charging = false;
        /** 蓄力剩余时间，单位：tick。 */
        public transient float chargeTimer = 0f;
        /** 蓄力总时长。 */
        public static final float chargeDuration = 90f;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile() {
            if (satelliteId < 0)
                return;
            // 兜底：若扩展已经完成但建筑仍残留（例如脏存档读入），立即移除并跳过逻辑
            if (expanded) {
                if (tile != null && !headless) {
                    tile.setBlock(Blocks.air);
                }
                return;
            }
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null)
                return;

            recalculateCost();

            if (charging) {
                // 资源不足时取消蓄力
                if (voidCount <= 0 || !hasResources()) {
                    charging = false;
                    chargeTimer = 0f;
                    return;
                }

                chargeTimer -= Time.delta;
                spawnChargeEffects();

                if (chargeTimer <= 0f) {
                    finishExpansion(s);
                }
                return;
            }

            if (voidCount <= 0)
                return;

            if (hasResources()) {
                // 开始蓄力：draw() 会绘制四角线条与发光，等特效结束后再真正扩展
                charging = true;
                chargeTimer = chargeDuration;
            }
        }

        /** 检查当前物品和电力是否满足扩展需求。 */
        boolean hasResources() {
            if (voidCount <= 0)
                return false;
            for (ItemStack stack : currentCost) {
                if (items.get(stack.item) < stack.amount)
                    return false;
            }
            return powerUse <= 0f || (power != null && power.status >= 0.999f);
        }

        /** 蓄力特效已改为在 draw() 中手动绘制四角线条与发光，此处不再额外生成粒子。 */
        void spawnChargeEffects() {
        }

        /** 特效结束后执行扩展、移除建筑并保存无扩展块的干净地图。 */
        void finishExpansion(Satellite s) {
            if (expanded)
                return;
            expanded = true;
            charging = false;

            for (ItemStack stack : currentCost) {
                items.remove(stack.item, stack.amount);
            }

            // 先记下扩展坐标，然后立即把建筑从世界中移除，再执行地图扩展。
            // 这样可以确保 expandArea 内部触发的 captureFromWorld 不会把扩展块保存下来。
            int tx = tile.x;
            int ty = tile.y;
            int rot = rotation;
            if (tile != null) {
                tile.setBlock(Blocks.air);
            }

            expandMapAt(s, tx, ty, rot);

            // 此时世界中已没有扩展块，captureFromWorld 一定是干净的
            if (s.mapData != null) {
                s.mapData.captureFromWorld();
            }
            SatelliteManager.save();
        }

        /** 使用已保存的坐标执行地图扩展（扩展块本身必须已经移除）。 */
        void expandMapAt(Satellite s, int tx, int ty, int rot) {
            SatelliteMapData data = s.mapData;
            int[] b = getTileBounds(tx, ty, rot);
            int converted = data.expandArea(b[0], b[1], b[2], b[3]);
            if (converted > 0) {
                ui.showInfoFade("已扩展 " + converted + " 格卫星区域");
            }
        }

        /** 根据建筑朝向与位置重新计算虚线框覆盖的 void 格数及对应消耗。 */
        void recalculateCost() {
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null || s.mapData == null) {
                voidCount = 0;
                currentCost = new ItemStack[0];
                return;
            }
            int[] b = getTileBounds(tile.x, tile.y, rotation);
            voidCount = s.mapData.countVoidTiles(b[0], b[1], b[2], b[3]);

            currentCost = new ItemStack[baseItemCost.length];
            for (int i = 0; i < baseItemCost.length; i++) {
                currentCost[i] = new ItemStack(baseItemCost[i].item, baseItemCost[i].amount * voidCount);
            }
        }

        @Override
        public void draw() {
            super.draw();
            if (!charging || headless)
                return;

            Rect r = getRect(Tmp.r1, tile.x, tile.y, rotation);
            float fin = Mathf.clamp(1f - chargeTimer / chargeDuration, 0f, 1f);

            // 阶段划分：0~80% 线条从四角向边中点延伸；80~100% 框体发光并逐渐消失
            if (fin < 0.8f) {
                drawFrameLines(r, fin / 0.8f);
            } else {
                drawFrameGlow(r, (fin - 0.8f) / 0.2f);
            }
        }

        /** 从虚线框四角向四边中点延伸线条。 */
        void drawFrameLines(Rect r, float fin) {
            float cx = r.x + r.width / 2f;
            float cy = r.y + r.height / 2f;

            // 四角
            float lbx = r.x, lby = r.y;
            float rbx = r.x + r.width, rby = r.y;
            float ltx = r.x, lty = r.y + r.height;
            float rtx = r.x + r.width, rty = r.y + r.height;
            // 四边中点
            float bx = cx, by = r.y;
            float tx = cx, ty = r.y + r.height;
            float lx = r.x, ly = cy;
            float rx = r.x + r.width, ry = cy;

            // 颜色从 accent 渐变到 white，线条随进度加粗
            Color col = Tmp.c1.set(Pal.accent).lerp(Color.white, fin);
            Draw.color(col);
            Lines.stroke(2f + fin * 2f);

            // 四角向相邻边中点延伸，共 8 条线
            drawLineSegment(lbx, lby, bx, by, fin);
            drawLineSegment(lbx, lby, lx, ly, fin);
            drawLineSegment(rbx, rby, bx, by, fin);
            drawLineSegment(rbx, rby, rx, ry, fin);
            drawLineSegment(ltx, lty, tx, ty, fin);
            drawLineSegment(ltx, lty, lx, ly, fin);
            drawLineSegment(rtx, rty, tx, ty, fin);
            drawLineSegment(rtx, rty, rx, ry, fin);

            Draw.reset();
        }

        void drawLineSegment(float x1, float y1, float x2, float y2, float fin) {
            float mx = x1 + (x2 - x1) * fin;
            float my = y1 + (y2 - y1) * fin;
            Lines.line(x1, y1, mx, my);
        }

        /** 框体完全框住后发光，光芒随 fin 从 0 到 1 逐渐减弱消失。 */
        void drawFrameGlow(Rect r, float fin) {
            float alpha = 1f - fin;
            if (alpha <= 0.01f)
                return;

            float cx = r.x + r.width / 2f;
            float cy = r.y + r.height / 2f;
            float pulse = 1f + 0.5f * Mathf.sin(Time.time * 0.3f);

            // 外框高亮
            Draw.color(Pal.accent, alpha);
            Lines.stroke((2f + alpha * 3f) * pulse);
            Lines.rect(r.x, r.y, r.width, r.height);

            // 内部填充光
            Draw.color(Pal.accent, alpha * 0.25f);
            Fill.rect(cx, cy, r.width, r.height);

            // 四角高光
            Draw.color(Color.white, alpha);
            float s = 5f * alpha * pulse;
            Fill.rect(r.x, r.y, s, s);
            Fill.rect(r.x + r.width, r.y, s, s);
            Fill.rect(r.x, r.y + r.height, s, s);
            Fill.rect(r.x + r.width, r.y + r.height, s, s);

            Draw.reset();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Rect rect = getRect(Tmp.r1, tile.x, tile.y, rotation);
            Drawf.dashRect(Pal.accent, rect);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (expanded || charging || item == null)
                return false;
            recalculateCost();
            for (ItemStack stack : currentCost) {
                if (stack.item == item) {
                    return items.get(item) < stack.amount;
                }
            }
            return false;
        }

        @Override
        public void handleItem(Building source, Item item) {
            items.add(item, 1);
            // 物品到达后立即尝试扩展（updateTile 会检查全部条件）
            recalculateCost();
        }

        @Override
        public int getMaximumAccepted(Item item) {
            recalculateCost();
            for (ItemStack stack : currentCost) {
                if (stack.item == item) {
                    return stack.amount;
                }
            }
            return 0;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(4f);

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                infoTable.add("地图扩展块").style(Styles.outlineLabel).row();

                infoTable.add("虚线框覆盖 void 格数: ").style(Styles.outlineLabel);
                infoTable.add(String.valueOf(voidCount)).style(Styles.outlineLabel);
                infoTable.row();

                if (currentCost.length == 0) {
                    infoTable.add("当前不覆盖任何可扩展区域").style(Styles.outlineLabel);
                } else {
                    infoTable.add("扩展所需物品:").style(Styles.outlineLabel);
                    infoTable.row();
                    for (ItemStack stack : currentCost) {
                        int have = items.get(stack.item);
                        String color = have >= stack.amount ? "[lightgray]" : "[scarlet]";
                        infoTable.add(color + stack.item.localizedName + ": " + have + "/" + stack.amount)
                                .style(Styles.outlineLabel);
                        infoTable.row();
                    }
                }

                if (powerUse > 0f) {
                    infoTable.row();
                    float p = power != null ? power.status * powerUse : 0f;
                    String color = p >= powerUse * 0.999f ? "[lightgray]" : "[scarlet]";
                    infoTable.add(color + "电力: " + (int) p + "/" + (int) powerUse + " /s")
                            .style(Styles.outlineLabel);
                }
            });
            table.add(infoTable).row();
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar(() -> {
                if (currentCost.length == 0) return "资源准备: 0/0";
                int haveTotal = 0, needTotal = 0;
                for (ItemStack stack : currentCost) {
                    haveTotal += Math.min(items.get(stack.item), stack.amount);
                    needTotal += stack.amount;
                }
                return "资源准备: " + haveTotal + "/" + needTotal;
            }, () -> Pal.accent, () -> {
                if (currentCost.length == 0) return 0f;
                int haveTotal = 0, needTotal = 0;
                for (ItemStack stack : currentCost) {
                    haveTotal += Math.min(items.get(stack.item), stack.amount);
                    needTotal += stack.amount;
                }
                return needTotal <= 0 ? 0f : haveTotal / (float) needTotal;
            })).growX().height(18f).row();
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
