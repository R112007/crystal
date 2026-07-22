package crystal.entities.comp;

import arc.math.Mathf;
import arc.math.WindowedMean;
import arc.struct.Seq;
import arc.util.Time;
import crystal.Crystal;
import crystal.entities.mindustryX.MindustryXUnitc;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.FaShen;
import crystal.gen.Magicc;
import crystal.type.MagicUnitInterface;
import crystal.type.MagicUnitType;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.Replace;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.game.Team;
import mindustry.gen.Healthc;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;

import java.lang.reflect.Method;

@EntityComponent
abstract class MagicComp implements Unitc, Magicc, MindustryXUnitc {
  @Import
  float x, y, health, maxHealth, hitTime;
  @Import
  Team team;
  @Import
  public boolean dead, added;
  @Import
  float shield, shieldAlpha, armor;
  @Import
  UnitType type;
  @Import
  Seq<StatusEntry> statuses;

  public float magicPower;
  public float maxMagicPower;
  public float magicPowerRegen;
  public float magicPowerRegenTime;
  public float timer;
  public XiuWei xiuWei;
  public Seq<ShenTong> shenTongs = new Seq<>();
  public float fashenHealthf;
  public boolean inited = false;
  public int fashenId = -1;
  public transient FaShen cachedFashen = null;

  public static Class<?> HealthChanged;
  public static boolean hasHealthChanged = false;

  private transient float lastHealth = 0f;             // 用于 healthBalance 的上一帧血量
  private transient float lastShield = 0f;  // 用于 healthBalance 的上一帧护盾
  protected transient float lastHealthChanged;
  private transient final WindowedMean healthBalanceMean = new WindowedMean(120); // 滑动窗

  static {
    try {
      HealthChanged = Class.forName("mindustryX.events.HealthChangedEvent");
      hasHealthChanged = true;
    } catch (ClassNotFoundException e) {
      hasHealthChanged = false;
    }
  }

  @Override
  public Seq<StatusEntry> statuses() {
    return statuses;
  }

  @Override
  public float healthBalance() {
    return healthBalanceMean.mean();
  }

  @Override
  public void healthChanged() {
    float var1 = this.lastHealthChanged;
    if (var1 != 0.0F) {
      var1 -= health;
      if (var1 != 0.0F) {
        try {
          Method m = HealthChanged.getMethod("fire", Healthc.class, float.class);
          m.invoke(null, this, var1);
        } catch (Exception e) {
        }
      }
    }

    this.lastHealthChanged = health;
  }

  public FaShen getFaShen() {
    if (fashenId < 0)
      return null;
    if (cachedFashen == null || cachedFashen.dead() || !cachedFashen.isAdded() || Crystal.timer % 60 == 0) {
      cachedFashen = FaTianXiangDi.faShens.getByID(fashenId);
    }
    return cachedFashen;
  }

  @Replace
  public void damage(float amount) {
    if (Float.isNaN(health))
      health = 0.0F;
    FaShen faShen = getFaShen();
    if (faShen != null && faShen.isAdded() && faShen.health > 0) {
      if (faShen.health <= amount * (Mathf.clamp(xiuWei.ordinal() / 4f))) {
        amount -= faShen.health;
      } else {
        float damage = amount * (Mathf.clamp(xiuWei.ordinal() / 4f));
        faShen.damage(damage);
        amount -= damage;
      }
    }
    health -= amount;
    healthChanged();
    hitTime = 1.0F;
    if (health <= 0 && !dead) {
      kill();
    }
  }

  @Override
  public void killed() {
  }

  @Override
  public void clampHealth() {
    health = Math.min(health, maxHealth);
    if (Float.isNaN(health))
      health = 0.0F;
    healthChanged();
  }

  @Override
  public void heal() {
    dead = false;
    health = maxHealth;
    healthChanged();
  }

  @Replace
  public void rawDamage(float amount) {
    boolean hadShields = shield > 1.0E-4F;
    if (Float.isNaN(health))
      health = 0.0F;
    if (hadShields) {
      shieldAlpha = 1.0F;
    }
    float shieldDamage = Math.min(Math.max(shield, 0), amount);
    FaShen faShen = getFaShen();
    if (faShen != null && faShen.isAdded() && faShen.health > 0) {
      if (faShen.health <= amount * (Math.min(xiuWei.ordinal() / 4, 1))) {
        amount -= faShen.health;
      } else {
        float damage = amount * (Math.min(xiuWei.ordinal() / 4f, 1));
        faShen.damage(damage);
        amount -= damage;
      }
    }
    shield -= shieldDamage;
    hitTime = 1.0F;
    amount -= shieldDamage;
    healthChanged();
    if (amount > 0 && type.killable) {
      health -= amount;
      if (health <= 0 && !dead) {
        kill();
      }
      if (hadShields && shield <= 1.0E-4F) {
        Fx.unitShieldBreak.at(x, y, 0, type.shieldColor(self()), this);
      }
    }
  }

  private void updateHealthBalance() {
    float delta = Time.delta;
    if (delta > 0.001f) {
      float rate = (shield - lastShield + (health - lastHealth)) / delta;
      healthBalanceMean.add(rate);
    }
    lastHealth = health;
    lastShield = shield;
  }

  public float xiuWeiMultiplier(XiuWei xiuWei) {
      return switch (xiuWei) {
          case fan -> 1f;
          case shen -> 2f;
          case sheng -> 4f;
          case xian -> 6f;
          case dijun -> 10f;
          default -> 0f;
      };
  }

  @Override
  public void setType(UnitType type) {
    if (type instanceof MagicUnitInterface magic) {
      if (!inited) {
        this.magicPower = magic.magicPower();
        inited = true;
      }
      this.xiuWei = magic.xiuWei();
      this.maxMagicPower = magic.magicPower();
      this.magicPowerRegen = magic.magicPowerRegen();
      this.magicPowerRegenTime = magic.magicPowerRegen();
      if (!this.shenTongs.equals(magic.shenTongs())) {
        this.shenTongs = new Seq<>();
        for (var s : magic.shenTongs()) {
          this.shenTongs.add(s.create());
        }
      }
    } else
      throw new IllegalArgumentException("MagicUnit must implement MagicUnit interface");
  }

  @Override
  public void update() {
    if ((timer += Time.delta) >= magicPowerRegenTime) {
      healMagic(magicPowerRegen);
      timer = 0;
    }
    for (ShenTong shenTong : shenTongs) {
      shenTong.update(this);
    }

    updateHealthBalance();
  }

  @Override
  public void destroy() {
    for (ShenTong shenTong : shenTongs) {
      shenTong.death(this);
    }
  }

  public void consumeMagic(float magic) {
    this.magicPower -= magic;
  }

  public void healMagic(float magic) {
    this.magicPower += magic;
    this.magicPower = Math.min(this.magicPower, this.maxMagicPower);
  }

}
