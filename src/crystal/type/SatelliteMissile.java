package crystal.type;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Nullable;
import arc.util.Time;
import crystal.gen.SMissile;
import crystal.gen.SMissilec;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Drawc;
import mindustry.gen.Entityc;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Timedc;
import mindustry.graphics.Layer;

public class SatelliteMissile implements Comparable<SatelliteMissile> {
  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static arc.struct.ObjectMap<Integer, SatelliteMissile> map = new arc.struct.ObjectMap<>();

  public static SatelliteMissile basic;
  public static SatelliteMissile heavy;
  public static SatelliteMissile cluster;

  public String name;
  public int id;

  // 原字段
  public float splashDamageRadius = 30f;
  public float splashDamage = 50f;
  public float lifetime = 120f;
  public String sprite;
  public @Nullable String backSprite;

  public TextureRegion region;
  public TextureRegion backRegion;
  public TextureRegion frontRegion;
  // 新增 BulletType 风格字段
  public float speed = 4f;
  public float damage = 40f;
  public float hitSize = 8f;
  public float width = 8f;
  public float height = 12f;
  public Color trailColor = Color.valueOf("ff4444");
  public Color frontColor = Color.valueOf("ffdd55");
  public Color backColor = Color.valueOf("ff6633");

  public Effect despawnEffect = Fx.hitBulletSmall;
  public Effect shootEffect = Fx.shootSmall;
  public Effect hitEffect = Fx.explosion;
  public Effect smokeEffect = Fx.smoke;
  public Effect trailEffect = Fx.missileTrail;

  // 命中 / 销毁相关（仿 BulletType）
  public Sound hitSound = Sounds.none;
  public Sound despawnSound = Sounds.none;
  public float hitShake = 0f;
  public float despawnShake = 0f;
  /** 生命周期结束时是否触发命中效果（类似 BulletType.despawnHit） */
  public boolean despawnHit = true;

  public SatelliteMissile(String name) {
    this.name = name;
    this.id = maxId.getAndIncrement();
    map.put(id, this);
  }

  /** 初始化纹理（应在 atlas 加载完成后调用） */
  public void initRegion() {
    backRegion = Core.atlas.find(backSprite == null ? (sprite + "-back") : backSprite);
    frontRegion = Core.atlas.find(sprite);
    region = Icon.box.getRegion();
  }

  /**
   * 绘制导弹本体，仿 BasicBulletType.draw(Bullet b)。
   * 子类可覆盖以实现自定义外观。
   */
  public void draw(SMissile missile) {
    if (missile == null)
      return;

    Draw.reset();

    // 伪 3D：从高空飞向地面，随进度从小变大、高度降低
    float progress = Mathf.clamp(missile.time / missile.lifetime, 0f, 1f);
    float scale = 0.45f + progress * 0.55f;

    float width = this.width * scale;
    float height = this.height * scale;
    float rotation = missile.rotation;

    if (backRegion.found()) {
      Draw.color(backColor);
      Draw.rect(backRegion, missile.x, missile.y, width, height, rotation - 90);
    }

    Draw.color(frontColor);
    Draw.rect(frontRegion, missile.x, missile.y, width, height, rotation - 90);
  }

  /** 导弹命中时调用（仿 BulletType.hit） */
  public void hit(SMissile missile) {
    if (missile == null || missile.hitCalled)
      return;
    missile.hitCalled = true;

    if (hitEffect != null)
      hitEffect.at(missile.x, missile.y, missile.rotation, trailColor);

    // 落地大爆炸特效 + 震动
    if (splashDamageRadius > 0) {
      float bigRadius = splashDamageRadius / 8f;
      Fx.massiveExplosion.at(missile.x, missile.y, bigRadius, trailColor);
      Fx.shockwave.at(missile.x, missile.y, bigRadius, trailColor);
    }

    if (hitSound != Sounds.none)
      hitSound.at(missile.x, missile.y, 1f + Mathf.range(0.1f));

    Effect.shake(hitShake, hitShake, missile.x, missile.y);

    if (splashDamage > 0) {
      Damage.damage(missile.team(), missile.x, missile.y, splashDamageRadius, splashDamage, false, true, true);
    }
  }

  /** 导弹自然销毁时调用（仿 BulletType.despawned） */
  public void despawned(SMissile missile) {
    if (missile == null)
      return;
    if (despawnHit) {
      hit(missile);
    } else {
      if (despawnEffect != null)
        despawnEffect.at(missile.x, missile.y, missile.rotation, trailColor);
      Effect.shake(despawnShake, despawnShake, missile.x, missile.y);
      if (despawnSound != Sounds.none)
        despawnSound.at(missile.x, missile.y, 1f + Mathf.range(0.1f));
    }
  }

  /** 导弹被移除时调用（仿 BulletType.removed） */
  public void removed(SMissile missile) {
    // 可在这里清理 trail 等持久状态；当前无需要清理的数据
  }

  /** 创建并发射一枚导弹（仿 BulletType.create） */
  public SMissile create(Entityc owner, Team team, float sx, float sy, float tx, float ty) {
    float angle = Mathf.angle(tx - sx, ty - sy);
    float dist = Mathf.dst(sx, sy, tx, ty);
    float life = dist / speed + 60f;

    SMissile missile = SMissile.create();
    missile.type = this;
    missile.team = team;
    missile.set(sx, sy);
    missile.lifetime = life;
    missile.vel.trns(angle, speed);
    missile.damage = this.damage;
    missile.shooter = owner;
    missile.owner = owner;
    missile.targetX = tx;
    missile.targetY = ty;
    missile.rotation = angle;
    missile.removalReason = null;
    missile.add();

    if (shootEffect != null)
      shootEffect.at(sx, sy, angle, trailColor);
    if (smokeEffect != null)
      smokeEffect.at(sx, sy, angle, Color.gray);
    Fx.launchPod.at(sx, sy, angle, trailColor);
    Fx.shootSmokeMissile.at(sx, sy, angle, trailColor);

    return missile;
  }

  /** 每帧更新 */
  public void update(SMissile missile) {
    missile.x += missile.vel().x * Time.delta;
    missile.y += missile.vel().y * Time.delta;
    if (missile.vel().len2() > 0.001f) {
      missile.rotation = missile.vel.angle();
    }

    // 发射与飞行尾迹
    if (trailEffect != null) {
      // missileTrail 用 rotation 参数作为半径
      trailEffect.at(missile.x, missile.y, width * 0.6f, trailColor);
    }
    missile.smokeTimer += Time.delta;
    if (smokeEffect != null && missile.smokeTimer >= 4f) {
      smokeEffect.at(missile.x, missile.y, missile.rotation, Color.gray);
      missile.smokeTimer = 0f;
    }

    // 越界销毁：放宽边界，给从屏幕边缘飞入的导弹足够余量
    float ww = mindustry.Vars.world.unitWidth();
    float wh = mindustry.Vars.world.unitHeight();
    if (missile.x < -500f || missile.y < -500f || missile.x > ww + 500f || missile.y > wh + 500f) {
      missile.removalReason = "out_of_bounds";
      missile.remove();
      return;
    }

    // 导弹需要飞到目标准星处再爆炸：只有非常接近目标或生命周期快结束时才启用碰撞检测，
    // 避免途中撞山/撞敌提前引爆，确保导弹能到达准星附近。
    float toTarget = Mathf.dst(missile.x, missile.y, missile.targetX, missile.targetY);
    boolean nearTarget = toTarget < 12f || missile.time > missile.lifetime * 0.95f;
    if (!nearTarget) {
      return;
    }

    // 撞击固体地面
    mindustry.world.Tile tile = mindustry.Vars.world.tileWorld(missile.x, missile.y);
    if (tile != null && tile.solid()) {
      missile.removalReason = "solid_tile";
      hit(missile);
      missile.remove();
      return;
    }

    float collideSize = Math.max(hitSize, 12f);

    // 碰撞敌方单位
    mindustry.gen.Unit unit = mindustry.entities.Units.closestEnemy(missile.team(), missile.x, missile.y,
        collideSize * 3f, u -> u.checkTarget(true, false));
    if (unit != null && unit.within(missile.x, missile.y, collideSize + unit.hitSize / 2f)) {
      missile.removalReason = "hit_unit";
      hit(missile);
      missile.remove();
      return;
    }

    // 碰撞敌方建筑
    mindustry.gen.Building build = mindustry.Vars.world.buildWorld(missile.x, missile.y);
    if (build != null && build.team != missile.team()
        && build.within(missile.x, missile.y, collideSize + build.hitSize() / 2f)) {
      missile.removalReason = "hit_building";
      hit(missile);
      missile.remove();
      return;
    }

    // 到达准星附近且未命中任何物体：在准星处引爆
    if (toTarget < 8f) {
      missile.removalReason = "reached_target";
      hit(missile);
      missile.remove();
      return;
    }
  }

  @Override
  public int compareTo(SatelliteMissile other) {
    return Integer.compare(id, other.id);
  }

  public static void load() {
    basic = new SatelliteMissile("basic-missile") {
      {
        sprite = "missile-large";
        speed = 10f;
        damage = 90f;
        splashDamage = 300f;
        splashDamageRadius = 50f;
        lifetime = 160f;
        width = 14f;
        height = 22f;
        hitSize = 10f;
        hitEffect = Fx.flakExplosionBig;
        despawnEffect = Fx.flakExplosionBig;
        shootEffect = Fx.shootSmokeMissile;
        smokeEffect = Fx.shootBigSmoke;
        hitShake = 3f;
        despawnShake = 2f;
      }
    };

    heavy = new SatelliteMissile("heavy-missile") {
      {
        sprite = "missile-large";
        speed = 10f;
        damage = 80f;
        splashDamage = 80f;
        splashDamageRadius = 48f;
        lifetime = 80f;
        width = 18f;
        height = 28f;
        hitSize = 9f;
        hitEffect = Fx.massiveExplosion;
        despawnEffect = Fx.explosion;
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.shootBigSmoke;
        hitShake = 6f;
        despawnShake = 4f;
      }
    };

    cluster = new SatelliteMissile("cluster-missile") {
      {
        sprite = "missile-large";
        speed = 4f;
        damage = 20f;
        splashDamage = 20f;
        splashDamageRadius = 32f;
        lifetime = 180f;
        width = 16f;
        height = 24f;
        hitSize = 7f;
        hitEffect = Fx.flakExplosionBig;
        despawnEffect = Fx.flakExplosion;
        shootEffect = Fx.shootSmall;
        smokeEffect = Fx.shootSmallSmoke;
        hitShake = 4f;
        despawnShake = 3f;
      }
    };
    Events.on(ClientLoadEvent.class, e -> {
      for (SatelliteMissile missile : map.values()) {
        missile.initRegion();
      }
    });
  }

  @EntityDef(value = { SMissilec.class }, serialize = false, pooled = true)
  @EntityComponent
  public static abstract class SMissileComp implements Drawc, Teamc, Timedc, SMissilec {
    @Import
    public float lifetime;
    @Import
    public float x, y;
    @Import
    public Team team;
    @Import
    public float time;

    public SatelliteMissile type;
    public float rotation;
    public Vec2 vel = new Vec2();
    public float damage;
    public Entityc owner;
    public Entityc shooter;
    public boolean hitCalled;
    public float smokeTimer;
    public float targetX;
    public float targetY;
    /** 记录移除原因，便于调试“原地爆炸”等问题 */
    public String removalReason;

    @Override
    public void update() {
      // 生命周期由 Timedc 自动生成，这里只处理导弹自身逻辑
      type.update(self());
    }

    @Override
    public void remove() {
      // 仿 BulletComp.remove：未命中过时调用 despawned，最后调用 removed
      if (!hitCalled) {
        if (removalReason == null && time >= lifetime) {
          removalReason = "lifetime_expired";
        }
        if (type != null)
          type.despawned(self());
      }
      if (type != null)
        type.removed(self());
    }

    @Override
    public void draw() {
      if (type == null)
        return;

      // 仿 Bullet.draw：设置图层后交给 type 绘制
      float prevZ = Draw.z();
      Draw.z(Layer.flyingUnit + 1f);
      type.draw(self());
      Draw.reset();
      Draw.z(prevZ);
    }
  }
}
