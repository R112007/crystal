package crystal.aviation.blocks;

import arc.scene.ui.layout.Table;
import arc.util.Time;
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
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 卫星电力接收器。
 *
 * 地面建筑，从绑定到当前区块的卫星电力仓库中汲取能量并转化为电网电力。
 * 实际发电量受卫星当前储电量限制，可多个接收器共享同一颗卫星的电力。
 */
public class SatellitePowerReceiver extends PowerGenerator {
    /** 最大输出功率（能量/秒）。 */
    public float maxOutput = 180f;
    /** 每帧最多从卫星汲取的能量。 */
    public float drawRate = 5f;

    public SatellitePowerReceiver(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasPower = true;
        outputsPower = true;
        powerProduction = maxOutput / 60f;
        requirements(Category.power, new ItemStack[] {
                new ItemStack(mindustry.content.Items.copper, 120),
                new ItemStack(mindustry.content.Items.lead, 100),
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.titanium, 60)
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.basePowerGeneration, maxOutput, StatUnit.powerSecond);
    }

    public class SatellitePowerReceiverBuild extends GeneratorBuild {
        /** 本建筑当前实际输出功率（能量/秒） */
        public float currentOutput = 0f;

        @Override
        public void updateTile() {
            Satellite s = findBoundSatellite();
            if (s != null && s.powerStorage > 0.001f) {
                // 计算本帧期望输出能量
                float want = Math.min(drawRate, Math.max(0f, maxOutput * Time.delta / 60f));
                float actual = Math.min(want, s.powerStorage);
                if (actual > 0f) {
                    s.powerStorage -= actual;
                    currentOutput = actual / Math.max(0.001f, Time.delta) * 60f;
                    productionEfficiency = 1f;
                } else {
                    currentOutput = 0f;
                    productionEfficiency = 0f;
                }
            } else {
                currentOutput = 0f;
                productionEfficiency = 0f;
            }
        }

        @Override
        public float getPowerProduction() {
            return enabled ? currentOutput / 60f : 0f;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(4f);

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = findBoundSatellite();
                if (s == null) {
                    infoTable.add("[scarlet]当前区块未绑定卫星[]").style(Styles.outlineLabel).row();
                } else {
                    infoTable.add("绑定卫星: " + s.name).style(Styles.outlineLabel).row();
                    infoTable.add("卫星储电: " + (int) s.powerStorage + " / " + (int) s.powerCapacity)
                            .style(Styles.outlineLabel).row();
                    infoTable.add("当前输出: " + (int) currentOutput + " /s").style(Styles.outlineLabel).row();
                }
            });
            table.add(infoTable).row();
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(new Bar(() -> {
                Satellite s = findBoundSatellite();
                int storage = s == null ? 0 : (int) s.powerStorage;
                int cap = s == null ? 0 : (int) s.powerCapacity;
                return "卫星电力: " + storage + "/" + cap;
            }, () -> Pal.power, () -> {
                Satellite s = findBoundSatellite();
                if (s == null || s.powerCapacity <= 0.001f) return 0f;
                return s.powerStorage / s.powerCapacity;
            })).growX().height(18f).row();
            table.add(new Bar(() -> "当前输出: " + (int) currentOutput + "/" + (int) maxOutput + " /s",
                    () -> Pal.powerLight, () -> currentOutput / maxOutput))
                    .growX().height(18f).row();
        }

        /** 查找绑定到当前区块的卫星。 */
        Satellite findBoundSatellite() {
            if (state.rules.sector == null)
                return null;
            mindustry.type.Sector sector = state.rules.sector;
            for (Satellite s : SatelliteManager.satellites.values()) {
                if (s.planet == sector.planet && s.boundToSector && s.targetSectorId == sector.id) {
                    return s;
                }
            }
            return null;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(currentOutput);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            currentOutput = read.f();
        }
    }
}
