package crystal.entities.bullet;

import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

public class HealUnitBulletType extends BasicBulletType {
  public float healpercent;

  public HealUnitBulletType(float speed, float damage) {
    this.speed = speed;
    this.damage = damage;
  }

  public HealUnitBulletType() {
  }

  @Override
  public void update(Bullet b) {
    super.update(b);
    Unit unit = Units.closest(b.team, b.x, b.y, 4, u -> u.damaged());
    if (unit != null) {
      unit.heal(unit.maxHealth * healpercent * 0.01f);
      Fx.heal.at(unit);
      b.remove();
    }
  }
}
