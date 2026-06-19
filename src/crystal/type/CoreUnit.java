package crystal.type;

import mindustry.world.blocks.storage.CoreBlock;

public interface CoreUnit {
  int storageCapacity();

  float suckRange();

  int unitCapBonus();

  CoreBlock core();
}
