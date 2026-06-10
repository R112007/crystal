package crystal.entities.units;

import arc.graphics.Color;
import arc.struct.Seq;
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
    yong(Color.white, bundle.get("yong"), 0),
    fan(Color.gray, bundle.get("fan"), 100),
    shen(Color.red, bundle.get("shen"), 1800),
    sheng(Color.blue, bundle.get("sheng"), 6000),
    xian(Color.gold, bundle.get("xian"), 15000),
    dijun(Color.valueOf("#B800F5FF"), bundle.get("dijun"), 40000);

    public static final XiuWei[] all = values();
    public final Color color;
    public String str;
    public int amount;

    XiuWei(Color color, String str, int amount) {
      this.color = color;
      this.str = str;
      this.amount = amount;
    }

    public static float xiuWeiMultiplier(XiuWei xiuWei) {
      switch (xiuWei) {
        case yong:
          return 0.1f;
        case fan:
          return 1f;
        case shen:
          return 2f;
        case sheng:
          return 4f;
        case xian:
          return 6f;
        case dijun:
          return 10f;
        default:
          return 0f;
      }
    }
  }

  public static enum JingJie {
    fan(bundle.get("yong"), 0, false, false, none, false, null),
    kaiqiao(bundle.get("kaiqiao"), 10, false, false, taiXuanTianGong1, false, null),
    zhenyuan(bundle.get("zhenyuan"), 250, false, false, taiXuanTianGong1, false, null),
    huayuan(bundle.get("huayuan"), 600, false, false, taiXuanTianGong2, false, null),
    tianren(bundle.get("tianren"), 1000, false, false, taiXuanTianGong2, false, null),
    shenhai(bundle.get("shenhai"), 2000, false, false, taiXuanTianGong3, false, null),
    hualong(bundle.get("hualong"), 3200, false, false, taiXuanTianGong3, false, null),
    // 旧路神级境界：不需要渡劫，最后两个参数为false、null
    weishen(bundle.get("weishen"), 5000, true, false, xinHuang, false, null),
    zhenshen(bundle.get("zhenshen"), 7500, true, false, xinHuang, false, null),
    shenwang(bundle.get("shenwang"), 11000, true, false, xinHuang, false, null),
    shenhuang(bundle.get("shenhuang"), 14500, true, false, xinZun, false, null),
    shenjun(bundle.get("shenjun"), 19000, true, false, xinHuang, false, null),
    shenling(bundle.get("shenling"), 25000, true, false, xinZun, false, null),
    shenming(bundle.get("shenming"), 31000, true, false, xinZun, false, null),
    shenzun(bundle.get("shenzun"), 50000, true, false, xinZun, false, null),
    // 新路神级境界：需要渡劫，绑定对应Sector预设名（需提前创建好对应的SectorPreset）
    kaiyang(bundle.get("kaiyang"), 5000, true, true, guHuang, true, SectorPresets.groundZero),
    shentu(bundle.get("shentu"), 7500, true, true, guHuang, true, SectorPresets.frozenForest),
    canghai(bundle.get("canghai"), 11000, true, true, guHuang, true, SectorPresets.crateredBattleground),
    tianqiao(bundle.get("tianqiao"), 14500, true, true, guHuang, true, SectorPresets.biomassFacility),
    wanling(bundle.get("wanling"), 19000, true, true, guZun, true, SectorPresets.basin),
    sixiang(bundle.get("sixiang"), 25000, true, true, guZun, true, SectorPresets.atolls),
    shengong(bundle.get("shengong"), 31000, true, true, guZun, true, SectorPresets.frontier),
    zunzhu(bundle.get("zunzhu"), 50000, true, true, guZun, true, SectorPresets.planetaryTerminal),

    weisheng(bundle.get("weisheng"), 6000, false, false, yinYang1, false, null),
    yasheng(bundle.get("yasheng"), 80000, false, false, yinYang1, false, null),
    zhunsheng(bundle.get("zhunsheng"), 100000, false, false, yinYang1, false, null),
    dasheng(bundle.get("dasheng"), 130000, false, false, yinYang2, false, null),
    shengdaodadi(bundle.get("shengdaodadi"), 160000, false, false, yinYang2, false, null),

    tianxian(bundle.get("tianxian"), 180000, false, false, yuHua, false, null),
    zhenxian(bundle.get("zhenxian"), 210000, false, false, yuHua, false, null),
    xuanxian(bundle.get("xuanxian"), 250000, false, false, yuHua, false, null),
    jinxian(bundle.get("jinxian"), 280000, false, false, ziRi, false, null),
    xianjun(bundle.get("xianjun"), 320000, false, false, ziRi, false, null),
    xianzun(bundle.get("xianzun"), 380000, false, false, ziRi, false, null),
    xianhuang(bundle.get("xianhuang"), 450000, false, false, buMie, false, null),
    xianwang(bundle.get("xianwang"), 550000, false, false, buMie, false, null),

    xiandi(bundle.get("xiandi"), 650000, false, false, dongHua, false, null),
    tiandi(bundle.get("tiandi"), 770000, false, false, dongHua, false, null),
    daodi(bundle.get("daodi"), 900000, false, false, suiYue, false, null),
    dizun(bundle.get("dizun"), 1000000, false, false, suiYue, false, null),
    dijun(bundle.get("jingjie.dijun"), 1500000, false, false, daDao, false, null);

    public String str;
    public int amount;
    public boolean hasMirror;
    public boolean newRoad;
    public GongFa gongFa;
    public boolean needDuJie; // 是否需要渡劫才能突破
    public SectorPreset duJiePreset;
    public static final JingJie[] all = values();
    public static final Seq<JingJie> fajing = new Seq<>(
        new JingJie[] { kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong });
    public static final Seq<JingJie> shenOldRoad = new Seq<>(
        new JingJie[] { weishen, zhenshen, shenjun, shenwang, shenhuang, shenling, shenming, shenzun });
    public static final Seq<JingJie> shenNewRoad = new Seq<>(
        new JingJie[] { kaiyang, shentu, canghai, tianqiao, wanling, sixiang, shengong, zunzhu });
    public static final Seq<JingJie> shenjing = new Seq<>();
    public static final JingJie[] jiuLu = { fan, kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong, weishen,
        zhenshen, shenjun, shenwang, shenhuang, shenling, shenming, shenzun, weisheng, yasheng, zhunsheng, dasheng,
        shengdaodadi, tianxian, zhenxian, xuanxian, jinxian, xianjun, xianzun, xianhuang, xianwang, xiandi, tiandi,
        daodi, dizun, dijun };
    public static final JingJie[] xinLu = { fan, kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong,
        kaiyang, shentu, canghai, tianqiao, wanling, sixiang, shengong, zunzhu, weisheng, yasheng, zhunsheng, dasheng,
        shengdaodadi, tianxian, zhenxian, xuanxian, jinxian, xianjun, xianzun, xianhuang, xianwang, xiandi, tiandi,
        daodi, dizun, dijun };
    static {
      shenjing.add(shenOldRoad);
      shenjing.add(shenNewRoad);
    }
    public static final Seq<JingJie> shengjing = new Seq<>(
        new JingJie[] { weisheng, yasheng, zhunsheng, dasheng, shengdaodadi });
    public static final Seq<JingJie> xianjing = new Seq<>(
        new JingJie[] { tianxian, zhenxian, xuanxian, jinxian, xianjun, xianzun, xianhuang, xianwang });
    public static final Seq<JingJie> dijing = new Seq<>(
        new JingJie[] { xiandi, tiandi, daodi, dizun, dijun });

    JingJie(String str, int amount, boolean hasMirror, boolean newRoad, GongFa gongFa, boolean needDuJie,
        SectorPreset duJiePreset) {
      this.str = str;
      this.amount = amount;
      this.hasMirror = hasMirror;
      this.newRoad = newRoad;
      this.gongFa = gongFa;
      this.needDuJie = needDuJie;
      this.duJiePreset = duJiePreset;
    }

    public static JingJie getByOrdinal(int ordinal) {
      if (ordinal < 0 || ordinal >= all.length) {
        return null;
      }
      return all[ordinal];
    }

    public static JingJie getMin() {
      return fan;
    }
  }

  public enum Mode {
    easy, normal, hard, extreme
  }
}
