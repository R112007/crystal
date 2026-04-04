package crystal.ui;

import arc.Core;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;

public class UITools {
  public static void addBottomButton(String name, TextureRegionDrawable icon, Drawable drawable, Runnable run,
      BaseDialog baseDialog) {
    Button button = new Button(drawable);
    button.button(name, icon, run);
    Core.app.post(() -> {
      baseDialog.buttons.removeChild(button);
      baseDialog.buttons.add(button).pad(2).bottom();
    });
  }
}
