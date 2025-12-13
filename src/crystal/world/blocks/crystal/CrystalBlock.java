package crystal.world.blocks.crystal;

import crystal.world.meta.CStat;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.consumers.Consume;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawDefault;

public class CrystalBlock extends Block {
  public float crystal;
  public float crystalCapacity;
  public boolean useConsumerMultiplier = true;
  public boolean hasCrystal;
  public DrawBlock drawer = new DrawDefault();

  public CrystalBlock(String name) {
    super(name);
  }

  @Override
  public void load() {
    super.load();
    drawer.load(this);
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.hasCrystal, this.hasCrystal);
    if (this.hasCrystal) {
      stats.add(CStat.consumeCrystalE, this.crystal);
    }
  }

  public class CrystalBuild extends Building {

    @Override
    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      return super.init(tile, team, shouldAdd, rotation);
    }

    public CrystalBlock block() {
      return ((CrystalBlock) this.block);
    }

    public float efficiencyMultiplier() {
      float val = 1;
      if (!useConsumerMultiplier)
        return val;
      for (Consume consumer : consumers) {
        val *= consumer.efficiencyMultiplier(this);
      }
      return val;
    }

    @Override
    public float efficiencyScale() {
      return super.efficiencyScale() * efficiencyMultiplier();
    }

    @Override
    public float getProgressIncrease(float baseTime) {
      return super.getProgressIncrease(baseTime) * efficiencyMultiplier();
    }
  }
}
