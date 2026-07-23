package crystal.entities.comp;

import crystal.entities.mindustryX.MindustryXAdapter;
import crystal.entities.mindustryX.MindustryXUnitc;
import ent.anno.Annotations;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.SyncLocal;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.units.BuildPlan;
import mindustry.entities.units.StatusEntry;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unitc;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

import static mindustry.Vars.*;

import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import crystal.game.WaitTime;
import crystal.type.BuildShieldUnit;
import crystal.type.BuildShieldUnitType;

@EntityComponent
abstract class ShieldBuilderComp implements Unitc, MindustryXUnitc {
  @Import
  UnitType type;
  @Import
  Team team;
  @Import
  float x, y, rotation, buildSpeedMultiplier, health, maxHealth, hitTime, shield, shieldAlpha;
  @Import
  boolean dead;

  @SyncLocal
  transient float shieldHealth;
  @SyncLocal
  transient float shieldMaxHealth;
  @SyncLocal
  transient float regenRate;
  @SyncLocal
  transient float cooldown, temp;
  @SyncLocal
  transient float widthScale = 0.8f, alpha, angle = 365f;
  @SyncLocal
  transient boolean open = true;
  @Import
  Seq<StatusEntry> statuses = new Seq<>(4);
  protected transient BuildPlan last;
  transient BuildPlan currentPlan;
  public boolean drawArc = true;
  protected static Vec2 paramPos = new Vec2();
  protected static Cons<Bullet> bulletc;
  private transient MindustryXAdapter mindustryXAdapter = new MindustryXAdapter(self());

  @Annotations.Insert(value = "add()", block = Unitc.class)
  public void init() {
    mindustryXAdapter.init(self());
  }

  @Override
  public Seq<StatusEntry> statuses() {
    return statuses;
  }

  @Override
  public void rawDamage(float amount) {
    boolean hadShields = shield > 1.0E-4F;
    if (Float.isNaN(health))
      health = 0.0F;
    if (hadShields) {
      shieldAlpha = 1.0F;
    }
    float shieldDamage = Math.min(Math.max(shield, 0), amount);
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
    healthChanged();
  }

  @Override
  public float healthBalance() {
    return mindustryXAdapter.getHealthBalance();
  }

  @Override
  public void healthChanged() {
    mindustryXAdapter.fireHealthChanged(self());
  }

  @Override
  public void heal() {
    dead = false;
    health = maxHealth;
    healthChanged();
  }

  @Override
  public void clampHealth() {
    health = Math.min(health, maxHealth);
    if (Float.isNaN(health))
      health = 0.0F;

    healthChanged();
  }

  @Override
  public void setType(UnitType type) {
    // 改为判断 BuildShieldUnit 接口，不再强转具体 BuildShieldUnitType
    if (!(type instanceof BuildShieldUnit)) {
      throw new IllegalArgumentException("ShieldBuilderUnit must implement BuildShieldUnit interface");
    }
    BuildShieldUnit shieldUnit = (BuildShieldUnit) type;
    // 从接口getter读取配置
    this.shieldHealth = this.shieldMaxHealth = shieldUnit.shieldHealth();
    this.cooldown = shieldUnit.cooldown();
    this.regenRate = shieldUnit.regenRate();

    bulletc = b -> {
      if (b.team != this.team &&
          b.type.absorbable &&
          currentPlan != null &&
          !(b.within(paramPos, getRange(currentPlan) - getWidth(currentPlan) / 2) &&
              paramPos.within(b.x - b.deltaX, b.y - b.deltaY, getRange(currentPlan) - getWidth(currentPlan) / 2f))
          &&
          (Tmp.v1.set(b).add(b.deltaX, b.deltaY).within(paramPos, getRange(currentPlan) + getWidth(currentPlan) / 2f)
              || b.within(paramPos, getRange(currentPlan) + getWidth(currentPlan) / 2f))
          &&
          (Angles.within(paramPos.angleTo(b), 0f, angle / 2f)
              || Angles.within(paramPos.angleTo(b.x + b.deltaX, b.y + b.deltaY), 0f, angle / 2f))) {
        b.absorb();
        Fx.absorb.at(b);
        if (shieldHealth <= b.damage()) {
          shieldHealth -= cooldown * regenRate;
          Fx.arcShieldBreak.at(paramPos.x, paramPos.y, 0,
              Color.valueOf("ff0000").shiftHue((Time.time * 0.2f) + (1f * (360 / 16))));
        }
        shieldHealth -= b.damage();
        alpha = 1f;
      }
    };
  }

  @Override
  public void update() {
    if (!open) {
      boolean b = WaitTime.waittime(cooldown);
      if (b)
        open = true;
      return;
    }
    if (shieldHealth <= 0.01) {
      open = false;
      return;
    }
    currentPlan = buildPlan();
    if (shieldHealth < shieldMaxHealth)
      shieldHealth += regenRate;

    boolean active = activelyBuilding() && shieldHealth > 0;
    alpha = Math.max(alpha - Time.delta / 10f, 0f);
    if (active && currentPlan != null) {
      widthScale = Mathf.lerpDelta(widthScale, 1f, 0.06f);
      int tileX = currentPlan.x;
      int tileY = currentPlan.y;
      float worldX = tileX * tilesize + currentPlan.block.offset;
      float worldY = tileY * tilesize + currentPlan.block.offset;
      paramPos.set(worldX, worldY);
      float reach = getRange(currentPlan) + getWidth(currentPlan) / 2f;
      Groups.bullet.intersect(paramPos.x - reach, paramPos.y - reach, reach * 2f, reach * 2f, bulletc);
    } else {
      widthScale = Mathf.lerpDelta(widthScale, 0f, 0.11f);
    }
    mindustryXAdapter.update(self());
  }

  @Override
  public void drawBuildingBeam(float px, float py) {
    currentPlan = buildPlan();
    boolean active = activelyBuilding() && shieldHealth > 0;
    if (!active && last == null) {
      return;
    }
    if (currentPlan != null) {
      int tileX = currentPlan.x;
      int tileY = currentPlan.y;
      int worldX = tileX * tilesize;
      int worldY = tileY * tilesize;
      if (widthScale > 0.001f) {
        Draw.z(Layer.shields);
        Draw.color(Color.valueOf("ff0000").shiftHue((Time.time * 0.2f) + (1f * (360f / 16f))), Color.white,
            Mathf.clamp(this.alpha));// 变色
        if (!Vars.renderer.animateShields) {
          Draw.alpha(0.4f);
        }
        if (drawArc) {
          Lines.stroke(currentPlan.block.size * 2 * widthScale);
          Lines.arc(worldX + currentPlan.block.offset, worldY + currentPlan.block.offset, getRange(currentPlan),
              angle / 360f);
        } /*
           * Draw.color(Color.red);
           * Fill.rect(worldX + currentPlan.block.offset,
           * worldY + 2 * currentPlan.block.offset + getRange(currentPlan) + 10f,
           * getRange(currentPlan) * 2, 4f);
           * Draw.color(Color.green);
           * Fill.rect(worldX + currentPlan.block.offset,
           * worldY + 2 * currentPlan.block.offset + getRange(currentPlan) + 10f,
           * getRange(currentPlan) * 2 * (shieldHealth / shieldMaxHealth), 4f);
           */
        Draw.reset();
      }
    } else {
      return;
    }
  }

  public float getWidth(BuildPlan currentPlan) {
    return currentPlan.block.size * 2 * widthScale;
  }

  public float getRange(BuildPlan currentPlan) {
    return currentPlan.block.size * (tilesize + 1);
  }
}
