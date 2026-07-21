package crystal.type;

import crystal.world.blocks.stroage.MCoreBlock;

public interface CoreUnit {
  int storageCapacity();

  float suckRange();

  float auxiliaryRange();

  int unitCapBonus();

  MCoreBlock core();

}
