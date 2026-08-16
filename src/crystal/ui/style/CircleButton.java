package crystal.ui.style;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;

/**
 * 圆形图像按钮。
 * 命中区域为圆形，并自带圆形背景绘制，可直接用于导弹模式等军事风格 UI。
 */
public class CircleButton extends ImageButton {

  /** 按钮背景色（不影响图标颜色） */
  public Color backgroundColor = Color.darkGray;
  /** 是否绘制圆形背景 */
  public boolean drawBackground = true;
  /** 是否绘制外圈描边 */
  public boolean drawStroke = true;
  /** 描边颜色 */
  public Color strokeColor = Color.gray;
  /** 描边宽度 */
  public float strokeWidth = 2f;

  public CircleButton(Drawable icon, ImageButtonStyle style) {
    super(icon, style);
    style.up = style.down = style.checked = style.over = null;
  }

  @Override
  public void draw() {
    float cx = x + getWidth() / 2f;
    float cy = y + getHeight() / 2f;
    float radius = Math.min(getWidth(), getHeight()) / 2f;
    if (isPressed()) {
      Draw.color(Color.valueOf("#000000"));
      Fill.circle(cx, cy, radius);
    }
    if (drawBackground) {
      Draw.color(backgroundColor);
      Fill.circle(cx, cy, radius);
      Draw.color();
    }

    if (drawStroke) {
      Draw.color(strokeColor);
      arc.graphics.g2d.Lines.stroke(strokeWidth);
      arc.graphics.g2d.Lines.circle(cx, cy, radius - strokeWidth / 2f);
      Draw.color();
    }

    // 调用父类绘制图标，图标颜色由 setColor() 控制
    super.draw();
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
