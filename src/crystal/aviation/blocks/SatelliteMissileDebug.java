package crystal.aviation.blocks;

import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.*;
import crystal.world.meta.CStat;
import mindustry.world.meta.Stat;

import crystal.aviation.*;
import crystal.type.SatelliteMissile;
import crystal.world.meta.CBuildVisibility;

/**
 * 导弹调试建筑。
 * 放置在卫星地图上，点击后可以为当前卫星补充导弹库存。
 */
public class SatelliteMissileDebug extends Block {
    public SatelliteMissileDebug(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {});
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.missileTypes, SatelliteMissile.map.size);
    }

    public class SatelliteMissileDebugBuild extends Building {
        public int satelliteId = -1;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            Satellite s = SatelliteManager.get(satelliteId);
            if (satelliteId < 0 || s == null) {
                table.add("未绑定卫星").row();
                return;
            }

            table.clear();
            table.top();

            table.add("[accent]导弹补给面板[]").padBottom(6f).row();
            table.add("当前库存:").left().row();

            for (SatelliteMissile m : SatelliteMissile.map.values()) {
                table.table(row -> {
                    row.label(() -> m.name + ": " + (s.missileModule != null ? s.missileModule.get(m) : 0)).left().growX();
                    row.button("+10", () -> {
                        s.missileModule.add(m, 10);
                    }).size(50f, 36f);
                    row.button("+100", () -> {
                        s.missileModule.add(m, 100);
                    }).size(60f, 36f);
                }).growX().pad(2f).row();
            }

            table.row();
            table.button("全部补满 (+100每种)", () -> {
                for (SatelliteMissile m : SatelliteMissile.map.values()) {
                    s.missileModule.add(m, 100);
                }

            }).growX().height(40f).padTop(6f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
        }
    }
}
