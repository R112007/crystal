package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.Crystal;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.type.Sector;
import mindustry.type.UnitType;

/**
 * 仙纹基类，参考 {@link Skill} 的注册与懒加载方式。
 *
 * <p>
 * 仙纹只在核心发射前配置，确认发射时由
 * {@link crystal.core.XianWenSystem} 生成一次运行快照。数值型仙纹覆盖
 * {@link #modify(Stat, float)}；行为型仙纹覆盖 {@link #onLaunch(LaunchContext)}
 * 或后续按实际需求增加世界事件钩子。
 * </p>
 */
public abstract class XianWen {
  private static final AtomicInteger maxId = new AtomicInteger(0);

  /** 按注册 id 查询，供未来运行时快照或序列化使用。 */
  public static final ObjectMap<Integer, XianWen> map = new ObjectMap<>();
  /** 原始名到仙纹，存档只保存这里的稳定 name。 */
  public static final ObjectMap<String, XianWen> all = new ObjectMap<>();
  /** 按注册顺序展示。 */
  public static final Seq<XianWen> list = new Seq<>();

  public final int id;
  /** 稳定注册名，同时作为语言包和存档键。 */
  public final String name;
  /** 带模组前缀的名称，供贴图查找使用。 */
  public final String fullName;
  public String localizedName;
  public @Nullable String description;
  public Color iconColor = Color.gold;
  /** 携带时占用的道载，必须为正数。 */
  public int loadCost = 1;
  /** 后续接入获取/解锁流程时由子类或系统修改。 */
  public boolean unlocked = true;

  private final String defaultName;
  private TextureRegion loadedIcon;
  private boolean iconLoaded;
  private boolean fallbackIcon;

  static {
    Events.on(ClientLoadEvent.class, event -> {
      for (XianWen xianWen : list) {
        xianWen.loadIcon();
        xianWen.refreshLocalization();
      }
    });
  }

  protected XianWen(String name, String defaultName) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("XianWen name cannot be empty");
    }
    if (all.containsKey(name)) {
      throw new IllegalArgumentException(
          "Two xianwen cannot have the same name! (issue: '" + name + "')");
    }

    this.name = name;
    this.defaultName = defaultName == null || defaultName.isBlank() ? name : defaultName;
    this.fullName = mindustry.Vars.content.transformName(name);
    this.id = maxId.getAndIncrement();

    map.put(id, this);
    all.put(name, this);
    list.add(this);
    refreshLocalization();
  }

  /** 当前是否已解锁并允许进入配置列表。 */
  public boolean unlocked() {
    return unlocked;
  }

  public void unlock() {
    unlocked = true;
  }

  /**
   * 修改一个数值。默认原样返回。
   *
   * <p>
   * 多个仙纹按配置顺序依次调用，因此实现应明确自己是加法、乘法还是覆盖，
   * 不要假设只会在基础值上调用一次。
   * </p>
   */
  public float modify(Stat stat, float value) {
    return value;
  }

  /**
   * 核心发射确认后的行为钩子。只对本次发射快照中的仙纹调用一次。
   */
  public void onLaunch(LaunchContext context) {
  }

  /** 刷新语言包文案；内容注册早于客户端加载时会在这里重新取值。 */
  public void refreshLocalization() {
    if (Core.bundle == null) {
      localizedName = defaultName;
      return;
    }
    localizedName = Core.bundle.get("xianwen." + name + ".name", defaultName);
    description = Core.bundle.getOrNull("xianwen." + name + ".description");
  }

  /** 贴图约定为 xianwen-&lt;fullName&gt;，缺失时使用统一占位图标。 */
  public void loadIcon() {
    if (iconLoaded || Core.atlas == null) {
      return;
    }
    TextureRegion found = findRegion(name);
    fallbackIcon = !found.found();
    loadedIcon = fallbackIcon ? Icon.bookOpen.getRegion() : found;
    iconLoaded = true;
  }

  private TextureRegion findRegion(String name) {
    if (Core.atlas == null) {
      return null;
    }
    TextureRegion region = Core.atlas.find(Crystal.name(name));
    if (region.found()) {
      return region;
    }
    region = Core.atlas.find("icon/xianWen/" + name);
    if (region.found()) {
      return region;
    }
    region = Core.atlas.find("crystal-" + name.toLowerCase());
    return region.found() ? region : null;
  }

  public TextureRegion uiIcon() {
    if (!iconLoaded) {
      loadIcon();
    }
    return loadedIcon == null ? Icon.bookOpen.getRegion() : loadedIcon;
  }

  public boolean usingFallbackIcon() {
    if (!iconLoaded) {
      loadIcon();
    }
    return fallbackIcon;
  }

  /** 仙纹可影响的通用数值入口，具体项目在效果实现中按需扩展。 */
  public enum Stat {
    resourceProduction,
    coreStorage,
    unitCap,
    unitAttack,
    unitHealth,
    unitSpeed,
    magicPower,
    launchCapacity
  }

  /** 一次核心发射的只读上下文。 */
  public static final class LaunchContext {
    public final Sector from;
    public final Sector destination;
    public final @Nullable UnitType coreType;
    public final boolean mobileCore;

    public LaunchContext(Sector from, Sector destination,
        @Nullable UnitType coreType, boolean mobileCore) {
      this.from = from;
      this.destination = destination;
      this.coreType = coreType;
      this.mobileCore = mobileCore;
    }
  }
}
