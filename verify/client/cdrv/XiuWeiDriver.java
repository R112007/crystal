package cdrv;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;

/**
 * 修为系统的真客户端验证驱动（只依赖游戏类）。
 * 它拿到 crystal 的 classloader，反射调用塞进 Crystal.jar 里的 crystal.dbg.XiuWeiClientTest。
 */
public class XiuWeiDriver extends Mod {
    static boolean started;

    @Override
    public void init() {
        Log.info("[xwd] driver init pid=" + ProcessHandle.current().pid());
        // 预置道号，免得"首次进游戏强制输名字"的弹窗挡住面板
        Core.settings.put("crystal_player_name", System.getProperty("xw.player", "测试者"));
        Core.settings.autosave();
        Events.run(EventType.Trigger.update, () -> {
            if (!started && arc.util.Time.time > 25f) {
                Log.info("[xwd] Trigger.update 兜底启动");
                start();
            }
        });
        Events.on(EventType.ClientLoadEvent.class, e -> {
            Log.info("[xwd] ClientLoadEvent");
            Timer.schedule(XiuWeiDriver::start, 3f);
        });
    }

    static void start() {
        if (started) return;
        started = true;
        try {
            ClassLoader ml = Vars.mods.getMod("crystal").main.getClass().getClassLoader();
            Class<?> c = Class.forName("crystal.dbg.XiuWeiClientTest", true, ml);
            c.getMethod("run").invoke(null);
        } catch (Throwable t) {
            Log.err("[xwd] 启动场景失败", t);
            Core.app.exit();
        }
    }
}
