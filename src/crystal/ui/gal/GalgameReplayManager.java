package crystal.ui.gal;

import arc.struct.Seq;
import mindustry.Vars;

/**
 * 专门用于回放模块副本的 Manager。
 * 与主 GalgameDialogueManager 完全隔离，不注册战役事件、不创建继续按钮、不修改原模块进度。
 */
public class GalgameReplayManager extends GalgameDialogueManager {
  public static final GalgameReplayManager instance = new GalgameReplayManager();

  private GalgameReplayManager() {
    super(true);
  }

  /**
   * 回放已完成的模块副本：不影响原模块的进度与完成状态，可反复播放。
   */
  @Override
  public void replayModule(DialogueModule module) {
    if (module == null) {
      Vars.ui.showErrorMessage("存在无效模块");
      return;
    }
    module.loadModuleData();
    if (!module.isCompleted) {
      Vars.ui.showInfo("该模块尚未完成，无法回放。");
      return;
    }
    try {
      hide();
      isPlayingModule = true;
      currentLine = null;
      lastPlayedCharacterId = null;
      isAutoPlay = false;
      isTyping = false;
      ui.reset();
      dialogueQueue.clear();
      currentModuleId = null;

      Seq<DialogueLine> copies = module.createReplayCopies();
      if (copies == null || copies.isEmpty()) {
        Vars.ui.showInfo("该模块没有可回放内容。");
        isPlayingModule = false;
        hide();
        return;
      }
      dialogueQueue.addAll(copies);

      show();
      if (!dialogueQueue.isEmpty()) {
        nextLine();
      } else {
        isPlayingModule = false;
        hide();
      }
    } finally {
      // 回放 Manager 不需要处理等待队列
    }
  }
}
