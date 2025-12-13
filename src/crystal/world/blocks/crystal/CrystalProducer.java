package crystal.world.blocks.crystal;

import arc.struct.ObjectSet;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.graphics.CPal;
import crystal.world.blocks.crystal.CrystalDrill.CrystalDrillBuild;
import crystal.world.interfaces.CrystalInterface;
import crystal.world.meta.CStat;
import mindustry.gen.Building;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.Stats;

public class CrystalProducer implements CrystalInterface {
  public float produceCrystal, crystalCapacity;
  public float crystalSaver;
  public static final ObjectSet<Class<?>> clazzes = new ObjectSet<>();
  static {
    clazzes.add(CrystalDrillBuild.class);
  }

  @Override
  public void update(Building entity) {

  }

  @Override
  public void setBars(Block block) {
    block.addBar("crystalproduce", entity -> new Bar("crystal.produce", CPal.blue1, () -> produceCrystal));
    block.addBar("crystalSaver", entity -> new Bar("crystal.saver", CPal.blue2, () -> crystalSaver / crystalCapacity));
  }

  @Override
  public void addStats(Stats stats) {
    stats.add(CStat.produceCrystal, this.produceCrystal);
  }

  @Override
  public boolean full() {
    return crystalSaver >= crystalCapacity;
  }

  @Override
  public void setCrystal(float produceCrystal, float crystalCapacity) {
    this.produceCrystal = produceCrystal;
    this.crystalCapacity = crystalCapacity;
  }

  @Override
  public void write(Writes write) {
    write.f(this.produceCrystal);
    write.f(this.crystalCapacity);
    write.f(this.crystalSaver);
  }

  @Override
  public void read(Reads read, byte revision) {
    this.produceCrystal = read.f();
    this.crystalCapacity = read.f();
    this.crystalSaver = read.f();
  }
}
