package crystal.world.meta;

import mindustry.gen.Iconc;
import mindustry.world.meta.StatUnit;

public class CStatUnit {

  public static final StatUnit reduce;
  static {
    reduce = new StatUnit("reduce", "[white]" + Iconc.left + "[]");
  }
}
