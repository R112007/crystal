package crystal.type;

import mindustry.type.UnitType;

public class BuildShieldUnitType extends UnitType {
  public float shieldHealth = 1200f; // 护盾最大生命值
  public float regenRate = 3f; // 护盾再生速率
  public float cooldown = 3f; // 护盾再生速率

  public BuildShieldUnitType(String name) {
    super(name);
    this.buildSpeed = 1f;
  }

}
