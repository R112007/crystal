package crystal.entities.comp;

import arc.struct.Seq;
import crystal.type.Stage;
import crystal.type.SuperBossUnitType;
import crystal.type.SuperBossUnitType.WeaponStage1;
import crystal.type.SuperBossUnitType.WeaponStage2;
import crystal.type.SuperBossUnitType.WeaponStage3;
import crystal.type.SuperBossUnitType.WeaponStage4;
import ent.anno.Annotations.EntityComponent;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unitc;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

@EntityComponent
public abstract class SuperBossComp implements Unitc {
  public Stage stage;
  transient Ability[] abStage2 = {};
  transient Ability[] abStage3 = {};
  transient Ability[] abStage4 = {};

  @Override
  public void update() {
    updateStage();
    updateAbility();
  }

  @Override
  public void destroy() {
    for (Ability a : abStage4) {
      a.death(self());
    }
  }

  @Override
  public void setType(UnitType type) {
    if (type instanceof SuperBossUnitType boss) {
      stage = Stage.stage1;
      if (abStage2.length != boss.abStage2.size) {
        abStage2 = new Ability[boss.abStage2.size];
        for (int i = 0; i < boss.abStage2.size; i++) {
          abStage2[i] = boss.abStage2.get(i).copy();
        }
      }
      if (abStage3.length != boss.abStage3.size) {
        abStage3 = new Ability[boss.abStage3.size];
        for (int i = 0; i < boss.abStage3.size; i++) {
          abStage3[i] = boss.abStage2.get(i).copy();
        }
      }
      if (abStage4.length != boss.abStage4.size) {
        abStage4 = new Ability[boss.abStage4.size];
        for (int i = 0; i < boss.abStage4.size; i++) {
          abStage4[i] = boss.abStage4.get(i).copy();
        }
      }
    } else {
      throw new IllegalArgumentException("SuperBossUnit must use SuperBossUnitType…");
    }
  }

  public boolean belong1(Weapon weapon) {
    if (weapon instanceof WeaponStage1) {
      return true;
    } else {
      return false;
    }
  }

  public boolean belong2(Weapon weapon) {
    if (weapon instanceof WeaponStage2) {
      return true;
    } else {
      return false;
    }
  }

  public boolean belong3(Weapon weapon) {
    if (weapon instanceof WeaponStage3) {
      return true;
    } else {
      return false;
    }
  }

  public boolean belong4(Weapon weapon) {
    if (weapon instanceof WeaponStage4) {
      return true;
    } else {
      return false;
    }
  }

  public boolean stage1() {
    return stage == Stage.stage1;
  }

  public boolean stage2() {
    return health() < maxHealth() * 0.75f && health() >= maxHealth() * 0.5f;
  }

  public boolean stage3() {
    return health() < maxHealth() * 0.5f && health() >= maxHealth() * 0.25f;
  }

  public boolean stage4() {
    return health() < maxHealth() * 0.25;
  }

  public void updateStage() {
    if (stage2()) {
      stage = Stage.stage2;
    } else if (stage3()) {
      stage = Stage.stage3;
    } else if (stage4()) {
      stage = Stage.stage4;
    }
  }

  public void updateAbility() {
    if (stage == Stage.stage1) {
      return;
    }
    if (stage == Stage.stage2) {
      for (Ability a : abStage2) {
        a.update(self());
      }
    } else if (stage == Stage.stage3) {
      for (Ability a : abStage2) {
        a.update(self());
      }
      for (Ability a : abStage3) {
        a.update(self());
      }
    } else if (stage == Stage.stage4) {
      for (Ability a : abStage4) {
        a.update(self());
      }
    }
  }

}
