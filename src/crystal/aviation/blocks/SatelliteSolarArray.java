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
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;

import crystal.aviation.*;

import static mindustry.Vars.*;

/**
 * 卫星太阳能阵列。
 * 放置在卫星地图上，根据卫星朝向与自转产生电力，并提升卫星总发电量。
 */
public class SatelliteSolarArray extends PowerGenerator{
    /** 单块基础发电量（能量/秒） */
    public float basePower = 90f;

    public SatelliteSolarArray(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasPower = true;
        outputsPower = true;
        powerProduction = basePower / 60f; // 每 tick
        requirements(Category.power, BuildVisibility.shown, new ItemStack[]{
            new ItemStack(mindustry.content.Items.silicon, 80),
            new ItemStack(mindustry.content.Items.lead, 60),
            new ItemStack(mindustry.content.Items.metaglass, 40)
        });
    }

    public class SatelliteSolarArrayBuild extends GeneratorBuild{
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
        public void updateTile(){
            Satellite s = SatelliteManager.get(satelliteId);
            if(s != null){
                // 发电量随太阳能板自转角度变化，模拟朝向
                float efficiency = 0.5f + 0.5f * Mathf.sin(s.spinAngle);
                productionEfficiency = Mathf.clamp(efficiency, 0.05f, 1f);
                s.solarPower = powerProduction * productionEfficiency * 60f;
            }else{
                productionEfficiency = 0f;
            }
        }

        @Override
        public float getPowerProduction(){
            return enabled ? powerProduction * productionEfficiency : 0f;
        }

        @Override
        public void buildConfiguration(Table table){
            Satellite s = SatelliteManager.get(satelliteId);
            if(s != null){
                table.add("输出：" + Strings.fixed(getPowerProduction() * 60f, 1) + " /s").pad(4f);
            }else{
                table.add("未绑定卫星");
            }
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
