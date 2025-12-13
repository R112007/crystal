package crystal.world.blocks.crystal;

import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.graphics.CPal;
import crystal.world.interfaces.CrystalInterface;
import crystal.world.meta.CStat;
import crystal.world.meta.CrystalGroup;
import mindustry.gen.Building;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.Stats;

public class CrystalConsumer implements CrystalInterface {
  public float consumeCrystal, crystalCapacity;
  public float tick = 0f, crystalSaver = 0f, efficiency = 0f;
  public CrystalGroup group = CrystalGroup.none;

  public void setGroup(CrystalGroup group) {
    this.group = group;
  }

  @Override
  public void addStats(Stats stats) {
    stats.add(CStat.consumeCrystalE, this.consumeCrystal);
  }

  @Override
  public void update(Building entity) {
    Log.info(tick + "tick");
    Log.info(efficiency + "efficiency");
    if (crystalSaver < 0f)
      crystalSaver = 0f;
    if (crystalSaver > crystalCapacity)
      crystalSaver = crystalCapacity;
    if (tick <= 0 && crystalSaver <= 0)
      efficiency = 0;
    if (tick > consumeCrystal && crystalSaver < crystalCapacity && canConsume(entity)) {
      crystalSaver += (tick - consumeCrystal);
      Log.info("level1");
    } else if (tick > consumeCrystal && crystalSaver < crystalCapacity && !canConsume(entity)) {
      crystalSaver += tick;
      Log.info("level2");
    }
    if (canConsume(entity)) {
      if (tick < consumeCrystal && crystalSaver > 0f) {
        Log.info("level3");
        crystalSaver -= (consumeCrystal - tick);
        efficiency = 1f;
      } else if (tick < consumeCrystal && crystalSaver <= 0f) {
        Log.info("level4");
        efficiency = tick / consumeCrystal;
      } else if (tick > consumeCrystal) {
        Log.info("level5");
        efficiency = 1f;
      }
    }
  }

  public boolean canConsume(Building entity) {
    return entity.shouldConsume();
  }

  public float efficiency() {
    return this.efficiency;
  }

  @Override
  public boolean full() {
    return crystalSaver >= crystalCapacity;
  }

  @Override
  public void setBars(Block block) {
    block.addBar("crystalEfficiency", entity -> new Bar("crystal.efficiency", CPal.blue1, () -> efficiency()));
    block.addBar("crystalSaver", entity -> new Bar("crystal.saver", CPal.blue2, () -> crystalSaver / crystalCapacity));
  }

  @Override
  public void setCrystal(float consumeCrystal, float crystalCapacity) {
    this.consumeCrystal = consumeCrystal;
    this.crystalCapacity = crystalCapacity;
  }

  public void tickImprove(float amount) {
    Log.info("tick+" + amount);
    this.tick += amount;
  }

  public void tickReduce(float amount) {
    this.tick -= amount;
  }

  @Override
  public void write(Writes write) {
    write.f(this.consumeCrystal);
    write.f(this.crystalCapacity);
    write.f(this.tick);
    write.f(this.crystalSaver);
  }

  @Override
  public void read(Reads read, byte revision) {
    this.consumeCrystal = read.f();
    this.crystalCapacity = read.f();
    this.tick = read.f();
    this.crystalSaver = read.f();
  }
}
