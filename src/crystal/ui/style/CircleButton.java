package crystal.ui.style;

import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;

public class CircleButton extends ImageButton {

  public CircleButton(Drawable icon, ImageButtonStyle style) {
    super(icon, style);
  }

  @Override
  public Element hit(float x, float y, boolean touchable) {
    if (super.hit(x, y, touchable) == null)
      return null;
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float radius = Math.min(getWidth(), getHeight()) / 2f;
    float dx = x - cx;
    float dy = y - cy;
    return (dx * dx + dy * dy <= radius * radius) ? this : null;
  }
}
