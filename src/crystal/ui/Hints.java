package crystal.ui;

import arc.Core;
import arc.func.Boolp;
import arc.util.Nullable;
import arc.util.Structs;
import crystal.ui.gal.*;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ui.fragments.HintsFragment;
import mindustry.ui.fragments.HintsFragment.Hint;

public enum Hints implements Hint {
  // TODO 安亦雨的提示
  pauseWatch(100, () -> GalgameDialogueManager.instance.isShowing, () -> !GalgameDialogueManager.instance.isShowing);

  @Nullable
  String text;
  Hint[] dependencies = {};
  int visibility = visibleAll;
  boolean finished, cached;
  Boolp complete, shown = () -> true;

  Hints(Boolp complete) {
    this.complete = complete;
  }

  Hints(int visiblity, Boolp complete) {
    this(complete);
    this.visibility = visiblity;
  }

  Hints(Boolp shown, Boolp complete) {
    this(complete);
    this.shown = shown;
  }

  Hints(int visiblity, Boolp shown, Boolp complete) {
    this(complete);
    this.shown = shown;
    this.visibility = visiblity;
  }

  @Override
  public boolean finished() {
    if (!cached) {
      cached = true;
      finished = Core.settings.getBool(name() + "-hint-done", false);
    }
    return finished;
  }

  @Override
  public void finish() {
    Core.settings.put(name() + "-hint-done", finished = true);
    Core.settings.forceSave();
    cached = true;
  }

  @Override
  public String text() {
    if (text == null) {
      text = Vars.mobile && Core.bundle.has("hint." + name() + ".mobile")
          ? Core.bundle.get("hint." + name() + ".mobile")
          : Core.bundle.get("hint." + name());
      if (!Vars.mobile)
        text = text.replace("tap", "click").replace("Tap", "Click");
    }
    return UI.formatIcons(text);
  }

  @Override
  public boolean complete() {
    return complete.get();
  }

  @Override
  public boolean show() {
    if (finished())
      return false;
    return shown.get() && (dependencies.length == 0 || !Structs.contains(dependencies, d -> !d.finished()));
  }

  @Override
  public int order() {
    return ordinal();
  }

  @Override
  public boolean valid() {
    return (Vars.mobile && (visibility & visibleMobile) != 0) || (!Vars.mobile && (visibility & visibleDesktop) != 0);
  }

  public static void load() {
    HintsFragment hints = Vars.ui.hints;
    for (Hint h : values()) {
      if (!hints.hints.contains(h)) {
        hints.hints.add(h);
      }
    }
  }
}
