package crystal.test;

import crystal.world.blocks.environment.SpawnBossFloor;
import mindustry.world.Block;

public class TestLoad {
  public static Block spfloor;

  public static void load() {
    CataclysmUnit.load();
    YourModFactories.load();
    spfloor = new SpawnBossFloor("boss") {
      {
      }
    };
  }
}
