package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import crystal.magic.FaBao;
import crystal.util.DLog;
import mindustry.game.EventType.ClientLoadEvent;

/**
 * 法宝持有数管理：一次性消耗品，用一件少一件。
 * 剧情/区块奖励要发法宝，调 FaBaoSystem.give(FaBaos.luoLeiFu, 1) 即可。
 */
public class FaBaoSystem {
  private static final String SAVE_KEY = "crystal.fabaoCounts";
  /** 法宝 → 持有数 */
  public static final ObjectMap<FaBao, Integer> counts = new ObjectMap<>();
  private static boolean initialized = false;

  public static void init() {
    if (initialized)
      return;
    initialized = true;
    Events.on(ClientLoadEvent.class, e -> load());
  }

  public static int count(FaBao f) {
    return counts.get(f, 0);
  }

  public static void give(FaBao f, int amount) {
    if (f == null || amount <= 0)
      return;
    counts.put(f, count(f) + amount);
    save();
    DLog.info("获得法宝：" + f.localizedName + " ×" + amount + "，现有 " + count(f));
  }

  /** 消耗一个；没有存货返回 false（不会触发效果） */
  public static boolean consume(FaBao f) {
    int c = count(f);
    if (c <= 0)
      return false;
    if (c == 1)
      counts.remove(f);
    else
      counts.put(f, c - 1);
    save();
    return true;
  }

  /** 当前持有的法宝种类，按注册顺序（UI 展示用） */
  public static Seq<FaBao> owned() {
    Seq<FaBao> out = new Seq<>();
    for (FaBao f : FaBao.list) {
      if (count(f) > 0)
        out.add(f);
    }
    return out;
  }

  public static void save() {
    try {
      // 手动拼 "name=count;" 串，不走 json：读回不用担心类型问题
      StringBuilder sb = new StringBuilder();
      for (ObjectMap.Entry<FaBao, Integer> en : counts.entries()) {
        if (en.value == null || en.value <= 0)
          continue;
        sb.append(en.key.name).append('=').append(en.value).append(';');
      }
      Core.settings.put(SAVE_KEY, sb.toString());
      Core.settings.manualSave();
    } catch (Exception e) {
      DLog.err("法宝存档失败", e);
    }
  }

  public static void load() {
    counts.clear();
    try {
      String s = Core.settings.getString(SAVE_KEY, "");
      if (s == null || s.isEmpty())
        return;
      for (String part : s.split(";")) {
        int eq = part.indexOf('=');
        if (eq <= 0)
          continue;
        FaBao f = FaBao.all.get(part.substring(0, eq).trim());
        if (f == null)
          continue;
        try {
          int c = (int) Float.parseFloat(part.substring(eq + 1).trim());
          if (c > 0)
            counts.put(f, c);
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (Exception e) {
      DLog.err("法宝读档失败", e);
      counts.clear();
    }
  }
}
