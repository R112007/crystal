package crystal.aviation.blocks;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.Block;
import crystal.world.meta.CStat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星液体仓。
 *
 * 放置在卫星地图上，为卫星增加液体总容量。卫星的液体仓库为全局共享，
 * 所有液体仓共同扩容，支持同时储存多种液体。同一卫星上的液体仓数量受卫星等级限制。
 * 数量与容量直接按当前世界中实际存在的建筑实体统计，避免计数漂移。
 */
public class SatelliteLiquidTank extends Block {

    /** 每座液体仓为卫星增加的液体容量。 */
    public float capacityPerTank = 1000f;

    public SatelliteLiquidTank(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 100),
                new ItemStack(mindustry.content.Items.metaglass, 80),
                new ItemStack(mindustry.content.Items.titanium, 60)
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.liquidCapacityIncrease, (int) capacityPerTank, StatUnit.liquidUnits);
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
        return count < s.liquidTankLimit;
    }

    public class SatelliteLiquidTankBuild extends Building {
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
                s.liquidCapacity += capacityPerTank;
                s.liquidTankCount++;
                Core.app.post(() -> {
                    if (s != null && !dead) {
                        s.recalcStorageCapacityFromWorld(false);
                        SatelliteManager.save();
                    }
                });
            }
        }

        @Override
        public void updateTile() {
            // 液体仓作为静态容量建筑，无需每帧更新
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(2f);

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null) {
                    infoTable.add("未绑定卫星").row();
                    return;
                }

                int count = SatelliteManager.countCurrentWorldBuildings(SatelliteLiquidTank.this);
                infoTable.add("液体仓: " + count + " / " + s.liquidTankLimit)
                        .style(Styles.outlineLabel).row();
                infoTable.add("单种容量上限: " + (int) s.liquidCapacity).style(Styles.outlineLabel).row();

                infoTable.row();
                infoTable.add("[accent]液体储量[]").padTop(6f).row();
                if (s.liquidStorage.isEmpty()) {
                    infoTable.add("  无液体").row();
                } else {
                    for (var entry : s.liquidStorage) {
                        Liquid liquid = entry.key;
                        float amount = entry.value;
                        if (liquid == null || amount <= 0.001f)
                            continue;
                        infoTable
                                .add(new Bar(
                                        () -> liquid.localizedName + ": "
                                                + Strings.fixed(s.liquidStorage.get(liquid, 0f), 1) + "/"
                                                + (int) s.liquidCapacity,
                                        () -> liquid.color,
                                        () -> s.liquidCapacity <= 0.001f ? 0f
                                                : s.liquidStorage.get(liquid, 0f) / s.liquidCapacity))
                                .growX().height(18f).padTop(2f).row();
                    }
                }
            });
            table.add(infoTable).row();
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            Table liquidTable = new Table();
            liquidTable.defaults().growX().left();
            liquidTable.update(() -> {
                liquidTable.clearChildren();
                Satellite s = SatelliteManager.get(satelliteId);
                if (s == null || s.liquidStorage.isEmpty()) {
                    liquidTable.add("无液体").row();
                    return;
                }
                for (var entry : s.liquidStorage) {
                    Liquid liquid = entry.key;
                    if (liquid == null || entry.value <= 0.001f)
                        continue;
                    liquidTable
                            .add(new Bar(() -> liquid.localizedName + ": "
                                    + Strings.fixed(s.liquidStorage.get(liquid, 0f), 1) + "/" + (int) s.liquidCapacity,
                                    () -> liquid.color,
                                    () -> s.liquidCapacity <= 0.001f ? 0f
                                            : s.liquidStorage.get(liquid, 0f) / s.liquidCapacity))
                                    .growX().height(18f).padTop(2f).row();
                }
            });
            table.add(liquidTable).growX().row();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                // 直接按实体数量重新计算容量，排除自身；拆除后容量可能降低，需要裁剪多余液体
                s.recalcStorageCapacityFromWorld(this);
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
