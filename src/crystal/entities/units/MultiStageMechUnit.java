package crystal.entities.units;

import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.Crystal;
import crystal.entities.units.UnitEnum.Mode;
import crystal.entities.units.UnitEnum.Stage;
import crystal.type.MultiStageUnitType;
import crystal.type.weapons.StageWeapon;
import crystal.util.DLog;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.MechUnit;
import mindustry.gen.Unit;
import mindustry.io.TypeIO;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

import static mindustry.Vars.*;

public class MultiStageMechUnit extends MechUnit {
  public Seq<Weapon> template = new Seq<>();
  public Stage stage = Stage.stage1;
  public Mode mode;
  public Ability[] abStage2 = {};
  public Ability[] abStage3 = {};
  public Ability[] abStage4 = {};

  @Override
  public int classId() {
    return 51;
  }

  public void updateab2(Unit unit) {
    for (var a : abStage2) {
      a.update(unit);
    }
  }

  public void updateab3(Unit unit) {
    for (var a : abStage3) {
      a.update(unit);
    }
  }

  public void updateab4(Unit unit) {
    for (var a : abStage4) {
      a.update(unit);
    }
  }

  public void updateab(Unit unit) {
    if (stage1()) {
      return;
    }
    if (stage2()) {
      updateab2(unit);
      return;
    }
    switch (mode) {
      case easy:
        if (stage3()) {
          updateab3(unit);
        } else {
          updateab4(unit);
        }
        break;
      case normal:
        if (stage3()) {
          updateab3(unit);
        } else {
          updateab3(unit);
          updateab4(unit);
        }
        break;
      case hard:
        if (stage3()) {
          updateab3(unit);
        } else {
          updateab2(unit);
          updateab3(unit);
          updateab4(unit);
        }
        break;
      default:
        if (stage3()) {
          updateab2(unit);
          updateab3(unit);
        } else {
          updateab2(unit);
          updateab3(unit);
          updateab4(unit);
        }
        break;
    }
  }

  public void deathab(Unit unit) {
    for (var a : abStage2) {
      a.death(unit);
    }
    for (var a : abStage3) {
      a.death(unit);
    }
    for (var a : abStage4) {
      a.death(unit);
    }
  }

  @Override
  public void setType(UnitType type) {
    if (type instanceof MultiStageUnitType boss) {
      this.type = boss;
      this.maxHealth = boss.health;
      this.drag = boss.drag;
      this.armor = boss.armor;
      this.hitSize = boss.hitSize;
      if (mounts().length != type.weapons.size)
        setupWeapons(type);
      if (abilities.length != type.abilities.size) {
        abilities = new Ability[type.abilities.size];
        for (int i = 0; i < type.abilities.size; i++) {
          abilities[i] = type.abilities.get(i).copy();
        }
      }
      if (controller == null)
        controller(type.createController(this));
      mode = boss.mode;
      if (abStage2.length != boss.abStage2.size) {
        abStage2 = new Ability[boss.abStage2.size];
        for (int i = 0; i < boss.abStage2.size; i++) {
          abStage2[i] = boss.abStage2.get(i).copy();
        }
      }
      if (abStage3.length != boss.abStage3.size) {
        abStage3 = new Ability[boss.abStage3.size];
        for (int i = 0; i < boss.abStage3.size; i++) {
          abStage3[i] = boss.abStage3.get(i).copy();
        }
      }
      if (abStage4.length != boss.abStage4.size) {
        abStage4 = new Ability[boss.abStage4.size];
        for (int i = 0; i < boss.abStage4.size; i++) {
          abStage4[i] = boss.abStage4.get(i).copy();
        }
      }
    } else {
      throw new IllegalArgumentException("MultitStageUnit must use MultitStageUnitType……😇");
    }
  }

  @Override
  public void controlWeapons(boolean rotate, boolean shoot) {
    for (WeaponMount mount : mounts) {
      if (mount.weapon instanceof StageWeapon s) {
        switch (stage) {
          case stage1:
            if (s.controllable && (s.weappnStage == 1)) {
              mount.rotate = rotate;
              mount.shoot = shoot;
            }
            break;
          case stage2:
            if (s.controllable && (s.weappnStage == 1 || s.weappnStage == 2)) {
              mount.rotate = rotate;
              mount.shoot = shoot;
            }

            break;
          case stage3:
            if (s.controllable && (s.weappnStage == 1 || s.weappnStage == 2 || s.weappnStage == 3)) {
              mount.rotate = rotate;
              mount.shoot = shoot;
            }

            break;
          case stage4:
            if (s.controllable
                && (s.weappnStage == 1 || s.weappnStage == 2 || s.weappnStage == 3 || s.weappnStage == 4)) {
              mount.rotate = rotate;
              mount.shoot = shoot;
            }

            break;
          default:
            break;
        }
      }
    }
    isRotate = rotate;
    isShooting = shoot;
  }

  @Override
  public void remove() {
    super.remove();
  }

  @Override
  public void update() {
    if (Crystal.timer % 360 == 0) {
      DLog.info("mounts:");
      for (var w : mounts) {
        DLog.info(w);
      }
    }
    super.update();
    updateUpgrade();
    updateab(this);
    if (Crystal.timer % 120 == 0) {
      DLog.info("stage:" + stage);
    }
  }

  public void updateUpgrade() {
    if (stage4() && stage.lowerThan(Stage.stage4)) {
      stage = Stage.stage4;
    } else if (stage3() && stage.lowerThan(Stage.stage3)) {
      stage = Stage.stage3;
    } else if (stage2() && stage.lowerThan(Stage.stage2)) {
      stage = Stage.stage2;
    } else if (stage1() && stage.lowerThan(Stage.stage1)) {
      stage = Stage.stage1;
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

  @Override
  public void write(Writes write) {
    write.i(stage.ordinal());
    TypeIO.writeAbilities(write, this.abStage2);
    TypeIO.writeAbilities(write, this.abStage3);
    TypeIO.writeAbilities(write, this.abStage4);
    super.write(write);
  }

  @Override
  public void writeSync(Writes write) {
    write.i(stage.ordinal());
    TypeIO.writeAbilities(write, this.abStage2);
    TypeIO.writeAbilities(write, this.abStage3);
    TypeIO.writeAbilities(write, this.abStage4);
    super.writeSync(write);
  }

  @Override
  public void read(Reads read) {
    this.stage = Stage.all[read.i()];
    this.abStage2 = TypeIO.readAbilities(read, this.abStage2);
    this.abStage3 = TypeIO.readAbilities(read, this.abStage3);
    this.abStage4 = TypeIO.readAbilities(read, this.abStage4);
    super.read(read);
  }

  @Override
  public void readSync(Reads read) {
    this.stage = Stage.all[read.i()];
    this.abStage2 = TypeIO.readAbilities(read, this.abStage2);
    this.abStage3 = TypeIO.readAbilities(read, this.abStage3);
    this.abStage4 = TypeIO.readAbilities(read, this.abStage4);
    super.readSync(read);
  }

  public static MultiStageMechUnit create() {
    return new MultiStageMechUnit();
  }
}
