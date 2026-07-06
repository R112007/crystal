package crystal.entities.comp;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import crystal.Crystal;
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
import mindustry.game.Team;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;

@EntityComponent
abstract class MagicComp implements Unitc, Magicc {
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
    hitTime = 1.0F;
    if (health <= 0 && !dead) {
      kill();
    }
  }

  @Override
  public void killed() {
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

  public float xiuWeiMultiplier(XiuWei xiuWei) {
    switch (xiuWei) {
      case fan:
        return 1f;
      case shen:
        return 2f;
      case sheng:
        return 4f;
      case xian:
        return 6f;
      case dijun:
        return 10f;
      default:
        return 0f;
    }
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
