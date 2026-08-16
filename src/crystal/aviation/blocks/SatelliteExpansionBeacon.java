package crystal.aviation.blocks;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import crystal.world.meta.CStat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星扩容仓。
 *
 * 放置后为当前卫星增加物品仓库容量上限；拆除后相应减少。
 * 同一卫星上的扩容仓数量受卫星等级限制。
 * 数量与容量直接按当前世界中实际存在的建筑实体统计，避免计数漂移。
 */
public class SatelliteExpansionBeacon extends Block {

    /** 每座扩容仓为卫星增加的物品仓库容量。 */
    public float itemCapacityIncrease = 1000f;

    public SatelliteExpansionBeacon(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.titanium, 60),
                new ItemStack(mindustry.content.Items.metaglass, 40)
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.itemCapacityIncrease, (int) itemCapacityIncrease, StatUnit.items);
    }

    @Override
    public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
        if (SatelliteManager.currentSatelliteId < 0)
            return false;
        Satellite s = SatelliteManager.get(SatelliteManager.currentSatelliteId);
        if (s == null)
            return false;
        // 直接用实体数量判断
        int count = SatelliteManager.countCurrentWorldBuildings(this);
        return count < s.expansionBeaconLimit;
    }

    public class SatelliteExpansionBeaconBuild extends Building {
        /** 绑定的卫星ID */
        public int satelliteId = -1;
        /** 已弃用：保留字段仅用于旧存档读取兼容。 */
        public boolean added = false;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
            final Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                // created() 调用时建筑尚未加入 Groups.build，此时遍历世界统计不到自己。
                // 先按建筑本身直接增加容量，保证玩家立刻看到上限提升；
                // 下一帧再通过 Groups.build 重新校准计数，避免漂移。
                s.itemStorageCapacity += itemCapacityIncrease;
                s.expansionBeaconCount++;
                s.refreshCoreCapacity();
                Core.app.post(() -> {
                    if (s != null && !dead) {
                        s.recalcStorageCapacityFromWorld(false);
                        s.refreshCoreCapacity();
                        SatelliteManager.save();
                    }
                });
            }
        }

        @Override
        public void updateTile() {
            // 扩容仓作为静态容量建筑，无需每帧更新
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(2f);

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null) {
                    infoTable.add("未绑定卫星").style(Styles.outlineLabel).row();
                    return;
                }

                int count = SatelliteManager.countCurrentWorldBuildings(SatelliteExpansionBeacon.this);
                infoTable.add("扩容仓: " + count + " / " + s.expansionBeaconLimit)
                        .style(Styles.outlineLabel).row();
                infoTable.add("物品总容量: " + (int) s.itemStorageCapacity).style(Styles.outlineLabel).row();
            });
            table.add(infoTable).row();
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar(() -> {
                Satellite s = SatelliteManager.get(satelliteId);
                int total = s == null || s.items == null ? 0 : s.items.total();
                int cap = s == null ? 0 : (int) s.itemStorageCapacity;
                return "卫星物品: " + total + "/" + cap;
            }, () -> Pal.items, () -> {
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null || s.items == null || s.itemStorageCapacity <= 0)
                    return 0f;
                return s.items.total() / s.itemStorageCapacity;
            })).growX().height(18f).row();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                // 直接按实体数量重新计算容量，排除自身
                s.recalcStorageCapacityFromWorld(this);
                s.refreshCoreCapacity();
                SatelliteManager.save();
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
            write.bool(added);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
            added = read.bool();
        }
    }
}
