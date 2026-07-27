package crystal.aviation;

import arc.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.GameState.State;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.*;

import crystal.aviation.blocks.*;
import crystal.aviation.render.*;

public class CrystalAviationMod {
    /** 卫星发射台 */
    public static Block satelliteLauncher;
    /** 卫星控制中心（选择目标区块） */
    public static Block satelliteControlCenter;
    /** 卫星扩容信标 */
    public static Block satelliteExpansionBeacon;
    /** 卫星太阳能阵列 */
    public static Block satelliteSolarArray;

    /** 默认卫星地板（可在卫星数据里自定义） */
    public static final String defaultSatelliteFloor = "metal-floor";
    public static boolean allow = true;

    /** 手动加载所有内容，供源码集成时从 ContentLoader 调用。 */
    public static void loadAllContent() {
        if (!allow)
            return;
        Log.info("[CrystalAviation] Loading content...");

        satelliteLauncher = new SatelliteLauncher("satellite-launcher") {
            {
                size = 2;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };

        satelliteControlCenter = new SatelliteControlCenter("satellite-control-center") {
            {
                size = 2;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };

        satelliteExpansionBeacon = new SatelliteExpansionBeacon("satellite-expansion-beacon") {
            {
                size = 2;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };

        satelliteSolarArray = new SatelliteSolarArray("satellite-solar-array") {
            {
                size = 2;
                this.requirements(Category.units, ItemStack.with(new Object[] { Items.copper, 1 }));
                this.alwaysUnlocked = true;
            }
        };
        // 注册自定义存档块，确保卫星数据随地图存档一起保存
        SatelliteManager.registerSaveChunk();

        // 注册事件监听器（源码集成时 Mod 构造函数不会被自动调用）
        Events.on(ClientLoadEvent.class, e -> {
            SatelliteManager.load();
            SatelliteRenderer.init();
        });
        Events.on(WorldLoadEvent.class, e -> SatelliteManager.onWorldLoaded());
        // 任意游戏存档写入前，若当前在卫星地图中则先捕获世界状态，避免建筑丢失
        Events.on(SaveWriteEvent.class, e -> SatelliteManager.onSaveWrite());
        // 每帧更新，用于 30 秒自动保存
        Events.run(Trigger.update, () -> SatelliteManager.update());

        // 游戏状态重置前（如退出到菜单），若当前在卫星地图中则立即捕获并保存卫星数据
        // 进入卫星地图时 logic.reset() 也会触发该事件，需跳过避免覆盖未加载完成的数据
        Events.on(ResetEvent.class, e -> {
            if (SatelliteManager.currentSatelliteId >= 0 && !SatelliteManager.isEnteringSatellite()
                    && !SatelliteManager.isExitingSatellite()) {
                SatelliteManager.resetRuntimeState(true);
            }
        });
        // 进入菜单状态时重置卫星运行时状态，避免旧状态污染下一场游戏
        // 此时世界已被 logic.reset() 清空，不能再次捕获，否则保存的卫星地图会是空地图
        // 进入卫星地图时也会先进入 menu 状态，需跳过
        Events.on(StateChangeEvent.class, e -> {
            if (e.to == State.menu && !SatelliteManager.isEnteringSatellite()
                    && !SatelliteManager.isExitingSatellite()) {
                SatelliteManager.resetRuntimeState(false);
            }
        });
    }
}
