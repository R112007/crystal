package crystal.type;

import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.XiuWei;
import arc.struct.Seq;

public interface MagicUnitInterface {
  float magicPower();

  float magicPowerRegen();

  float magicPowerRegenTime();

  XiuWei xiuWei();

  Seq<ShenTong> shenTongs();

  float xiuWeiAmount();

  boolean useCircleCoat();
}
