package crystal.type;

import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.entities.units.UnitEnum.XiuWei;
import arc.struct.Seq;

public interface MagicUnitInterface {
  float magicPower();

  float magicPowerRegen();

  float magicPowerRegenTime();

  JingJie jingJie();

  default XiuWei xiuWei() {
    JingJie jingJie = jingJie();
    return jingJie == null ? XiuWei.yong : jingJie.xiuWei;
  }

  Seq<ShenTong> shenTongs();

  float cultivationReward();

  boolean useCircleCoat();
}
