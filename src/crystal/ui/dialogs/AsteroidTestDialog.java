package crystal.ui.dialogs;

import crystal.world.asteroid.AsteroidBeltSystem;
import mindustry.ui.dialogs.BaseDialog;

/** 小行星带调试面板：手动触发一波，或进入持续遭遇测试。 */
public class AsteroidTestDialog extends BaseDialog {
  public AsteroidTestDialog() {
    super("小行星测试");

    cont.defaults().size(260f, 52f).pad(6f);
    cont.add("[lightgray]Ctrl+Shift+B：立即触发一波\nCtrl+Shift+G：强制遭遇开/关").row();
    cont.button("立即触发一波", () -> AsteroidBeltSystem.debugTriggerWave()).row();
    cont.button("强制遭遇：开/关",
        () -> AsteroidBeltSystem.debugSetForced(!AsteroidBeltSystem.isDebugForced())).row();
    cont.button("关闭", this::hide).row();

    addCloseButton();
  }
}
