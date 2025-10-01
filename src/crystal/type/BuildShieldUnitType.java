package crystal.type;

import ent.anno.Annotations.SyncField;
import mindustry.type.UnitType;

public class BuildShieldUnitType extends UnitType {
  @SyncField(value = true)
  public float shieldHealth = 1200f; // 护盾最大生命值
  @SyncField(value = true)
  public float regenRate = 3f; // 护盾再生速率
  @SyncField(value = true)
  public float cooldown = 3f; // 护盾再生速率

  public BuildShieldUnitType(String name) {
    super(name);
    this.buildSpeed = 1f;
  }

}
