package crystal.content;

import crystal.type.GongFa;

public class GongFas {
  public static GongFa none, taiXuanTianGong1, taiXuanTianGong2, taiXuanTianGong3,
      xinHuang, guHuang, xinZun, guZun,
      yinYang1, yinYang2, yuHua, ziRi, buMie, dongHua, suiYue, daDao;

  // 👇 包装成方法，不在类加载时执行
  public static void load() {
    none = new GongFa("none", 0);
    taiXuanTianGong1 = new GongFa("taixuan1", 1);
    taiXuanTianGong2 = new GongFa("taixuan2", 2);
    taiXuanTianGong3 = new GongFa("taixuan3", 3);
    xinHuang = new GongFa("xinhuang", 4);
    guHuang = new GongFa("guhuang", 4);
    xinZun = new GongFa("xinzun", 5);
    guZun = new GongFa("guzun", 5);
    yinYang1 = new GongFa("yinyang1", 6);
    yinYang2 = new GongFa("yinyang2", 7);
    yuHua = new GongFa("yuhua", 8);
    ziRi = new GongFa("ziri", 9);
    buMie = new GongFa("bumie", 10);
    dongHua = new GongFa("donghua", 11);
    suiYue = new GongFa("suiyue", 12);
    daDao = new GongFa("dadao", 13);

    none.unlock(); // 默认解锁
  }
}
