package crystal.aviation.blocks;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import crystal.world.meta.CStat;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星升级中心。
 * 放置在卫星地图上，向建筑注入指定物品后升级当前卫星（tier + 1、外观改变），
 * 随后该建筑自毁。
 *
 * 升级消耗随当前卫星等级变化，最高支持升至等级 5。
 * 所有可能用到的物品统一通过 consumeItems 注册，实际扣除按当前等级对应的花费执行。
 */
public class SatelliteUpgradeCenter extends Block {

    /** 每次升级所需的物品（index 0 对应 1→2 级，以此类推）。 */
    public ItemStack[][] tierCosts = new ItemStack[][] {
            new ItemStack[] {
                    new ItemStack(mindustry.content.Items.silicon, 200),
                    new ItemStack(mindustry.content.Items.titanium, 150),
                    new ItemStack(mindustry.content.Items.thorium, 80)
            },
            new ItemStack[] {
                    new ItemStack(mindustry.content.Items.silicon, 400),
                    new ItemStack(mindustry.content.Items.titanium, 300),
                    new ItemStack(mindustry.content.Items.thorium, 180),
                    new ItemStack(mindustry.content.Items.plastanium, 120)
            },
            new ItemStack[] {
                    new ItemStack(mindustry.content.Items.silicon, 800),
                    new ItemStack(mindustry.content.Items.titanium, 600),
                    new ItemStack(mindustry.content.Items.thorium, 360),
                    new ItemStack(mindustry.content.Items.plastanium, 240),
                    new ItemStack(mindustry.content.Items.phaseFabric, 80)
            },
            new ItemStack[] {
                    new ItemStack(mindustry.content.Items.silicon, 1600),
                    new ItemStack(mindustry.content.Items.titanium, 1200),
                    new ItemStack(mindustry.content.Items.thorium, 720),
                    new ItemStack(mindustry.content.Items.plastanium, 480),
                    new ItemStack(mindustry.content.Items.phaseFabric, 200),
                    new ItemStack(mindustry.content.Items.surgeAlloy, 120)
            }
    };

    public SatelliteUpgradeCenter(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 100),
                new ItemStack(mindustry.content.Items.titanium, 80),
                new ItemStack(mindustry.content.Items.copper, 120)
        });
        // 注册所有可能用到的物品，使建筑能够接收全部升级材料
        consumeItems(allPossibleItems());
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.moduleTier, Satellite.maxTier);
        stats.add(Stat.input, table -> {
            table.row();
            for (int i = 0; i < tierCosts.length; i++) {
                table.add("[lightgray]" + (i + 1) + "->" + (i + 2) + "：[]").left();
                for (ItemStack stack : tierCosts[i]) {
                    table.image(stack.item.uiIcon).size(24f).padRight(2f);
                    table.add(String.valueOf(stack.amount)).padRight(6f);
                }
                table.row();
            }
        });
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理物品 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("items");
    }

    @Override
    public void init() {
        // 根据最大可能消耗设置足够库存
        int max = 0;
        for (ItemStack[] cost : tierCosts) {
            for (ItemStack stack : cost) {
                max = Math.max(max, stack.amount);
            }
        }
        itemCapacity = Math.max(itemCapacity, max * 2 + 10);
        super.init();
    }

    /** 收集所有等级消耗中出现的物品并集。 */
    private ItemStack[] allPossibleItems() {
        ObjectSet<Item> set = new ObjectSet<>();
        Seq<ItemStack> list = new Seq<>();
        for (ItemStack[] cost : tierCosts) {
            for (ItemStack stack : cost) {
                if (set.add(stack.item)) {
                    list.add(new ItemStack(stack.item, stack.amount));
                }
            }
        }
        return list.toArray(ItemStack.class);
    }

    public class SatelliteUpgradeCenterBuild extends Building {
        /** 绑定的卫星ID */
        public int satelliteId = -1;
        /** 是否已完成升级并等待自毁 */
        public boolean upgraded = false;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile() {
            if (upgraded || satelliteId < 0)
                return;
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null)
                return;

            if (canUpgrade(s)) {
                ItemStack[] cost = currentCost(s);
                for (ItemStack stack : cost) {
                    items.remove(stack.item, stack.amount);
                }
                int oldTier = s.tier;
                s.upgrade();
                SatelliteManager.save();
                Events.fire(new SatelliteUpgradeEvent(s, oldTier, s.tier));
                ui.showInfoFade("卫星 \"" + s.name + "\" 已升级至等级 " + s.tier);
                upgraded = true;
                // 下一逻辑帧自毁，避免在 updateTile 中直接 kill 造成竞态
                Core.app.post(() -> {
                    if (!dead && tile != null) {
                        tile.setBlock(Blocks.air);
                    }
                });
            }
        }

        /** 获取当前等级对应的升级消耗。 */
        ItemStack[] currentCost(Satellite s) {
            int index = Math.max(0, s.upgradeTier);
            if (index >= tierCosts.length)
                return new ItemStack[0];
            return tierCosts[index];
        }

        boolean canUpgrade(Satellite s) {
            if (s == null || satelliteId < 0)
                return false;
            if (s.tier >= Satellite.maxTier)
                return false;
            ItemStack[] cost = currentCost(s);
            for (ItemStack stack : cost) {
                if (items.get(stack.item) < stack.amount)
                    return false;
            }
            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(4f);

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = SatelliteManager.get(satelliteId);

                if (s != null) {
                    infoTable.add("当前等级: " + s.tier + " / " + Satellite.maxTier).style(Styles.outlineLabel).row();
                }

                if (s != null && s.tier < Satellite.maxTier) {
                    infoTable.add("升级所需物品:").style(Styles.outlineLabel).row();
                    for (ItemStack stack : currentCost(s)) {
                        int have = items.get(stack.item);
                        String color = have >= stack.amount ? "[lightgray]" : "[scarlet]";
                        infoTable.add(color + stack.item.localizedName + ": " + have + "/" + stack.amount)
                                .style(Styles.outlineLabel).row();
                    }
                } else {
                    infoTable.add("[gray]卫星已达最高等级").style(Styles.outlineLabel).row();
                }

                infoTable.add("[gray]物品满足后自动升级并移除建筑").style(Styles.outlineLabel).row();
            });
            table.add(infoTable).row();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (item == null || upgraded)
                return false;
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null || s.tier >= Satellite.maxTier)
                return false;
            // 总容量 guard：防止建筑被无关物品塞满
            if (items != null && items.total() >= itemCapacity)
                return false;
            ItemStack[] cost = currentCost(s);
            for (ItemStack stack : cost) {
                if (stack.item == item) {
                    // 只接收当前升级所需的差额，避免过量
                    return items.get(item) < stack.amount;
                }
            }
            return false;
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            if (items == null)
                return;
            table.add(new Bar(() -> "物品容量: " + items.total() + "/" + itemCapacity,
                    () -> Pal.items, () -> items.total() / (float) itemCapacity))
                    .growX().height(18f).row();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
            write.bool(upgraded);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
            upgraded = read.bool();
        }
    }

    /** 卫星升级事件，可供监听器订阅。 */
    public static class SatelliteUpgradeEvent {
        public final Satellite satellite;
        public final int oldTier;
        public final int newTier;

        public SatelliteUpgradeEvent(Satellite satellite, int oldTier, int newTier) {
            this.satellite = satellite;
            this.oldTier = oldTier;
            this.newTier = newTier;
        }
    }
}
