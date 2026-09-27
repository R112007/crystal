package crystal.dbg;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.core.CultivationState;
import crystal.core.PlayerXiuWeiSystem;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.DuJieEndEvent;
import crystal.game.CEventType.DuJieStartEvent;
import crystal.game.CEventType.GongFaBuQuanEvent;
import crystal.game.CEventType.JingJieRecalc;
import crystal.game.CEventType.MagicPowerChange;
import crystal.game.CEventType.XiuWeiRecalc;
import crystal.type.GongFa;
import crystal.type.MagicUnitType;
import mindustry.Vars;
import mindustry.content.SectorPresets;
import mindustry.content.UnitTypes;
import mindustry.core.GameState.State;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.Team;
import mindustry.gen.Unit;

/**
 * 修为系统 headless 逻辑测试（注入 crystal.jar，跑在真游戏类里）。
 *
 * 覆盖：境界/路线数据体检、功法门槛、渡劫门槛、渡劫成功/失败、境界→修为档位映射、
 * 灵力自然恢复、击杀敌方法术单位加修为、存档往返（同进程 + 跨进程两阶段）、旧档兼容、下一境界。
 *
 * 说明：headless 里没有 GL/Scene，而 crystal 是纯客户端模组 —— 启动器（crystalverify.Boot）装了
 * 最小替身（空图集 / Icon 替身 / UI 空壳）。因此面板、渡劫确认弹窗、HUD 按钮这些"要画面"的东西
 * 不在这里验（交给真客户端那层），本层只验状态机与存档，判定标准是数值/集合。
 *
 * 阶段（第一个参数）：空 = 主电池；write/read = 跨进程存档两阶段；legacy = 旧档兼容。
 */
public class XiuWeiHeadlessTest {
    static int pass, fail;
    static String phase = "";

    // 观测值
    static JingJie lastDuJieEndTarget;
    static boolean lastDuJieEndSuccess, sawDuJieEnd;
    static float lastMagicDelta;
    static boolean sawMagicChange;
    static boolean sawShortGongFa;
    static JingJie shortGongFaTarget;
    static GongFa shortGongFaMissing;

    public static void run(String[] args) {
        phase = args.length > 0 ? args[0] : "";
        Events.on(DuJieEndEvent.class, e -> {
            lastDuJieEndTarget = e.targetJingJie;
            lastDuJieEndSuccess = e.success;
            sawDuJieEnd = true;
        });
        Events.on(MagicPowerChange.class, e -> {
            lastMagicDelta = e.amount;
            sawMagicChange = true;
        });
        Events.on(GongFaBuQuanEvent.class, e -> {
            sawShortGongFa = true;
            shortGongFaTarget = e.jingJie;
            shortGongFaMissing = e.gongFa;
        });
        try {
            clientLoad();
            switch (phase) {
                case "write" -> doWrite();
                case "read" -> doRead();
                case "legacy" -> doLegacy();
                case "seed" -> doSeed();
                default -> doMain();
            }
        } catch (Throwable t) {
            System.out.println("[XW] 崩了: " + t);
            t.printStackTrace();
            System.out.println("[XW] RESULT 崩了 (pass=" + pass + " fail=" + fail + ")");
            System.exit(2);
        }
    }

    // ==================== 基础设施 ====================

    /** 触发模组的 ClientLoadEvent 流程（headless 里 HUD 按钮那段会失败，属预期）。 */
    static void clientLoad() {
        try {
            Events.fire(new ClientLoadEvent());
            System.out.println("[XW] ClientLoadEvent 派发完成");
        } catch (Throwable t) {
            System.out.println("[XW] ClientLoadEvent 在 HUD 部分中断（headless 预期，状态已恢复）: " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(8, st.length); i++) System.out.println("[XW]    at " + st[i]);
        }
    }

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println("[XW] " + (ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : "  " + detail));
        if (ok) pass++; else fail++;
    }

    static void checkEq(String name, Object expect, Object actual) {
        check(name, java.util.Objects.equals(expect, actual), "期望=" + expect + " 实际=" + actual);
    }

    static void checkNear(String name, float expect, float actual) {
        check(name, Math.abs(expect - actual) < 0.01f, "期望=" + expect + " 实际=" + actual);
    }

    static void fire(Object event) {
        try {
            if (event instanceof Enum<?> e) {
                Events.fire(e);
            } else {
                Events.fire(event);
            }
        } catch (Throwable t) {
            System.out.println("[XW] fire(" + event.getClass().getSimpleName() + ") 抛异常: " + t);
        }
    }

    /** 加/减灵力（走真实事件链路，会被功法/渡劫门槛截断）。 */
    static void gain(float amount) {
        fire(new MagicPowerChange(amount));
    }

    /** 直接设成目标灵力（先直接写字段，再让事件链路自己重算境界）。 */
    static void setPower(float target) {
        CultivationState.playerMagicPower = target;
        fire(new JingJieRecalc(target));
    }

    static void tick() {
        Time.delta = 1f;
        fire(Trigger.update);
    }

    static void tick(int n) {
        for (int i = 0; i < n; i++) tick();
    }

    /** 渡劫击杀计数要求 Vars.player 存在（用它的队伍判定敌我）；headless 里没有真玩家，造一个。 */
    static void asPlayer() {
        var p = mindustry.gen.Player.create();
        p.team(Team.sharded);
        p.name = "测试者";
        Vars.player = p;
    }

    /** 渡劫确认弹窗的 60 秒冷却先顶掉：headless 没有 Scene，弹窗建不出来；门槛判定本身照跑。 */
    static void primeDuJieConfirmCooldown() {
        try {
            var f = PlayerXiuWeiSystem.class.getDeclaredField("lastDuJieConfirmTimestamp");
            f.setAccessible(true);
            f.setLong(null, Time.millis());
        } catch (Throwable t) {
            System.out.println("[XW] 顶掉渡劫弹窗冷却失败: " + t);
        }
    }

    static boolean monotonic(JingJie[] route) {
        for (int i = 1; i < route.length; i++) if (route[i].amount <= route[i - 1].amount) return false;
        return true;
    }

    static String routeText(JingJie[] route) {
        StringBuilder sb = new StringBuilder();
        for (JingJie j : route) sb.append(j.str).append('(').append(j.amount).append(") ");
        return sb.toString();
    }

    static String firstOutOfOrder(JingJie[] route) {
        for (int i = 1; i < route.length; i++) {
            if (route[i].amount <= route[i - 1].amount) {
                return "第" + i + "位 " + route[i].name() + "(" + route[i].amount + ") 不大于前一位 "
                        + route[i - 1].name() + "(" + route[i - 1].amount + ")";
            }
        }
        return "";
    }

    static void reset() {
        PlayerXiuWeiSystem.clear();
        // clear()（调试用的硬重置）会把 none 也锁上；none 是"凡人"这个境界的功法，
        // 锁上之后功法门槛会退化成"凡人缺功法"，而凡人被 getMin() 豁免 —— 修为就再也升不上去。
        // 这是模组里的真问题（见 clearLocksSentinelGongFa()），测试要复现"新档"状态就得把它补回来。
        GongFas.none.unlock();
        CultivationState.availableMagicPower = 0f;
        CVars.chooseNewRoad = false;
        CultivationState.playerMagicPower = 0f;
        fire(new JingJieRecalc(0f));
    }

    /** 缺陷复现：debug 的"重置修为"（clear）把 none 功法也锁了 → 之后灵力涨再多也升不了境界。 */
    static void clearLocksSentinelGongFa() {
        System.out.println("[XW] --- I. 调试重置（clear）的副作用 ---");
        unlockAll();
        setPower(3200f);
        checkEq("重置前：灵力 3200 → 化龙", JingJie.hualong, CultivationState.playerJingJie);

        PlayerXiuWeiSystem.clear();
        boolean noneLocked = !GongFas.none.unlocked() && !CultivationState.gongfaHave.contains(GongFas.none);
        System.out.println("[XW] clear 后 none（凡人境界绑定的占位功法）已锁=" + noneLocked
                + " 已持有功法数=" + CultivationState.gongfaHave.size);

        // 玩家"正常习得功法"：把除 none 之外的都解锁（玩家不会去习得 none 这个占位）
        for (GongFa g : GongFa.gongFas.values()) if (g != GongFas.none) g.unlock();
        gain(1000f);
        boolean stuck = CultivationState.playerJingJie == JingJie.fan;
        check("clear 之后（功法补全、但 none 被锁死）灵力 1000 仍能升到天人",
                !stuck, "实际境界=" + CultivationState.playerJingJie.str
                        + " 灵力=" + CultivationState.playerMagicPower + "（卡在凡人 = 功法门槛被 none 卡死）");
    }

    static void unlockAll() {
        GongFas.none.unlock();
        for (GongFa g : GongFa.gongFas.values()) g.unlock();
    }

    static void finish(String title) {
        System.out.println("[XW] RESULT " + (fail == 0 ? "ALL PASS" : fail + " FAILED") + " (pass=" + pass + " fail=" + fail + ")");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ==================== 主电池 ====================

    static void doMain() {
        System.out.println("[XW] ===== 修为系统 headless 逻辑测试 =====");
        System.out.println("[XW] 游戏 " + mindustry.core.Version.type + " build " + mindustry.core.Version.build);
        schema();
        gongFaGate();
        duJieGateAndFlow();
        xiuWeiMapping();
        nextJingJie();
        regen();
        killMagicUnit();
        persistenceInProcess();
        clearLocksSentinelGongFa();
        finish("修为系统 headless 逻辑测试");
    }

    // ---------- A. 数据体检 ----------

    static void schema() {
        System.out.println("[XW] --- A. 境界/路线数据体检 ---");
        System.out.println("[XW] 旧路: " + routeText(JingJie.jiuLu));
        System.out.println("[XW] 新路: " + routeText(JingJie.xinLu));
        check("旧路境界按灵力严格递增", monotonic(JingJie.jiuLu), firstOutOfOrder(JingJie.jiuLu));
        check("新路境界按灵力严格递增", monotonic(JingJie.xinLu), firstOutOfOrder(JingJie.xinLu));
        // 两条路线是"同门槛的镜像"：逐个位置的灵力必须一样
        Seq<String> mismatch = new Seq<>();
        for (int i = 0; i < JingJie.jiuLu.length && i < JingJie.xinLu.length; i++) {
            if (JingJie.jiuLu[i].amount != JingJie.xinLu[i].amount) {
                mismatch.add("第" + i + "位 旧路 " + JingJie.jiuLu[i].name() + "(" + JingJie.jiuLu[i].amount
                        + ") vs 新路 " + JingJie.xinLu[i].name() + "(" + JingJie.xinLu[i].amount + ")");
            }
        }
        check("新旧路逐个位置的灵力门槛一致", mismatch.size == 0, mismatch.toString("；"));
        checkEq("两条路线境界数相同", JingJie.jiuLu.length, JingJie.xinLu.length);
        checkEq("凡人是最低境界", JingJie.fan, JingJie.getMin());
        checkEq("旧路最高境界=帝君", JingJie.dijun, JingJie.jiuLu[JingJie.jiuLu.length - 1]);

        int needNoCond = 0, noNeedHasCond = 0, needCount = 0;
        for (JingJie j : JingJie.all) {
            if (j.needDuJie) {
                needCount++;
                if (j.duJieCondition == null) needNoCond++;
            } else if (j.duJieCondition != null) {
                noNeedHasCond++;
            }
        }
        check("需要渡劫的境界都带了渡劫条件", needNoCond == 0, "带劫境界=" + needCount + " 缺条件=" + needNoCond);
        check("不需要渡劫的境界没有多余条件", noNeedHasCond == 0, "多余=" + noNeedHasCond);
        int newRoadNeed = JingJie.shenNewRoad.select(j -> j.needDuJie).size;
        check("渡劫境界都在新路（神境）", newRoadNeed == needCount, "新路带劫=" + newRoadNeed + " 全部带劫=" + needCount);

        int mirrorBad = 0;
        Seq<String> mirrorDetail = new Seq<>();
        for (int i = 0; i < JingJie.shenOldRoad.size && i < JingJie.shenNewRoad.size; i++) {
            JingJie o = JingJie.shenOldRoad.get(i), n = JingJie.shenNewRoad.get(i);
            if (!(o.amount == n.amount && o.hasMirror && n.hasMirror && !o.newRoad && n.newRoad)) {
                mirrorBad++;
                mirrorDetail.add(o.name() + "(" + o.amount + ")/" + n.name() + "(" + n.amount + ")");
            }
        }
        check("神境新旧路一一镜像（同灵力/同顺序）", mirrorBad == 0, mirrorDetail.toString(","));

        int noGongFa = 0;
        for (JingJie j : JingJie.all) if (j.gongFa == null) noGongFa++;
        check("每个境界都绑定了功法", noGongFa == 0, "缺功法=" + noGongFa);

        boolean xwAsc = true;
        for (int i = 1; i < XiuWei.all.length; i++) if (XiuWei.all[i].amount <= XiuWei.all[i - 1].amount) xwAsc = false;
        check("修为档位阈值递增", xwAsc);
        int badMult = 0;
        for (XiuWei w : XiuWei.all) if (XiuWei.xiuWeiMultiplier(w) <= 0f) badMult++;
        check("每个修为档位倍率都 > 0", badMult == 0, "坏的=" + badMult);
    }

    // ---------- B. 功法门槛 ----------

    static void gongFaGate() {
        System.out.println("[XW] --- B. 功法门槛 ---");
        reset();
        checkEq("重置后境界=凡人", JingJie.fan, CultivationState.playerJingJie);
        checkEq("重置后修为档=用", XiuWei.yong, CultivationState.playerXiuWei);
        checkNear("重置后灵力=0", 0f, CultivationState.playerMagicPower);
        checkEq("重置后可用境界只有凡人", 1, CultivationState.currentAvailableJingJie.size);

        gain(1000f);
        checkNear("没功法时灵力被截在 开窍-0.1", JingJie.kaiqiao.amount - 0.1f, CultivationState.playerMagicPower);
        checkEq("没功法时境界仍是凡人", JingJie.fan, CultivationState.playerJingJie);

        check("缺功法时发 GongFaBuQuanEvent", sawShortGongFa);
        checkEq("缺功法的目标境界=开窍", JingJie.kaiqiao, shortGongFaTarget);
        checkEq("缺的功法=太玄天功一", GongFas.taiXuanTianGong1, shortGongFaMissing);
        // 3 秒冷却：同一次缺功法的重复提示不该疯狂刷
        sawShortGongFa = false;
        gain(1f);
        check("同一秒内不重复弹「功法不足」提示（3 秒冷却）", !sawShortGongFa);

        GongFas.taiXuanTianGong1.unlock();
        gain(1000f);
        checkNear("解锁太玄一后灵力被截在 化元-0.1", JingJie.huayuan.amount - 0.1f, CultivationState.playerMagicPower);
        checkEq("解锁太玄一后境界=真元", JingJie.zhenyuan, CultivationState.playerJingJie);

        GongFas.taiXuanTianGong2.unlock();
        setPower(JingJie.shenhai.amount + 100f);
        checkNear("解锁太玄二后灵力被截在 神海-0.1", JingJie.shenhai.amount - 0.1f, CultivationState.playerMagicPower);
        checkEq("解锁太玄二后境界=天人", JingJie.tianren, CultivationState.playerJingJie);

        GongFas.taiXuanTianGong3.unlock();
        setPower(3199f);
        checkEq("灵力 3199 时境界=神海", JingJie.shenhai, CultivationState.playerJingJie);
        setPower(3200f);
        checkEq("灵力 3200 时境界=化龙", JingJie.hualong, CultivationState.playerJingJie);

        check("历史境界记录了到过的境界", CultivationState.reachedJingJie.contains(JingJie.hualong)
                && CultivationState.reachedJingJie.contains(JingJie.kaiqiao));
        check("可用境界=凡人~化龙（7 个）", CultivationState.currentAvailableJingJie.size == 7,
                "可用=" + CultivationState.currentAvailableJingJie.size);

        gain(-3000f);
        checkEq("灵力掉到 200 时境界回落到真元", JingJie.zhenyuan, CultivationState.playerJingJie);
        check("掉境界后历史境界不清空", CultivationState.reachedJingJie.contains(JingJie.hualong));

        setPower(6000f);
        checkNear("旧路缺 心皇 时灵力被截在 伪神-0.1", JingJie.weishen.amount - 0.1f, CultivationState.playerMagicPower);
        checkEq("旧路缺功法时境界停在化龙", JingJie.hualong, CultivationState.playerJingJie);
        GongFas.xinHuang.unlock();
        setPower(5000f);
        checkEq("补上心皇后境界=伪神", JingJie.weishen, CultivationState.playerJingJie);
        checkEq("伪神落在神境档位", XiuWei.shen, CultivationState.playerXiuWei);
    }

    // ---------- C. 渡劫 ----------

    static void duJieGateAndFlow() {
        System.out.println("[XW] --- C. 渡劫门槛 / 成功 / 失败 ---");
        reset();
        unlockAll();
        CVars.chooseNewRoad = true;
        // 先按正常玩法打到化龙（旧路/新路在神境之前是同一批境界）
        setPower(JingJie.hualong.amount);
        checkEq("渡劫前先正常升到化龙", JingJie.hualong, CultivationState.playerJingJie);

        primeDuJieConfirmCooldown();
        setPower(5200f);
        checkNear("没渡劫时灵力被顶在 开阳-0.1", JingJie.kaiyang.amount - 0.1f, CultivationState.playerMagicPower);
        checkEq("没渡劫时境界停在化龙", JingJie.hualong, CultivationState.playerJingJie);
        check("没渡劫时不进入渡劫状态", !CultivationState.isInDuJie);

        fire(new DuJieStartEvent(JingJie.kaiyang));
        check("渡劫开始后 isInDuJie=true", CultivationState.isInDuJie);
        checkEq("待渡劫目标=开阳", JingJie.kaiyang, CultivationState.pendingDuJieJingJie);
        checkEq("渡劫开始清空击杀计数", 0, PlayerXiuWeiSystem.getDuJieKillCount());

        asPlayer();
        for (int i = 0; i < 3; i++) fire(new UnitDestroyEvent(UnitTypes.dagger.create(Team.crux)));
        checkEq("渡劫期间击杀敌方 3 次 → 计数=3", 3, PlayerXiuWeiSystem.getDuJieKillCount());
        fire(new UnitDestroyEvent(UnitTypes.dagger.create(Team.sharded)));
        checkEq("友军死亡不计入渡劫击杀", 3, PlayerXiuWeiSystem.getDuJieKillCount());

        sawDuJieEnd = false;
        CultivationState.playerMagicPower = 6600f;
        tick();
        check("渡劫成功后 isInDuJie=false", !CultivationState.isInDuJie);
        check("渡劫成功后 pending 清空", CultivationState.pendingDuJieJingJie == null);
        check("渡劫成功记录进已完成集合", CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
        check("渡劫成功发 DuJieEndEvent(success=true)",
                sawDuJieEnd && lastDuJieEndSuccess && lastDuJieEndTarget == JingJie.kaiyang);
        checkEq("渡劫成功后境界=开阳", JingJie.kaiyang, CultivationState.playerJingJie);
        checkNear("灵力没超过下一劫门槛就不动", 6600f, CultivationState.playerMagicPower);

        fire(new DuJieStartEvent(JingJie.shentu));
        check("进入神途渡劫", CultivationState.isInDuJie && CultivationState.pendingDuJieJingJie == JingJie.shentu);
        sawDuJieEnd = false;
        CultivationState.playerMagicPower = 100f;
        tick();
        check("渡劫失败后 isInDuJie=false", !CultivationState.isInDuJie);
        check("渡劫失败发 DuJieEndEvent(success=false)",
                sawDuJieEnd && !lastDuJieEndSuccess && lastDuJieEndTarget == JingJie.shentu);
        checkEq("渡劫失败境界归零到凡人", JingJie.fan, CultivationState.playerJingJie);
        checkEq("渡劫失败修为档归零到用", XiuWei.yong, CultivationState.playerXiuWei);
        checkNear("渡劫失败灵力归零", 0f, CultivationState.playerMagicPower);
        check("渡劫失败保留已习得功法", CultivationState.gongfaHave.contains(GongFas.guHuang));
        check("渡劫失败保留历史境界", CultivationState.reachedJingJie.contains(JingJie.hualong));
        check("渡劫失败保留已完成渡劫记录", CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
        checkEq("渡劫失败后可用境界只有凡人", 1, CultivationState.currentAvailableJingJie.size);
    }

    // ---------- D. 修为档位映射 ----------

    static void xiuWeiMapping() {
        System.out.println("[XW] --- D. 境界 → 修为档位映射 ---");
        int bad = 0;
        Seq<String> detail = new Seq<>();
        for (JingJie j : JingJie.all) {
            XiuWei expect;
            if (JingJie.fajing.contains(j)) expect = XiuWei.fan;
            else if (JingJie.shenjing.contains(j)) expect = XiuWei.shen;
            else if (JingJie.shengjing.contains(j)) expect = XiuWei.sheng;
            else if (JingJie.xianjing.contains(j)) expect = XiuWei.xian;
            else if (JingJie.dijing.contains(j)) expect = XiuWei.dijun;
            else expect = XiuWei.yong;
            fire(new XiuWeiRecalc(j));
            if (CultivationState.playerXiuWei != expect) {
                bad++;
                detail.add(j.name() + "→" + CultivationState.playerXiuWei + "(期望" + expect + ")");
            }
        }
        check("每个境界都映射到正确的修为档位", bad == 0, detail.toString(","));
        check("修为档位倍率：用 0.1 / 凡 1 / 神 2 / 圣 4 / 仙 6 / 帝 10",
                XiuWei.xiuWeiMultiplier(XiuWei.yong) == 0.1f
                        && XiuWei.xiuWeiMultiplier(XiuWei.fan) == 1f
                        && XiuWei.xiuWeiMultiplier(XiuWei.shen) == 2f
                        && XiuWei.xiuWeiMultiplier(XiuWei.sheng) == 4f
                        && XiuWei.xiuWeiMultiplier(XiuWei.xian) == 6f
                        && XiuWei.xiuWeiMultiplier(XiuWei.dijun) == 10f);
    }

    // ---------- E. 下一境界 ----------

    static void nextJingJie() {
        System.out.println("[XW] --- E. 下一境界 ---");
        CVars.chooseNewRoad = false;
        CultivationState.playerJingJie = JingJie.fan;
        checkEq("旧路：凡人 → 开窍", JingJie.kaiqiao, PlayerXiuWeiSystem.getNextJingJie());
        CultivationState.playerJingJie = JingJie.shenzun;
        checkEq("旧路：神尊 → 伪圣", JingJie.weisheng, PlayerXiuWeiSystem.getNextJingJie());
        CultivationState.playerJingJie = JingJie.dijun;
        checkEq("旧路：帝君（最高）→ 帝君自己", JingJie.dijun, PlayerXiuWeiSystem.getNextJingJie());
        // jiuLu 数组里 shenjun(19000) 排在 shenwang(11000) 前面 → 这一跳会跳过神王/神皇
        CultivationState.playerJingJie = JingJie.zhenshen;
        checkEq("旧路：真神 → 神王", JingJie.shenwang, PlayerXiuWeiSystem.getNextJingJie());
        CultivationState.playerJingJie = JingJie.shenwang;
        checkEq("旧路：神王 → 神皇", JingJie.shenhuang, PlayerXiuWeiSystem.getNextJingJie());

        CVars.chooseNewRoad = true;
        CultivationState.playerJingJie = JingJie.hualong;
        checkEq("新路：化龙 → 开阳", JingJie.kaiyang, PlayerXiuWeiSystem.getNextJingJie());
        CultivationState.playerJingJie = JingJie.shenzun;
        checkEq("新路：旧路的神尊 → 伪圣（按灵力找第一个更高的）", JingJie.weisheng,
                PlayerXiuWeiSystem.getNextJingJie());
        CVars.chooseNewRoad = false;
    }

    // ---------- F. 灵力自然恢复 ----------

    static void regen() {
        System.out.println("[XW] --- F. 灵力自然恢复 ---");
        reset();
        Vars.state.set(State.playing);
        Vars.state.rules.sector = SectorPresets.groundZero.sector;
        check("headless 里能摆出战役进行中状态", Vars.state.isPlaying() && Vars.state.isCampaign());

        CultivationState.playerMagicPower = 10f;
        CultivationState.availableMagicPower = 0f;
        tick(600);
        checkNear("10 秒（600 tick）按 0.1/秒 恢复 → 可用灵力=1", 1f, CultivationState.availableMagicPower);

        CultivationState.availableMagicPower = 10f;
        tick(600);
        checkNear("可用灵力不超过总上限", 10f, CultivationState.availableMagicPower);

        CultivationState.availableMagicPower = 0f;
        Vars.state.set(State.menu);
        tick(600);
        checkNear("不在游戏中时不恢复", 0f, CultivationState.availableMagicPower);
    }

    // ---------- G. 击杀法术单位加修为 ----------

    static void killMagicUnit() {
        System.out.println("[XW] --- G. 击杀敌方法术单位 → 加修为 ---");
        reset();
        unlockAll();
        Vars.state.set(State.playing);
        Vars.state.rules.sector = SectorPresets.groundZero.sector;
        asPlayer();

        MagicUnitType type = null;
        for (var u : Vars.content.units()) {
            if (u instanceof MagicUnitType m && m.xiuWeiAmount > 0) {
                type = m;
                break;
            }
        }
        if (type == null) {
            check("找得到本模组的法术单位", false);
            return;
        }
        System.out.println("[XW] 被测单位: " + type.name + " 修为产出=" + type.xiuWeiAmount
                + " 档位=" + type.xiuWei + " 档位倍率=" + XiuWei.xiuWeiMultiplier(type.xiuWei));

        setPower(1000f);
        float before = CultivationState.playerMagicPower;
        sawMagicChange = false;
        type.killed(type.create(Team.crux));
        float expected = type.xiuWeiAmount * Math.max(1f, XiuWei.xiuWeiMultiplier(type.xiuWei));
        check("击杀敌方法术单位会发 MagicPowerChange", sawMagicChange);
        checkNear("修为增量 = 单位产出 × 档位倍率", expected, lastMagicDelta);
        checkNear("灵力确实涨了这么多", before + expected, CultivationState.playerMagicPower);

        before = CultivationState.playerMagicPower;
        sawMagicChange = false;
        type.killed(type.create(Team.sharded));
        check("自己队伍的单位死亡不加修为", !sawMagicChange && CultivationState.playerMagicPower == before);
    }

    // ---------- H. 同进程存档往返 ----------

    static void persistenceInProcess() {
        System.out.println("[XW] --- H. 同进程存档往返（save → 清内存 → 重新 load） ---");
        reset();
        unlockAll();
        setPower(1234f);
        CultivationState.availableMagicPower = 56.5f;
        PlayerXiuWeiSystem.savePower();
        Core.settings.manualSave();

        float savedPower = CultivationState.playerMagicPower;
        int savedReached = CultivationState.reachedJingJie.size;
        int savedAvailable = CultivationState.currentAvailableJingJie.size;

        CultivationState.playerMagicPower = -1f;
        CultivationState.availableMagicPower = -1f;
        CultivationState.reachedJingJie.clear();
        CultivationState.currentAvailableJingJie.clear();
        clientLoad();

        checkNear("灵力存档往返一致", savedPower, CultivationState.playerMagicPower);
        checkNear("可用灵力存档往返一致", 56.5f, CultivationState.availableMagicPower);
        checkEq("历史境界存档往返一致", savedReached, CultivationState.reachedJingJie.size);
        checkEq("可用境界存档往返一致", savedAvailable, CultivationState.currentAvailableJingJie.size);
        check("读档后功法解锁状态还在", CultivationState.gongfaHave.contains(GongFas.taiXuanTianGong1));
    }

    // ---------- 跨进程两阶段：写 ----------

    static void doWrite() {
        System.out.println("[XW] ===== 跨进程存档：写阶段 =====");
        reset();
        unlockAll();
        PlayerXiuWeiSystem.setChooseNewRoad(true);
        CultivationState.playerMagicPower = 12345.5f;
        CultivationState.availableMagicPower = 678.25f;
        CultivationState.playerJingJie = JingJie.kaiyang;
        CultivationState.reachedJingJie.clear();
        CultivationState.reachedJingJie.add(JingJie.fan);
        CultivationState.reachedJingJie.add(JingJie.kaiqiao);
        CultivationState.reachedJingJie.add(JingJie.hualong);
        CultivationState.currentAvailableJingJie.clear();
        CultivationState.currentAvailableJingJie.add(JingJie.fan);
        CultivationState.currentAvailableJingJie.add(JingJie.hualong);
        CultivationState.completedDuJieJingJies.clear();
        CultivationState.completedDuJieJingJies.add(JingJie.kaiyang);
        asPlayer();
        fire(new DuJieStartEvent(JingJie.shentu));
        fire(new UnitDestroyEvent(UnitTypes.dagger.create(Team.crux)));

        // 走真实落盘路径：回主菜单（StateChangeEvent → savePower/saveReached/saveAvailable/saveDuJieState）
        fire(new mindustry.game.EventType.StateChangeEvent(State.playing, State.menu));
        System.out.println("[XW] 写好了：灵力=" + CultivationState.playerMagicPower
                + " 可用=" + CultivationState.availableMagicPower
                + " 新路=" + CVars.chooseNewRoad
                + " 待渡劫=" + CultivationState.pendingDuJieJingJie
                + " 击杀数=" + PlayerXiuWeiSystem.getDuJieKillCount()
                + " 历史=" + CultivationState.reachedJingJie.size
                + " 可用境界=" + CultivationState.currentAvailableJingJie.size
                + " 已完成渡劫=" + CultivationState.completedDuJieJingJies.size);
        check("写阶段：进入渡劫状态", CultivationState.isInDuJie);
        checkEq("写阶段：击杀计数=1", 1, PlayerXiuWeiSystem.getDuJieKillCount());
        finish("写阶段");
    }

    // ---------- 跨进程两阶段：读 ----------

    static void doRead() {
        System.out.println("[XW] ===== 跨进程存档：读阶段（新进程） =====");
        checkNear("settings 里的原始灵力（读档前）", 12345.5f,
                Core.settings.getFloat("crystal.magicpower", -1f));
        checkNear("灵力跨进程恢复", 12345.5f, CultivationState.playerMagicPower);
        checkNear("可用灵力跨进程恢复", 678.25f, CultivationState.availableMagicPower);
        check("新路开关跨进程恢复", CVars.chooseNewRoad);
        checkEq("境界由灵力重算（渡劫中不顶灵力）", JingJie.kaiyang, CultivationState.playerJingJie);
        check("历史境界跨进程恢复", CultivationState.reachedJingJie.contains(JingJie.kaiqiao)
                && CultivationState.reachedJingJie.contains(JingJie.hualong));
        // 可用境界不入档（PlayerXiuWeiSystem.loadCurrentAvailableJingJie 其实从没被调用过），
        // 每次读档都按当前境界重算 → 开阳(第 16 个，含凡人)之前 8 个
        checkEq("可用境界按当前境界重算（凡人~开阳=8）", 8, CultivationState.currentAvailableJingJie.size);
        check("已完成渡劫记录跨进程恢复", CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
        checkEq("待渡劫目标跨进程恢复", JingJie.shentu, CultivationState.pendingDuJieJingJie);
        check("渡劫中状态跨进程恢复", CultivationState.isInDuJie);
        checkEq("渡劫击杀数跨进程恢复", 1, PlayerXiuWeiSystem.getDuJieKillCount());
        check("功法解锁状态跨进程恢复", CultivationState.gongfaHave.contains(GongFas.guZun));

        // 恢复出来的渡劫要能继续推：神途条件 = 灵力>=9000 且 击杀>=100
        asPlayer();
        CultivationState.playerMagicPower = 9500f;
        for (int i = 0; i < 99; i++) fire(new UnitDestroyEvent(UnitTypes.dagger.create(Team.crux)));
        System.out.println("[XW] 复活后的击杀数=" + PlayerXiuWeiSystem.getDuJieKillCount());
        checkEq("击杀数累加到 100", 100, PlayerXiuWeiSystem.getDuJieKillCount());
        primeDuJieConfirmCooldown();
        sawDuJieEnd = false;
        tick();
        check("恢复出来的渡劫能继续推到成功", !CultivationState.isInDuJie
                && CultivationState.completedDuJieJingJies.contains(JingJie.shentu)
                && sawDuJieEnd && lastDuJieEndSuccess && lastDuJieEndTarget == JingJie.shentu);
        finish("读阶段");
    }

    // ---------- 旧档兼容 ----------

    /**
     * 给真客户端准备一份 settings：清空 + locale=zh_CN。
     * 模组只带了 bundles/bundle_zh_CN.properties；游戏在英文 locale 下找不到对应 key，
     * 所有文案都会变成 ???key???（模组自己的锅，但和修为逻辑无关），截图证据就不好看了。
     */
    static void doSeed() {
        Core.settings.clear();
        Core.settings.put("locale", "zh_CN");
        Core.settings.put("showXiuWei", true);
        Core.settings.manualSave();
        System.out.println("[XW] seed 完成：locale=" + Core.settings.getString("locale", "?")
                + " showXiuWei=" + Core.settings.getBool("showXiuWei", false));
        check("seed 写入 locale", "zh_CN".equals(Core.settings.getString("locale", "")));
        finish("seed");
    }

    static void doLegacy() {
        System.out.println("[XW] ===== 旧档兼容（ordinal / 旧 key / 坏数据） =====");

        Core.settings.put("crystal.reachedJingJie_ordinal", "0,1,6");
        Core.settings.put("crystal.availableJingJie_ordinal", "0,1,6");
        Core.settings.put("crystal.completedDuJieJingJies", String.valueOf(JingJie.kaiyang.ordinal()));
        Core.settings.put("crystal.pendingDuJieJingJieOrdinal", String.valueOf(JingJie.shentu.ordinal()));
        Core.settings.put("crystal.duJieKillCount", 7);
        // 灵力压在门槛以下：读档后的"按灵力重算"不会顺手把历史境界补全，才能照原样核对读回结果
        Core.settings.put("crystal.magicpower", 0.5f);
        Core.settings.put("crystal.availableMagicPower", 12.5f);
        Core.settings.manualSave();
        clientLoad();
        checkEq("老格式 ordinal：历史境界读回 3 个", 3, CultivationState.reachedJingJie.size);
        check("老格式 ordinal：化龙在里面", CultivationState.reachedJingJie.contains(JingJie.hualong));
        check("老格式 ordinal：已完成渡劫读回", CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
        checkEq("老格式 ordinal：待渡劫读回神途", JingJie.shentu, CultivationState.pendingDuJieJingJie);
        checkEq("老格式 ordinal：击杀数读回 7", 7, PlayerXiuWeiSystem.getDuJieKillCount());
        checkNear("老格式 ordinal：灵力读回", 0.5f, CultivationState.playerMagicPower);
        // 可用境界不读档（loadCurrentAvailableJingJie 从没被调用），按当前境界重算 → 灵力 0.5 只有凡人
        checkEq("可用境界按境界重算（灵力 0.5 → 只有凡人）", 1, CultivationState.currentAvailableJingJie.size);
        boolean availableKeyCleaned = Core.settings.get("crystal.availableJingJie_ordinal", null) != null;
        System.out.println("[XW] 观察: 存档里的可用境界 key 仍然被写（值="
                + Core.settings.get("crystal.availableJingJie_ordinal", null) + "），但没有任何代码读它");
        check("可用境界 key 只写不读（记录）", availableKeyCleaned);

        Core.settings.remove("crystal.reachedJingJie_ordinal");
        Core.settings.put("crystal.reachedJingJie", "fan,kaiqiao,hualong");
        Core.settings.manualSave();
        clientLoad();
        check("旧 key（名字格式）也能读回", CultivationState.reachedJingJie.contains(JingJie.hualong)
                && CultivationState.reachedJingJie.contains(JingJie.kaiqiao));
        check("读回后旧 key 被清理", Core.settings.get("crystal.reachedJingJie", null) == null);

        Core.settings.put("crystal.reachedJingJie_ordinal", "0,999,abc");
        Core.settings.put("crystal.availableJingJie_ordinal", "");
        Core.settings.put("crystal.pendingDuJieJingJieOrdinal", "不是境界");
        Core.settings.put("crystal.magicpower", 0f);
        Core.settings.put("crystal.availableMagicPower", 0f);
        Core.settings.manualSave();
        clientLoad();
        check("坏 ordinal 只丢坏项", CultivationState.reachedJingJie.contains(JingJie.fan)
                && CultivationState.reachedJingJie.size == 1);
        check("坏可用境界回落到凡人", CultivationState.currentAvailableJingJie.size == 1
                && CultivationState.currentAvailableJingJie.first() == JingJie.fan);
        check("坏待渡劫目标被清掉", CultivationState.pendingDuJieJingJie == null && !CultivationState.isInDuJie);
        finish("旧档兼容");
    }
}
