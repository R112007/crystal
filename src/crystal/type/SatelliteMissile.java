package crystal.type;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import crystal.gen.SMissile;
import crystal.gen.SMissilec;
import crystal.content.CFx;
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
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import crystal.world.meta.CStat;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.Stats;

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
  /** 从高空落到地面的总时长（tick）。与落点距离无关，保证"砸下来"而不是"慢慢飘"。 */
  public float descentTime = 42f;
  /** 高空尾迹补烟的间隔（tick）。 */
  public float smokeInterval = 3f;
  /**
   * 出射时相对地面投影的高度（世界单位）。俯视视角下"从两边射向地面"的观感全靠它：
   * 出射时弹体画得比地面影子高这么多，落地时归零。
   */
  public float liftHeight = 120f;

  /** 高空物体的大气色：越远越偏灰蓝，用来表现"还在天上"。 */
  public static final Color hazeColor = Color.valueOf("93a4bb");
  private static final Color tmpColor = new Color();

  // 命中 / 销毁相关（仿 BulletType）
  public Sound hitSound = Sounds.none;
  public Sound despawnSound = Sounds.none;
  public float hitShake = 0f;
  public float despawnShake = 0f;
  /** 生命周期结束时是否触发命中效果（类似 BulletType.despawnHit） */
  public boolean despawnHit = true;

  public final Stats stats = new Stats();

  public SatelliteMissile(String name) {
    this.name = name;
    this.id = maxId.getAndIncrement();
    map.put(id, this);
  }

  /** 初始化该导弹的 Stat 面板（必须在子类字段赋值完成后调用）。 */
  public void initStats() {
    stats.add(Stat.damage, damage);
    stats.add(CStat.splashDamage, splashDamage);
    stats.add(Stat.range, splashDamageRadius, StatUnit.blocks);
    stats.add(Stat.speed, speed, StatUnit.tilesSecond);
    stats.add(CStat.missileLifetime, lifetime, StatUnit.seconds);
  }

  /** 把导弹属性渲染到指定的 Table 中（用于切换导弹界面）。 */
  public void displayStats(Table table) {
    if (stats.toMap().size == 0)
      return;
    table.add("[accent]导弹属性[]").padTop(6f).padBottom(4f).row();
    for (var catEntry : stats.toMap().entries()) {
      for (var statEntry : catEntry.value.entries()) {
        table.table(row -> {
          row.add("[lightgray]" + statEntry.key.localized() + ":[] ").left();
          for (var value : statEntry.value) {
            value.display(row);
            row.add().size(8f);
          }
        }).left().padLeft(6f).row();
      }
    }
  }

  /** 初始化纹理（应在 atlas 加载完成后调用） */
  public void initRegion() {
    backRegion = Core.atlas.find(backSprite == null ? (sprite + "-back") : backSprite);
    frontRegion = Core.atlas.find(sprite);
    region = Icon.box.getRegion();
  }

  /**
   * 高空下落曲线：起步慢、越接近地面越快（重力感）。p 为时间进度 0~1。
   */
  public static float fallCurve(float p) {
    p = Mathf.clamp(p, 0f, 1f);
    return p * (0.25f + 0.75f * p);
  }

  /**
   * 绘制导弹本体。
   * 视角是高空俯瞰，所以"从两边射向地面"只能靠高度差来表现：
   * 1) 弹体画在**地面投影 + 高度**的位置上，地面那一点留一个影子（两者间距 = 当前高度）；
   * 2) 高度随行程降到 0，投影一路从出射侧滑到准星，影子越贴越近 = 落到地面上；
   * 3) 本体随之从小变大、颜色脱离大气色（远处发灰蓝），准星上给一圈落点环。
   */
  public void draw(SMissile missile) {
    if (missile == null)
      return;

    Draw.reset();

    // 地面轨迹进度直接用位置反推，永远和本体所在的位置一致（不受帧率影响）
    float total = Mathf.dst(missile.startX, missile.startY, missile.targetX, missile.targetY);
    float remain = Mathf.dst(missile.x, missile.y, missile.targetX, missile.targetY);
    float e = total <= 0.001f ? 1f : Mathf.clamp(1f - remain / total, 0f, 1f);
    float alt = 1f - e;

    // 高度：出射时最高，砸到准星时为 0
    float lift = liftHeight * alt;
    float px = missile.x;
    float py = missile.y + lift;
    // 弹头朝向 = 画面上实际的运动方向（横向射入 + 向下落），所以看起来是"斜着扎向地面"
    float dir = Mathf.angle(missile.targetX - missile.startX, missile.targetY - missile.startY - liftHeight);

    drawGroundShadow(missile, e);
    drawTargetMarker(missile, e);

    float scale = Mathf.lerp(0.4f, 1.05f, e);
    float width = this.width * scale;
    float height = this.height * scale;

    drawContrail(missile, e, width, liftHeight);

    // 大气透视：越远越偏灰蓝、越淡
    if (backRegion.found()) {
      Draw.color(tmpColor.set(backColor).lerp(hazeColor, alt * 0.3f), 1f);
      Draw.rect(backRegion, px, py, width, height, dir - 90);
    }

    Draw.color(tmpColor.set(frontColor).lerp(hazeColor, alt * 0.3f), 1f);
    Draw.rect(frontRegion, px, py, width, height, dir - 90);

    // 尾部喷焰：朝运动方向的反方向喷
    Tmp.v1.trns(dir + 180f, height * 0.45f);
    Draw.blend(Blending.additive);
    Draw.color(frontColor, trailColor, 0.3f + 0.7f * e);
    Drawf.tri(px + Tmp.v1.x, py + Tmp.v1.y, width * 0.6f,
        height * (0.6f + 0.7f * e), dir + 180f);
    Draw.color(Color.white, 0.7f * (0.3f + 0.7f * e));
    Fill.circle(px + Tmp.v1.x, py + Tmp.v1.y, width * 0.22f);
    Draw.blend();

    Drawf.light(px, py, width * 3f, trailColor, 0.22f * (0.4f + 0.6f * e));
    Draw.reset();
  }

  /** 地面投影的影子：跟着弹从出射侧一路滑到准星，和本体的间距就是"还在空中"。 */
  private void drawGroundShadow(SMissile missile, float e) {
    float a = 0.3f * (0.25f + 0.75f * e);
    Draw.color(Color.black, a);
    Fill.arc(missile.x, missile.y, Math.max(width * (0.5f + 0.45f * e), 3f), 1f);

    Draw.color(Color.black, 0.4f * (0.25f + 0.75f * e));
    Lines.stroke(0.8f + 0.6f * e);
    Lines.ellipse(missile.x, missile.y, Math.max(width * (0.9f + 0.5f * e), 5f), 1f, 0.5f, 0f);
  }

  /** 准星上的落点环：后半段才明显，提示"会砸在这里"。 */
  private void drawTargetMarker(SMissile missile, float e) {
    float a = Mathf.clamp((e - 0.25f) / 0.75f, 0f, 1f);
    if (a <= 0.01f)
      return;

    float rad = Math.max(width * 1.6f, 8f);
    Draw.color(trailColor, 0.7f * a);
    Lines.stroke(1.2f);
    Lines.arc(missile.targetX, missile.targetY, rad, 0.62f, Time.time * 1.6f);
    Lines.arc(missile.targetX, missile.targetY, rad, 0.62f, Time.time * 1.6f + 180f);
  }

  /** 高空尾迹：沿地面轨迹一路拖到本体，同时按各点的高度抬起来，越靠近本体越亮。 */
  private void drawContrail(SMissile missile, float e, float bodyWidth, float lift) {
    if (e <= 0.02f)
      return;

    int steps = 16;
    float strength = 0.3f + 0.7f * e;

    Draw.blend(Blending.additive);
    for (int i = 0; i <= steps; i++) {
      float f = i / (float) steps; // 0 = 本体, 1 = 出射点
      float q = Mathf.lerp(e, 0f, f);
      float cx = Mathf.lerp(missile.startX, missile.targetX, q);
      float cy = Mathf.lerp(missile.startY, missile.targetY, q) + lift * (1f - q);
      float fade = 1f - f;
      Draw.color(trailColor, fade * fade * 0.55f * strength);
      Fill.circle(cx, cy, bodyWidth * (0.75f * fade + 0.25f));
    }
    Draw.blend();

    Draw.color(Color.gray, 0.22f * strength);
    for (int i = 0; i <= steps; i += 2) {
      float f = i / (float) steps;
      float q = Mathf.lerp(e, 0f, f);
      float cx = Mathf.lerp(missile.startX, missile.targetX, q);
      float cy = Mathf.lerp(missile.startY, missile.targetY, q) + lift * (1f - q);
      Fill.circle(cx, cy, bodyWidth * (1f - 0.3f * f));
    }
    Draw.reset();
  }

  /** 导弹命中时调用（仿 BulletType.hit） */
  public void hit(SMissile missile) {
    if (missile == null || missile.hitCalled)
      return;
    missile.hitCalled = true;

    // 落点对齐到准星：下坠路径的终点就是目标点
    missile.x = missile.targetX;
    missile.y = missile.targetY;

    float radius = Math.max(splashDamageRadius, 16f);

    if (hitEffect != null)
      hitEffect.at(missile.x, missile.y, missile.rotation, trailColor);

    // 起爆：白色爆闪 + 橙色火球 + 贴地尘环
    CFx.orbitalStrikeFlash.at(missile.x, missile.y, radius * 0.55f, trailColor);
    CFx.orbitalImpactRing.at(missile.x, missile.y, radius, trailColor);
    Fx.massiveExplosion.at(missile.x, missile.y, radius / 8f, trailColor);
    Fx.dynamicExplosion.at(missile.x, missile.y, Mathf.clamp(radius / 32f, 0.8f, 3.5f), trailColor);
    Fx.shockwave.at(missile.x, missile.y, 0f, trailColor);
    Drawf.light(missile.x, missile.y, radius * 2.6f, trailColor, 0.9f);

    // 烟柱：朝天空方向缓慢升起，和导弹下落方向相反
    for (int i = 0; i < 5; i++) {
      int idx = i;
      float sx = missile.x + Mathf.range(-radius * 0.18f, radius * 0.18f);
      float sy = missile.y + radius * (0.12f + idx * 0.16f);
      Time.run(idx * 4f, () -> {
        Fx.smoke.at(sx, sy, 0f, Color.gray);
        Fx.smokePuff.at(sx, sy, 0f, Color.gray);
      });
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

  /**
   * 发射一枚导弹（仿 BulletType.create）。
   * (sx, sy) 是**出射点**（调用方取画面左右两侧的炮口），导弹从那里一路下坠砸到 (tx, ty)：
   * 地面轨迹是从侧面射进来的直线，"从高处落到地面"由 draw() 里的高度差表现。
   */
  public SMissile create(Entityc owner, Team team, float sx, float sy, float tx, float ty) {
    float angle = Mathf.angle(tx - sx, ty - sy);
    float dist = Mathf.dst(sx, sy, tx, ty);
    float life = Mathf.clamp(descentTime, 20f, 300f);

    SMissile missile = SMissile.create();
    missile.type = this;
    missile.team = team;
    missile.set(sx, sy);
    missile.startX = sx;
    missile.startY = sy;
    missile.targetX = tx;
    missile.targetY = ty;
    missile.lifetime = life;
    // vel 只作为"平均速度"留档，真正的位移由 update() 的下坠曲线算，不靠积分
    missile.vel.trns(angle, dist / life);
    missile.damage = this.damage;
    missile.shooter = owner;
    missile.owner = owner;
    missile.rotation = angle;
    missile.hitCalled = false;
    missile.smokeTimer = 0f;
    missile.removalReason = null;
    missile.add();

    // 出射点火：炮口那一小下火光 + 尾烟（画在弹体的实际高度上，和本体对得上）
    float vy = sy + liftHeight;
    if (shootEffect != null)
      shootEffect.at(sx, vy, angle, trailColor);
    if (smokeEffect != null)
      smokeEffect.at(sx, vy, angle, Color.gray);
    Fx.rocketSmokeLarge.at(sx, vy, 0.7f, trailColor);
    // 烟朝飞行的反方向（=出射方向）拖出去
    Fx.shootSmokeMissileColor.at(sx, vy, angle, Color.gray);

    return missile;
  }

  /** 每帧更新：沿"出射点 → 落点"的直线做重力式加速推进（地面轨迹），高度由 draw() 表现。 */
  public void update(SMissile missile) {
    float life = Math.max(missile.lifetime, 1f);
    float p = Mathf.clamp(missile.time / life, 0f, 1f);
    float e = fallCurve(p);

    missile.x = missile.startX + (missile.targetX - missile.startX) * e;
    missile.y = missile.startY + (missile.targetY - missile.startY) * e;
    missile.rotation = Mathf.angle(missile.targetX - missile.startX, missile.targetY - missile.startY);

    // 弹体的实际画面位置 = 地面轨迹 + 当前高度；尾迹/烟都按这个位置撒，才和本体对得上
    float lift = liftHeight * (1f - e);
    float vx = missile.x, vy = missile.y + lift;

    // 下坠尾迹：越接近地面越粗越亮
    if (trailEffect != null) {
      // missileTrail 用 rotation 参数作为半径
      trailEffect.at(vx, vy, width * (0.5f + 0.9f * e), trailColor);
    }
    missile.smokeTimer += Time.delta;
    if (smokeInterval > 0f && missile.smokeTimer >= smokeInterval) {
      missile.smokeTimer = 0f;
      // 跟着弹体补一小撮烟，凑成一条斜着落下来的烟柱
      Fx.missileTrailSmokeSmall.at(vx, vy, 1f, Color.gray);
      if (smokeEffect != null && p > 0.35f)
        smokeEffect.at(vx, vy, missile.rotation, Color.gray);
    }

    // 落地判定用"这一帧回调之后"的时间：生成的 update 会在回调后再推进 time，
    // 所以按真正的落地时刻把弹体对齐到准星，不会因为帧率不同而落偏。
    float pNext = Mathf.clamp((missile.time + Time.delta) / life, 0f, 1f);
    if (pNext >= 1f) {
      missile.removalReason = "reached_target";
      hit(missile);
      missile.remove();
      return;
    }

    // 高空不做碰撞：导弹是从天上砸下来的，半路的山/楼不该把它拦下来（拦下来就落偏了）。
    // 只有最后一小段（离准星 8 单位内）才做接触判定，等价于"砸到准星上"。
    float toTarget = Mathf.dst(missile.x, missile.y, missile.targetX, missile.targetY);
    if (toTarget > 8f) {
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
        descentTime = 42f;
        liftHeight = 120f;
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
        hitShake = 7f;
        despawnShake = 4f;
      }
    };

    heavy = new SatelliteMissile("heavy-missile") {
      {
        sprite = "missile-large";
        speed = 10f;
        descentTime = 48f;
        liftHeight = 150f;
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
        hitShake = 10f;
        despawnShake = 6f;
      }
    };

    cluster = new SatelliteMissile("cluster-missile") {
      {
        sprite = "missile-large";
        speed = 4f;
        descentTime = 36f;
        liftHeight = 100f;
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
        hitShake = 5f;
        despawnShake = 3f;
      }
    };

    // 必须在所有字段赋值完成后初始化 stats，否则匿名子类字段会覆盖默认值之前就被读取。
    for (SatelliteMissile missile : map.values()) {
      missile.initStats();
    }

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
    /** 高空入场点（发射位置），下坠路径从这里开始。 */
    public float startX, startY;
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
