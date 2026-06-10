package crystal.type;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.util.Nullable;
import crystal.CVars;
import crystal.ui.dialogs.GongFaDialog;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.type.ItemStack;

public class GongFa {
  public static ObjectMap<String, GongFa> gongFas = new ObjectMap<>();
  public String name;
  public int id;
  public String localizedName;
  public @Nullable String description;
  protected boolean unlocked;
  public ItemStack[] researchRequirements = ItemStack.empty;
  // 移除构造函数里的纹理加载，改为懒加载+缓存
  private TextureRegion _uiIcon;
  private boolean iconLoaded = false;

  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (GongFa gongFa : gongFas.values()) {
        gongFa.unlocked = Core.settings.getBool(gongFa.name + "-unlocked", false);
        // 游戏加载完成后，预加载所有功法图标
        gongFa.loadIcon();
        if (gongFa.unlocked) {
          CVars.gongfaHave.add(gongFa);
        }
      }
    });
  }

  public GongFa(String name, int id) {
    this.name = Vars.content.transformName(name);
    this.id = id;
    handleMap(this);
    this.localizedName = Core.bundle.get("gongfa" + "." + this.name + ".name", this.name);
    this.description = Core.bundle.getOrNull("gongfa" + "." + this.name + ".description");
    // 构造函数里不再加载纹理，此时atlas还没初始化
  }

  // 新增：图标加载方法，正确判断纹理是否存在
  public void loadIcon() {
    if (iconLoaded)
      return;
    TextureRegion findRegion = Core.atlas.find("gongfa-" + name);
    // 正确判断：用found()方法，而不是判断null
    this._uiIcon = findRegion.found() ? findRegion : Icon.bookOpen.getRegion();
    this.iconLoaded = true;
  }

  // 新增：对外暴露uiIcon，保证永远返回有效纹理
  public TextureRegion uiIcon() {
    if (!iconLoaded)
      loadIcon();
    return _uiIcon;
  }

  public void handleMap(GongFa gongFa) {
    if (gongFas.keys().toSeq().contains(gongFa.name))
      throw new IllegalArgumentException(
          "Two gongfa cannot have the same name! (issue: '" + gongFa.name + "')");
    else
      gongFas.put(gongFa.name, gongFa);
  }

  public void unlock() {
    this.unlocked = true;
    Core.settings.put(name + "-unlocked", true);
    Core.settings.manualSave();
    CVars.gongfaHave.add(this);
    Events.fire(new GongFaDialog.GongFaUnlockEvent(this));
  }

  public void lock() {
    this.unlocked = false;
    Core.settings.put(name + "-unlocked", false);
    Core.settings.manualSave();
    CVars.gongfaHave.remove(this);
  }

  public boolean unlocked() {
    return unlocked;
  }

  public boolean unlockedHost() {
    return Vars.net.client() ? unlocked : unlocked;
  }
}
