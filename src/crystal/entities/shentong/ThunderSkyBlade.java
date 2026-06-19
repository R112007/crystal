package crystal.entities.shentong;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.Magicc;
import crystal.graphics.CPal;
import crystal.type.MagicUnitType;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Bar;

public class ThunderSkyBlade extends ShenTong {
  // ========== 可配置基础参数 ==========
  public float baseDamage;
  public float cooldown; // 冷却时间(帧)
  public float magicCost; // 法力消耗
  public float bladeLength; // 刀身长度(像素)
  public float bladeWidth; // 刀身宽度(像素)
  public float warningTime; // 预警前摇(帧)
  public float residualTime; // 雷域持续(帧)
  public int chainCount; // 闪电链最大跳转数
  public float chainRange; // 闪电链跳转范围(像素)

  // ========== 运行时状态 ==========
  private float cooldownTimer;
  private boolean casting;
  private float castTimer;
  private float targetX;
  private float targetY;

  // ========== 特效定义 ==========
  public final Effect strikeEffect = new Effect(30f, e -> {
    Draw.z(Layer.effect + 1f);

    // 刀身劈砍光效
    e.scaled(15f, i -> {
      Draw.color(CPal.light_blue1, Color.white, i.fin());
      Lines.stroke(bladeWidth * 0.6f * i.fout(Interp.pow2Out));
      Lines.line(e.x, e.y - bladeLength, e.x, e.y);
      Drawf.light(e.x, e.y - bladeLength / 2f, bladeWidth * 3f * i.fout(), CPal.light_blue1, 0.6f * i.fout());
    });

    // 落地冲击扩散圈
    e.scaled(20f, i -> {
      Draw.color(CPal.light_blue1, i.fout());
      Lines.stroke(3f * i.fout());
      Lines.circle(e.x, e.y, 40f * i.fin(Interp.pow2Out));
      Fill.circle(e.x, e.y, 8f * i.fout());
    });

    Draw.reset();
  }) {
    {
      clip = 500f;
    }
  };

  public final Effect residualEffect = new Effect(180f, e -> {
    // 每0.5秒触发一次小闪电
    if (e.time % 30f < 1f) {
      for (int i = 0; i < 3; i++) {
        float angle = Mathf.random(360f);
        float len = Mathf.random(bladeWidth * 0.8f);
        if (e.data instanceof Magicc caster) {
          Lightning.create(caster.team(), CPal.light_blue1, (Float) e.data, e.x, e.y, angle, (int) (len / 8f));
        }
      }
    }
  }) {
    {
      clip = 200f;
    }
  };

  // ========== 构造函数 ==========
  public ThunderSkyBlade(float baseDamage, float cooldown, float magicCost, float bladeLength, float bladeWidth) {
    this.baseDamage = baseDamage;
    this.cooldown = cooldown;
    this.magicCost = magicCost;
    this.bladeLength = bladeLength;
    this.bladeWidth = bladeWidth;
    this.warningTime = 30f;
    this.residualTime = 180f;
    this.chainCount = 3;
    this.chainRange = 24f;
    this.id = 1;
  }

  public ThunderSkyBlade() {
    this(200f, 900f, 80f, 96f, 24f);
  }

  // ========== 抽象方法实现 ==========
  @Override
  public String name() {
    return "ThunderSkyBlade";
  }

  @Override
  public String description() {
    return Core.bundle.get("shengtong.thunderskyblade.description");
  }

  @Override
  public XiuWei limitMinXiuWei() {
    return XiuWei.shen;
  }

  @Override
  public void init(MagicUnitType type) {
    super.init(type);
    shengTongMap.put(this.id, this);
  }

  // ========== 核心更新逻辑 ==========
  @Override
  public void update(Magicc magic) {
    if (magic.xiuWei().ordinal() < limitMinXiuWei().ordinal())
      return;

    if (cooldownTimer > 0) {
      cooldownTimer -= Time.delta;
    }

    if (casting) {
      castTimer += Time.delta;
      if (castTimer >= warningTime) {
        executeStrike(magic);
        casting = false;
        castTimer = 0f;
        cooldownTimer = cooldown;
      }
    }
  }

  // ========== 释放入口 ==========
  public boolean cast(Magicc caster, float targetX, float targetY) {
    if (caster.xiuWei().ordinal() < limitMinXiuWei().ordinal()
        || cooldownTimer > 0
        || caster.magicPower() < magicCost) {
      return false;
    }

    this.targetX = targetX;
    this.targetY = targetY;
    casting = true;
    castTimer = 0f;

    caster.consumeMagic(magicCost);
    Sounds.shootLancer.at(targetX, targetY, 1f, 0.7f);

    return true;
  }

  // ========== 核心劈砍逻辑 ==========
  private void executeStrike(Magicc caster) {
    float damageMult = caster.xiuWeiMultiplier(caster.xiuWei());
    float realDamage = baseDamage * damageMult;

    strikeEffect.at(targetX, targetY, 0, caster.team().color, caster);
    Sounds.explosion.at(targetX, targetY, 1.2f, 0.9f);
    Effect.shake(4f, 20f, targetX, targetY);

    // 1. 主线劈砍伤害
    Units.nearbyEnemies(caster.team(), targetX, targetY - bladeLength / 2f, bladeLength, unit -> {
      if (Math.abs(unit.x - targetX) <= bladeWidth / 2f && !unit.dead) {
        unit.damage(realDamage * 3f);
      }
    });

    // 2. 落地范围冲击伤害
    Units.nearbyEnemies(caster.team(), targetX, targetY, 40f, unit -> {
      if (!unit.dead) {
        unit.damage(realDamage * 1.5f);
      }
    });

    // 3. 残留雷域
    createResidualField(caster, realDamage);

    // 4. 闪电链
    applyLightningChain(caster, realDamage);

    // 5. 减益与斩杀
    applyDebuffs(caster, realDamage);
  }

  private void createResidualField(Magicc caster, float realDamage) {
    int steps = (int) (bladeLength / 8f);
    float tickDamage = realDamage * 0.15f;

    for (int i = 0; i <= steps; i++) {
      float y = targetY - i * 8f;
      residualEffect.at(targetX, y, 0, caster.team().color, caster);

      final float fy = y;
      final float finalTickDamage = tickDamage;
      Time.run(i * 2f, () -> {
        Units.nearbyEnemies(caster.team(), targetX, fy, bladeWidth, unit -> {
          if (!unit.dead) {
            unit.damage(finalTickDamage);
          }
        });
      });
    }
  }

  // ========== 【修复】闪电链lambda final变量约束 ==========
  private void applyLightningChain(Magicc caster, float realDamage) {
    Units.nearbyEnemies(caster.team(), targetX, targetY - bladeLength / 2f, bladeLength, unit -> {
      if (unit.dead || Math.abs(unit.x - targetX) > bladeWidth / 2f)
        return;

      Unit current = unit;
      for (int i = 0; i < chainCount; i++) {
        // 每次循环创建final临时变量，满足lambda语法约束
        final Unit finalCurrent = current;
        Unit next = Units.closestEnemy(caster.team(), finalCurrent.x, finalCurrent.y, chainRange,
            u -> u != finalCurrent && !u.dead);
        if (next == null)
          break;

        float angle = Angles.angle(finalCurrent.x, finalCurrent.y, next.x, next.y);
        float dist = finalCurrent.dst(next);
        Lightning.create(caster.team(), CPal.magicColor1, realDamage * 0.5f,
            finalCurrent.x, finalCurrent.y, angle, (int) (dist / 8f));

        current = next;
      }
    });
  }

  private void applyDebuffs(Magicc caster, float realDamage) {
    Units.nearbyEnemies(caster.team(), targetX, targetY - bladeLength / 2f, bladeLength, unit -> {
      if (unit.dead || Math.abs(unit.x - targetX) > bladeWidth / 2f)
        return;

      // 减速2秒（final变量满足lambda约束）
      final float originalSpeed = unit.speedMultiplier();
      unit.speedMultiplier(originalSpeed * 0.7f);
      Time.run(120f, () -> unit.speedMultiplier(originalSpeed));

      // 建筑禁用武器1秒
      if (unit.isBuilding()) {
        unit.disarmed = true;
        Time.run(60f, () -> unit.disarmed = false);
      }

      // 斩杀效果
      if (unit.health < unit.maxHealth * 0.2f) {
        unit.damage(realDamage);
      }
    });
  }

  // ========== UI属性展示 ==========
  @Override
  public void setStat(MagicUnitType magic, Table table) {
    if (magic.xiuWei.ordinal() < limitMinXiuWei().ordinal()) {
      table.add(Core.bundle.get("toolowxiuwei"));
      return;
    }
    float mult = XiuWei.xiuWeiMultiplier(magic.xiuWei);
    table.add(Core.bundle.get("thunderskyblade.basedamage") + ": " + (int) (baseDamage * mult)).row();
    table.add(Core.bundle.get("thunderskyblade.cooldown") + ": " + (int) (cooldown / 60) + "秒").row();
    table.add(Core.bundle.get("thunderskyblade.magiccost") + ": " + (int) magicCost).row();
    table.add(Core.bundle.get("thunderskyblade.bladelength") + ": " + (int) (bladeLength / 8f) + "格").row();
    table.add(Core.bundle.get("thunderskyblade.residualtime") + ": " + (int) (residualTime / 60) + "秒").row();
    table.add(Core.bundle.get("thunderskyblade.chaincount") + ": " + chainCount).row();
  }

  // ========== 状态栏冷却条 ==========
  @Override
  public void setBar(Magicc magic, Table table) {
    if (magic.xiuWei().ordinal() < limitMinXiuWei().ordinal())
      return;
    table.add(
        new Bar(Core.bundle.get("shengtong.thunderskyblade"), CPal.light_blue1, () -> 1f - cooldownTimer / cooldown))
        .growX().row();
  }

  // ========== 数据序列化 ==========
  @Override
  public void write(Writes writes) {
    writes.f(baseDamage);
    writes.f(cooldown);
    writes.f(magicCost);
    writes.f(bladeLength);
    writes.f(bladeWidth);
    writes.f(warningTime);
    writes.f(residualTime);
    writes.i(chainCount);
    writes.f(chainRange);
    writes.f(cooldownTimer);
    writes.bool(casting);
    writes.f(castTimer);
    writes.f(targetX);
    writes.f(targetY);
  }

  @Override
  public void read(Reads reads) {
    baseDamage = reads.f();
    cooldown = reads.f();
    magicCost = reads.f();
    bladeLength = reads.f();
    bladeWidth = reads.f();
    warningTime = reads.f();
    residualTime = reads.f();
    chainCount = reads.i();
    chainRange = reads.f();
    cooldownTimer = reads.f();
    casting = reads.bool();
    castTimer = reads.f();
    targetX = reads.f();
    targetY = reads.f();
  }

  // ========== 原型克隆 ==========
  @Override
  public ThunderSkyBlade create() {
    try {
      ThunderSkyBlade clone = (ThunderSkyBlade) super.clone();
      clone.cooldownTimer = 0f;
      clone.casting = false;
      clone.castTimer = 0f;
      clone.targetX = 0f;
      clone.targetY = 0f;
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("java sucks", e);
    }
  }
}
