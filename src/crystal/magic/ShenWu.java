package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Scaling;
import crystal.CVars;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.world.meta.Stat;
import mindustry.world.meta.Stats;

/**
 * 神武基类。
 * 子类在构造中 addSkill(...) 装配技能；不同神武技能数量、种类完全不同。
 * 技能等级：0 = 未解锁，1~maxLevel = 已解锁并可升级。
 */
public abstract class ShenWu {

  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static final ObjectMap<Integer, ShenWu> map = new ObjectMap<>();
  public static final ObjectMap<String, ShenWu> all = new ObjectMap<>();
  public static final Seq<ShenWu> list = new Seq<>();

  public String name, fullName, localizedName;
  public @Nullable String description;
  public int id;
  public float magicAmount;
  public Stats stats = new Stats();
  public Seq<Skill> skills = new Seq<>();

  private TextureRegion icon;
  private boolean iconLoaded = false;

  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (ShenWu s : list) {
        s.loadIcon();
        for (Skill sk : s.skills)
          sk.loadIcon();
      }
    });
    // 渲染线程：绘制所有激活中的技能
    Events.run(Trigger.draw, () -> {
      if (!Vars.state.isPlaying())
        return;
      for (ShenWu sw : list)
        for (Skill sk : sw.skills)
          if (sk.active)
            sk.drawWorld(sw);
    });
  }

  public ShenWu(String name) {
    this.name = name;
    this.fullName = Vars.content.transformName(name);
    this.id = maxId.getAndIncrement();
    map.put(id, this);
    if (all.containsKey(name))
      throw new IllegalArgumentException("Two shenwu cannot have the same name! (issue: '" + name + "')");
    all.put(name, this);
    list.add(this);
    this.localizedName = Core.bundle.get("shenwu." + fullName + ".name", name);
    this.description = Core.bundle.getOrNull("shenwu." + fullName + ".description");
    setStats();
  }

  public void setStats() {
  }

  protected ShenWu addSkill(Skill skill) {
    skills.add(skill);
    return this;
  }

  public void loadIcon() {
    if (iconLoaded)
      return;
    TextureRegion found = Core.atlas.find("shenwu-" + fullName);
    this.icon = found.found() ? found : Icon.bookOpen.getRegion();
    this.iconLoaded = true;
  }

  public TextureRegion uiIcon() {
    if (!iconLoaded)
      loadIcon();
    return icon;
  }

  public TextureRegionDrawable uiIconDrawable() {
    return new TextureRegionDrawable(uiIcon());
  }

  /** 更新所有技能冷却 */
  public void update(float delta) {
    for (Skill s : skills)
      s.update(delta);
  }

  // ==================== Skill ====================

  /**
   * 神武技能。
   * level = 0 未解锁；level >= 1 已解锁，可升级到 maxLevel。
   * 解锁/升级由 ShenWuSystem + 科技树统一处理，Skill 只负责战斗逻辑。
   */
  public static abstract class Skill {
    public String name, localizedName;
    public @Nullable String description;

    /** 当前等级（0 = 未解锁） */
    public int level = 0;
    /** 最大等级 */
    public int maxLevel = 3;

    /** 基础冷却（秒），可被等级影响 */
    public float baseCooldown = 1f;
    /** 基础灵力消耗，可被等级影响 */
    public float baseCost = 0f;

    /** 是否有方向 */
    public boolean directional = false;
    /** 扇形角度（directional=true 时有效，360 = 全向） */
    public float sectorAngle = 360f;

    // 瞄准状态
    public float angle = 0f;
    public float aimX, aimY;
    public boolean active = false;
    public float cooldownTimer = 0f;

    public Color iconColor = Color.white;

    private TextureRegion icon;
    private boolean iconLoaded = false;
    // 在 Skill 类里加回这两个字段
    /** 解锁条件列表 */
    public Seq<Boolp> unlockConditions = new Seq<>();

    public Skill(String name) {
      this.name = name;
      this.localizedName = Core.bundle.get("shenwu.skill." + name + ".name", name);
      this.description = Core.bundle.getOrNull("shenwu.skill." + name + ".description");
    }

    /** 快捷添加解锁条件 */
    public Skill require(arc.func.Boolp condition) {
      unlockConditions.add(condition);
      return this;
    }

    /** 当前冷却时间（子类可覆盖实现等级衰减） */
    public float cooldown() {
      return baseCooldown;
    }

    /** 当前灵力消耗（子类可覆盖实现等级衰减） */
    public float cost() {
      return baseCost;
    }

    /** 是否已解锁 */
    public boolean unlocked() {
      return level > 0;
    }

    /** 是否满级 */
    public boolean maxed() {
      return level >= maxLevel;
    }

    /** 激活：传入目标坐标和方向 */
    public void activate(ShenWu owner, float aimX, float aimY, float angle) {
      this.aimX = aimX;
      this.aimY = aimY;
      this.angle = angle;
      this.active = true;
      instance(owner, aimX, aimY);
    }

    /** 瞬时触发：返回 true 表示成功释放（扣灵力、进冷却） */
    public abstract boolean instance(ShenWu owner, float x, float y);

    /** 持续效果：每帧调用（active = true 时） */
    public void continuous(ShenWu owner, float delta) {
    }

    /** 世界渲染层绘制：在 draw 线程调用 */
    public void drawWorld(ShenWu owner) {
    }

    /** 停用 */
    public void deactivate() {
      active = false;
    }

    /** 升级回调：level 已增加后调用，子类可覆盖调整属性 */
    public void onLevelUp(int newLevel) {
    }

    /** 是否可用：已解锁 + 冷却完毕 + 灵力足够 */
    public boolean ready() {
      return unlocked() && cooldownTimer <= 0 && CVars.availableMagicPower >= cost();
    }

    /** 尝试使用 */
    public boolean tryUse(ShenWu owner, float x, float y, float angle) {
      if (!ready())
        return false;
      if (!instance(owner, x, y))
        return false;
      CVars.availableMagicPower -= cost();
      cooldownTimer = cooldown();
      activate(owner, x, y, angle);
      return true;
    }

    /** 更新冷却 */
    public void update(float delta) {
      cooldownTimer = Math.max(0, cooldownTimer - delta / 60f);
      if (active)
        continuous(null, delta);
    }

    /** 冷却进度：0 = 冷却完毕，1 = 刚开始冷却 */
    public float cooldownProgress() {
      float cd = cooldown();
      return cd <= 0 ? 0 : Mathf.clamp(cooldownTimer / cd);
    }

    public void loadIcon() {
      if (iconLoaded)
        return;
      TextureRegion found = Core.atlas.find("shenwu-skill-" + name);
      this.icon = !found.found() ? Icon.bookOpen.getRegion() : found;
      this.iconLoaded = true;
    }

    public TextureRegion uiIcon() {
      if (!iconLoaded)
        loadIcon();
      return icon;
    }

    public boolean usingFallbackIcon() {
      if (!iconLoaded)
        loadIcon();
      return icon == Icon.bookOpen.getRegion();
    }

    /** 构建带冷却/锁定遮罩的技能图标 */
    public Stack buildIconStack(float size) {
      Stack stack = new Stack();
      Image img = new Image(uiIcon());
      img.setScaling(Scaling.fit);
      if (usingFallbackIcon())
        img.setColor(iconColor);
      stack.add(img);

      Element overlay = new Element() {
        @Override
        public void draw() {
          if (!unlocked()) {
            Draw.color(Color.black, 0.65f);
            Fill.rect(x + width / 2f, y + height / 2f, width, height);
            // 画锁图标
            Draw.color(Color.gray);
            // 简单画个叉表示锁定
            Lines.stroke(2f);
            Lines.line(x + width * 0.3f, y + height * 0.3f, x + width * 0.7f, y + height * 0.7f);
            Lines.line(x + width * 0.7f, y + height * 0.3f, x + width * 0.3f, y + height * 0.7f);
            Draw.color();
            return;
          }
          if (cooldownTimer > 0) {
            float fraction = 1f - cooldownProgress();
            if (fraction > 0) {
              float cx = x + width / 2f, cy = y + height / 2f;
              float r = Math.min(width, height) / 2f;
              Draw.color(Color.black, 0.55f);
              Fill.arc(cx, cy, r, fraction * 360f, 90f);
              Draw.color();
            }
          }
        }
      };
      overlay.touchable = Touchable.disabled;
      stack.add(overlay);
      return stack;
    }

    /** 快捷绘制方向指示（扇形/箭头） */
    protected void drawDirectional(float x, float y, float range, float angle, float sectorAngle, Color color) {
      Draw.z(Layer.flyingUnit + 1f);
      Draw.color(color, 0.2f);
      if (sectorAngle >= 360f) {
        Fill.circle(x, y, range);
      } else {
        Fill.arc(x, y, range, sectorAngle, angle - sectorAngle / 2f);
      }
      Draw.color(color, 0.6f);
      Lines.stroke(2f);
      if (sectorAngle >= 360f) {
        Lines.circle(x, y, range);
      } else {
        Lines.arc(x, y, range, sectorAngle, angle - sectorAngle / 2f);
        float a1 = (angle - sectorAngle / 2f) * Mathf.degRad;
        float a2 = (angle + sectorAngle / 2f) * Mathf.degRad;
        Lines.line(x, y, x + Mathf.cos(a1) * range, y + Mathf.sin(a1) * range);
        Lines.line(x, y, x + Mathf.cos(a2) * range, y + Mathf.sin(a2) * range);
      }
      float arrowLen = range * 0.7f;
      float ax = x + Mathf.cos(angle * Mathf.degRad) * arrowLen;
      float ay = y + Mathf.sin(angle * Mathf.degRad) * arrowLen;
      Lines.stroke(3f, Color.white);
      Lines.line(x, y, ax, ay);
      Draw.color();
    }
  }
}
