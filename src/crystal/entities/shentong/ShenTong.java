package crystal.entities.shentong;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.*;
import crystal.type.MagicUnitType;

public abstract class ShenTong implements Cloneable {
  public static ObjectMap<Integer, ShenTong> shengTongMap = new ObjectMap<>();
  public int id;

  public void update(Magicc magic) {
  }

  public void setStat(MagicUnitType magic, Table table) {
  }

  public void setBar(Magicc magic, Table table) {
  }

  public void death(Magicc magic) {
  }

  public void write(Writes writes) {
  }

  public void read(Reads reads) {
  }

  public void init(MagicUnitType type) {
  }

  public abstract String name();

  public abstract String description();

  public ShenTong create() {
    try {
      return (ShenTong) clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("java sucks", e);
    }
  }

  public abstract XiuWei limitMinXiuWei();

  @Override
  public boolean equals(Object object) {
    if (object == this)
      return true;
    ShenTong shenTong = (ShenTong) object;
    if (this.name() == shenTong.name() && this.description() == shenTong.description())
      return true;
    else
      return false;
  }
}
