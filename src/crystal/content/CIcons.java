package crystal.content;

import arc.scene.style.TextureRegionDrawable;

import static arc.Core.atlas;

public class CIcons {
    public static TextureRegionDrawable crystalCore, onefire, timesfire, shentong;

    public static void load() {
        crystalCore = atlas.getDrawable("crystal-crystal-core");
        onefire = atlas.getDrawable("crystal-onefire");
        timesfire = atlas.getDrawable("crystal-timesfire");
        shentong = atlas.getDrawable("crystal-shentong");
    }
}
