package crystal.ui.gal;

import arc.Core;
import arc.scene.ui.layout.Table;
import crystal.CVars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/** Bottom-left affordance for resuming partially played modules. */
final class DialogueContinueButton {
  private final GalgameDialogueManager manager;
  private Table root;

  DialogueContinueButton(GalgameDialogueManager manager) {
    this.manager = manager;
  }

  void ensure() {
    if (root != null) return;
    root = new Table();
    root.bottom().left();
    Table box = new Table();
    box.background(Tex.pane);
    box.button(CVars.plot.getOrBundle("contineStory"), Styles.flatt, manager::resumeLatestModule)
        .size(120f, 60f);
    root.add(box).size(120f, 60f).pad(60f);
    ui.hudGroup.addChild(root);
  }

  void update() {
    if (root == null) return;
    boolean enabled = Core.settings.getBool("showContinueButton", true);
    boolean unfinished = false;
    for (DialogueModule module : manager.modules) {
      if (module != null && !module.isCompleted && module.progressIndex > 0
          && module.progressIndex < module.dialogueNodes.size) {
        unfinished = true;
        break;
      }
    }
    root.visible = enabled && unfinished && !manager.isShowing();
  }

  void hide() {
    if (root != null) root.visible = false;
  }
}
