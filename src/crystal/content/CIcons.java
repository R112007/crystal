package crystal.content;

import arc.scene.style.TextureRegionDrawable;

import static arc.Core.atlas;

public class CIcons {
    public static TextureRegionDrawable crystalCore, onefire, timesfire, shentong, fatianxiangdi_icon, box, yi_normal, yi_relax;

    public static void load() {
        crystalCore = atlas.getDrawable("crystal-crystal-core");
        onefire = atlas.getDrawable("crystal-onefire");
        timesfire = atlas.getDrawable("crystal-timesfire");
        shentong = atlas.getDrawable("crystal-shentong");
        fatianxiangdi_icon = atlas.getDrawable("crystal-fatianxiangdi-icon");
        box = atlas.getDrawable("crystal-box");
        yi_normal = atlas.getDrawable("crystal-yi-normal");
        yi_relax = atlas.getDrawable("crystal-yi-relax");
    }
}
