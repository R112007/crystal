package crystal.type.weapons;

import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.type.Weapon;

/**
 * 治疗武器：优先自动瞄准范围内受伤的友方单位；没有受伤友方时，会正常攻击敌人。
 *
 * 使用方式：
 * 1. 把武器挂到单位的 weapons 列表里。
 * 2. bullet 使用能够治疗友方的子弹，例如 HealUnitBulletType。
 * 3. 这个武器已经设置 controllable = false、autoTarget = true，
 * 所以它会完全自动寻找目标并开火。
 */
public class HealUnitWeapon extends Weapon {

  public HealUnitWeapon(String name) {
    super(name);

    // 不由玩家/AI控制，完全自动索敌
    controllable = false;
    autoTarget = true;
    rotate = true;

    // 不纳入单位的攻击范围计算（这是治疗武器）
    useAttackRange = false;
  }

  @Override
  protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground) {
    // 1. 优先找最近的、受伤的、可被该子弹命中的友方单位
    Teamc ally = Units.closest(unit.team, x, y, range + Math.abs(shootY), u -> u.team == unit.team &&
        u.isValid() &&
        u.damaged() &&
        u.checkTarget(air, ground));

    // 2. 没有受伤友方时，按原 Weapon 逻辑攻击敌人
    return ally != null ? ally : super.findTarget(unit, x, y, range, air, ground);
  }

  @Override
  protected boolean checkTarget(Unit unit, Teamc target, float x, float y, float range) {
    if (target == null)
      return true;

    // 允许友方目标，只判断：是否出射程 / 是否已死亡
    return !target.within(x, y, range + (target instanceof Sized hb ? hb.hitSize() / 2f : 0f))
        || (target instanceof Healthc h && !h.isValid());
  }
}
