package crystal.entities.abilities;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

public class AddWeaponAbility extends Ability {
  public float range, reload, duration;
  public Weapon weapon;
  public int amount;
  public boolean limited;
  public float timer;
  public static ObjectMap<String, WeaponEntry> map = new ObjectMap<>();

  public AddWeaponAbility(Weapon weapon, float duration, float range, float reload, int amount) {
    this.weapon = weapon;
    this.duration = duration;
    this.range = range;
    this.reload = reload;
    this.amount = amount;
    this.limited = true;
  }

  public AddWeaponAbility(Weapon weapon, float duration, float range, float reload) {
    this.weapon = weapon;
    this.duration = duration;
    this.range = range;
    this.reload = reload;
    this.limited = false;
  }

  @Override
  public String localized() {
    return Core.bundle.get("ability.addweaponability");
  }

  @Override
  public void addStats(Table t) {
    super.addStats(t);
    if (this.weapon.inaccuracy > 0) {
      t.row();
      t.add("[lightgray]" + Stat.inaccuracy.localized() + ": [white]" + (int) this.weapon.inaccuracy + " "
          + StatUnit.degrees.localized());
    }
    if (!this.weapon.alwaysContinuous && reload > 0 && !this.weapon.bullet.killShooter) {
      t.row();
      t.add("[lightgray]" + Stat.reload.localized() + ": " + (this.weapon.mirror ? "2x " : "") + "[white]"
          + Strings.autoFixed(60f / reload * this.weapon.shoot.shots, 2) + " " + StatUnit.perSecond.localized());
    }

  }

  @Override
  public void update(Unit unit) {
    timer += Time.delta;
    if (timer >= reload) {
      if (limited) {
        Units.nearby(unit.team, unit.x, unit.y, range, other -> {
          if (haveWeapon(other.id)) {
            map.get(StringKey(other)).set(this.duration);
          } else {
            WeaponEntry entry = new WeaponEntry(this.weapon, this.duration, other.id);
            map.put(StringKey(other), entry);
          }
        });
      } else {
        Units.nearby(unit.team, unit.x, unit.y, range, other -> {
          if (haveWeapon(other.id)) {
          } else {
            addWeapon(this.weapon, other);
          }
        });
      }
    }
  }

  public void addWeapon(Weapon weapon, Unit unit) {
    WeaponMount[] origin = unit.mounts();
    Seq<WeaponMount> originSeq = new Seq<>();
    originSeq.add(origin);
    originSeq.add(new WeaponMount(weapon) {
      {
        this.reload = 30f;
      }
    });
    unit.mounts(originSeq.toArray());
  }

  public String StringKey(Unit unit) {
    return "" + unit.id + unit.type + unit.type.name + this.weapon.name;
  }

  public Unit getUnitByID(int id) {
    return Groups.unit.getByID(id);
  }

  public boolean haveWeapon(int id) {
    Unit unit = getUnitByID(id);
    for (var w : unit.mounts()) {
      if (w.weapon.name.equals(this.weapon.name))
        return true;
    }
    return false;
  }

  public class WeaponEntry {
    public float duration;
    public Weapon weapon;
    public int id;
    public Runnable update = () -> {
      this.duration -= Time.delta;
      if (this.duration <= 0) {
        removeWeapon(id);
      }
    };

    public WeaponEntry(Weapon weapon, float duration, int id) {
      this.weapon = weapon;
      this.duration = duration;
      this.id = id;
      addWeapon(weapon, getUnitByID(id));
      Events.run(Trigger.update, update);
    }

    public void removeWeapon(int id) {
      Unit unit = Groups.unit.getByID(id);
      WeaponMount[] origin = unit.mounts();
      Seq<WeaponMount> originSeq = new Seq<>();
      originSeq.add(origin);
      int index = -1;
      for (int i = 0; i < originSeq.size; i++) {
        if (originSeq.get(i).weapon.name.equals(this.weapon.name)) {
          index = i;
        }
      }
      if (index > -1) {
        originSeq.remove(index);
        unit.mounts(originSeq.toArray());
        map.remove(StringKey(getUnitByID(id)));
        Events.remove(Trigger.update.getClass(), e -> update.run());
      } else {
        throw new IllegalArgumentException("try to delete a weapon never added " + this.weapon.name);
      }
    }

    public void set(float duration) {
      this.duration = Math.max(this.duration, duration);
    }
  }
}
