package crystal.entities.units;

import arc.Events;
import arc.util.Threads;
import crystal.game.CEventType.SummonUnitEvent;

public class SummonUnit {
  public static void init() {
    Events.on(SummonUnitEvent.class, e -> {
      Threads.thread(() -> {
        Threads.sleep((long) e.delay * 1000);
        e.type.spawn(e.team, e.x, e.y);
      });
      e.effect.at(e.x, e.y);
    });
  }
}
