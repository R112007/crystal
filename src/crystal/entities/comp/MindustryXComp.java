package crystal.entities.comp;

import arc.math.WindowedMean;
import arc.struct.Seq;
import arc.util.Time;
import crystal.entities.mindustryX.MindustryXAdapter;
import crystal.entities.mindustryX.MindustryXUnitc;
import crystal.gen.MindustryXc;
import ent.anno.Annotations;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;

@Annotations.EntityComponent
abstract class MindustryXComp implements Unitc, MindustryXUnitc {
    @Annotations.Import
    Seq<StatusEntry> statuses;
    @Annotations.Import
    float health, shield, shieldAlpha, hitTime, x, y;
    @Annotations.Import
    UnitType type;
    @Annotations.Import
    boolean dead;

    private transient float lastHealth, lastShield, lastHealthChanged;
    private transient WindowedMean healthBalanceMean = new WindowedMean(120);

    @Annotations.MethodPriority(999f)
    @Override
    public void update() {
        if (Time.delta > 0.001f)
           healthBalanceMean.add((shield - lastShield + health - lastHealth) / Time.delta);

        lastHealth = health;
        lastShield = shield;
    }

//    @Annotations.Replace
    @Annotations.Replace
    public void rawDamage(float amount) {
        if (amount > 0) {
            boolean hadShields = shield > 1.0E-4F;
            if (Float.isNaN(health)) health = 0.0F;
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
        }
        healthChanged();
    }

    @Annotations.MethodPriority(999f)
    @Override
    public void heal() {
        healthChanged();
    }

    @Annotations.MethodPriority(999f)
    @Override
    public void clampHealth() {
        healthChanged();
    }

    @Annotations.Replace
    @Override
    public Seq<StatusEntry> statuses() {
        return statuses;
    }

    @Annotations.MethodPriority(999f)
    @Override
    public void add() {
        lastHealth = lastHealthChanged = health;
        lastShield = shield;
    }

    @Override
    public float healthBalance() {
        return healthBalanceMean.mean();
    }

    @Override
    public void healthChanged() {
        MindustryXAdapter.fireHealthChanged(self());
    }

    public float lastHealthChanged() {
        return lastHealthChanged;
    }

    public void lastHealthChanged(float lastHealthChanged) {
        this.lastHealthChanged = lastHealthChanged;
    }
}

