package crystal.aviation.blocks;

import arc.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import crystal.aviation.*;

import static mindustry.Vars.*;

/**
 * 卫星对接港。
 * 两个卫星均建有对接港后，可通过建筑界面发起对接，
 * 对接后两颗卫星地图合并，3D模型也会组合显示。
 */
public class SatelliteDockingPort extends Block{
    public SatelliteDockingPort(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
            new ItemStack(mindustry.content.Items.silicon, 120),
            new ItemStack(mindustry.content.Items.titanium, 100),
            new ItemStack(mindustry.content.Items.plastanium, 40)
        });
    }

    public class SatelliteDockingPortBuild extends Building{
        public int satelliteId = -1;

        @Override
        public void created(){
            super.created();
            // 在卫星地图中新建造时绑定当前卫星
            if(SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0){
                satelliteId = SatelliteManager.currentSatelliteId;
            }
        }

        @Override
        public void buildConfiguration(Table table){
            if(satelliteId < 0){
                table.add("未绑定卫星");
                return;
            }
            Satellite self = SatelliteManager.get(satelliteId);
            if(self == null){
                table.add("卫星已丢失");
                return;
            }

            table.button(Icon.link, Styles.cleari, () -> {
                // 寻找同一星球上最近的另一颗未对接卫星
                Satellite nearest = null;
                float minDst = Float.MAX_VALUE;
                for(Satellite other : SatelliteManager.satellites.values()){
                    if(other.id == self.id || other.planet != self.planet) continue;
                    if(self.dockedSatellites.contains(other.id)) continue;
                    float dst = Math.abs(other.orbitAngle - self.orbitAngle);
                    if(dst < minDst){
                        minDst = dst;
                        nearest = other;
                    }
                }
                if(nearest == null){
                    ui.showInfo("没有可对接目标");
                    return;
                }
                self.dockWith(nearest);
                ui.showInfoFade("已与卫星 \"" + nearest.name + "\" 对接");
            }).size(40f).tooltip("对接卫星");
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(satelliteId);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            satelliteId = read.i();
        }
    }
}
