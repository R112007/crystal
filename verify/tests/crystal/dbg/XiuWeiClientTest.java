package crystal.dbg;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Timer;
import crystal.CVars;
import crystal.content.FaBaos;
import crystal.content.GongFas;
import crystal.core.CultivationState;
import crystal.core.FaBaoSystem;
import crystal.core.PlayerXiuWeiSystem;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.DuJieStartEvent;
import crystal.game.CEventType.JingJieRecalc;
import crystal.game.CEventType.MagicPowerChange;
import crystal.type.GongFa;
import crystal.ui.dialogs.XiuWeiDialog;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.maps.Map;

/**
 * 修为系统的**真客户端**（单人）验证：面板绘制、渡劫确认弹窗、HUD 按钮、toast、法宝/神武槽位。
 *
 * 由 cdrv 驱动 mod 通过 crystal 的 classloader 反射调用（必须共享同一份静态状态）。
 * 截图统一写到 ~/sd/shots（按跨次运行的连续序号命名）。
 */
public class XiuWeiClientTest {
    static int pass, fail, counter, frames;
    static Fi shotDir, outDir;
    static final Seq<Runnable> steps = new Seq<>();
    static final Seq<String> rep = new Seq<>();
    static boolean started, finished;
    static int totalSteps;

    public static void run() {
        outDir = Core.files.absolute(System.getProperty("xw.out", System.getProperty("user.home") + "/sd/xw"));
        outDir.mkdirs();
        shotDir = Core.files.absolute(System.getProperty("xw.shots", System.getProperty("user.home") + "/sd/shots"));
        shotDir.mkdirs();
        counter = nextIndex();
        rep.add("=== Crystal 修为系统 真客户端验证 ===");
        rep.add("游戏 " + mindustry.core.Version.type + " build " + mindustry.core.Version.build);
        Log.info("[xw] 场景开始 截图目录=@ 序号起点=@", shotDir.absolutePath(), counter);

        hideDialogs();
        plan();
        totalSteps = steps.size;
        frames = 0;
        Events.run(Trigger.update, XiuWeiClientTest::tick);
    }

    static void tick() {
        if (finished) return;
        frames++;
        // 软渲染很慢（几 fps），每 12 帧推进一步，保证"上一步开的界面"已经画出来了
        // （同一帧里 show()+截图 会截到上一帧的画面 —— 这里踩过一次）
        if (frames % 12 == 1 && !steps.isEmpty()) {
            Runnable r = steps.remove(0);
            try {
                r.run();
            } catch (Throwable t) {
                fail("步骤异常: " + t);
                Log.err("[xw] 步骤异常", t);
            }
            if (steps.isEmpty()) finish();
        }
    }

    // ==================== 步骤脚本 ====================

    static void plan() {
        steps.add(XiuWeiClientTest::loadWorld);

        // ---------- A. 凡人面板（干净状态） ----------
        steps.add(() -> {
            PlayerXiuWeiSystem.clear();
            GongFas.none.unlock();   // clear() 会把 none 也锁上（见 headless 层的 I 段），面板要不卡住得补回来
            PlayerXiuWeiSystem.savePower();
            resetDuJieDialogCooldown();
            CVars.playerName = "测试者";
            CVars.chooseNewRoad = false;
            checkEq("干净状态：境界=凡人", JingJie.fan, CultivationState.playerJingJie);
            checkEq("干净状态：修为档=用", XiuWei.yong, CultivationState.playerXiuWei);
            checkNear("干净状态：灵力=0", 0f, CultivationState.playerMagicPower);
        });
        steps.add(() -> {
            // 真点 HUD 上的「修为」按钮（按钮默认被 showXiuWei 设置顶到屏幕外）
            Core.settings.put("showXiuWei", true);
            TextButton btn = findHudXiuWeiButton();
            check("HUD 上找得到「" + Core.bundle.get("stat.xiuwei") + "」按钮", btn != null);
            if (btn != null) clickElement(btn);
        });
        steps.add(() -> {
            check("点 HUD 按钮后修为面板打开", topDialog() instanceof XiuWeiDialog);
        });
        steps.add(() -> shot("xiuwei_panel_fan"));
        steps.add(() -> {
            XiuWeiDialog d = (XiuWeiDialog) topDialog();
            check("凡人面板：显示「尚未习得功法」", findLabel(d, Core.bundle.get("gongfa.empty", "尚未习得功法")) != null);
            check("凡人面板：神武区为空提示", findLabel(d, Core.bundle.get("shenwu.empty", "尚未炼化任何神武")) != null);
            boolean hasEmptySlot = countLabel(d, "?") > 0;
            check("凡人面板：法宝/神武空格位画出来了", hasEmptySlot);
            hideDialogs();
        });

        // ---------- B. 有功法 + 有灵力 ----------
        steps.add(() -> {
            GongFas.taiXuanTianGong1.unlock();
            GongFas.taiXuanTianGong2.unlock();
            GongFas.taiXuanTianGong3.unlock();
            Events.fire(new MagicPowerChange(3200f));
            checkEq("灵力 3200 → 境界=化龙", JingJie.hualong, CultivationState.playerJingJie);
            checkEq("化龙 → 修为档=凡", XiuWei.fan, CultivationState.playerXiuWei);
            check("升级弹了 toast", Vars.ui.hudfrag.hasToast());
        });
        steps.add(() -> shot("xiuwei_toast_breakthrough"));
        steps.add(() -> {
            PlayerXiuWeiSystem.showMagic();
        });
        steps.add(() -> shot("xiuwei_panel_faljing"));
        steps.add(() -> {
            XiuWeiDialog d = (XiuWeiDialog) topDialog();
            check("有功法面板：列出了太玄天功（3 个功法卡片）",
                    findLabel(d, GongFas.taiXuanTianGong1.localizedName) != null
                            && findLabel(d, GongFas.taiXuanTianGong3.localizedName) != null);
            check("有功法面板：下一境界文案 = " + PlayerXiuWeiSystem.getNextJingJie().str,
                    findLabel(d, PlayerXiuWeiSystem.getNextJingJie().str) != null);
            hideDialogs();
        });

        // ---------- C. 渡劫：确认弹窗 → 点「确认」真起劫 ----------
        steps.add(() -> {
            PlayerXiuWeiSystem.setChooseNewRoad(true);
            for (GongFa g : GongFa.gongFas.values()) g.unlock();
            resetDuJieDialogCooldown();
            CultivationState.playerMagicPower = 5200f;
            Events.fire(new JingJieRecalc(5200f));
            checkNear("没渡劫：灵力被顶在 开阳-0.1", JingJie.kaiyang.amount - 0.1f, CultivationState.playerMagicPower);
            check("没渡劫：弹出了渡劫确认弹窗", topDialog() != null && !(topDialog() instanceof XiuWeiDialog));
        });
        steps.add(() -> shot("xiuwei_dujie_confirm"));
        steps.add(() -> {
            Dialog d = topDialog();
            check("确认弹窗里写着渡劫目标", findLabel(d, JingJie.kaiyang.duJieCondition.str) != null);
            Element cancel = findTextButton(d, Core.bundle.get("cancel"));
            check("确认弹窗有「取消」按钮", cancel != null);
            if (cancel != null) clickElement(cancel);
            check("点取消：没有进入渡劫", !CultivationState.isInDuJie);
            hideDialogs();
        });
        steps.add(() -> {
            resetDuJieDialogCooldown();
            CultivationState.playerMagicPower = 5200f;
            Events.fire(new JingJieRecalc(5200f));
            Element ok = findTextButton(topDialog(), Core.bundle.get("dujie.confirm"));
            check("确认弹窗有「" + Core.bundle.get("dujie.confirm") + "」按钮", ok != null);
            if (ok != null) clickElement(ok);
            check("点确认：进入渡劫", CultivationState.isInDuJie);
            checkEq("点确认：待渡劫目标=开阳", JingJie.kaiyang, CultivationState.pendingDuJieJingJie);
        });

        // ---------- D. 渡劫中的面板（含击杀进度） ----------
        steps.add(() -> {
            // 换成"神途"渡劫：条件里带击杀进度，面板会多一行
            CultivationState.isInDuJie = false;
            CultivationState.pendingDuJieJingJie = null;
            Events.fire(new DuJieStartEvent(JingJie.shentu));
            if (Vars.player == null) {
                Player player = Player.create();
                player.team(Team.sharded);
                Vars.player = player;
            }
            for (int i = 0; i < 42; i++) Events.fire(new UnitDestroyEvent(mindustry.content.UnitTypes.dagger.create(Team.crux)));
            checkEq("渡劫目标=神途", JingJie.shentu, CultivationState.pendingDuJieJingJie);
            checkEq("击杀进度=42", 42, PlayerXiuWeiSystem.getDuJieKillCount());
        });
        steps.add(() -> {
            PlayerXiuWeiSystem.showMagic();
        });
        steps.add(() -> shot("xiuwei_panel_dujie"));
        steps.add(() -> {
            XiuWeiDialog d = (XiuWeiDialog) topDialog();
            check("渡劫面板：显示当前渡劫目标文案", findLabel(d, JingJie.shentu.duJieCondition.str) != null);
            check("渡劫面板：击杀进度 42/100 画出来了", findLabel(d, "42/100") != null);
            hideDialogs();
        });

        // ---------- E. 法宝 / 神武槽位 ----------
        steps.add(() -> {
            FaBaoSystem.give(FaBaos.luoLeiFu, 2);
            FaBaoSystem.give(FaBaos.bingFengFu, 1);
            CultivationState.shenwuHave.clear();
            CultivationState.shenwuHave.add("测试神武");
            CultivationState.completedDuJieJingJies.add(JingJie.shentu);
            CultivationState.isInDuJie = false;
            CultivationState.pendingDuJieJingJie = null;
            PlayerXiuWeiSystem.showMagic();
        });
        steps.add(() -> shot("xiuwei_panel_fabao"));
        steps.add(() -> {
            // 内容比屏幕高，滚到底才能看到法宝/神武那两段
            arc.scene.ui.ScrollPane sp = findScrollPane(topDialog());
            if (sp != null) sp.setScrollY(sp.getMaxY());
            check("面板里有可滚动的 ScrollPane", sp != null);
        });
        steps.add(() -> shot("xiuwei_panel_fabao_bottom"));
        steps.add(() -> {
            XiuWeiDialog d = (XiuWeiDialog) topDialog();
            check("法宝卡片：显示法宝名 " + FaBaos.luoLeiFu.localizedName,
                    findLabel(d, FaBaos.luoLeiFu.localizedName) != null);
            check("法宝卡片：显示持有数 ×2", findLabel(d, "×2") != null);
            check("神武槽位：显示占位内容", findLabel(d, "测试神武") != null);
            hideDialogs();
        });

        // ---------- F. 面板开关不炸 ----------
        steps.add(() -> {
            for (int i = 0; i < 3; i++) {
                PlayerXiuWeiSystem.showMagic();
                hideDialogs();
            }
            check("连续开关面板 3 次不抛异常", true);
        });
    }

    // ==================== 世界 / UI 工具 ====================

    static void loadWorld() {
        Map map = Vars.maps.all().find(m -> m.name().contains("Archipelago"));
        if (map == null) map = Vars.maps.all().first();
        Vars.world.loadMap(map, map.applyRules(Gamemode.survival));
        Vars.state.rules.canGameOver = false;
        Vars.state.rules.waves = false;
        Vars.logic.play();
        rep.add("已加载地图: " + (map == null ? "null" : map.name()));
    }

    static void hideDialogs() {
        for (Element e : Core.scene.root.getChildren())
            if (e instanceof Dialog d) d.hide();
    }

    static Dialog topDialog() {
        Dialog found = null;
        for (Element e : Core.scene.root.getChildren())
            if (e instanceof Dialog d && d.isShown()) found = d;
        return found;
    }

    static int nextIndex() {
        int max = 0;
        try {
            Fi d = Core.files.absolute(System.getProperty("xw.shots", System.getProperty("user.home") + "/sd/shots"));
            d.mkdirs();
            for (Fi f : d.list()) {
                String n = f.name();
                int i = 0;
                while (i < n.length() && Character.isDigit(n.charAt(i))) i++;
                if (i > 0) max = Math.max(max, Integer.parseInt(n.substring(0, i)));
            }
        } catch (Throwable t) {
            Log.err("[xw] 读截图目录失败", t);
        }
        return max + 1;
    }

    static void shot(String name) {
        try {
            Fi f = shotDir.child(String.format("%03d_%s.png", counter++, name));
            ScreenUtils.saveScreenshot(f);
            rep.add("截图 " + f.absolutePath());
            Log.info("[xw] 截图 @", f.absolutePath());
        } catch (Throwable t) {
            Log.err("[xw] 截图失败", t);
        }
    }

    /** 在元素中心发一次真实点击（走 scene 命中测试，和玩家点鼠标同一条路）。 */
    static void clickElement(Element el) {
        try {
            var v = el.localToStageCoordinates(new arc.math.geom.Vec2(el.getWidth() / 2f, el.getHeight() / 2f));
            Element hit = Core.scene.hit(v.x, v.y, true);
            if (hit == null) {
                fail("点击 " + el.getClass().getSimpleName() + " 时没有命中任何元素");
                return;
            }
            Log.info("[xw] 点击 @（stage @,@）命中 @", el.getClass().getSimpleName(), (int) v.x, (int) v.y,
                    hit.getClass().getSimpleName());
            for (InputEvent.InputEventType type : new InputEvent.InputEventType[] {
                    InputEvent.InputEventType.touchDown, InputEvent.InputEventType.touchUp }) {
                InputEvent e = new InputEvent();
                e.type = type;
                e.stageX = v.x;
                e.stageY = v.y;
                e.pointer = 0;
                e.keyCode = arc.input.KeyCode.mouseLeft;
                hit.fire(e);
            }
        } catch (Throwable t) {
            fail("点击异常: " + t);
        }
    }

    static Element findTextButton(Element root, String text) {
        if (root == null) return null;
        if (root instanceof TextButton b && b.getText() != null && b.getText().toString().equals(text)) return b;
        if (root instanceof arc.scene.Group g) {
            for (Element c : g.getChildren()) {
                Element r = findTextButton(c, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    static Label findLabel(Element root, String contains) {
        if (root == null) return null;
        if (root instanceof Label l && l.getText() != null && l.getText().toString().contains(contains)) return l;
        if (root instanceof arc.scene.Group g) {
            for (Element c : g.getChildren()) {
                Label r = findLabel(c, contains);
                if (r != null) return r;
            }
        }
        return null;
    }

    static int countLabel(Element root, String exact) {
        if (root == null) return 0;
        int n = 0;
        if (root instanceof Label l && exact.equals(String.valueOf(l.getText()))) n++;
        if (root instanceof arc.scene.Group g) for (Element c : g.getChildren()) n += countLabel(c, exact);
        return n;
    }

    static arc.scene.ui.ScrollPane findScrollPane(Element root) {
        if (root == null) return null;
        if (root instanceof arc.scene.ui.ScrollPane sp) return sp;
        if (root instanceof arc.scene.Group g) {
            for (Element c : g.getChildren()) {
                arc.scene.ui.ScrollPane r = findScrollPane(c);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** HUD 上那个「修为」按钮（模组 init 里塞进 hudGroup 的）。 */
    static TextButton findHudXiuWeiButton() {
        return (TextButton) findTextButton(Vars.ui.hudGroup, Core.bundle.get("stat.xiuwei"));
    }

    static void resetDuJieDialogCooldown() {
        try {
            var f = PlayerXiuWeiSystem.class.getDeclaredField("lastDuJieConfirmTimestamp");
            f.setAccessible(true);
            f.setLong(null, 0L);
            var f2 = PlayerXiuWeiSystem.class.getDeclaredField("lastToastTimestamp");
            f2.setAccessible(true);
            f2.setLong(null, 0L);
            var f3 = PlayerXiuWeiSystem.class.getDeclaredField("lastDuJieToastTimestamp");
            f3.setAccessible(true);
            f3.setLong(null, 0L);
        } catch (Throwable t) {
            Log.err("[xw] 清冷却失败", t);
        }
    }

    // ==================== 判定 / 报告 ====================

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        Log.info("[xw] @ @ @", ok ? "PASS" : "FAIL", name, detail);
        rep.add((ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : "  " + detail));
        if (ok) pass++; else fail++;
    }

    static void checkEq(String name, Object expect, Object actual) {
        check(name, java.util.Objects.equals(expect, actual), "期望=" + expect + " 实际=" + actual);
    }

    static void checkNear(String name, float expect, float actual) {
        check(name, Math.abs(expect - actual) < 0.01f, "期望=" + expect + " 实际=" + actual);
    }

    static void fail(String name) {
        check(name, false);
    }

    static void finish() {
        finished = true;
        rep.add("RESULT " + (fail == 0 ? "ALL PASS" : fail + " FAILED") + " (pass=" + pass + " fail=" + fail + ")");
        Log.info("[xw] RESULT @ (pass=@ fail=@)", fail == 0 ? "ALL PASS" : fail + " FAILED", pass, fail);
        try {
            outDir.child("xiuwei-client-report.txt").writeString(rep.toString("\n"), false, "UTF-8");
            Log.info("[xw] 报告 @", outDir.child("xiuwei-client-report.txt").absolutePath());
        } catch (Throwable t) {
            Log.err("[xw] 写报告失败", t);
        }
        Timer.schedule(() -> Core.app.exit(), 3f);
    }
}
