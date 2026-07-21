package crystal.entities.bullet;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * 瞬时扇形激光子弹。
 * 仿照 LaserBulletType：发射瞬间在扇形区域内造成一次伤害，
 * 随后扇形激光随 lifetime 逐渐淡出，本身不会作为实体飞行。
 */
public class SectorLaserBulletType extends BulletType {

  /** 扇形最大半径。 */
  public float sectorLength = 160f;
  /** 扇形总夹角（度），以子弹朝向为中心。 */
  public float sectorAngle = 45f;
  /** 扇形绘制分段数，越高越圆滑。 */
  public int divisions = 40;
  /** 生成时播放的激光特效。 */
  public Effect laserEffect = Fx.lancerLaserShootSmoke;

  /** 激光颜色，从外到内。 */
  public Color[] colors = {
      Color.valueOf("ec745855"),
      Color.valueOf("ec7458aa"),
      Color.valueOf("ff9c5a"),
      Color.white
  };

  public SectorLaserBulletType(float damage) {
    this.damage = damage;
    this.speed = 0f;

    hitEffect = Fx.hitLaserBlast;
    hitColor = colors[colors.length - 1];
    despawnEffect = Fx.none;
    shootEffect = Fx.hitLancer;
    smokeEffect = Fx.none;
    hitSize = 4;
    lifetime = 16f;
    impact = true;
    keepVelocity = false;
    collides = false;
    pierce = true;
    hittable = false;
    absorbable = false;
    removeAfterPierce = false;
    delayFrags = true;
  }

  public SectorLaserBulletType() {
    this(1f);
  }

  @Override
  public void init() {
    super.init();
    drawSize = Math.max(drawSize, sectorLength * 2f);
  }

  @Override
  public float estimateDPS() {
    return super.estimateDPS() * 3f;
  }

  @Override
  protected float calculateRange() {
    return Math.max(sectorLength, maxRange);
  }

  @Override
  public void init(Bullet b) {
    float realLength = Damage.findLength(b, sectorLength, laserAbsorb, pierceCap);
    float rot = b.rotation();
    b.fdata = realLength;

    laserEffect.at(b.x, b.y, rot, realLength * 0.75f);

    applySectorDamage(b, realLength);
  }

  /** 对扇形区域内的单位和建筑造成一次伤害。 */
  protected void applySectorDamage(Bullet b, float realLength) {
    float rot = b.rotation();
    float half = sectorAngle / 2f;

    // ----- 单位 -----
    Tmp.r1.setCentered(b.x, b.y, realLength * 2f);
    Units.nearbyEnemies(b.team, Tmp.r1, u -> {
      if (!u.checkTarget(collidesAir, collidesGround) || !u.hittable())
        return;

      float dst = b.dst(u);
      if (dst > realLength + u.hitSize() / 2f)
        return;

      float ang = b.angleTo(u);
      if (!Angles.within(ang, rot, half))
        return;

      u.collision(b, u.x, u.y);
      b.collision(u, u.x, u.y);
    });

    // ----- 建筑 -----
    indexer.eachBlock(null, b.x, b.y, realLength, build -> {
      if (build.dead())
        return false;

      if (build.team == b.team) {
        return heals() && testCollision(b, build)
            && Angles.within(b.angleTo(build), rot, half);
      }

      return build.collide(b)
          && Angles.within(b.angleTo(build), rot, half);
    }, build -> {
      if (build.team == b.team) {
        hitTile(b, build, build.x, build.y, build.health, false);
      } else {
        float health = build.health;

        build.collision(b);
        if (build.collide(b)) {
          hit(b, build.x, build.y);
        }

        if (testCollision(b, build)) {
          hitTile(b, build, build.x, build.y, health, false);
        }
      }
    });
  }

  @Override
  public void draw(Bullet b) {
    float realLength = b.fdata;
    float rot = b.rotation();
    float half = sectorAngle / 2f;
    float fraction = sectorAngle / 360f;
    float start = rot - half;

    float f = Mathf.curve(b.fin(), 0f, 0.2f);
    float fout = b.fout();
    float baseLen = realLength * f;
    float alpha = fout;

    Draw.z(layer);

    // === 扇形主体：用 lerp 在 colors 之间插值，实现径向渐变 ===
    int layers = 20;
    for (int i = 0; i < layers; i++) {
      float layerFin = i / (float) (layers - 1);

      // 在 colors 数组中 lerp 取色
      float colorPos = layerFin * (colors.length - 1);
      int idx = (int) colorPos;
      float t = colorPos - idx;

      Color col;
      if (idx >= colors.length - 1) {
        col = colors[colors.length - 1];
      } else {
        col = Tmp.c1.set(colors[idx]).lerp(colors[idx + 1], t);
      }

      // 远处更浅：lerp 到透明色
      Color fadeColor = Tmp.c2.set(col).a(0f);
      col.lerp(fadeColor, layerFin * 0.7f); // 0.7f 控制远处淡化程度，可调

      float radius = baseLen * Mathf.lerp(1f, 0.15f, layerFin);
      Draw.color(Tmp.c1.set(col).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)).a(col.a * alpha));
      Fill.arc(b.x, b.y, radius, fraction, start, divisions);
    }

    // 扇形边缘高光
    Draw.color(Tmp.c1.set(colors[colors.length - 1]).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)).a(alpha));
    Lines.stroke(1.5f * alpha);
    Lines.arc(b.x, b.y, baseLen, fraction, start, divisions);

    // 两条边界半径
    for (int s : Mathf.signs) {
      float a = rot + half * s;
      float x2 = b.x + Angles.trnsx(a, baseLen);
      float y2 = b.y + Angles.trnsy(a, baseLen);
      Lines.line(b.x, b.y, x2, y2);
    }

    Draw.reset();

    // 扇形区域光效
    Drawf.light(b.x, b.y, baseLen * 1.2f, lightColor, lightOpacity * alpha);
  }

  @Override
  public void drawLight(Bullet b) {
    // 光效已在 draw() 中绘制，避免默认圆形光叠加
  }
}
