package crystal.world.tumor;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;

/** Draws tumor regions through the engine's normal block scale instead of hard-coded dimensions. */
public final class TumorRender {
  private TumorRender() {}

  public static void draw(TextureRegion region, float x, float y, float rotation, float scale) {
    float oldX = Draw.xscl;
    float oldY = Draw.yscl;
    Draw.xscl *= scale;
    Draw.yscl *= scale;
    Draw.rect(region, x, y, rotation);
    Draw.xscl = oldX;
    Draw.yscl = oldY;
  }
}
