package crystal.core;

import static mindustry.Vars.*;

import arc.Core;
import crystal.content.CIcons;

public class CSettings {
  public static void load() {
    ui.settings.addCategory(Core.bundle.get("settings.crystal"), CIcons.crystalCore, table -> {
      table.checkPref("showXiuWei", true);
    });
  }
}
