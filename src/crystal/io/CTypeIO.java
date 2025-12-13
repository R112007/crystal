package crystal.io;

import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.util.io.Writes;
import crystal.type.UnitStack;
import mindustry.ai.UnitCommand;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.Content;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.io.TypeIO.BuildingBox;
import mindustry.io.TypeIO.UnitBox;
import mindustry.logic.LAccess;

import static mindustry.io.TypeIO.*;

public class CTypeIO {
  public static void writeObject(Writes write, Object object) {
    if (object == null) {
      write.b((byte) 0);
    } else if (object instanceof Integer i) {
      write.b((byte) 1);
      write.i(i);
    } else if (object instanceof Long l) {
      write.b((byte) 2);
      write.l(l);
    } else if (object instanceof Float f) {
      write.b((byte) 3);
      write.f(f);
    } else if (object instanceof String s) {
      write.b((byte) 4);
      writeString(write, s);
    } else if (object instanceof Content map) {
      write.b((byte) 5);
      write.b((byte) map.getContentType().ordinal());
      write.s(map.id);
    } else if (object instanceof IntSeq arr) {
      write.b((byte) 6);
      write.s((short) arr.size);
      for (int i = 0; i < arr.size; i++) {
        write.i(arr.items[i]);
      }
    } else if (object instanceof Point2 p) {
      write.b((byte) 7);
      write.i(p.x);
      write.i(p.y);
    } else if (object instanceof Point2[] p) {
      write.b((byte) 8);
      write.b(p.length);
      for (Point2 point2 : p) {
        write.i(point2.pack());
      }
    } else if (object instanceof TechNode map) {
      write.b(9);
      write.b((byte) map.content.getContentType().ordinal());
      write.s(map.content.id);
    } else if (object instanceof Boolean b) {
      write.b((byte) 10);
      write.bool(b);
    } else if (object instanceof Double d) {
      write.b((byte) 11);
      write.d(d);
    } else if (object instanceof Building b) {
      write.b(12);
      write.i(b.pos());
    } else if (object instanceof BuildingBox b) {
      write.b(12);
      write.i(b.pos);
    } else if (object instanceof LAccess l) {
      write.b((byte) 13);
      write.s(l.ordinal());
    } else if (object instanceof byte[] b) {
      write.b((byte) 14);
      write.i(b.length);
      write.b(b);
    } else if (object instanceof boolean[] b) {
      write.b(16);
      write.i(b.length);
      for (boolean bool : b) {
        write.bool(bool);
      }
    } else if (object instanceof Unit u) {
      write.b(17);
      write.i(u.id);
    } else if (object instanceof UnitBox u) {
      write.b(17);
      write.i(u.id);
    } else if (object instanceof Vec2[] vecs) {
      write.b(18);
      write.s(vecs.length);
      for (Vec2 v : vecs) {
        write.f(v.x);
        write.f(v.y);
      }
    } else if (object instanceof Vec2 v) {
      write.b((byte) 19);
      write.f(v.x);
      write.f(v.y);
    } else if (object instanceof Team t) {
      write.b((byte) 20);
      write.b(t.id);
    } else if (object instanceof int[] i) {
      write.b((byte) 21);
      writeInts(write, i);
    } else if (object instanceof Object[] objs) {
      write.b((byte) 22);
      write.i(objs.length);
      for (Object obj : objs) {
        writeObject(write, obj);
      }
    } else if (object instanceof UnitCommand command) {
      write.b(23);
      write.s(command.id);
    } else if (object instanceof UnitStack stack) {
      write.b((byte) 24);
      write.i(stack.unit.id);
      write.i(stack.amount);
    } else if (object instanceof UnitStack[] stacks) {
      write.b((byte) 25);
      for (var stack : stacks) {
        write.i(stack.unit.id);
        write.i(stack.amount);
      }
    } else {
      throw new IllegalArgumentException("Unknown object type: " + object.getClass());
    }
  }
}
