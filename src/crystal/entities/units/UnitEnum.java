package crystal.entities.units;

import arc.graphics.Color;
import arc.struct.Seq;
import crystal.type.GongFa;

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
    fan(bundle.get("yong"), 0, false, false, none),
    kaiqiao(bundle.get("kaiqiao"), 10, false, false, taiXuanTianGong1),
    zhenyuan(bundle.get("zhenyuan"), 80, false, false, taiXuanTianGong1),
    huayuan(bundle.get("huayuan"), 200, false, false, taiXuanTianGong2),
    tianren(bundle.get("tianren"), 500, false, false, taiXuanTianGong2),
    shenhai(bundle.get("shenhai"), 1000, false, false, taiXuanTianGong3),
    hualong(bundle.get("hualong"), 2000, false, false, taiXuanTianGong3),

    weishen(bundle.get("weishen"), 3200, true, false, xinHuang),
    kaiyang(bundle.get("kaiyang"), 3200, true, true, guHuang),
    zhenshen(bundle.get("zhenshen"), 5000, true, false, xinHuang),
    shentu(bundle.get("shentu"), 5000, true, true, guHuang),
    shenjun(bundle.get("shenjun"), 7500, true, false, xinHuang),
    canghai(bundle.get("canghai"), 7500, true, true, guHuang),
    shenwang(bundle.get("shenwang"), 11000, true, false, xinHuang),
    tianqiao(bundle.get("tianqiao"), 11000, true, true, guHuang),
    shenhuang(bundle.get("shenhuang"), 14500, true, false, xinZun),
    wanling(bundle.get("wanling"), 14500, true, true, guZun),
    shenling(bundle.get("shenling"), 19000, true, false, xinZun),
    sixiang(bundle.get("sixiang"), 19000, true, true, guZun),
    shenming(bundle.get("shenming"), 24000, true, false, xinZun),
    shengong(bundle.get("shengong"), 24000, true, true, guZun),
    shenzun(bundle.get("shenzun"), 30000, true, false, xinZun), zunzhu(bundle.get("zunzhu"), 30000, true, true, guZun),

    weisheng(bundle.get("weisheng"), 42000, false, false, yinYang1),
    yasheng(bundle.get("yasheng"), 60000, false, false, yinYang1),
    zhunsheng(bundle.get("zhunsheng"), 80000, false, false, yinYang1),
    dasheng(bundle.get("dasheng"), 100000, false, false, yinYang2),
    shengdaodadi(bundle.get("shengdaodadi"), 130000, false, false, yinYang2),

    tianxian(bundle.get("tianxian"), 160000, false, false, yuHua),
    zhenxian(bundle.get("zhenxian"), 180008, false, false, yuHua),
    xuanxian(bundle.get("xuanxian"), 210000, false, false, yuHua),
    jinxian(bundle.get("jinxian"), 240000, false, false, ziRi),
    xianjun(bundle.get("xianjun"), 280000, false, false, ziRi),
    xianzun(bundle.get("xianzun"), 320000, false, false, ziRi),
    xianhuang(bundle.get("xianhuang"), 380000, false, false, buMie),
    xianwang(bundle.get("xianwang"), 450000, false, false, buMie),

    xiandi(bundle.get("xiandi"), 550000, false, false, dongHua),
    tiandi(bundle.get("tiandi"), 650000, false, false, dongHua),
    daodi(bundle.get("daodi"), 770000, false, false, suiYue),
    dizun(bundle.get("dizun"), 900000, false, false, suiYue),
    dijun(bundle.get("jingjie.dijun"), 1000000, false, false, daDao);

    public String str;
    public int amount;
    public boolean hasMirror;
    public boolean newRoad;
    public GongFa gongFa;

    public static final JingJie[] all = values();
    public static final Seq<JingJie> fajing = new Seq<>(
        new JingJie[] { kaiqiao, zhenyuan, huayuan, tianren, shenhai, hualong });
    public static final Seq<JingJie> shenOldRoad = new Seq<>(
        new JingJie[] { weishen, zhenshen, shenjun, shenwang, shenhuang, shenling, shenming, shenzun });
    public static final Seq<JingJie> shenNewRoad = new Seq<>(
        new JingJie[] { kaiyang, shentu, canghai, tianqiao, wanling, sixiang, shengong, zunzhu });
    public static final Seq<JingJie> shenjing = new Seq<>();
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

    JingJie(String str, int amount, boolean hasMirror, boolean newRoad, GongFa gongFa) {
      this.str = str;
      this.amount = amount;
      this.hasMirror = hasMirror;
      this.newRoad = newRoad;
      this.gongFa = gongFa;
    }
  }

  public enum Mode {
    easy, normal, hard, extreme
  }
}
