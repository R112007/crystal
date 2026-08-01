package crystal.aviation;

import arc.Events;
import crystal.gen.SMissile;
import mindustry.entities.EntityGroup;
import mindustry.game.EventType.Trigger;

/** 自定义 EntityGroup，用于管理卫星导弹实体。 */
public class SatelliteMissileGroup {
  public static EntityGroup<SMissile> missiles;

  public static void init() {
    if (missiles != null)
      return;
    // spatial=true 会实例化 QuadTree，mapping=true 会实例化 IntMap，供未来使用
    missiles = new EntityGroup<>(SMissile.class, true, true, (e, pos) -> {
    });

    // 导弹已在 Groups.all 中更新，此处不再单独 update
      }

  public static void clear() {
    if (missiles != null)
      missiles.clear();
  }
}
