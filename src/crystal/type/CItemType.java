package crystal.type;

import arc.graphics.Color;
import crystal.world.meta.CStat;
import mindustry.type.Item;

public class CItemType extends Item {
  public float crystalEnergy;

  public CItemType(String name, Color color) {
    super(name);
    this.color = color;
    this.crystalEnergy = 0.0f;
  }

  public CItemType(String name) {
    super(name);
    this.color = Color.valueOf("#114514");
    this.crystalEnergy = 0.0f;
  }

  public CItemType() {
    super("byd名字都不写是吧");
    this.color = Color.valueOf("#114514");
    this.crystalEnergy = 0.0f;
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.crystalEnergy, crystalEnergy);
  }

  @Override
  public void init() {
    super.init();
  }
}
