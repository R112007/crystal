package crystal.world.interfaces;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.meta.Stats;

public interface CrystalInterface {
  public void setCrystal(float crystal, float crystalCapacity);

  public boolean full();

  public void update(Building entity);

  public void write(Writes writes);

  public void read(Reads read, byte revision);

  public void setBars(Block block);

  public void addStats(Stats stats);
}
