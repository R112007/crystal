package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import crystal.core.CultivationState;
import crystal.game.CEventType.SkillUnlockedEvent;
import crystal.gen.SkillEntity;
import crystal.gen.SkillEntityc;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Drawc;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Teamc;
import mindustry.gen.Timedc;
import mindustry.graphics.Layer;

/**
 * A player skill that can either run immediately or be hosted by a
 * {@link SkillEntity}.
 *
 * <p>
 * Instant skills implement {@link #influence()}. Continuous skills set
 * {@link #continuous} to {@code true} and implement
 * {@link #influence(SkillEntity)}; the entity calls that method once per game
 * update until its lifetime ends. The target and caster used by an instant
 * skill are available through {@link #aimX}, {@link #aimY}, {@link #angle} and
 * {@link #caster} for the duration of the call.
 * </p>
 */
public abstract class Skill implements SkillUnlockable {

  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static final ObjectMap<Integer, Skill> map = new ObjectMap<>();
  public static final ObjectMap<String, Skill> all = new ObjectMap<>();
  public static final Seq<Skill> list = new Seq<>();

  public int id;
  public String name, fullName, localizedName;
  public @Nullable String description;
  public TextureRegion icon;
  public Color iconColor = Color.white;

  /** Whether this skill is executed by a persistent {@link SkillEntity}. */
  public boolean continuous;

  /** Cooldown in seconds between casts. */
  public float baseCooldown;
  /** Spirit power consumed when the skill starts. */
  public float baseCost;
  /** Entity lifetime in seconds for continuous skills. */
  public float duration = 1f;
  /**
   * Whether the HUD should ask for a world-space target before casting.
   * Self-centered or state-toggle skills leave this false.
   */
  public boolean targeted = false;
  /** Maximum distance from the caster to a targeted point, in world units. */
  public float targetRange = 20f * 8f;
  /** Radius of the default target marker, in world units. */
  public float targetAreaRadius = 24f;
  /** Whether the skill has been unlocked. */
  public boolean unlocked = false;

  /** Runtime cooldown, in seconds. */
  public float cooldownTimer;
  /** The player that started the current cast. */
  public @Nullable Player caster;
  public float aimX, aimY, angle;

  private final Seq<SkillEntity> activeEntities = new Seq<>();
  private TextureRegion loadedIcon;
  private boolean iconLoaded;

  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (Skill skill : list) {
        skill.loadIcon();
        skill.refreshLocalization();
      }
    });
    Events.run(Trigger.update, () -> {
      if (!Vars.state.isPlaying()) {
        return;
      }
      for (Skill skill : list) {
        skill.update(Time.delta);
      }
    });
  }

  public Skill(String name) {
    this.name = name;
    this.fullName = Vars.content.transformName(name);
    this.id = maxId.getAndIncrement();
    map.put(id, this);
    if (all.containsKey(name)) {
      throw new IllegalArgumentException("Two skills cannot have the same name! (issue: '" + name + "')");
    }
    all.put(name, this);
    list.add(this);
    this.localizedName = Core.bundle.get("skill." + name + ".name", name);
    this.description = Core.bundle.getOrNull("skill." + name + ".description");
    loadUnlock();
  }

  /**
   * Executes an instant skill. Aim data is available from the public fields on
   * this instance while this method is running.
   */
  public void influence() {
  }

  /**
   * Updates a continuous skill. This is called once when the entity is created
   * and once per game update afterwards.
   */
  public void influence(SkillEntity skillEntity) {
  }

  /** Called after a continuous skill entity has ended. */
  public void onEnd(SkillEntity skillEntity) {
  }

  /** Optional world rendering callback for a continuous skill. */
  public void draw(SkillEntity skillEntity) {
  }

  /**
   * Draws the target preview while the player is choosing where to cast. Skills
   * with a special footprint can override this without touching the HUD.
   */
  public void drawTargeting(Player player, float x, float y, boolean valid) {
    if (player == null) {
      return;
    }

    Color color = valid ? iconColor : Color.scarlet;
    Draw.z(Layer.flyingUnit + 1f);
    Draw.color(color, 0.14f);
    Fill.circle(x, y, targetAreaRadius);
    Draw.color(color, 0.72f);
    Lines.stroke(2f);
    Lines.circle(x, y, targetAreaRadius);

    float angle = Mathf.angle(x - player.x(), y - player.y());
    Lines.stroke(1.5f);
    Lines.line(player.x(), player.y(),
        x - Mathf.cosDeg(angle) * targetAreaRadius,
        y - Mathf.sinDeg(angle) * targetAreaRadius);
    Draw.reset();
  }

  /**
   * Checks a candidate world-space target. This deliberately uses the same
   * range check as actual casting so the preview and cast cannot disagree.
   */
  public boolean validTarget(@Nullable Player player, float x, float y) {
    if (!targeted()) {
      return true;
    }
    if (player == null) {
      return false;
    }
    float dx = x - player.x();
    float dy = y - player.y();
    return dx * dx + dy * dy <= targetRange * targetRange;
  }

  /** Whether the skill should appear in the radial HUD. */
  public boolean visible() {
    return unlocked();
  }

  @Override
  public boolean unlocked() {
    return unlocked;
  }

  /** Loads the persistent unlock flag from player settings. */
  public void loadUnlock() {
    if (Core.settings != null) {
      unlocked = Core.settings.getBool(unlockKey(), unlocked);
    }
  }

  /** Refreshes bundle-backed text after all mod bundles are available. */
  public void refreshLocalization() {
    if (Core.bundle != null) {
      localizedName = Core.bundle.get("skill." + name + ".name", name);
      description = Core.bundle.getOrNull("skill." + name + ".description");
    }
  }

  @Override
  public String unlockKey() {
    return "skill." + name + "-unlocked";
  }

  @Override
  public String displayName() {
    return localizedName == null || localizedName.isEmpty() ? name : localizedName;
  }

  @Override
  public @Nullable String displayDescription() {
    return description;
  }

  @Override
  public TextureRegion unlockIcon() {
    return uiIcon();
  }

  @Override
  public boolean unlock() {
    if (unlocked) {
      return false;
    }
    unlocked = true;
    if (Core.settings != null) {
      Core.settings.put(unlockKey(), true);
      Core.settings.manualSave();
    }
    Events.fire(new SkillUnlockedEvent(this));
    return true;
  }

  public void lock() {
    unlocked = false;
    if (Core.settings != null) {
      Core.settings.put(unlockKey(), false);
      Core.settings.manualSave();
    }
  }

  public boolean ready() {
    return visible()
        && cooldownTimer <= 0f
        && CultivationState.availableMagicPower >= baseCost
        && !isActive();
  }

  public boolean isActive() {
    return caster != null && isActive(caster);
  }

  public boolean isActive(Player player) {
    for (SkillEntity entity : activeEntities) {
      if (entity.isAdded() && entity.ownerId == player.id) {
        return true;
      }
    }
    return false;
  }

  /** Reattaches a deserialized entity to this skill's active-entity list. */
  public void trackEntity(SkillEntity entity) {
    if (entity != null && !activeEntities.contains(entity)) {
      activeEntities.add(entity);
    }
  }

  /**
   * Casts this skill for {@code player}. Instant skills enter through
   * {@link #influence()}; continuous skills allocate a {@link SkillEntity} and
   * enter through {@link #influence(SkillEntity)}.
   *
   * @return whether a cast was started
   */
  public boolean cast(@Nullable Player player, float x, float y, float angle) {
    if (player == null || !canCast(player) || !validTarget(player, x, y)) {
      return false;
    }

    caster = player;
    aimX = x;
    aimY = y;
    this.angle = angle;

    if (cost() > 0f) {
      CultivationState.availableMagicPower = Math.max(
          0f, CultivationState.availableMagicPower - cost());
    }
    if (cooldown() > 0f) {
      cooldownTimer = cooldown();
    }

    if (continuous()) {
      SkillEntity entity = SkillEntity.create();
      entity.skill = this;
      entity.skillId = id;
      entity.ownerId = player.id;
      entity.team = player.team();
      entity.targetX = x;
      entity.targetY = y;
      entity.targetAngle = angle;
      entity.set(x, y);
      entity.lifetime = duration <= 0f ? Float.POSITIVE_INFINITY : duration * 60f;
      entity.add();
      trackEntity(entity);
      influence(entity);
      return entity.isAdded();
    }

    influence();
    return true;
  }

  /** Whether a cast may start. Subclasses can add resource or state checks. */
  public boolean canCast(Player player) {
    return visible() && cooldownTimer <= 0f
        && CultivationState.availableMagicPower >= cost()
        && !(continuous() && isActive(player));
  }

  public boolean continuous() {
    return continuous;
  }

  /** Whether this skill requires a world-space cast point. */
  public boolean targeted() {
    return targeted;
  }

  public float cooldown() {
    return baseCooldown;
  }

  public float cost() {
    return baseCost;
  }

  /** Updates cooldown and removes expired continuous entities. */
  public void update(float delta) {
    cooldownTimer = Math.max(0f, cooldownTimer - delta / 60f);
    for (int i = activeEntities.size - 1; i >= 0; i--) {
      SkillEntity entity = activeEntities.get(i);
      if (!entity.isAdded()) {
        activeEntities.remove(i);
        onEnd(entity);
      }
    }
  }

  /** Cooldown fraction: 1 right after casting, 0 when ready. */
  public float cooldownProgress() {
    float cooldown = cooldown();
    return cooldown <= 0f ? 0f : Math.max(0f, Math.min(1f, cooldownTimer / cooldown));
  }

  /** Ends every active entity belonging to this skill. */
  public void endActive() {
    for (int i = activeEntities.size - 1; i >= 0; i--) {
      SkillEntity entity = activeEntities.get(i);
      activeEntities.remove(i);
      if (entity.isAdded()) {
        entity.remove();
      }
      onEnd(entity);
    }
  }

  public void loadIcon() {
    if (iconLoaded) {
      return;
    }
    TextureRegion found = Core.atlas.find("skill-" + fullName);
    loadedIcon = found.found() ? found : Icon.bookOpen.getRegion();
    icon = loadedIcon;
    iconLoaded = true;
  }

  /** Sets an already-resolved icon and marks loading complete. */
  protected void setIcon(TextureRegion region) {
    loadedIcon = region;
    icon = region;
    iconLoaded = true;
  }

  public TextureRegion uiIcon() {
    if (!iconLoaded) {
      loadIcon();
    }
    return icon;
  }

  public boolean usingFallbackIcon() {
    if (!iconLoaded) {
      loadIcon();
    }
    return loadedIcon == Icon.bookOpen.getRegion();
  }

  /**
   * Entity-backed runtime for continuous skills. The public fields are
   * synchronized normally; the live skill reference is rebuilt from
   * {@link #skillId} after loading a save.
   */
  @EntityDef({ SkillEntityc.class })
  @EntityComponent
  static abstract class SkillEntityComp implements Drawc, Timedc, Teamc {
    /** Serialized registry id used to restore the skill reference. */
    public int skillId = -1;
    public int ownerId = -1;
    public float targetX, targetY, targetAngle;
    /** True after the first influence call for this entity. */
    public boolean started;

    public transient Skill skill;

    @Override
    public void update() {
      if (skill == null) {
        skill = Skill.map.get(skillId);
      }
      if (skill != null) {
        SkillEntity entity = (SkillEntity) (Object) this;
        skill.trackEntity(entity);
        skill.influence(entity);
      }
    }

    @Override
    public void draw() {
      if (skill == null) {
        skill = Skill.map.get(skillId);
      }
      if (skill != null) {
        skill.draw((SkillEntity) (Object) this);
      }
    }
  }
}
