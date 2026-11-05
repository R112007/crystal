package crystal.ui;

import arc.Core;
import arc.Events;
import arc.scene.style.TextureRegionDrawable;
import crystal.game.CEventType.ScrollObtainedEvent;
import crystal.game.CEventType.ScrollUsedEvent;
import crystal.ui.dialogs.SkillDetailDialog;
import mindustry.Vars;

/** Event-driven visual feedback for scroll acquisition and consumption. */
public final class ScrollUI {
  private static boolean initialized;

  private ScrollUI() {
  }

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;

    Events.on(ScrollObtainedEvent.class, event -> {
      if (Vars.headless || Vars.ui == null) {
        return;
      }
      Core.app.post(() -> {
        if (Vars.ui.hudfrag != null) {
          String text = Core.bundle.format(
              "scroll.obtained",
              event.amount,
              event.scroll.localizedName);
          Vars.ui.hudfrag.showToast(
              new TextureRegionDrawable(event.scroll.uiIcon()),
              54f,
              text);
        }
      });
    });

    Events.on(ScrollUsedEvent.class, event -> {
      if (Vars.headless || Vars.ui == null) {
        return;
      }
      Core.app.post(() -> new SkillDetailDialog(event.skill, true).show());
    });
  }
}
