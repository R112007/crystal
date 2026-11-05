package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import crystal.game.CEventType.ScrollObtainedEvent;
import crystal.game.CEventType.ScrollUsedEvent;
import crystal.magic.Scroll;
import crystal.util.DLog;
import mindustry.game.EventType.ClientLoadEvent;

/**
 * Persistent inventory for unused skill scrolls. A scroll is consumed only
 * when its target skill is successfully unlocked.
 */
public final class ScrollSystem {
  private static final String SAVE_KEY = "crystal.scrollCounts";
  public static final ObjectMap<Scroll, Integer> counts = new ObjectMap<>();
  private static boolean initialized;

  private ScrollSystem() {
  }

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;
    Events.on(ClientLoadEvent.class, e -> load());
  }

  public static int count(Scroll scroll) {
    return scroll == null ? 0 : counts.get(scroll, 0);
  }

  /** Grants unused scrolls and emits a UI-facing notification event. */
  public static void give(Scroll scroll, int amount) {
    if (scroll == null || amount <= 0) {
      return;
    }
    counts.put(scroll, count(scroll) + amount);
    save();
    Events.fire(new ScrollObtainedEvent(scroll, amount));
  }

  /**
   * Uses one scroll. Nothing is consumed if the target is malformed, already
   * unlocked, or fails to unlock.
   */
  public static boolean use(Scroll scroll) {
    if (scroll == null || scroll.targetSkill == null || count(scroll) <= 0
        || scroll.targetSkill.unlocked()) {
      return false;
    }

    if (!scroll.targetSkill.unlock()) {
      return false;
    }

    int remaining = count(scroll) - 1;
    if (remaining <= 0) {
      counts.remove(scroll);
    } else {
      counts.put(scroll, remaining);
    }
    save();
    Events.fire(new ScrollUsedEvent(scroll, scroll.targetSkill));
    return true;
  }

  /** Owned unused scroll kinds in stable content-registration order. */
  public static Seq<Scroll> owned() {
    Seq<Scroll> result = new Seq<>();
    for (Scroll scroll : Scroll.list) {
      if (count(scroll) > 0) {
        result.add(scroll);
      }
    }
    return result;
  }

  public static void save() {
    try {
      StringBuilder result = new StringBuilder();
      for (ObjectMap.Entry<Scroll, Integer> entry : counts.entries()) {
        if (entry.value == null || entry.value <= 0) {
          continue;
        }
        result.append(entry.key.name).append('=').append(entry.value).append(';');
      }
      Core.settings.put(SAVE_KEY, result.toString());
      Core.settings.manualSave();
    } catch (Exception error) {
      DLog.err("卷轴存档失败", error);
    }
  }

  public static void load() {
    counts.clear();
    try {
      String saved = Core.settings.getString(SAVE_KEY, "");
      if (saved == null || saved.isEmpty()) {
        return;
      }
      for (String part : saved.split(";")) {
        int equals = part.indexOf('=');
        if (equals <= 0) {
          continue;
        }
        Scroll scroll = Scroll.all.get(part.substring(0, equals).trim());
        if (scroll == null) {
          continue;
        }
        try {
          int amount = (int) Float.parseFloat(part.substring(equals + 1).trim());
          if (amount > 0) {
            counts.put(scroll, amount);
          }
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (Exception error) {
      DLog.err("卷轴读档失败", error);
      counts.clear();
    }
  }
}
