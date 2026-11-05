package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;

/**
 * A consumable scroll that unlocks one player skill. The target may be either a
 * {@link Skill} or a {@link ShenWu.Skill}.
 */
public class Scroll {
  private static final AtomicInteger maxId = new AtomicInteger();
  public static final ObjectMap<Integer, Scroll> map = new ObjectMap<>();
  public static final ObjectMap<String, Scroll> all = new ObjectMap<>();
  public static final Seq<Scroll> list = new Seq<>();

  public final int id;
  /** Stable registry and settings name. */
  public final String name;
  public final String fullName;
  public String localizedName;
  public @Nullable String description;
  public @Nullable SkillUnlockable targetSkill;

  private final String iconName;
  private final String defaultName;
  private TextureRegion icon;
  private boolean iconLoaded;

  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (Scroll scroll : list) {
        scroll.loadIcon();
        scroll.refreshLocalization();
      }
    });
  }

  public Scroll(String name, String defaultName, SkillUnlockable targetSkill) {
    this(name, defaultName, targetSkill, "scroll-" + name.toLowerCase());
  }

  public Scroll(String name, String defaultName, SkillUnlockable targetSkill, String iconName) {
    this.name = name;
    this.fullName = Vars.content == null ? name : Vars.content.transformName(name);
    this.id = maxId.getAndIncrement();
    this.targetSkill = targetSkill;
    this.iconName = iconName;
    this.defaultName = defaultName;
    map.put(id, this);
    if (all.containsKey(name)) {
      throw new IllegalArgumentException("Two scrolls cannot have the same name! (issue: '" + name + "')");
    }
    all.put(name, this);
    list.add(this);

    refreshLocalization();
  }

  public void loadIcon() {
    if (iconLoaded) {
      return;
    }
    TextureRegion found = findRegion(iconName);
    if (found == null) {
      found = findRegion("scroll-blank");
    }
    icon = found != null && found.found() ? found : Icon.bookOpen.getRegion();
    iconLoaded = true;
  }

  private TextureRegion findRegion(String name) {
    if (Core.atlas == null) {
      return null;
    }
    TextureRegion region = Core.atlas.find(name);
    if (region.found()) {
      return region;
    }
    region = Core.atlas.find("icon/scrolls/" + name);
    if (region.found()) {
      return region;
    }
    region = Core.atlas.find("crystal-" + name.toLowerCase());
    return region.found() ? region : null;
  }

  /** Refreshes bundle-backed text after all mod bundles are available. */
  public void refreshLocalization() {
    String fallbackName = targetSkill == null || targetSkill.displayName() == null
        ? defaultName
        : targetSkill.displayName() + defaultName;
    this.localizedName = Core.bundle == null
        ? fallbackName
        : Core.bundle.get("scroll." + name + ".name", fallbackName);
    String bundleDescription = Core.bundle == null
        ? null
        : Core.bundle.getOrNull("scroll." + name + ".description");
    this.description = bundleDescription != null
        ? bundleDescription
        : targetSkill == null ? null : targetSkill.displayDescription();
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
    return icon == Icon.bookOpen.getRegion();
  }
}
