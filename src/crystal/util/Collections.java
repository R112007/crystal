package crystal.util;

import arc.struct.ObjectSet;
import arc.struct.Queue;
import arc.struct.Seq;

public class Collections {
  public static <T> Seq<T> seqFromObj(T obj) {
    Seq<T> seq = new Seq<>();
    return seq;
  }

  public static <T> ObjectSet<T> setFromObj(T obj) {
    ObjectSet<T> set = new ObjectSet<>();
    return set;
  }

  public static <T> Queue<T> queueFromObj(T obj) {
    Queue<T> que = new Queue<>();
    return que;
  }
}
