package crystal.aviation.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import crystal.aviation.*;
import crystal.aviation.ui.*;

import static mindustry.Vars.*;

/**
 * 卫星发射台。
 * 获得足够物品后，玩家点击可输入卫星名称，播放发射特效并自毁，
 * 随后在所属星球轨道创建一颗新卫星实例。
 */
public class SatelliteLauncher extends Block{
    /** 发射所需物品 */
    public ItemStack[] launchCost = new ItemStack[]{
        new ItemStack(Items.copper, 200),
        new ItemStack(Items.lead, 200),
        new ItemStack(Items.silicon, 150),
        new ItemStack(Items.titanium, 100),
        new ItemStack(Items.thorium, 50)
    };
    /** 发射特效 */
    public Effect launchEffect = Fx.launch;
    /** 发射持续时间（tick） */
    public float launchDuration = 120f;

    public SatelliteLauncher(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        itemCapacity = 400;
        buildCostMultiplier = 0.5f;
        requirements(Category.effect, BuildVisibility.shown, launchCost);
        consumeItems(launchCost);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.input, StatValues.items(launchDuration / 60f, launchCost));
    }

    public class SatelliteLauncherBuild extends Building{
        /** 发射进度 */
        public float launchProgress = 0f;
        /** 是否正在发射 */
        public boolean launching = false;
        /** 已输入的卫星名称 */
        public String satelliteName = "";
        /** 本次发射使用的自定义地图文件 */
        public @Nullable arc.files.Fi selectedMapFile;
        /** 发射完成后等待下一逻辑帧自毁，避免在 Android 渲染线程直接 kill 导致 tile 事件竞态崩溃 */
        public boolean pendingKill = false;

        @Override
        public void updateTile(){
            if(pendingKill){
                pendingKill = false;
                if(!dead) kill();
                return;
            }

            if(launching){
                launchProgress += edelta();
                if(launchProgress >= launchDuration){
                    finishLaunch();
                }
            }
        }

        @Override
        public void draw(){
            super.draw();
            // 绘制简单的发射进度指示
            if(launching){
                float p = launchProgress / launchDuration;
                Draw.color(Color.valueOf("ffaa00"), p);
                Fill.circle(x, y, 8f + p * 16f);
                Draw.color();
            }
        }

        @Override
        public void buildConfiguration(Table table){
            if(launching) return;

            table.button(Icon.upOpen, Styles.cleari, () -> {
                // 检查是否满足物品
                if(!hasLaunchItems()){
                    ui.showInfo("资源不足");
                    return;
                }
                // 检查星球卫星上限
                Planet planet = state.rules.sector != null ? state.rules.sector.planet : Planets.serpulo;
                if(!SatelliteManager.canLaunchOn(planet)){
                    ui.showInfo("该星球卫星数量已达上限");
                    return;
                }
                // 弹出名称输入框，可选择自定义地图文件
                SatelliteNameDialog dialog = new SatelliteNameDialog(result -> {
                    if(result == null || result.name == null || result.name.isEmpty()) return;
                    this.satelliteName = result.name;
                    this.selectedMapFile = result.mapFile;
                    configure(result.name); // 只同步名称，地图文件保留在本地
                });
                dialog.show();
            }).size(40f).tooltip("发射卫星");
        }

        @Override
        public void configured(Unit builder, Object value){
            if(value instanceof String name && !launching){
                satelliteName = name;
                launching = true;
                launchProgress = 0f;
                launchEffect.at(x, y, rotation, Color.white, this);
                Effect.shake(6f, 60f, x, y);
            }
        }

        /** 是否已集齐发射所需物品 */
        public boolean hasLaunchItems(){
            for(ItemStack stack : launchCost){
                if(items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        void finishLaunch(){
            launching = false;
            launchProgress = 0f;

            // 消耗物品
            for(ItemStack stack : launchCost){
                items.remove(stack.item, stack.amount);
            }

            // 创建卫星，传入自定义地图文件（如果有）
            Planet planet = state.rules.sector != null ? state.rules.sector.planet : Planets.serpulo;
            Satellite satellite = SatelliteManager.launch(planet, satelliteName.isEmpty() ? "Satellite" : satelliteName, selectedMapFile);
            selectedMapFile = null;

            // 自毁：标记为等待下一逻辑帧在 updateTile 中执行，避免在 Android 渲染线程直接 kill
            // 导致 TilePreChangeEvent / MinimapRenderer 的竞态 NullPointerException。
            pendingKill = true;
            final float sx = x, sy = y;
            if(satellite != null){
                Fx.rocketSmokeLarge.at(sx, sy);
                ui.showInfoFade("卫星 \"" + satellite.name + "\" 已发射");
            }
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(launchProgress);
            write.bool(launching);
            write.str(satelliteName);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            launchProgress = read.f();
            launching = read.bool();
            satelliteName = read.str();
        }
    }
}
