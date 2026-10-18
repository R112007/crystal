package crystal.dbg;

import arc.Events;
import crystal.CVars;
import crystal.content.GongFas;
import crystal.core.CultivationState;
import crystal.core.PlayerXiuWeiSystem;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.game.CEventType.DuJieStartEvent;
import crystal.game.CEventType.JingJieRecalc;

/** Headless logic test for cultivation ranks, gongfa gates, dujie and persistence. */
public class CultivationSystemTest {
    private static boolean ok = true;

    public static void run(String[] args) {
        try {
            testRouteIntegrity();
            testGongfaGates();
            testDuJieFlow();
            testPersistenceRoundTrip();
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        System.out.println("[XIUXIAN] " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }

    private static void testRouteIntegrity() {
        for (int i = 7; i < 15; i++) {
            check("route mirror " + i + " has equal thresholds",
                    JingJie.jiuLu[i].amount, JingJie.xinLu[i].amount);
        }
        CVars.chooseNewRoad = false;
        CultivationState.playerJingJie = JingJie.shenwang;
        check("old-road next rank follows threshold order", JingJie.shenhuang,
                PlayerXiuWeiSystem.getNextJingJie());
        CVars.chooseNewRoad = true;
        check("cross-road next rank uses the next higher threshold", JingJie.tianqiao,
                PlayerXiuWeiSystem.getNextJingJie());
    }

    private static void testGongfaGates() {
        PlayerXiuWeiSystem.clear();
        CVars.chooseNewRoad = false;
        CultivationState.playerMagicPower = 4.9f;
        Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
        check("below-threshold cultivation stays mortal", JingJie.fan, CultivationState.playerJingJie);

        GongFas.taiXuanTianGong1.unlock();
        CultivationState.playerMagicPower = JingJie.kaiqiao.amount;
        Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
        check("first gongfa unlocks kaiqiao", JingJie.kaiqiao, CultivationState.playerJingJie);
        check("fajing maps to mortal cultivation tier", XiuWei.fan, CultivationState.playerXiuWei);

        CultivationState.playerMagicPower = 1000f;
        Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
        check("missing second gongfa blocks huayuan", JingJie.zhenyuan, CultivationState.playerJingJie);
        check("blocked power is clamped below gate", JingJie.huayuan.amount - 0.1f,
                CultivationState.playerMagicPower);

        GongFas.taiXuanTianGong2.unlock();
        CultivationState.playerMagicPower = 1000f;
        Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
        check("second gongfa allows tianren", JingJie.tianren, CultivationState.playerJingJie);

        GongFas.taiXuanTianGong3.unlock();
        GongFas.xinHuang.unlock();
        CultivationState.playerMagicPower = JingJie.weishen.amount;
        Events.fire(new JingJieRecalc(CultivationState.playerMagicPower));
        check("old-road god rank is selected", JingJie.weishen, CultivationState.playerJingJie);
        check("god rank maps to god cultivation tier", XiuWei.shen, CultivationState.playerXiuWei);
    }

    private static void testDuJieFlow() {
        PlayerXiuWeiSystem.clear();
        CVars.chooseNewRoad = true;
        GongFas.taiXuanTianGong1.unlock();
        GongFas.taiXuanTianGong2.unlock();
        GongFas.taiXuanTianGong3.unlock();
        GongFas.guHuang.unlock();
        CultivationState.playerMagicPower = 6500f;

        Events.fire(new DuJieStartEvent(JingJie.kaiyang));
        check("dujie enters active state", true, CultivationState.isInDuJie);
        check("dujie records its target", JingJie.kaiyang, CultivationState.pendingDuJieJingJie);
        invoke("tickDuJieCheck");
        check("dujie success clears active state", false, CultivationState.isInDuJie);
        check("dujie success is persisted in memory", true,
                CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
        check("dujie success allows target rank", JingJie.kaiyang, CultivationState.playerJingJie);

        Events.fire(new DuJieStartEvent(JingJie.shentu));
        CultivationState.playerMagicPower = 150f;
        invoke("tickDuJieCheck");
        check("dujie failure exits active state", false, CultivationState.isInDuJie);
        check("dujie failure resets to mortal", JingJie.fan, CultivationState.playerJingJie);
        check("dujie failure clears available power", 0f, CultivationState.availableMagicPower);
    }

    private static void testPersistenceRoundTrip() {
        PlayerXiuWeiSystem.clear();
        CultivationState.playerMagicPower = 1234.5f;
        CultivationState.availableMagicPower = 321.25f;
        CultivationState.reachedJingJie.clear();
        CultivationState.reachedJingJie.add(JingJie.fan, JingJie.kaiqiao);
        CultivationState.currentAvailableJingJie.clear();
        CultivationState.currentAvailableJingJie.add(JingJie.fan, JingJie.kaiqiao);
        CultivationState.completedDuJieJingJies.clear();
        CultivationState.completedDuJieJingJies.add(JingJie.kaiyang);

        invoke("savePower");
        invoke("saveReachedJingJie");
        invoke("saveCurrentAvailableJingJie");
        invoke("saveDuJieState");

        CultivationState.playerMagicPower = 0f;
        CultivationState.availableMagicPower = 0f;
        CultivationState.reachedJingJie.clear();
        CultivationState.currentAvailableJingJie.clear();
        CultivationState.completedDuJieJingJies.clear();

        invoke("loadReachedJingJie");
        invoke("loadCurrentAvailableJingJie");
        invoke("loadDuJieState");
        CultivationState.playerMagicPower = 1234.5f;
        CultivationState.availableMagicPower = 321.25f;
        CultivationState.load();

        check("cultivation power round-trips", 1234.5f, CultivationState.playerMagicPower);
        check("available power round-trips", 321.25f, CultivationState.availableMagicPower);
        check("reached ranks round-trip", 2, CultivationState.reachedJingJie.size);
        check("available ranks round-trip", 2, CultivationState.currentAvailableJingJie.size);
        check("completed dujie round-trips", true,
                CultivationState.completedDuJieJingJies.contains(JingJie.kaiyang));
    }

    private static void invoke(String method) {
        try {
            var target = method.equals("savePower") || method.equals("loadReachedJingJie")
                    || method.equals("saveReachedJingJie") || method.equals("loadCurrentAvailableJingJie")
                    || method.equals("saveCurrentAvailableJingJie") || method.equals("loadDuJieState")
                    || method.equals("saveDuJieState") || method.equals("tickDuJieCheck")
                            ? PlayerXiuWeiSystem.class
                            : CultivationState.class;
            var m = target.getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException("cannot invoke " + method, t);
        }
    }

    private static void check(String label, int expected, int actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[XIUXIAN] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, float expected, float actual) {
        boolean pass = Math.abs(expected - actual) < 0.001f;
        ok &= pass;
        System.out.println("[XIUXIAN] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, Object expected, Object actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[XIUXIAN] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }

    private static void check(String label, boolean expected, boolean actual) {
        boolean pass = expected == actual;
        ok &= pass;
        System.out.println("[XIUXIAN] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
    }
}
