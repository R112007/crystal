package crystal.content;

import crystal.magic.FaBao;
import crystal.magic.fabaos.BingFengFu;
import crystal.magic.fabaos.HuiChunFu;
import crystal.magic.fabaos.LuoLeiFu;

/**
 * 法宝内容注册。具体法宝是 magic/fabaos/ 下的子类，这里只负责实例化。
 * 新增法宝：在 magic/fabaos/ 写个子类继承 FaBao，然后在这里 new 出来。
 */
public class FaBaos {
  public static FaBao luoLeiFu, bingFengFu, huiChunFu;

  public static void load() {
    luoLeiFu = new LuoLeiFu();
    bingFengFu = new BingFengFu();
    huiChunFu = new HuiChunFu();
  }
}
