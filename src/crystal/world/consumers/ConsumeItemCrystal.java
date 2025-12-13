package crystal.world.consumers;

import crystal.type.CItemType;
import mindustry.type.Item;
import mindustry.world.consumers.ConsumeItemEfficiency;

public class ConsumeItemCrystal extends ConsumeItemEfficiency {
  public float minCrystal;

  public ConsumeItemCrystal(float minCrystal) {
    this.minCrystal = minCrystal;
    this.filter = item -> {
      if (item instanceof CItemType i) {
        return i.crystalEnergy >= minCrystal;
      } else {
        return false;
      }
    };
  }

  public ConsumeItemCrystal() {
    this(0.1f);
  }

  @Override
  public float itemEfficiencyMultiplier(Item item) {
    if (item instanceof CItemType i) {
      return i.crystalEnergy;
    } else {
      return 0.0f;
    }

  }
}
