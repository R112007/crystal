package crystal.entities.units;

import crystal.core.CultivationState;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.gen.Magicc;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/** 统一单位创建入口，确保魔法单位的境界在实例创建时确定。 */
public final class UnitSpawner {
  private UnitSpawner() {
  }

  /** 按单位模板默认境界创建。适用于敌人、地图预设单位和普通内容单位。 */
  public static Unit create(UnitType type, Team team) {
    return type.create(team);
  }

  /** 按指定境界创建。 */
  public static Unit create(UnitType type, Team team, JingJie jingJie) {
    Unit unit = type.create(team);
    bind(unit, jingJie);
    return unit;
  }

  /** 玩家发兵入口：单位实例绑定玩家当前境界。 */
  public static Unit createPlayer(UnitType type, Team team) {
    return create(type, team, CultivationState.currentJingJie());
  }

  /** 玩家发兵并直接放入世界。 */
  public static Unit spawnPlayer(UnitType type, Team team, float x, float y, float rotation) {
    Unit unit = createPlayer(type, team);
    unit.set(x, y);
    unit.rotation(rotation);
    unit.add();
    return unit;
  }

  public static void bind(Unit unit, JingJie jingJie) {
    if (unit instanceof Magicc magic) {
      bind(magic, jingJie);
    }
  }

  public static void bind(Magicc magic, JingJie jingJie) {
    if (magic != null) {
      magic.jingJie(jingJie == null ? JingJie.fan : jingJie);
    }
  }
}
