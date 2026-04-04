package crystal.type;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.util.Nullable;
import crystal.CVars;
import mindustry.Vars;
import mindustry.type.ItemStack;

public class GongFa {
  public static ObjectMap<String, GongFa> gongFas = new ObjectMap<>();
  public String name;
  public int id;
  public String localizedName;
  public @Nullable String description;
  protected boolean unlocked;
  public ItemStack[] researchRequirements = ItemStack.empty;
  public TextureRegion uiIcon;

  public GongFa(String name, int id) {
    this.name = Vars.content.transformName(name);
    this.id = id;
    handleMap(this);
    this.localizedName = Core.bundle.get("gongfa" + "." + this.name + ".name", this.name);
    this.description = Core.bundle.getOrNull("gongfa" + "." + this.name + ".description");
    this.unlocked = Core.settings != null && Core.settings.getBool(this.name + "-unlocked", false);
    this.uiIcon = Core.atlas.find("gongfa-" + name);
    if (unlocked)
      CVars.gongfaHave.add(this);
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
  }

  public boolean unlocked() {
    return unlocked;
  }

  public boolean unlockedHost() {
    return Vars.net.client() ? unlocked : unlocked;
  }
}
