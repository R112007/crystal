package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.CVars;
import crystal.entities.units.UnitEnum.XiuWei;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.world.meta.Stat;
import mindustry.world.meta.Stats;

/**
 * 法宝基类：一次性消耗品，用了就没了（持有数由 crystal.core.FaBaoSystem 管理）。
 * 具体法宝写子类放 magic/fabaos/ 下（参考 LuoLeiFu），必须实现 release()；
 * 要自定义瞄准显示（扇形/矩形覆盖之类）就覆盖 drawBeforeRelease()。
 * 使用流程：法宝栏（FaBaoHUD）拖出图标 → 拖动中时间变缓 + 每帧调 drawBeforeRelease()
 * → 松手调 release() 并消耗一个。
 */
public abstract class FaBao {
  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static final ObjectMap<Integer, FaBao> map = new ObjectMap<>();
  /** 原始名 → 法宝（存档用） */
  public static final ObjectMap<String, FaBao> all = new ObjectMap<>();
  /** 注册顺序列表（ObjectMap 无序，UI 展示用这个保证顺序稳定） */
  public static final Seq<FaBao> list = new Seq<>();

  public int id;
  /** 原始名（注册表/存档 key） */
  public String name;
  /** 加 mod 前缀的全名（atlas/bundle key 用） */
  public String fullName;
  public String localizedName;
  public @Nullable String description;
  /** 松手即生效（保留骨架语义） */
  public boolean instantWork;
  /** 效果数值是否受修为档位加成（见 powerScale()） */
  public boolean effectedByXiuWei;
  public boolean update;
  /** 作用半径（世界单位，1 格 = 8），默认瞄准圈用 */
  public float range = 8 * 8f;
  /** 瞄准显示颜色 */
  public Color rangeColor = Color.gold;

  /** 属性统计面板，跟 Block.stats 一样。子类在 setStats() 里 add。 */
  public Stats stats = new Stats();

  private TextureRegion icon;
  private boolean iconLoaded = false;
  /** 图标是否走了兜底（没配贴图），HUD 可据此给兜底图标染色区分法宝 */
  private boolean fallbackIcon = false;

  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (FaBao f : list)
        f.loadIcon();
    });
  }

  /** @param defaultName 语言包没配文案时的默认中文名（localizedName 兜底） */
  public FaBao(String name, String defaultName) {
    this.name = name;
    this.fullName = Vars.content.transformName(name);
    this.id = maxId.getAndIncrement();
    map.put(id, this);
    if (all.containsKey(name))
      throw new IllegalArgumentException("Two fabao cannot have the same name! (issue: '" + name + "')");
    all.put(name, this);
    list.add(this);
    this.localizedName = Core.bundle.get("fabao." + fullName + ".name", defaultName);
    this.description = Core.bundle.getOrNull("fabao." + fullName + ".description");
    setStats();
  }

  /**
   * 配置法宝属性统计，子类覆盖以添加自定义 stat。
   * 用法跟 Block.setStats() 完全一致：stats.add(Stat.xxx, value)
   */
  public void setStats() {
  }

  /**
   * 松手释放效果，参数为目标点世界坐标 (worldX, worldY)。
   * 存货校验与消耗由 FaBaoHUD/FaBaoSystem 负责，这里直接放效果。
   */
  public abstract void release(float x, float y);

  /**
   * 瞄准（松手前）的自定义显示，拖动中每帧在世界渲染层调用，参数为瞄准点世界坐标。
   * 默认实现：半透明填充 + 描边圈 + 中心十字。子类覆盖它画自己的覆盖范围等。
   */
  public void drawBeforeRelease(float x, float y) {
    Draw.z(Layer.flyingUnit + 1f);
    Draw.color(rangeColor, 0.14f);
    Fill.circle(x, y, range);
    Draw.color(rangeColor);
    Lines.stroke(2f);
    Lines.circle(x, y, range);
    Lines.stroke(1f, Color.white);
    Lines.line(x - 6f, y, x + 6f, y);
    Lines.line(x, y - 6f, x, y + 6f);
    Draw.reset();
  }

  /**
   * 效果数值倍率：effectedByXiuWei = true 时随玩家修为档位放大
   * （yong×1.1 / fan×2 / shen×3 / sheng×5 / xian×7 / dijun×11）
   */
  protected float powerScale() {
    return effectedByXiuWei ? XiuWei.xiuWeiMultiplier(CVars.playerXiuWei) + 1f : 1f;
  }

  /** 懒加载图标（构造时 atlas 未就绪），atlas 键 fabao-<fullName>，缺失用通用图标兜底 */
  public void loadIcon() {
    if (iconLoaded)
      return;
    TextureRegion found = Core.atlas.find("fabao-" + fullName);
    this.fallbackIcon = !found.found();
    this.icon = fallbackIcon ? Icon.bookOpen.getRegion() : found;
    this.iconLoaded = true;
  }

  public TextureRegion uiIcon() {
    if (!iconLoaded)
      loadIcon();
    return icon;
  }

  /** 图标是否是兜底图标（没配 fabao-<全名> 贴图） */
  public boolean usingFallbackIcon() {
    if (!iconLoaded)
      loadIcon();
    return fallbackIcon;
  }
}
