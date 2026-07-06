package crystal.entities.abilities;

import arc.Core;
import arc.func.Boolp;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import crystal.world.meta.CStatValues;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

public class AddWeaponAbility extends Ability {
  public float per;
  public Weapon weapon;
  public boolean added = false;

  public AddWeaponAbility(float per, Weapon weapon) {
    this.per = per;
    this.weapon = weapon;
  }

  @Override
  public void update(Unit unit) {
    if (unit.healthf() <= per && added == false && weapon != null) {
      if (weapon.region == null || !weapon.region.found()) {
        weapon.load();
      }
      Seq<Weapon> weapons = new Seq<>(unit.type.weapons);
      weapons.add(weapon);
      unit.mounts = new WeaponMount[weapons.size];
      for (int i = 0; i < unit.mounts.length; i++) {
        unit.mounts[i] = weapons.get(i).mountType.get(weapons.get(i));
      }
      added = true;
    }
  }

  @Override
  public String localized() {
    return Core.bundle.get("ability.addweaponability");
  }

  @Override
  public void addStats(Table t) {
    super.addStats(t);
    t.add("血量低于" + per * 100 + "%时添加武器").row();
    CStatValues.displayWeapon(weapon, t);
    t.row();
  }
}
