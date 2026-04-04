package crystal.ui;

import arc.scene.ui.ImageButton.ImageButtonStyle;
import crystal.graphics.CPal;

import static mindustry.ui.Styles.*;

public class CStyles {
  public static ImageButtonStyle lightBlueButton;

  public static void load() {
    lightBlueButton = new ImageButtonStyle() {
      {
        down = flatDown;
        up = black6;
        over = flatOver;
        disabled = black8;
        imageDisabledColor = CPal.dark_blue2;
        imageUpColor = CPal.light_blue1;
      }
    };
  }
}
