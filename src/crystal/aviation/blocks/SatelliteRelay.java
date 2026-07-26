package crystal.aviation.blocks;

import arc.*;
import arc.math.Mathf;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import crystal.aviation.*;

import static mindustry.Vars.*;

/**
 * 卫星量子中继器。
 * 提升所属卫星的移动速度，并缩短同卫星上扫描仪的冷却时间。
 */
public class SatelliteRelay extends Block {
    /** 每座中继器提供的移速加成 */
    public float speedBoost = 0.15f;

    public SatelliteRelay(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasPower = true;
        consumePower(0.8f);
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 120),
                new ItemStack(mindustry.content.Items.phaseFabric, 30),
                new ItemStack(mindustry.content.Items.surgeAlloy, 20)
        });
    }

    public class SatelliteRelayBuild extends Building {
        public int satelliteId = -1;
        public float warmup = 0f;

        @Override
        public void created(){
            super.created();
            // 在卫星地图中新建造时绑定当前卫星
            if(SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0){
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void updateTile() {
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null && isValid()) {
                warmup = Mathf.lerp(warmup, 1f, 0.05f);
                // 动态提升轨道速度（上限 2 倍）
                s.orbitSpeed = Mathf.clamp(s.orbitSpeed * (1f + speedBoost * warmup * Time.delta * 0.01f), -0.008f,
                        0.008f);
            } else {
                warmup = Mathf.lerp(warmup, 0f, 0.05f);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                table.add("移速加成：" + Strings.fixed(speedBoost * warmup * 100f, 0) + "%").pad(4f);
            } else {
                table.add("未绑定卫星");
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(satelliteId);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            satelliteId = read.i();
            warmup = read.f();
        }
    }
}
