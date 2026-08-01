package crystal.world.modules;

import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.type.SatelliteMissile;
import mindustry.world.modules.BlockModule;

/** 卫星导弹仓库，仿 ItemModule 实现。 */
public class SatelliteMissleModule extends BlockModule {
  public static final SatelliteMissleModule empty = new SatelliteMissleModule();

  private final ObjectMap<SatelliteMissile, Integer> missiles = new ObjectMap<>();
  private int total;

  public void add(SatelliteMissile missile, int amount) {
    if (missile == null || amount <= 0)
      return;
    missiles.put(missile, get(missile) + amount);
    total += amount;
  }

  public void remove(SatelliteMissile missile, int amount) {
    if (missile == null || amount <= 0)
      return;
    int current = get(missile);
    if (current <= amount) {
      missiles.remove(missile);
      total -= current;
    } else {
      missiles.put(missile, current - amount);
      total -= amount;
    }
  }

  public int get(SatelliteMissile missile) {
    return missile == null ? 0 : missiles.get(missile, 0);
  }

  public boolean has(SatelliteMissile missile, int amount) {
    return get(missile) >= amount;
  }

  public int total() {
    return total;
  }

  public void clear() {
    missiles.clear();
    total = 0;
  }

  public void each(Cons2<SatelliteMissile, Integer> cons) {
    for (SatelliteMissile missile : missiles.keys()) {
      cons.get(missile, missiles.get(missile, 0));
    }
  }

  @Override
  public void write(Writes write) {
    write.i(total);
    write.i(missiles.size);
    for (SatelliteMissile missile : missiles.keys()) {
      write.i(missile.id);
      write.i(missiles.get(missile, 0));
    }
  }

  public void read(Reads read) {
    total = read.i();
    int size = read.i();
    missiles.clear();
    for (int i = 0; i < size; i++) {
      int id = read.i();
      int amount = read.i();
      SatelliteMissile missile = SatelliteMissile.map.get(id);
      if (missile != null) {
        missiles.put(missile, amount);
      } else {
        // 类型未加载时丢弃，避免 total 不一致
        total -= amount;
      }
    }
  }
}
