package crystal.world.blocks.crystal;

import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.interfaces.ConsumeCrystalInterface;
import mindustry.world.blocks.production.Drill;

public class CrystalDrill extends Drill {
  public float consumeCrystal, crystalCapacity;
  public CrystalConsumer crycons = new CrystalConsumer();

  public CrystalDrill(String name) {
    super(name);
  }

  @Override
  public void init() {
    super.init();
    crycons.setCrystal(this.consumeCrystal, this.crystalCapacity);
  }

  @Override
  public void setBars() {
    super.setBars();
    crycons.setBars(this);
  }

  @Override
  public void setStats() {
    super.setStats();
    crycons.addStats(stats);
  }

  public class CrystalDrillBuild extends DrillBuild implements ConsumeCrystalInterface {
    @Override
    public CrystalConsumer getConsumer() {
      return crycons;
    }

    @Override
    public void updateTile() {
      super.updateTile();
      crycons.update(this);
    }

    @Override
    public float getProgressIncrease(float baseTime) {
      return super.getProgressIncrease(baseTime) * crycons.efficiency();
    }

    @Override
    public float efficiencyScale() {
      return super.efficiencyScale() * crycons.efficiency();
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      crycons.write(write);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      crycons.read(read, revision);
    }
  }
}
