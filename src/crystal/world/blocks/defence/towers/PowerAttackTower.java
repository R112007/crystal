package crystal.world.blocks.defence.towers;

import crystal.graphics.CPal;

public class PowerAttackTower extends Tower {

  public PowerAttackTower(String name) {
    super(name);
    hasPower = true;
    consumePower(1f);
    beamColor = CPal.blue1;
  }

  public class PowerAttackTowerBuild extends TowerBuild {
    @Override
    public float cdelta() {
      return this.edelta();
    }
  }
}
