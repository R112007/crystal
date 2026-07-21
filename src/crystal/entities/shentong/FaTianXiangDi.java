package crystal.entities.shentong;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.entities.effect.MultiEffect;
import crystal.entities.effect.SeqEffect;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.FaShen;
import crystal.gen.Magicc;
import crystal.graphics.CPal;
import crystal.type.MagicUnitType;
import crystal.util.DLog;
import mindustry.entities.Effect;
import mindustry.entities.EntityGroup;
import mindustry.entities.Effect.EffectContainer;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.ExplosionEffect;
import mindustry.entities.effect.ParticleEffect;
import mindustry.entities.effect.WaveEffect;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.type.Weapon;
import mindustry.ui.Bar;

public class FaTianXiangDi extends ShenTong {
  public static EntityGroup<FaShen> faShens = new EntityGroup<>(FaShen.class, true, true);
  public float baseMultiplier, baseSizeMultiplier, healthf;
  public int fashenId = -1;
  public float regenAmount, restoreTime, timer;
  public boolean faShenisBroken;
  public FaShen fashen;
  public float createMagicCost = 300f;
  public boolean isCreating = false;
  public float createTimer = 0f;

  public float spawnAnimTime;
  public float spawnAnimDuration = 100f;
  public float targetSizeMultiplier;

  public final Effect summonEffect = new Effect(100f, e -> {
    // 从data获取施法单位，做空值保护
    Magicc magic = e.data();
    if (magic == null)
      return;

    // 复刻原逻辑的动态参数计算
    float baseSize = magic.hitSize() * this.getSizeMultiplier(magic);
    Color teamColor = e.color;
    EffectContainer inner = e.inner();

    // ==================== 阶段1：0-25帧 原首个MultiEffect ====================
    if (e.time <= 25f) {
      inner.set(e.id, e.color, e.time, 25f, e.rotation, e.x, e.y, e.data);
      float fin = inner.fin(Interp.pow2Out);

      // 1. 扩张冲击波圈
      Draw.color(CPal.magicColor1, teamColor, fin);
      Lines.stroke(4f * inner.fout());
      Lines.circle(inner.x, inner.y, baseSize * (0.8f + fin * 2.2f));
      Drawf.light(inner.x, inner.y, baseSize * 3f * fin, CPal.magicColor1, 0.4f * inner.fout());

      // 2. 锥形粒子效果
      int particles = Math.max(3, (int) (baseSize / 4f));
      float length = baseSize * 1.5f;
      float cone = 90f;
      float rad = Interp.pow2Out.apply(baseSize * 0.15f, 0f, fin) * 2;
      Rand rand = new Rand(inner.id);
      TextureRegion tex = Core.atlas.find("circle");

      Draw.color(CPal.light_blue1, Color.white, fin);
      for (int p = 0; p < particles; p++) {
        float l = length * fin;
        Vec2 rv = new Vec2().trns(inner.rotation + rand.range(cone), rand.random(l));
        float px = inner.x + rv.x, py = inner.y + rv.y;
        Draw.rect(tex, px, py, rad, rad / tex.ratio());
        Drawf.light(px, py, rad * 2f, CPal.light_blue1, 0.6f * Draw.getColorAlpha());
      }
      Draw.reset();
    }

    // ==================== 阶段2：25-60帧 原第二个MultiEffect ====================
    if (e.time > 25f && e.time <= 60f) {
      inner.set(e.id + 1, e.color, e.time - 25f, 35f, e.rotation, e.x, e.y, e.data);
      float fin = inner.fin(Interp.pow3In);
      float foutPow2 = inner.fout(Interp.pow2Out);

      // 1. 外层收缩冲击波
      Draw.color(CPal.blue1, teamColor, 0.8f);
      Lines.stroke(6f * foutPow2);
      Lines.circle(inner.x, inner.y, baseSize * (3.5f - fin * 2.5f));

      // 2. 内层收缩冲击波
      Draw.color(CPal.magicColor1, teamColor, 0.6f);
      Lines.stroke(3f * foutPow2);
      Lines.circle(inner.x, inner.y, baseSize * (4.5f - fin * 3.3f));

      // 3. 全向扩散粒子
      int particles = Math.max(8, (int) (baseSize / 2f));
      float length = baseSize * 3f;
      float baseLength = baseSize * 0.5f;
      float cone = 360f;
      float particleFin = inner.fin(Interp.pow2Out);
      float rad = Interp.pow2Out.apply(baseSize * 0.12f, 0f, particleFin) * 2;
      Rand rand = new Rand(inner.id + 2);
      TextureRegion tex = Core.atlas.find("circle");

      Draw.color(CPal.light_blue1, teamColor, particleFin);
      for (int p = 0; p < particles; p++) {
        float l = length * particleFin + baseLength;
        Vec2 rv = new Vec2().trns(inner.rotation + rand.range(cone), rand.random(l));
        float px = inner.x + rv.x, py = inner.y + rv.y;
        Draw.rect(tex, px, py, rad, rad / tex.ratio());
        Drawf.light(px, py, rad * 2f, CPal.light_blue1, 0.6f * Draw.getColorAlpha());
      }
      Draw.reset();
    }

    // ==================== 阶段3：60-85帧 原WaveEffect+ExplosionEffect
    // ====================
    if (e.time > 60f && e.time <= 85f) {
      inner.set(e.id + 3, e.color, e.time - 60f, 25f, e.rotation, e.x, e.y, e.data);
      float fin = inner.fin();
      float finPow2 = inner.fin(Interp.pow2Out);

      // 1. 复刻原WaveEffect扩散冲击波
      Draw.color(teamColor, CPal.magicColor1, finPow2);
      Lines.stroke(Interp.pow2Out.apply(8f, 0f, fin));
      float waveRad = Interp.pow2Out.apply(baseSize * 1f, baseSize * 4f, fin);
      Lines.circle(inner.x, inner.y, waveRad);
      Drawf.light(inner.x, inner.y, waveRad * 2.5f, teamColor, 0.7f * inner.fin(Interp.reverse));

      // 2. 复刻原ExplosionEffect爆炸效果
      // 2.1 爆炸内圈冲击波
      Draw.color(teamColor);
      inner.scaled(20f, w -> {
        Lines.stroke(4f * w.fout());
        Lines.circle(w.x, w.y, 2f + w.fin() * baseSize * 1.2f);
      });

      // 2.2 爆炸烟雾
      Color smokeColor = Color.gray.cpy().a(0.6f);
      Draw.color(smokeColor);
      int smokes = Math.max(4, (int) (baseSize / 3f));
      float smokeRad = baseSize * 1.5f;
      float smokeSize = baseSize * 0.25f;
      float smokeSizeBase = 0.5f;
      Angles.randLenVectors(inner.id, smokes, 2f + smokeRad * inner.finpow(), (x, y) -> {
        Fill.circle(inner.x + x, inner.y + y, inner.fout() * smokeSize + smokeSizeBase);
      });

      // 2.3 爆炸火花
      Draw.color(CPal.magicColor1);
      Lines.stroke(inner.fout() * 1f);
      int sparks = Math.max(6, (int) (baseSize / 2f));
      float sparkRad = baseSize * 1.8f;
      float sparkLen = baseSize * 0.3f;
      Angles.randLenVectors(inner.id + 4, sparks, 1f + sparkRad * inner.finpow(), (x, y) -> {
        Lines.lineAngle(inner.x + x, inner.y + y, Mathf.angle(x, y), 1f + inner.fout() * sparkLen);
        Drawf.light(inner.x + x, inner.y + y, inner.fout() * sparkLen * 4f, CPal.magicColor1, 0.7f);
      });
      Draw.reset();
    }

    // ==================== 阶段4：85-100帧 原收尾MultiEffect ====================
    if (e.time > 85f && e.time <= 100f) {
      inner.set(e.id + 5, e.color, e.time - 85f, 15f, e.rotation, e.x, e.y, e.data);
      float fin = inner.fin(Interp.linear);
      float fout = inner.fout();

      // 1. 收尾扩散圈
      Draw.color(teamColor, Color.white, fin);
      Lines.stroke(2f * fout);
      Lines.circle(inner.x, inner.y, baseSize * (1.2f + fin * 0.8f));
      Drawf.light(inner.x, inner.y, baseSize * 2f * fout, teamColor, 0.3f * fout);

      // 2. 旋转收尾粒子
      int particles = Math.max(4, (int) (baseSize / 3f));
      float length = baseSize * 1f;
      float baseLength = baseSize * 0.8f;
      float cone = 360f;
      float spin = 40f;
      float rad = Interp.linear.apply(baseSize * 0.1f, 0f, fin) * 2;
      Rand rand = new Rand(inner.id + 6);
      TextureRegion tex = Core.atlas.find("circle");

      Draw.color(CPal.light_blue1, teamColor, fin);
      for (int p = 0; p < particles; p++) {
        float l = length * fin + baseLength;
        Vec2 rv = new Vec2().trns(inner.rotation + rand.range(cone), rand.random(l));
        float px = inner.x + rv.x, py = inner.y + rv.y;
        Draw.rect(tex, px, py, rad, rad / tex.ratio(), inner.rotation + inner.time * spin);
        Drawf.light(px, py, rad * 2f, CPal.light_blue1, 0.6f * Draw.getColorAlpha());
      }
      Draw.reset();
    }
  }) {
    {
      // 初始化裁剪范围，覆盖所有可能的尺寸需求
      clip = 1000f;
    }
  };
  public Effect breakFaShenEffect = new Effect(200f, 800f, e -> {
    FaShen fashen = e.data();
    if (fashen == null)
      return;

    float baseSize = fashen.hitSize();
    Color teamColor = e.color;
    float rot = e.rotation;
    e.scaled(25f, i -> {
      Draw.color(Color.white, teamColor, i.fin());
      Fill.circle(e.x, e.y, baseSize * 0.8f * i.fout());
      Lines.stroke(12f * i.fout());
      Lines.circle(e.x, e.y, baseSize * 3f * i.fout());
    });
    e.scaled(60f, i -> {
      float fin = i.fin(Interp.pow2Out);
      Lines.stroke(8f * i.fout());
      Draw.color(teamColor, CPal.magicColor1, fin);
      Lines.circle(e.x, e.y, baseSize * 0.5f + baseSize * 2.5f * fin);
      Lines.stroke(4f * i.fout());
      Draw.color(CPal.light_blue1, Color.white, fin);
      Lines.circle(e.x, e.y, baseSize * 0.8f + baseSize * 3f * fin);
    });

    e.scaled(90f, i -> {
      float fin = i.fin();
      float fout = i.fout(Interp.pow2Out);
      TextureRegion region = Core.atlas.find("circle");
      Rand rand = new Rand(e.id);
      for (int j = 0; j < 12; j++) {
        float angle = rot + rand.range(180f);
        float distance = baseSize * 2.2f * fin;
        float px = e.x + Angles.trnsx(angle, distance);
        float py = e.y + Angles.trnsy(angle, distance);
        float size = baseSize * 0.35f * fout;
        float spin = (rand.random(360f) + i.time * 2.5f) * Mathf.sign(rand.range(1f));
        Draw.color(teamColor, CPal.light_blue1, fin);
        Draw.alpha(fout);
        Draw.rect(region, px, py, size, size, spin);
      }
    });
    e.scaled(100f, i -> {
      float fin = i.fin(Interp.pow2Out);
      Rand rand = new Rand(e.id + 1);
      Draw.color(CPal.magicColor1, CPal.light_blue1, fin);
      for (int j = 0; j < 20; j++) {
        float angle = 90f + rand.range(60f);
        float distance = baseSize * 1.8f * fin;
        float px = e.x + Angles.trnsx(angle, distance);
        float py = e.y + Angles.trnsy(angle, distance);
        float size = (2f + rand.random(3f)) * baseSize / 20f * i.fout();
        Fill.circle(px, py, size);
        Drawf.light(px, py, size * 6f, CPal.light_blue1, 0.4f * i.fout());
      }
    });

    Draw.reset();
  });

  public FaTianXiangDi(float baseMultiplier, float baseSizeMultiplier, float regenAmount, float restoreTime,
      float createMagicCost) {
    this.baseMultiplier = baseMultiplier;
    this.baseSizeMultiplier = baseSizeMultiplier;
    this.regenAmount = regenAmount;
    this.restoreTime = restoreTime;
    this.createMagicCost = createMagicCost;
    id = 0;
    icon = Icon.starSmall;
  }

  public FaTianXiangDi(float baseMultiplier, float baseSizeMultiplier, float regenAmount, float restoreTime) {
    this.baseMultiplier = baseMultiplier;
    this.baseSizeMultiplier = baseSizeMultiplier;
    this.regenAmount = regenAmount;
    this.restoreTime = restoreTime;
    this.createMagicCost = 300;
    id = 0;
    icon = Icon.starSmall;
  }

  @Override
  public void setStat(MagicUnitType magic, Table table) {
    if (magic.xiuWei.ordinal() < limitMinXiuWei().ordinal()) {
      table.add(Core.bundle.get("tooLowXiuwei"));
      return;
    }
    table.add(
        Core.bundle.get("fatianxiangdi.multiplier") + ":" + baseMultiplier * XiuWei.xiuWeiMultiplier(magic.xiuWei));
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.sizemultiplier") + ":" + getSizeMultiplier(magic));
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.basemultiplier") + ":" + baseMultiplier);
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.basesizemultiplier") + ":" + baseSizeMultiplier);
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.regenamount") + ":" + regenAmount);
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.restoreTime") + ":" + restoreTime / 60);
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.healthf") + ":"
        + magic.health * baseMultiplier * XiuWei.xiuWeiMultiplier(magic.xiuWei));
    table.row();
    table.add(Core.bundle.get("fatianxiangdi.specialArmorMultiplier") + ":"
        + 100 * Math.min(magic.xiuWei.ordinal() / 4f, 1) + "%");
    table.row();
  }

  @Override
  public void setBar(Magicc magic, Table bars) {
    bars.add(
        new Bar(Core.bundle.get("fatianxiangdi.healthf"), magic.xiuWei().color, () -> healthf).blink(Color.white))
        .row();
  }

  @Override
  public void update(Magicc magic) {
    if (magic.xiuWei().ordinal() < limitMinXiuWei().ordinal()) {
      return;
    }
    if (isCreating) {
      createTimer -= Time.delta;
      if (createTimer <= 0f) {
        if (!magic.dead() && magic.magicPower() >= createMagicCost) {
          createFaShen(magic); // 创建法身
        }
        isCreating = false;
        createTimer = 0f;
      }
      return;
    }
    if (!faShenisBroken) {
      if (fashenId == -1) {
        if (magic.magicPower() >= createMagicCost) {
          isCreating = true;
          createTimer = 80f;
          magic.consumeMagic(createMagicCost); // 扣除法力
          summonEffect.at(magic.x(), magic.y(), 0, magic.team().color, magic);
          return;
        }
        return; // 法力不足直接返回，不创建
      }
      if (fashen == null) {
        fashen = faShens.getByID(fashenId);
        if (fashen == null)
          return;
      }
      fashen.set(magic);
      healthf = fashen.healthf();
      fashen.rotation = magic.rotation();
      if (spawnAnimTime > 0f) {
        spawnAnimTime -= Time.delta;
        float progress = Mathf.clamp(1f - (spawnAnimTime / spawnAnimDuration), 0f, 1f);
        float currentSize = Interp.pow2Out.apply(0.15f, targetSizeMultiplier, progress);
        fashen.sizeMultiplier = currentSize;
        fashen.sizeScl(currentSize);
        fashen.refreshHitSize();
        if (spawnAnimTime <= 0f) {
          fashen.sizeMultiplier = targetSizeMultiplier;
          fashen.refreshHitSize();
          setWeaponShootPos(getSizeMultiplier(magic));
          fashen.sizeScl(1f);
        }
        return;
      }
      if (magic.isShooting()) {
        fashen.aimX = magic.aimX();
        fashen.aimY = magic.aimY();
        fashen.controlWeapons(magic.isRotate(), magic.isShooting());
        fashen.autoShoot = false;
      } else
        fashen.autoShoot = true;
      if (fashen.magicId() != magic.id()) {
        fashen.magicId(magic.id());
        fashen.getMagicUnit();
      }
      fashen.rotation = magic.rotation();
      if (fashen.mounts.length != magic.type().weapons.size) {
        setWeapon(magic, baseMultiplier * magic.xiuWeiMultiplier(magic.xiuWei()),
            baseSizeMultiplier * (1 + magic.xiuWeiMultiplier(magic.xiuWei()) / 5));
        setWeaponShootPos(getSizeMultiplier(magic));
      }
      if (fashen.dead) {
        breakFaShenEffect.at(fashen.x, fashen.y, fashen.rotation, fashen.team.color, fashen);
        Effect.shake(4f + fashen.hitSize() / 20f, 30f, fashen.x, fashen.y);
        Sounds.explosion.at(fashen.x, fashen.y, 0.8f);
        faShens.remove(fashen);
        faShenisBroken = true;
        fashenId = -1;
        magic.fashenId(-1);
        fashen = null;
        return;
      }
      if (fashen.health < fashen.maxHealth && magic.magicPower() > regenAmount) {
        float realRegen = regenAmount * baseMultiplier * magic.xiuWeiMultiplier(magic.xiuWei());
        fashen.heal(realRegen);
        magic.consumeMagic(fashen.maxHealth - fashen.health >= realRegen ? regenAmount
            : ((fashen.maxHealth - fashen.health) / baseMultiplier) * magic.xiuWeiMultiplier(magic.xiuWei()));
      }
    } else {
      if ((timer += Time.delta) >= restoreTime) {
        faShenisBroken = false;
        timer = 0;
      }
    }
  }

  public float getSizeMultiplier(MagicUnitType magic) {
    return baseSizeMultiplier * (1 + XiuWei.xiuWeiMultiplier(magic.xiuWei) / 5);
  }

  public float getSizeMultiplier(Magicc magic) {
    return baseSizeMultiplier * (1 + magic.xiuWeiMultiplier(magic.xiuWei()) / 5);
  }

  @Override
  public XiuWei limitMinXiuWei() {
    return XiuWei.fan;
  }

  public void setWeaponShootPos(float sizeMultiplier) {
    for (WeaponMount m : fashen.mounts) {
      m.weapon.x *= sizeMultiplier;
      m.weapon.y *= sizeMultiplier;
      // m.weapon.shootX *= sizeMultiplier;
      // m.weapon.shootY *= sizeMultiplier;
    }
    ;
  }

  @Override
  public String name() {
    return Core.bundle.get("shengtong.fatianxiangdi");
  }

  @Override
  public String description() {
    return Core.bundle.get("shengtong.fatianxiangdi.description");
  }

  public void setWeapon(Magicc magic, float multiplier, float sizeMultiplier) {
    setWeapon(magic, multiplier, sizeMultiplier, false);
  }

  public void setWeapon(Magicc magic, float multiplier, float sizeMultiplier, boolean read) {
    Weapon[] hostWeapons = magic.type().weapons.toArray(Weapon.class);
    Weapon[] amplifiedWeapons = new Weapon[hostWeapons.length];
    for (int i = 0; i < hostWeapons.length; i++) {
      Weapon original = hostWeapons[i];
      Weapon copy = original.copy();
      if (copy.bullet != null) {
        BulletType amplifiedBullet = copy.bullet.copy();
        amplifiedBullet.damage *= multiplier;
        amplifiedBullet.lifetime *= Math.min((1 + multiplier / 5), 2f);
        if (amplifiedBullet instanceof BasicBulletType basic) {
          basic.height *= sizeMultiplier;
          basic.width *= sizeMultiplier;
        } else if (amplifiedBullet instanceof ShrapnelBulletType shrap) {
          shrap.length *= multiplier;
          shrap.width *= multiplier;
        } else if (amplifiedBullet instanceof LightningBulletType lightning) {
          lightning.lightningLength *= (int) multiplier;
          lightning.lightningLengthRand *= (int) multiplier;
          lightning.lifetime *= (1 + multiplier / 5);
        } else if (amplifiedBullet instanceof ExplosionBulletType expl) {
          expl.splashDamage *= multiplier;
          expl.splashDamageRadius *= multiplier;
        } else if (amplifiedBullet instanceof RailBulletType rail) {
          rail.length *= multiplier;
        } else if (amplifiedBullet instanceof ContinuousLaserBulletType cl) {
          cl.length *= multiplier;
          cl.width *= multiplier;
        } else if (amplifiedBullet instanceof ContinuousFlameBulletType cf) {
          cf.length *= multiplier;
          cf.width *= multiplier;
        } else if (amplifiedBullet instanceof ArtilleryBulletType basic) {
          basic.height *= sizeMultiplier;
          basic.width *= sizeMultiplier;
        } else if (amplifiedBullet instanceof MissileBulletType basic) {
          basic.height *= sizeMultiplier;
          basic.width *= sizeMultiplier;
        } else if (amplifiedBullet instanceof LaserBulletType laser) {
          laser.length *= multiplier;
          laser.width *= multiplier;
          laser.sideLength *= multiplier;
          laser.sideWidth *= multiplier;
        } else if (amplifiedBullet instanceof LaserBoltBulletType basic) {
          basic.height *= sizeMultiplier;
          basic.width *= sizeMultiplier;
        } else {
          throw new IllegalArgumentException("Unkown bullet type" + amplifiedBullet);
        }
        copy.bullet = amplifiedBullet;
      }
      copy.shootY *= sizeMultiplier;
      copy.shootX *= sizeMultiplier;
      if (read) {
        copy.x *= sizeMultiplier;
        copy.y *= sizeMultiplier;
      }
      amplifiedWeapons[i] = copy;
    }
    fashen.setupWeapons(amplifiedWeapons);
  }

  public void createFaShen(Magicc magic) {
    float multiplier = baseMultiplier * magic.xiuWeiMultiplier(magic.xiuWei());
    float sizeMultiplier = baseSizeMultiplier * (1 + magic.xiuWeiMultiplier(magic.xiuWei()) / 5);
    FaShen fa = FaShen.create();
    fa.set(magic);
    fa.setField(magic.hitSize(), multiplier, sizeMultiplier, magic.id());
    fa.team = magic.team();
    fa.health = magic.health() * multiplier;
    fa.maxHealth = magic.maxHealth() * multiplier;
    this.targetSizeMultiplier = sizeMultiplier;
    this.spawnAnimTime = this.spawnAnimDuration;
    fa.sizeMultiplier = 0f;
    fa.refreshHitSize();
    fa.rotation = magic.rotation();
    fa.add();
    fashenId = fa.id();
    this.fashen = fa;
    setWeapon(magic, multiplier, sizeMultiplier);
    magic.fashenId(fashenId);
  }

  @Override
  public void write(Writes writes) {
    writes.f(baseMultiplier);
    writes.f(baseSizeMultiplier);
    writes.f(regenAmount);
    writes.f(restoreTime);
    writes.f(timer);
    writes.i(fashenId);
    writes.bool(faShenisBroken);
    writes.f(createTimer);
    writes.bool(isCreating);
    writes.f(spawnAnimTime);
    writes.f(spawnAnimDuration);
    writes.f(targetSizeMultiplier);
  }

  @Override
  public void read(Reads read) {
    baseMultiplier = read.f();
    baseSizeMultiplier = read.f();
    regenAmount = read.f();
    restoreTime = read.f();
    timer = read.f();
    fashenId = read.i();
    faShenisBroken = read.bool();
    createTimer = read.f();
    isCreating = read.bool();
    spawnAnimTime = read.f();
    spawnAnimDuration = read.f();
    targetSizeMultiplier = read.f();
  }

  @Override
  public FaTianXiangDi create() {
    try {
      FaTianXiangDi clone = (FaTianXiangDi) super.clone();
      clone.fashen = null;
      clone.fashenId = -1;
      clone.healthf = 0f;
      clone.faShenisBroken = false;
      clone.timer = 0f;
      clone.isCreating = false;
      clone.createTimer = 0f;
      clone.icon = Icon.starSmall;
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("java sucks", e);
    }
  }
}
