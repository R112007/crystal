package crystal.aviation.blocks;

import arc.*;
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
import crystal.aviation.ui.*;

import static mindustry.Vars.*;

/**
 * 卫星控制中心。
 * 放置在卫星地图上，用于选择目标区块并驱动卫星移动。
 */
public class SatelliteControlCenter extends Block{
    public SatelliteControlCenter(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasPower = true;
        consumePower(1f);
        requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
            new ItemStack(mindustry.content.Items.silicon, 100),
            new ItemStack(mindustry.content.Items.titanium, 80),
            new ItemStack(mindustry.content.Items.copper, 120)
        });
    }

    public class SatelliteControlCenterBuild extends Building{
        /** 绑定的卫星ID */
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
            Satellite s = SatelliteManager.get(satelliteId);

            if(satelliteId < 0 || s == null){
                table.add("未绑定卫星").row();
            }else{
                table.button(Icon.move, Styles.cleari, () -> {
                    SatelliteSectorDialog dialog = new SatelliteSectorDialog(s);
                    dialog.show();
                }).size(40f).tooltip("选择目标区块");
            }

            table.button(Icon.eye, Styles.cleari, () -> {
                // 打开卫星总览，可切换或退出
                new SatelliteAccessDialog().show();
            }).size(40f).tooltip("管理卫星");

            // 只要当前在卫星地图中，就显示返回星球按钮，避免玩家困在卫星里
            if(SatelliteManager.currentSatelliteId >= 0){
                table.button(Icon.exit, Styles.cleari, () -> {
                    SatelliteAccessDialog.exitToSector();
                }).size(40f).tooltip("退出卫星");
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
