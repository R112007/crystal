package crystal.world.blocks.defence.turrets;

import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * 电力治疗炮台：优先瞄准范围内受伤的友方单位/建筑；没有受伤友方时，会正常攻击敌人。
 *
 * 继承 PowerTurret，通过电力驱动， shootType 直接指定子弹。
 */
public class HealingPowerTurret extends PowerTurret {

  public HealingPowerTurret(String name) {
    super(name);

    targetAir = true;
    targetGround = true;
    targetHealing = true;
  }

  public class HealingPowerTurretBuild extends PowerTurretBuild {

    @Override
    protected void findTarget() {
      float range = range();

      // 1. 优先找最近受伤的友方单位
      target = Units.closest(team, x, y, range, u -> u.team == team &&
          u.isValid() &&
          u.damaged() &&
          (u.isGrounded() ? targetGround : targetAir) &&
          unitFilter.get(u));

      // 2. 没有受伤单位时，找受伤的建筑
      if (target == null && targetHealing) {
        target = Units.findAllyTile(team, x, y, range, b -> b.damaged() && b != this);
      }

      // 3. 友方没有可治疗的，才攻击敌人
      if (target == null) {
        target = findEnemy(range);
      }
    }

    @Override
    protected boolean validateTarget() {
      // 友方目标在默认判定里会被视为无效（同队）。
      // 当 canHeal() 为 true 时，用 Team.derelict 作为参数可以绕过这个限制。
      return !Units.invalidateTarget(target, canHeal() ? Team.derelict : team, x, y) || controlled()
          || logicControlled();
    }
  }
}
