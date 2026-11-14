package crystal.core;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.magic.XianWen;
import crystal.magic.XianWen.LaunchContext;
import crystal.magic.XianWen.Stat;
import crystal.util.DLog;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.type.Sector;
import mindustry.type.UnitType;

/**
 * 仙纹的携带配置与发射快照管理。
 *
 * <p>{@link #selected} 是下一次发射准备携带的配置；核心发射确认时复制为
 * {@link #launched}。运行期数值修正和行为读取 launched，避免玩家在发射后
 * 修改配置导致当前区块效果漂移。</p>
 */
public final class XianWenSystem {
  private static final String SAVE_KEY_SELECTED = "crystal.xianwen.selected";
  private static final String SAVE_KEY_LAUNCHED = "crystal.xianwen.launched";
  private static final int BASE_CAPACITY = 2;

  /** 下一次核心发射使用的仙纹配置。 */
  public static final Seq<XianWen> selected = new Seq<>();
  /** 本次运行实际生效的仙纹快照。 */
  public static final Seq<XianWen> launched = new Seq<>();

  private static boolean initialized;

  private XianWenSystem() {
  }

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;
    Events.on(ClientLoadEvent.class, event -> load());
  }

  /**
   * 当前道载上限。基础 2 点，每个修为档位增加 1 点。
   * 具体境界数值属于平衡项，集中在这里，避免 UI 与发射逻辑各算一套。
   */
  public static int capacity() {
    return BASE_CAPACITY + Math.max(0, CultivationState.currentXiuWei().ordinal());
  }

  public static int usedCapacity() {
    int used = 0;
    for (XianWen xianWen : selected) {
      used += Math.max(1, xianWen.loadCost);
    }
    return used;
  }

  public static int remainingCapacity() {
    return Math.max(0, capacity() - usedCapacity());
  }

  public static boolean isSelected(@Nullable XianWen xianWen) {
    return xianWen != null && selected.contains(xianWen);
  }

  public static boolean canSelect(@Nullable XianWen xianWen) {
    return xianWen != null
        && xianWen.unlocked()
        && !selected.contains(xianWen)
        && Math.max(1, xianWen.loadCost) <= remainingCapacity();
  }

  /** 添加仙纹。已经携带时返回 false。 */
  public static boolean select(@Nullable XianWen xianWen) {
    if (!canSelect(xianWen)) {
      return false;
    }
    selected.add(xianWen);
    save();
    return true;
  }

  /** 移除仙纹。 */
  public static boolean deselect(@Nullable XianWen xianWen) {
    if (xianWen == null) {
      return false;
    }
    boolean removed = selected.remove(xianWen);
    if (removed) {
      save();
    }
    return removed;
  }

  /** 切换携带状态，返回切换后是否携带。容量不足或已锁定时保持原状态。 */
  public static boolean toggle(@Nullable XianWen xianWen) {
    if (isSelected(xianWen)) {
      deselect(xianWen);
      return false;
    }
    select(xianWen);
    return isSelected(xianWen);
  }

  public static void clearSelection() {
    if (selected.isEmpty()) {
      return;
    }
    selected.clear();
    save();
  }

  /**
   * 确认一次核心发射，并将当前选择固化为运行快照。
   *
   * <p>发射按钮应在真正触发 launch 前调用此方法。即使发射随后被外部流程取消，
   * 这里也只更新配置快照，不直接修改世界。</p>
   */
  public static void commitLaunch(
      @Nullable Sector from,
      @Nullable Sector destination,
      @Nullable UnitType coreType,
      boolean mobileCore) {
    normalize(selected);
    launched.clear();
    launched.addAll(selected);
    save();

    LaunchContext context = new LaunchContext(from, destination, coreType, mobileCore);
    for (XianWen xianWen : launched) {
      xianWen.onLaunch(context);
    }
    DLog.info("核心发射携带仙纹：" + names(launched, true) + "，道载 "
        + usedCapacity() + "/" + capacity());
  }

  /** 对所有本次运行的仙纹依次应用数值修正。 */
  public static float modify(Stat stat, float baseValue) {
    float result = baseValue;
    for (XianWen xianWen : launched) {
      result = xianWen.modify(stat, result);
    }
    return result;
  }

  /** 修为面板/发射面板共用的当前配置摘要。 */
  public static String selectionSummary() {
    return Core.bundle.format(
        "xianwen.summary",
        usedCapacity(),
        capacity(),
        names(selected, false));
  }

  /** 当前发射快照摘要，供调试或后续 HUD 使用。 */
  public static String launchedSummary() {
    return Core.bundle.format(
        "xianwen.summary",
        loadCost(launched),
        capacity(),
        names(launched, false));
  }

  public static String names(Seq<XianWen> seq, boolean emptyMarker) {
    if (seq == null || seq.isEmpty()) {
      return emptyMarker ? "无" : Core.bundle.get("xianwen.summary.none", "未携带仙纹");
    }
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < seq.size; i++) {
      if (i > 0) {
        result.append("、");
      }
      result.append(seq.get(i).localizedName);
    }
    return result.toString();
  }

  public static void save() {
    try {
      Core.settings.put(SAVE_KEY_SELECTED, serialize(selected));
      Core.settings.put(SAVE_KEY_LAUNCHED, serialize(launched));
      Core.settings.manualSave();
    } catch (Exception e) {
      DLog.err("仙纹配置保存失败", e);
    }
  }

  public static void load() {
    selected.clear();
    launched.clear();

    try {
      readInto(selected, Core.settings.getString(SAVE_KEY_SELECTED, ""));
      readInto(launched, Core.settings.getString(SAVE_KEY_LAUNCHED, ""));
      normalize(selected);
      normalize(launched);
    } catch (Exception e) {
      selected.clear();
      launched.clear();
      DLog.err("仙纹配置读档失败", e);
    }
  }

  private static void readInto(Seq<XianWen> target, @Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    for (String part : raw.split(",")) {
      XianWen xianWen = XianWen.all.get(part.trim());
      if (xianWen != null && xianWen.unlocked()) {
        target.add(xianWen);
      }
    }
  }

  private static String serialize(Seq<XianWen> seq) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < seq.size; i++) {
      if (i > 0) {
        result.append(',');
      }
      result.append(seq.get(i).name);
    }
    return result.toString();
  }

  private static void normalize(Seq<XianWen> target) {
    ObjectSet<XianWen> seen = new ObjectSet<>();
    int capacity = capacity();
    int used = 0;
    for (int i = 0; i < target.size; i++) {
      XianWen xianWen = target.get(i);
      int cost = xianWen == null ? 0 : Math.max(1, xianWen.loadCost);
      if (xianWen == null || !xianWen.unlocked() || !seen.add(xianWen)
          || used + cost > capacity) {
        target.remove(i--);
        continue;
      }
      used += cost;
    }
  }

  private static int loadCost(Seq<XianWen> seq) {
    int result = 0;
    for (XianWen xianWen : seq) {
      result += Math.max(1, xianWen.loadCost);
    }
    return result;
  }
}
