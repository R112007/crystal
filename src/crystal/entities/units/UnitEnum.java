package crystal.entities.units;

import crystal.core.CultivationState;
import arc.func.Boolp;
import arc.graphics.Color;
import crystal.CVars;
import crystal.core.PlayerXiuWeiSystem;
import crystal.type.DuJieCondition;
import crystal.type.GongFa;
import mindustry.content.SectorPresets;
import mindustry.type.SectorPreset;

import static arc.Core.bundle;
import static crystal.content.GongFas.*;

public class UnitEnum {
  public enum Stage {
    stage1, stage2, stage3, stage4;

    public static final Stage[] all = values();

    public boolean lowerThan(Stage target) {
      return this.ordinal() < target.ordinal();
    }

  }

  public enum XiuWei {
    yong(Color.white, bundle.get("yong")),
    fan(Color.gray, bundle.get("fan")),
    shen(Color.red, bundle.get("shen")),
    sheng(Color.blue, bundle.get("sheng")),
    xian(Color.gold, bundle.get("xian")),
    dijun(Color.valueOf("#B800F5FF"), bundle.get("dijun"));

    public static final XiuWei[] all = values();
    public final Color color;
    public String str;

    XiuWei(Color color, String str) {
      this.color = color;
      this.str = str;
    }

  }

  public static enum JingJie {
    // 凡境/法境：无需渡劫
    fan(XiuWei.yong, bundle.get("yong"), 0, false, false, none, false, null),
    kaiqiao(XiuWei.fan, bundle.get("kaiqiao"), 5, false, false, taiXuanTianGong1, false, null),
    zhenyuan(XiuWei.fan, bundle.get("zhenyuan"), 100, false, false, taiXuanTianGong1, false, null),
    huayuan(XiuWei.fan, bundle.get("huayuan"), 600, false, false, taiXuanTianGong2, false, null),
    tianren(XiuWei.fan, bundle.get("tianren"), 1000, false, false, taiXuanTianGong2, false, null),
    shenhai(XiuWei.fan, bundle.get("shenhai"), 2000, false, false, taiXuanTianGong3, false, null),
    hualong(XiuWei.fan, bundle.get("hualong"), 3200, false, false, taiXuanTianGong3, false, null),

    // 旧路神境：无需渡劫
    weishen(XiuWei.shen, bundle.get("weishen"), 5000, true, false, xinHuang, false, null),
    zhenshen(XiuWei.shen, bundle.get("zhenshen"), 7500, true, false, xinHuang, false, null),
    shenwang(XiuWei.shen, bundle.get("shenwang"), 11000, true, false, xinHuang, false, null),
    shenhuang(XiuWei.shen, bundle.get("shenhuang"), 14500, true, false, xinZun, false, null),
    shenjun(XiuWei.shen, bundle.get("shenjun"), 19000, true, false, xinHuang, false, null),
    shenling(XiuWei.shen, bundle.get("shenling"), 25000, true, false, xinZun, false, null),
    shenming(XiuWei.shen, bundle.get("shenming"), 31000, true, false, xinZun, false, null),
    shenzun(XiuWei.shen, bundle.get("shenzun"), 50000, true, false, xinZun, false, null),

    // 新路神境：每个境界绑定独立渡劫条件。
    // 文本走 bundle.get(key, 默认值)：默认可直接用，要改文案/翻译就在 bundle 里加对应 key 覆盖
    kaiyang(XiuWei.shen, bundle.get("kaiyang"), 5000, true, true, guHuang, true,
        new DuJieCondition(
            bundle.get("dujie.cond.kaiyang", "灵力达到6500并习得古皇功法"),
            () -> CultivationState.playerMagicPower >= 6500 && CultivationState.gongfaHave.contains(guHuang),
            () -> CultivationState.playerMagicPower < 100// 失败条件示例：灵力跌破100则失败
        )),
    shentu(XiuWei.shen, bundle.get("shentu"), 7500, true, true, guHuang, true,
        new DuJieCondition(
            bundle.get("dujie.cond.shentu", "灵力达到9000并击杀100个敌方单位"),
            // 原来是 () -> true 占位，与文本不符；现接入 PlayerXiuWeiSystem 的渡劫击杀计数
            () -> CultivationState.playerMagicPower >= 9000 && PlayerXiuWeiSystem.getDuJieKillCount() >= 100,
            () -> CultivationState.playerMagicPower < 200)),
    canghai(XiuWei.shen, bundle.get("canghai"), 11000, true, true, guHuang, true,
        new DuJieCondition(
            bundle.get("dujie.cond.canghai", "灵力达到13000"),
            () -> CultivationState.playerMagicPower >= 13000,
            () -> CultivationState.playerMagicPower < 500)),
    tianqiao(XiuWei.shen, bundle.get("tianqiao"), 14500, true, true, guHuang, true,
        new DuJieCondition(
            bundle.get("dujie.cond.tianqiao", "灵力达到17000"),
            () -> CultivationState.playerMagicPower >= 17000,
            () -> CultivationState.playerMagicPower < 1000)),
    wanling(XiuWei.shen, bundle.get("wanling"), 19000, true, true, guZun, true,
        new DuJieCondition(
            bundle.get("dujie.cond.wanling", "灵力达到22000并习得古尊功法"),
            () -> CultivationState.playerMagicPower >= 22000 && CultivationState.gongfaHave.contains(guZun),
            () -> CultivationState.playerMagicPower < 2000)),
    sixiang(XiuWei.shen, bundle.get("sixiang"), 25000, true, true, guZun, true,
        new DuJieCondition(
            bundle.get("dujie.cond.sixiang", "灵力达到29000"),
            () -> CultivationState.playerMagicPower >= 29000,
            () -> CultivationState.playerMagicPower < 3000)),
    shengong(XiuWei.shen, bundle.get("shengong"), 31000, true, true, guZun, true,
        new DuJieCondition(
            bundle.get("dujie.cond.shengong", "灵力达到36000"),
            () -> CultivationState.playerMagicPower >= 36000,
            () -> CultivationState.playerMagicPower < 5000)),
    zunzhu(XiuWei.shen, bundle.get("zunzhu"), 50000, true, true, guZun, true,
        new DuJieCondition(
            bundle.get("dujie.cond.zunzhu", "灵力达到58000"),
            () -> CultivationState.playerMagicPower >= 58000,
            () -> CultivationState.playerMagicPower < 10000)),

    // 圣/仙/帝境：无需渡劫
    weisheng(XiuWei.sheng, bundle.get("weisheng"), 60000, false, false, yinYang1, false, null),
    yasheng(XiuWei.sheng, bundle.get("yasheng"), 80000, false, false, yinYang1, false, null),
    zhunsheng(XiuWei.sheng, bundle.get("zhunsheng"), 100000, false, false, yinYang1, false, null),
    dasheng(XiuWei.sheng, bundle.get("dasheng"), 130000, false, false, yinYang2, false, null),
    shengdaodadi(XiuWei.sheng, bundle.get("shengdaodadi"), 160000, false, false, yinYang2, false, null),
    tianxian(XiuWei.xian, bundle.get("tianxian"), 180000, false, false, yuHua, false, null),
    zhenxian(XiuWei.xian, bundle.get("zhenxian"), 210000, false, false, yuHua, false, null),
    xuanxian(XiuWei.xian, bundle.get("xuanxian"), 250000, false, false, yuHua, false, null),
    jinxian(XiuWei.xian, bundle.get("jinxian"), 280000, false, false, ziRi, false, null),
    xianjun(XiuWei.xian, bundle.get("xianjun"), 320000, false, false, ziRi, false, null),
    xianzun(XiuWei.xian, bundle.get("xianzun"), 380000, false, false, ziRi, false, null),
    xianhuang(XiuWei.xian, bundle.get("xianhuang"), 450000, false, false, buMie, false, null),
    xianwang(XiuWei.xian, bundle.get("xianwang"), 550000, false, false, buMie, false, null),
    xiandi(XiuWei.dijun, bundle.get("xiandi"), 650000, false, false, dongHua, false, null),
    tiandi(XiuWei.dijun, bundle.get("tiandi"), 770000, false, false, dongHua, false, null),
    daodi(XiuWei.dijun, bundle.get("daodi"), 900000, false, false, suiYue, false, null),
    dizun(XiuWei.dijun, bundle.get("dizun"), 1000000, false, false, suiYue, false, null),
    dijun(XiuWei.dijun, bundle.get("jingjie.dijun"), 1500000, false, false, daDao, false, null);

    public final XiuWei xiuWei;
    public String str;
    public int amount;
    public boolean hasMirror;
    public boolean newRoad;
    public GongFa gongFa;
    public boolean needDuJie;
    /** 渡劫条件封装，非渡劫境界为null */
    public final DuJieCondition duJieCondition;

    public static final JingJie[] all = values();
    // 旧路/新路两条路线必须"逐位同门槛"（镜像对）：
    // shenwang(11000) → shenhuang(14500) → shenjun(19000)。顺序错了 getNextJingJie 会跳境界。
    public static final JingJie[] jiuLu = { fan, kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong, weishen,
        zhenshen, shenwang, shenhuang, shenjun, shenling, shenming, shenzun, weisheng, yasheng, zhunsheng, dasheng,
        shengdaodadi, tianxian, zhenxian, xuanxian, jinxian, xianjun, xianzun, xianhuang, xianwang, xiandi, tiandi,
        daodi, dizun, dijun };
    public static final JingJie[] xinLu = { fan, kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong,
        kaiyang, shentu, canghai, tianqiao, wanling, sixiang, shengong, zunzhu, weisheng, yasheng, zhunsheng, dasheng,
        shengdaodadi, tianxian, zhenxian, xuanxian, jinxian, xianjun, xianzun, xianhuang, xianwang, xiandi, tiandi,
        daodi, dizun, dijun };

    JingJie(XiuWei xiuWei, String str, int amount, boolean hasMirror, boolean newRoad, GongFa gongFa,
        boolean needDuJie,
        DuJieCondition duJieCondition) {
      this.xiuWei = xiuWei;
      this.str = str;
      this.amount = amount;
      this.hasMirror = hasMirror;
      this.newRoad = newRoad;
      this.gongFa = gongFa;
      this.needDuJie = needDuJie;
      this.duJieCondition = duJieCondition;
    }

    public static JingJie getByOrdinal(int ordinal) {
      if (ordinal < 0 || ordinal >= all.length)
        return null;
      return all[ordinal];
    }

    public static JingJie getByName(String name) {
      if (name == null || name.isBlank())
        return fan;
      for (JingJie jingJie : all) {
        if (jingJie.name().equals(name))
          return jingJie;
      }
      return fan;
    }

    /** 单个境界的战斗倍率；大境界决定基础档，小境界提供段内成长。 */
    public float multiplier() {
      return switch (this) {
        case fan -> 0.1f;
        case kaiqiao -> 1.0f;
        case zhenyuan -> 1.16f;
        case huayuan -> 1.32f;
        case tianren -> 1.48f;
        case shenhai -> 1.64f;
        case hualong -> 1.8f;
        case weishen, kaiyang -> 2.0f;
        case zhenshen, shentu -> 2.25f;
        case shenwang, canghai -> 2.5f;
        case shenhuang, tianqiao -> 2.75f;
        case shenjun, wanling -> 3.0f;
        case shenling, sixiang -> 3.25f;
        case shenming, shengong -> 3.5f;
        case shenzun, zunzhu -> 3.75f;
        case weisheng -> 4.0f;
        case yasheng -> 4.4f;
        case zhunsheng -> 4.8f;
        case dasheng -> 5.2f;
        case shengdaodadi -> 5.6f;
        case tianxian -> 6.0f;
        case zhenxian -> 6.5f;
        case xuanxian -> 7.0f;
        case jinxian -> 7.5f;
        case xianjun -> 8.0f;
        case xianzun -> 8.5f;
        case xianhuang -> 9.0f;
        case xianwang -> 9.5f;
        case xiandi -> 10.0f;
        case tiandi -> 10.5f;
        case daodi -> 11.0f;
        case dizun -> 11.5f;
        case dijun -> 12.0f;
      };
    }

    public static float jingJieMultiplier(JingJie jingJie) {
      return jingJie == null ? fan.multiplier() : jingJie.multiplier();
    }

    public boolean atLeast(JingJie minimum) {
      return minimum == null || amount >= minimum.amount;
    }

    public static JingJie getMin() {
      return fan;
    }
  }

  public enum Mode {
    easy, normal, hard, extreme
  }
}
