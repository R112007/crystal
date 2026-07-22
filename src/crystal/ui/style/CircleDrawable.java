package crystal.ui.style;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.style.BaseDrawable;

public class CircleDrawable extends BaseDrawable {
  private Color color;

  public CircleDrawable(Color color) {
    this.color = color;
    setMinWidth(40f);
    setMinHeight(40f);
  }

  @Override
  public void draw(float x, float y, float width, float height) {
    Draw.color(color);
    Fill.circle(x + width / 2f, y + height / 2f, Math.min(width, height) / 2f);
    Draw.color();
  }
}
