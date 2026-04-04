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
    tick += 1f;
    float required = consumeCrystal;
    boolean canWork = canConsume(entity);

    // 2. 先处理储蓄上限
    crystalSaver = Math.min(crystalSaver, crystalCapacity);

    // 3. 核心消耗逻辑：工作时才消耗tick，不工作时不积累也不消耗
    if (canWork) {
      if (tick >= required) {
        // tick足够，直接消耗，多余的存入储蓄
        float excess = tick - required;
        if (crystalSaver < crystalCapacity) {
          crystalSaver += excess;
          crystalSaver = Math.min(crystalSaver, crystalCapacity);
        }
        efficiency = 1f;
        tick = 0f; // 消耗后重置tick
      } else {
        // tick不足，尝试用储蓄补足
        float deficit = required - tick;
        if (crystalSaver >= deficit) {
          // 储蓄足够，补足缺口
          crystalSaver -= deficit;
          efficiency = 1f;
          tick = 0f;
        } else {
          // 储蓄不足，按比例计算效率
          efficiency = (tick + crystalSaver) / required;
          crystalSaver = 0f;
          tick = 0f; // 用完所有tick和储蓄，重置
        }
      }
    } else {
      // 不工作时，重置tick，不积累储蓄
      tick = 0f;
      // 可选：不工作时缓慢消耗储蓄，或保持不变，根据需求调整
    }

    Log.info("tick: {}, efficiency: {}, crystalSaver: {}", tick, efficiency, crystalSaver);
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
