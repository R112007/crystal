package crystal.entities.comp;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import crystal.entities.mindustryX.MindustryXAdapter;
import crystal.entities.mindustryX.MindustryXUnitc;
import crystal.type.RammingUnitType;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import static mindustry.Vars.*;

@EntityComponent
abstract class RammingComp implements Unitc, MindustryXUnitc {

    @Import
    float x, y, hitSize, health, maxHealth, shield, shieldAlpha, hitTime;
    @Import
    Vec2 vel;
    @Import
    Team team;
    @Import
    UnitType type;
    @Import
    boolean dead;
    @Import
    Seq<StatusEntry> statuses;

    transient ObjectSet<Integer> hitThisFrame = new ObjectSet<>();
    private transient MindustryXAdapter mindustryXAdapter = new MindustryXAdapter(self());

    @Override
    public void update() {
        hitThisFrame.clear();

        if (dead)
            return;
        if (!(type instanceof RammingUnitType rt))
            return;

        float speed = vel.len();
        if (speed < rt.minRamSpeed)
            return;

        checkBuildingCollision(rt, speed);
        mindustryXAdapter.update(self());
    }

    @Override
    public Seq<StatusEntry> statuses() {
        return statuses;
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
        mindustryXAdapter.fireHealthChanged(self());
    }

    @Override
    public void clampHealth() {
        health = Math.min(health, maxHealth);
        if (Float.isNaN(health))
            health = 0.0F;

        mindustryXAdapter.fireHealthChanged(self());
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
        mindustryXAdapter.fireHealthChanged(self());
    }

    @Override
    public void collision(Hitboxc other, float x, float y) {
        if (dead || !(type instanceof RammingUnitType rt))
            return;
        float speed = vel.len();
        if (speed < rt.minRamSpeed)
            return;

        if (other instanceof Unit u && u.team != team && !u.dead) {
            if (hitThisFrame.add(u.id)) {
                applyRam(u, speed, rt, x, y);
            }
        }
    }

    private void checkBuildingCollision(RammingUnitType rt, float speed) {
        int tx = tileX(), ty = tileY();
        int r = Mathf.ceil(hitSize / tilesize / 2f) + 1;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                Tile tile = world.tile(tx + dx, ty + dy);
                if (tile == null || tile.build == null)
                    continue;
                Building b = tile.build;
                if (b.team == team || b.health <= 0)
                    continue;

                float dst = Mathf.dst(x, y, b.x, b.y);
                float range = (hitSize + b.block.size * tilesize) / 2f;
                if (dst > range)
                    continue;

                if (hitThisFrame.add(b.id)) {
                    applyRamBuilding(b, speed, rt);
                }
            }
        }
    }

    private void applyRam(Unit u, float speed, RammingUnitType rt, float x, float y) {
        float damage = speed * rt.ramDamageMultiplier;
        if (u.health <= damage) {
            u.kill();
            vel.scl(rt.killSlowdown);
        } else {
            if (rt.ignoreArmor)
                u.damagePierce(damage);
            else
                u.damage(damage);
            vel.scl(rt.hitSlowdown);
        }
        rt.ramEffect.at(x, y, rt.ramEffectScale);
    }

    private void applyRamBuilding(Building b, float speed, RammingUnitType rt) {
        float damage = speed * rt.ramDamageMultiplier;
        if (b.health <= damage) {
            b.kill();
            vel.scl(rt.killSlowdown);
        } else {
            if (rt.ignoreArmor)
                b.damagePierce(damage);
            else
                b.damage(damage);
            vel.scl(rt.hitSlowdown);
        }
        rt.ramEffect.at(b.x, b.y, rt.ramEffectScale);
    }
}
