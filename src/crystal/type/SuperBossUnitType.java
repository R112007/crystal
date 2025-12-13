package crystal.type;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class SuperBossUnitType extends UnitType {
  public Seq<Ability> abStage2 = new Seq<>(), abStage3 = new Seq<>(), abStage4 = new Seq<>();

  public SuperBossUnitType(String name) {
    super(name);
  }

  @Override
  public void display(Unit unit, Table table) {
    super.display(unit, table);

    table.table(bars -> {

    });
  }

  public static class WeaponStage1 extends Weapon {
    public WeaponStage1(String name) {
      this.name = name;
    }

    public WeaponStage1() {
      this("");
    }
  }

  public static class WeaponStage2 extends Weapon {
    public WeaponStage2(String name) {
      this.name = name;
    }

    public WeaponStage2() {
      this("");
    }
  }

  public static class WeaponStage3 extends Weapon {
    public WeaponStage3(String name) {
      this.name = name;
    }

    public WeaponStage3() {
      this("");
    }
  }

  public static class WeaponStage4 extends Weapon {
    public WeaponStage4(String name) {
      this.name = name;
    }

    public WeaponStage4() {
      this("");
    }
  }
}
