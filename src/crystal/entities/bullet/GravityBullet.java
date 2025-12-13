package crystal.entities.bullet;

import arc.math.geom.Vec2;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

public class GravityBullet extends BasicBulletType {
  public float foece;
  public float foeceRange;
  public String sprite;
  public @Nullable String backSprite;
  private final Vec2 tmpBulletPos = new Vec2();
  private final Vec2 tmpDirection = new Vec2();
  private final Vec2 tmpTargetVel = new Vec2();

  public GravityBullet(float speed, float damage, String bulletSprite) {
    super(speed, damage);
    this.sprite = bulletSprite;
  }

  public GravityBullet(float speed, float damage) {
    this(speed, damage, "bullet");
  }

  public GravityBullet() {
    this(1f, 1f, "bullet");
  }

  @Override
  public void load() {
    super.load();
  }

  @Override
  public void update(Bullet b) {
    super.update(b);
    updateGravity(b);
  }

  public void updateGravity(Bullet bullet) {
    if (bullet == null || bullet.hit() || !bullet.isAdded())
      return;
    float bulletX = bullet.x;
    float bulletY = bullet.y;
    Units.nearbyEnemies(bullet.team(), bullet.getX(), bullet.getY(), this.foeceRange, enemy -> {
      if (enemy.dead() || !enemy.isAdded())
        return;
      tmpBulletPos.set(bulletX, bulletY);
      moveEnemyToBulletSmoothly(enemy, tmpBulletPos, foece, bullet);
    });
  }

  private void moveEnemyToBulletSmoothly(Unit enemyEntity, Vec2 bulletPos, float moveSpeed, Bullet bullet) {
    if (!bullet.isAdded() || bullet.hit()) {
      enemyEntity.vel.setZero(); // 子弹消失，敌人停止运动
      return;
    }
    tmpDirection
        .set(bulletPos)
        .sub(enemyEntity.x, enemyEntity.y);
    float distanceToBullet = tmpDirection.len();
    float moveStepPerFrame = moveSpeed * Time.delta;
    if (distanceToBullet <= moveStepPerFrame) {
      enemyEntity.set(bulletPos);// 敌人移动到子弹位置
      enemyEntity.damage(bullet.damage / 5);
      enemyEntity.vel.setZero(); // 停止运动
      return;
    }
    tmpDirection.nor();
    tmpTargetVel.set(tmpDirection).scl(moveSpeed);
    enemyEntity.moveAt(tmpTargetVel, enemyEntity.type.accel);
  }

  @Override
  public void despawned(Bullet b) {
    super.despawned(b);

  }
}
