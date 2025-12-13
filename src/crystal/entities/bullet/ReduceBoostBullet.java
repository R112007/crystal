package crystal.entities.bullet;

import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;

import static mindustry.Vars.*;

public class ReduceBoostBullet extends BasicBulletType {
  public float range, duration, percent;
  public Effect damageEffect = Fx.chainLightning;

  public ReduceBoostBullet(float speed, float damage) {
    this(speed, damage, "bullet");
  }

  public ReduceBoostBullet(float speed, float damage, String bulletSprite) {
    super(speed, damage);
    this.sprite = bulletSprite;
  }

  @Override
  public void hit(Bullet b, float x, float y) {
    super.hit(b, x, y);
    indexer.eachBlock(null, x, y, this.range, build -> build.team != b.team && build.block.canOverdrive, build -> {
      build.applySlowdown(this.percent, this.duration);
      damageEffect.at(x, y, 0f, build);
    });
  }
}
