package crystal.editor;

import arc.Core;
import arc.scene.Action;
import arc.scene.Scene;
import arc.scene.ui.Dialog;
import mindustry.editor.WaveInfoDialog;

/**
 * 包装器：继承原版 WaveInfoDialog，满足 MapInfoDialog.waveInfo 字段类型，
 * 实际显示的是我们自己的 MagicWaveEditor。
 */
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
