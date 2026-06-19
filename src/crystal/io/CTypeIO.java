package crystal.io;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.type.UnitStack;
import ent.anno.Annotations.TypeIOHandler;
import mindustry.Vars;
import mindustry.world.modules.ItemModule;

@TypeIOHandler
public class CTypeIO {
  public static void writeItemModule(Writes writes, ItemModule item) {
    item.write(writes);
  }

  public static ItemModule readItemModule(Reads reads) {
    ItemModule item = new ItemModule();
    item.read(reads);
    return item;
  }

  public static void writeUnitStack(Writes write, UnitStack stack) {
    write.i(stack.unit.id);
    write.i(stack.amount);
  }

  public static UnitStack readUnitStack(Reads read) {
    int id = read.i();
    int amount = read.i();
    return new UnitStack(Vars.content.unit(id), amount);
  }

  public static void writeUnitStacks(UnitStack[] stacks, Writes write) {
    write.i(stacks.length);
    for (var u : stacks) {
      writeUnitStack(write, u);
    }
  }

  public static void writeUnitStacks(Seq<UnitStack> stacks, Writes write) {
    write.i(stacks.size);
    for (var stack : stacks) {
      writeUnitStack(write, stack);
    }
  }

  public static UnitStack[] readUnitStacks(Reads read) {
    int length = read.i();
    UnitStack[] stacks = new UnitStack[length];
    for (int i = 0; i < length; i++) {
      stacks[i] = readUnitStack(read);
    }
    return stacks;
  }

  public static void writeXiuWei(Writes writes, XiuWei xiuWei) {
    writes.i(xiuWei.ordinal());
  }

  public static XiuWei readXiuWei(Reads reads) {
    return XiuWei.all[reads.i()];
  }

  public static void writeShenTong(Writes writes, Seq<ShenTong> shenTongs) {
    writes.i(shenTongs.size);
    for (var s : shenTongs) {
      writes.i(s.id);
      s.write(writes);
    }
  }

  /*
   * public static Seq<ShenTong> readShenTong(Reads reads) {
   * Seq<ShenTong> read = new Seq<>();
   * ;
   * int size = reads.i();
   * for (int i = 0; i < size; i++) {
   * int id = reads.i();
   * var shen = ShenTong.shengTongMap.get(id).create();
   * shen.read(reads);
   * read.add(shen);
   * }
   * return read;
   * }
   */
  public static Seq<ShenTong> readShenTong(Reads reads) {
    Seq<ShenTong> result = new Seq<>();
    int size = reads.i();
    for (int i = 0; i < size; i++) {
      int id = reads.i();
      ShenTong prototype = ShenTong.shengTongMap.get(id);
      if (prototype == null) {
        Log.err("跳过未注册的神通，ID: " + id + "，请检查注册逻辑");
        new ShenTong() {
          @Override
          public String name() {
            return "unknown";
          }

          @Override
          public String description() {
            return "unknown";
          }

          @Override
          public XiuWei limitMinXiuWei() {
            return XiuWei.yong;
          }
        }.read(reads);
        continue;
      }
      ShenTong instance = prototype.create();
      instance.read(reads);
      result.add(instance);
    }
    return result;
  }
}
