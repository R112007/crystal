package crystal.type;

import crystal.world.blocks.stroage.MCoreBlock;
import mindustry.type.UnitType;

public interface CoreUnit {
  int storageCapacity();

  float suckRange();

  float auxiliaryRange();

  int unitCapBonus();

  UnitType playerUnitType();

  MCoreBlock core();

}
