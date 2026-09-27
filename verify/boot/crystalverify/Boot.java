package crystalverify;

import arc.ApplicationListener;
import arc.Core;
import arc.backend.headless.HeadlessApplication;
import arc.graphics.g2d.TextureAtlas;
import arc.scene.Scene;
import arc.scene.style.Drawable;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.Platform;
import mindustry.core.UI;
import mindustry.mod.Mod;
import mindustry.net.Net;
import mindustry.ui.Fonts;
import mindustry.ui.fragments.HudFragment;

/**
 * headless 逻辑测试的启动器（跑在系统 classpath 上）。
 *
 * 它只做游戏侧的初始化，真正的测试场景放在 crystal.dbg.* 里、**注入 crystal.jar**
 * 之后由 crystal 自己的 classloader 加载 —— 这样测试和模组共享同一份静态状态
 * （CultivationState 等），不会出现"两份类、两套状态"的假结果。
 */
public class Boot implements ApplicationListener {
    static String dataDir = "/tmp/mp_xiu/data";
    static String scenario = "crystal.dbg.XiuWeiHeadlessTest";
    static String[] args = new String[0];

    public static void main(String[] a) {
        int i = 0;
        if (a.length > i) dataDir = a[i++];
        if (a.length > i) scenario = a[i++];
        String[] rest = new String[a.length - i];
        System.arraycopy(a, i, rest, 0, rest.length);
        args = rest;
        Vars.platform = new Platform() {};
        Vars.net = new Net(Vars.platform.getNet());
        new HeadlessApplication(new Boot(), t -> t.printStackTrace());
    }

    @Override
    public void init() {
        try {
            Core.settings.setDataDirectory(Core.files.local(dataDir));
            Vars.loadLocales = false;
            Vars.loadSettings();
            Vars.headless = true;
            Vars.init();
            UI.loadColors();
            Fonts.loadContentIconsHeadless();
            Vars.content.createBaseContent();
            Vars.mods.loadScripts();
            Vars.content.createModContent();
            Vars.content.init();
            // Trigger.update 上有一个网络侧的监听（ArcNetProvider）会摸 Vars.netServer，
            // 不补上就会在第一次 fire(Trigger.update) 时把整条事件派发打断。
            if (Vars.netClient == null) Vars.netClient = new mindustry.core.NetClient();
            if (Vars.logic == null) Vars.logic = new mindustry.core.Logic();
            if (Vars.netServer == null) Vars.netServer = new mindustry.core.NetServer();
            installStubs();
            try {
                Vars.mods.eachClass(Mod::init);
            } catch (Throwable t) {
                // crystal 的 init() 里有纯客户端的东西（换 UI、立绘、图标…），headless 里不一定全能过；
                // 修为系统自己那部分下面会显式补上，所以这里只报告不中断。
                System.out.println("[XW-BOOT] 模组 init() 没跑完（headless 正常现象）: " + t);
                for (Throwable c = t.getCause(); c != null; c = c.getCause()) {
                    System.out.println("[XW-BOOT]   因为: " + c);
                    StackTraceElement[] st = c.getStackTrace();
                    for (int i = 0; i < Math.min(6, st.length); i++) System.out.println("[XW-BOOT]     at " + st[i]);
                }
            }
            initCultivation();

            var cm = Vars.mods.getMod("crystal");
            if (cm == null || cm.main == null) {
                System.out.println("[XW-BOOT] crystal 模组没加载（mods 目录里没有 Crystal.jar？）");
                System.exit(3);
            }
            ClassLoader ml = cm.main.getClass().getClassLoader();
            Class<?> c = Class.forName(scenario, true, ml);
            c.getMethod("run", String[].class).invoke(null, (Object) args);
        } catch (Throwable t) {
            System.out.println("[XW-BOOT] 崩了: " + t);
            t.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * crystal 是个纯客户端模组：它的 init() 会摸 Core.atlas / Vars.ui / Core.scene。
     * headless 里这三样都不存在，所以先装最小替身（blankAtlas 不需要 GL）。
     */
    static void installStubs() {
        if (Core.atlas == null) Core.atlas = stubAtlas();
        if (Vars.ui == null) Vars.ui = stubUi();
        stubIcons();
        System.out.println("[XW-BOOT] 替身就绪: atlas=" + Core.atlas + " scene=" + Core.scene + " ui=" + Vars.ui);
    }

    /**
     * headless 里 Icons.load() 没跑，mindustry.gen.Icon 的静态 drawable 全是 null；
     * 模组的 ClientLoadEvent 回调里有直接用 Icon.xxx.getRegion() 的（SatelliteMissile），
     * 会中途抛异常打断整个事件派发。这里统一塞个空 drawable。
     */
    static void stubIcons() {
        try {
            var dummy = new arc.scene.style.TextureRegionDrawable(new arc.graphics.g2d.TextureRegion());
            int n = 0;
            for (java.lang.reflect.Field f : Class.forName("mindustry.gen.Icon").getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && f.getType() == arc.scene.style.TextureRegionDrawable.class) {
                    f.set(null, dummy);
                    n++;
                }
            }
            System.out.println("[XW-BOOT] Icon 替身 " + n + " 个");
        } catch (Throwable t) {
            System.out.println("[XW-BOOT] Icon 替身失败: " + t);
        }
    }

    /** UI 的构造函数要加载字体（要 GL/资源系统），headless 里直接 Unsafe 分配一个空壳再塞替身字段。 */
    @SuppressWarnings("deprecation")
    static UI stubUi() {
        try {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) f.get(null);
            UI ui = (UI) unsafe.allocateInstance(UI.class);
            ui.hudGroup = new WidgetGroup();
            ui.menuGroup = new WidgetGroup();
            ui.hudfrag = new HudFragment() {
                @Override
                public void showToast(Drawable icon, float size, String text) {
                    System.out.println("[XW-TOAST] " + text);
                }
            };
            return ui;
        } catch (Throwable t) {
            throw new RuntimeException("造 UI 替身失败", t);
        }
    }

    /** 把修为系统自己的初始化补上（模组 init() 没跑到也要有）。 */
    static void initCultivation() {
        try {
            ClassLoader ml = Vars.mods.getMod("crystal").main.getClass().getClassLoader();
            Class.forName("crystal.core.CultivationState", true, ml);
            Class<?> sys = Class.forName("crystal.core.PlayerXiuWeiSystem", true, ml);
            sys.getMethod("init").invoke(null);
            Class<?> fabao = Class.forName("crystal.core.FaBaoSystem", true, ml);
            fabao.getMethod("init").invoke(null);
            System.out.println("[XW-BOOT] 修为系统 init 就绪");
        } catch (Throwable t) {
            System.out.println("[XW-BOOT] 修为系统 init 失败: " + t);
            t.printStackTrace();
            System.exit(4);
        }
    }

    /** headless 里 Core.atlas 是 null，而找图元的地方到处都是；给个"永远找得到空区域"的替身。 */
    static TextureAtlas stubAtlas() {
        return new TextureAtlas() {
            @Override
            public AtlasRegion find(String name) {
                return new AtlasRegion();
            }

            @Override
            public AtlasRegion white() {
                return new AtlasRegion();
            }
        };
    }
}
