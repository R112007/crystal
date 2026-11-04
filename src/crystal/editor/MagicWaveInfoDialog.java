package crystal.editor;

import arc.Core;
import arc.scene.Action;
import arc.scene.Scene;
import arc.scene.ui.Dialog;
import mindustry.editor.WaveInfoDialog;

public class MagicWaveInfoDialog extends WaveInfoDialog {

  public MagicWaveInfoDialog() {
    // 调用父类构造，构造出来的 UI 实际上不会显示
  }

  @Override
  public Dialog show(Scene stage, Action action) {
    return new MagicWaveEditor().show(stage, action);
  }

  @Override
  public Dialog show() {
    return new MagicWaveEditor().show();
  }

  @Override
  public Dialog show(Scene stage) {
    return new MagicWaveEditor().show(stage);
  }
}
