package crystal.world.blocks.unit;

import arc.Events;
import crystal.core.CultivationState;
import crystal.entities.units.UnitSpawner;
import crystal.gen.Magicc;
import mindustry.Vars;
import mindustry.game.EventType.UnitCreateEvent;

/** 独立的魔法单位生产方块，玩家队伍生产时绑定玩家当前境界。 */
public class MagicUnitFactory extends mindustry.world.blocks.units.UnitFactory {
  private static boolean initialized;

  public MagicUnitFactory(String name) {
    super(name);
    buildType = mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild::new;
    initEvents();
  }

  private static void initEvents() {
    if (initialized)
      return;
    initialized = true;
    Events.on(UnitCreateEvent.class, event -> {
      if (event.spawner != null
          && event.spawner.block instanceof MagicUnitFactory
          && event.unit instanceof Magicc magic
          && Vars.player != null
          && event.unit.team == Vars.player.team()) {
        UnitSpawner.bind(magic, CultivationState.currentJingJie());
      }
    });
  }
}
