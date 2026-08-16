package crystal.content;

import arc.scene.style.TextureRegionDrawable;
import static arc.Core.atlas;
import static mindustry.gen.Icon.icons;

public class CIcons {
  public static TextureRegionDrawable crystalCore, onefire, timesfire;

  public static void load() {
    crystalCore = atlas.getDrawable("crystal-crystal-core");
    onefire = atlas.getDrawable("crystal-onefire");
    timesfire = atlas.getDrawable("crystal-timesfire");
    icons.put("crystalCore", crystalCore);
  }
}
