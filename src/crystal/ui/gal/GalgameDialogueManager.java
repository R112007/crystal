package crystal.ui.gal;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Timer;
import crystal.CVars;
import crystal.Crystal;
import crystal.audio.CMusics;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.game.CEventType.SectorEnterEvent;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.gen.Tex;
import mindustry.io.JsonIO;
import mindustry.ui.Styles;

/**
 * Galgame 对话系统总控。
 * 负责模块管理、播放队列、自动播放、剧情回溯、存档持久化、战役模式进度恢复。
 */
public class GalgameDialogueManager {
  public static final GalgameDialogueManager instance = new GalgameDialogueManager();

  private static final String KEY_CURRENT_MODULE = "gal_current_module_id";
  private static final String KEY_WAITING_QUEUE = "gal_waiting_module_queue";

  public final Seq<String> waitingModuleIds = new Seq<>();
  public final Seq<DialogueLine> dialogueQueue = new Seq<>();
  public final Seq<DialogueModule> modules = new Seq<>();
  public final GalgameDialogueUI ui;
  public final DialogueHistoryUI historyUI;
  public final GalDebugDialog debugDialog;

  public boolean cachedAutoPlayBeforeOption = false;
  public boolean isShowing = false;
  public boolean isPlaying = false;
  public boolean isAutoPlay = false;
  public boolean isTyping = false;
  public boolean isPlayingModule = false;

  public float autoPlayInterval = 3.5f;
  public float typingSpeed = 25f;

  public DialogueLine currentLine;
  public Timer.Task autoPlayTask;
  public Table continuePlayTable;
  public String currentModuleId;
  public String lastPlayedCharacterId;

  /** 标记当前是否为回放专用 Manager，用于跳过战役相关事件与继续按钮。 */
  protected boolean isReplayManager = false;

  protected GalgameDialogueManager() {
    this(false);
  }

  protected GalgameDialogueManager(boolean replay) {
    this.isReplayManager = replay;
    this.ui = new GalgameDialogueUI(this);
    this.historyUI = replay ? null : new DialogueHistoryUI();
    this.debugDialog = replay ? null : new GalDebugDialog();
    if (!replay) {
      registerEvents();
    }
    Core.app.post(() -> {
      loadCurrentModule();
      if (!replay) {
        ensureContinueButton();
        updateContinueButtonVisibility();
      }
    });
  }

  // ==================== 事件注册 ====================
  public void registerEvents() {
    Events.on(SectorChangeEvent.class, e -> {
      hide();
      saveCurrentModule();
      saveWaitingQueue();
    });
    Events.on(StateChangeEvent.class, e -> {
      if (e.to == State.menu) {
        hide();
        saveCurrentModule();
        saveWaitingQueue();
      }
    });
    Events.on(SectorEnterEvent.class, e -> {
      updateContinueButtonVisibility();
      // 战役模式下，若存在未完成进度，自动继续播放
      if (Vars.state.isCampaign() && currentModuleId != null) {
        DialogueModule module = getModule(currentModuleId);
        if (module != null && !module.isCompleted && module.progressIndex > 0) {
          playModule(module);
        }
      }
    });
    Events.on(EventType.DisposeEvent.class, e -> {
      saveCurrentModule();
      saveWaitingQueue();
    });
    Events.run(EventType.Trigger.update, () -> {
      if (Crystal.timer % 180 == 0) {
        processWaitingQueue();
      }
    });
  }

  // ==================== 继续播放按钮 ====================
  public void updateContinueButtonVisibility() {
    if (continuePlayTable == null)
      return;
    boolean showSwitch = Core.settings.getBool("showContinueButton", true);
    boolean hasUnfinished = false;
    for (DialogueModule m : modules) {
      if (m == null)
        continue;
      if (!m.isCompleted && m.progressIndex > 0 && m.progressIndex < m.dialogueNodes.size && m.dialogueNodes.size > 0) {
        hasUnfinished = true;
        break;
      }
    }
    continuePlayTable.visible = showSwitch && hasUnfinished && !isShowing;
  }

  public void ensureContinueButton() {
    if (continuePlayTable == null) {
      continuePlayTable = new Table();
      continuePlayTable.bottom().left();
      Table buttonContainer = new Table();
      buttonContainer.background(Tex.pane);
      buttonContainer.button(Core.bundle.get("contineStory"), Styles.flatt, () -> {
        DialogueModule target = getModule(currentModuleId);
        if (target == null) {
          for (DialogueModule m : modules) {
            if (m != null && !m.isCompleted && m.progressIndex > 0) {
              target = m;
              currentModuleId = m.moduleId;
              break;
            }
          }
        }
        if (target != null) {
          playModule(target);
        }
        continuePlayTable.visible = false;
        updateContinueButtonVisibility();
      }).size(120f, 60f);
      continuePlayTable.add(buttonContainer).size(120f, 60f).pad(60f);
      Vars.ui.hudGroup.addChild(continuePlayTable);
    }
  }

  // ==================== 等待队列 ====================
  public void processWaitingQueue() {
    if (isPlayingModule || isShowing || waitingModuleIds.isEmpty() || Vars.state.isMenu() || !Vars.state.isCampaign()) {
      return;
    }
    String nextModuleId = waitingModuleIds.remove(0);
    DialogueModule nextModule = getModule(nextModuleId);
    if (nextModule == null || nextModule.isCompleted) {
      DLog.err("等待队列模块无效或已完成，自动跳过：" + nextModuleId);
      saveWaitingQueue();
      processWaitingQueue();
      return;
    }
    DLog.info("自动执行等待队列模块：" + nextModuleId);
    playModule(nextModule);
    saveWaitingQueue();
  }

  public void saveWaitingQueue() {
    if (waitingModuleIds.isEmpty()) {
      Core.settings.remove(KEY_WAITING_QUEUE);
    } else {
      String queueJson = JsonIO.json.toJson(waitingModuleIds);
      Core.settings.put(KEY_WAITING_QUEUE, queueJson);
    }
    Core.settings.autosave();
  }

  public void loadWaitingQueue() {
    waitingModuleIds.clear();
    String queueJson = Core.settings.getString(KEY_WAITING_QUEUE, "[]");
    try {
      Seq<String> savedIds = JsonIO.json.fromJson(Seq.class, queueJson);
      if (savedIds != null && !savedIds.isEmpty()) {
        savedIds.each(id -> {
          if (getModule(id) != null)
            waitingModuleIds.add(id);
        });
        DLog.info("恢复等待队列，共" + waitingModuleIds.size + "个模块");
      }
    } catch (Exception e) {
      DLog.err("等待队列恢复失败", e);
      waitingModuleIds.clear();
    }
  }

  public void clearWaitingQueue() {
    waitingModuleIds.clear();
    saveWaitingQueue();
  }

  // ==================== 模块管理 ====================
  public void addModules(String modulePrefix, Seq<DialogueModule> moduleList) {
    for (int i = 0; i < moduleList.size; i++) {
      DialogueModule module = moduleList.get(i);
      module.moduleId = modulePrefix + (i + 1);
      module.setNodeIds();
      modules.add(module);
    }
  }

  public DialogueModule getModule(String moduleId) {
    return modules.find(m -> m.moduleId.equals(moduleId));
  }

  /**
   * 通过模块 ID 播放。推荐使用 {@link #playModule(DialogueModule)} 直接传入模块对象，
   * 可避免字符串 ID 拼写错误。
   */
  public void playModule(String moduleId) {
    DialogueModule module = getModule(moduleId);
    if (module == null) {
      Vars.ui.showErrorMessage(moduleId + " 是无效模块ID");
      return;
    }
    playModule(module);
  }

  /** 打开模块副本陈列馆。 */
  public void openModuleGallery() {
    new ModuleGalleryDialog().music(CMusics.memoryGallery).looping(true).volume(0.7f).show();
  }

  /** 直接播放模块对象。 */
  public void playModule(DialogueModule module) {
    if (module == null) {
      Vars.ui.showErrorMessage("存在无效模块");
      return;
    }
    DLog.info("即将尝试播放模块 " + module.moduleId);
    if (isPlayingModule) {
      if (module.isCompleted || waitingModuleIds.contains(module.moduleId))
        return;
      waitingModuleIds.add(module.moduleId);
      saveWaitingQueue();
      return;
    }
    try {
      hide();
      isPlayingModule = true;
      module.loadModuleData();
      currentLine = null;
      lastPlayedCharacterId = null;
      isAutoPlay = false;
      isTyping = false;
      ui.reset();
      dialogueQueue.clear();
      currentModuleId = module.moduleId;
      for (int i = module.progressIndex; i < module.dialogueNodes.size; i++) {
        dialogueQueue.add(module.dialogueNodes.get(i));
      }
      show();
      if (!dialogueQueue.isEmpty()) {
        nextLine();
      } else {
        module.progressIndex = module.dialogueNodes.size;
        module.isCompleted = true;
        module.saveModuleData();
        isPlayingModule = false;
        hide();
      }
      if (continuePlayTable != null)
        continuePlayTable.visible = false;
    } finally {
      processWaitingQueue();
    }
  }

  /**
   * 回放已完成的模块副本：不影响原模块的进度与完成状态，可反复播放。
   */
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
      currentModuleId = null; // 副本回放不修改原模块进度

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
      if (continuePlayTable != null)
        continuePlayTable.visible = false;
    } finally {
      processWaitingQueue();
    }
  }

  // ==================== 核心播放逻辑 ====================
  public void play(DialogueLine line) {
    dialogueQueue.clear();
    dialogueQueue.add(line);
    show();
    nextLine();
  }

  public void playQueue(Seq<DialogueLine> lines) {
    dialogueQueue.clear();
    dialogueQueue.addAll(lines);
    show();
    nextLine();
  }

  public void appendLine(DialogueLine line) {
    boolean wasQueueEmpty = dialogueQueue.isEmpty();
    dialogueQueue.add(line);
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        module.dialogueNodes.add(line);
        line.moduleId = module.moduleId;
        line.nodeId = module.moduleId + "-" + module.dialogueNodes.size;
        module.isCompleted = false;
      }
    }
    if (wasQueueEmpty && !isPlaying && !isTyping) {
      show();
      nextLine();
    }
    saveCurrentModule();
  }

  public void appendLines(Seq<DialogueLine> branchLines) {
    if (branchLines == null || branchLines.isEmpty())
      return;
    boolean wasQueueEmpty = dialogueQueue.isEmpty();
    dialogueQueue.addAll(branchLines);
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        int originalSize = module.dialogueNodes.size;
        module.dialogueNodes.addAll(branchLines);
        for (int i = 0; i < branchLines.size; i++) {
          DialogueLine line = branchLines.get(i);
          line.moduleId = module.moduleId;
          line.nodeId = module.moduleId + "-" + (originalSize + i + 1);
        }
        module.isCompleted = false;
      }
    }
    if (wasQueueEmpty && !isPlaying && !isTyping) {
      show();
      nextLine();
    }
    saveCurrentModule();
  }

  public void show() {
    if (Vars.state.isMenu() || CVars.cui.cplanet.isShown())
      return;
    if (isShowing)
      return;
    isShowing = true;
    Core.scene.root.addChild(ui);
    ui.updateVisibility();
    updateContinueButtonVisibility();
  }

  public void hide() {
    if (!isShowing)
      return;
    isShowing = false;
    isPlaying = false;
    isTyping = false;
    isAutoPlay = false;
    cachedAutoPlayBeforeOption = false;
    stopAutoPlay();
    dialogueQueue.clear();
    currentLine = null;
    lastPlayedCharacterId = null;
    ui.remove();
    ui.reset();
    updateContinueButtonVisibility();
    isPlayingModule = false;
    Time.runTask(120f, () -> {
      if (isPlayingModule || isShowing || waitingModuleIds.isEmpty() || Vars.state.isMenu()
          || !Vars.state.isCampaign()) {
        return;
      }
      Vars.ui.showConfirm(Core.bundle.format("havewaittingmodule", getModule(waitingModuleIds.first()).moduleName),
          () -> processWaitingQueue());
    });
  }

  /** 推进到下一句对话。 */
  public void nextLine() {
    if (isTyping) {
      ui.finishTyping();
      return;
    }
    stopAutoPlay();

    if (dialogueQueue.isEmpty()) {
      if (currentModuleId != null) {
        DialogueModule module = getModule(currentModuleId);
        if (module != null) {
          module.progressIndex = module.dialogueNodes.size;
          module.isCompleted = true;
          saveCurrentModule();
        }
      }
      processWaitingQueue();
      hide();
      return;
    }

    currentLine = dialogueQueue.first();
    currentLine.refreshContent();

    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        module.advanceProgress();
        module.isCompleted = module.progressIndex >= module.dialogueNodes.size;
      }
    }

    dialogueQueue.remove(0);

    isPlaying = true;
    String currentCharacterId = currentLine.characterName;
    boolean sameChar = currentCharacterId != null && currentCharacterId.equals(lastPlayedCharacterId);

    ui.setDialogueLine(currentLine);

    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null)
        module.appendToHistory(currentLine);
    }

    if (!sameChar) {
      ui.playSpriteExitAction();
      ui.playSpriteEnterAction();
    }

    // 新版实例化动作优先
    if (currentLine.spriteAction != null) {
      currentLine.spriteAction.run(ui);
    } else if (sameChar && currentLine.spriteActionLegacy != null) {
      currentLine.spriteActionLegacy.run();
    }

    lastPlayedCharacterId = currentCharacterId;

    if (currentLine.beforePlay != null)
      currentLine.beforePlay.run();

    ui.startTyping();

    boolean hasOptions = currentLine.options != null && currentLine.options.length > 0;
    if (hasOptions) {
      cachedAutoPlayBeforeOption = isAutoPlay;
      ui.showOptions(currentLine.options);
      isAutoPlay = false;
      ui.updateAutoPlayButton();
      saveCurrentModule();
      return;
    }

    if (isAutoPlay)
      startAutoPlay();
    saveCurrentModule();
  }

  // ==================== 存档持久化 ====================
  public void saveCurrentModule() {
    DialogueModule module = getModule(currentModuleId);
    if (module != null) {
      module.saveModuleData();
      Core.settings.put(KEY_CURRENT_MODULE, currentModuleId);
      Core.settings.autosave();
    }
  }

  public void loadCurrentModule() {
    currentModuleId = Core.settings.getString(KEY_CURRENT_MODULE, null);
  }

  // ==================== 分支 ====================
  public void addBranch(Branch branch) {
    DialogueModule module = getModule(currentModuleId);
    if (module == null)
      return;
    if (!module.branchIds.contains(branch.id)) {
      module.addBranch(branch);
      dialogueQueue.addAll(branch.nodes);
    }
  }

  // ==================== 剧情回溯 ====================
  /**
   * 回溯到指定模块的指定节点。使用节点副本播放，不触发原回调。
   */
  public void backToNode(String moduleId, int nodeIndex) {
    DialogueModule module = getModule(moduleId);
    if (module == null)
      return;
    module.progressIndex = nodeIndex;
    module.isCompleted = false;
    currentModuleId = moduleId;
    dialogueQueue.clear();
    dialogueQueue.addAll(module.createPlaybackCopies(nodeIndex));
    show();
    nextLine();
  }

  public void backToLastNode() {
    if (currentModuleId == null)
      return;
    DialogueModule module = getModule(currentModuleId);
    if (module == null)
      return;
    int targetIndex = Math.max(0, module.progressIndex - 1);
    backToNode(currentModuleId, targetIndex);
  }

  public void backToModuleStart(String moduleId) {
    backToNode(moduleId, 0);
  }

  // ==================== 清除/重置 ====================
  public void clearAllHistory() {
    modules.each(m -> m.history.clear());
    modules.each(m -> m.playedNodeSet.clear());
  }

  public void clearModuleHistory(String moduleId) {
    DialogueModule module = getModule(moduleId);
    if (module == null) {
      Vars.ui.showErrorMessage(moduleId + " is a unknown id");
      return;
    }
    module.history.clear();
    module.playedNodeSet.clear();
  }

  public void resetAllProgress() {
    modules.each(DialogueModule::resetProgress);
    currentModuleId = null;
    lastPlayedCharacterId = null;
    Core.settings.remove(KEY_CURRENT_MODULE);
    clearWaitingQueue();
  }

  // ==================== 快速跳过 ====================
  public void fastSkipMainLine() {
    if (!isShowing || (dialogueQueue.isEmpty() && currentLine == null) || ui.optionTable.visible)
      return;
    isTyping = false;
    isAutoPlay = false;
    stopAutoPlay();
    ui.finishTyping();
    while (true) {
      if (dialogueQueue.isEmpty()) {
        hide();
        break;
      }
      DialogueLine nextLine = dialogueQueue.first();
      if (nextLine.options != null && nextLine.options.length > 0) {
        nextLine();
        break;
      }
      nextLine();
      if (!isShowing)
        break;
    }
    ui.updateAutoPlayButton();
    saveCurrentModule();
    updateContinueButtonVisibility();
  }

  public void fastForward() {
    if (!isShowing)
      return;
    if (isTyping) {
      ui.finishTyping();
      return;
    }
    nextLine();
  }

  // ==================== 自动播放 ====================
  public void toggleAutoPlay() {
    isAutoPlay = !isAutoPlay;
    ui.updateAutoPlayButton();
    if (isAutoPlay && !isTyping && (currentLine == null || currentLine.options == null)) {
      startAutoPlay();
    } else {
      stopAutoPlay();
    }
  }

  public void startAutoPlay() {
    stopAutoPlay();
    autoPlayTask = Timer.schedule(() -> Core.app.post(this::nextLine), autoPlayInterval);
  }

  public void stopAutoPlay() {
    if (autoPlayTask != null) {
      autoPlayTask.cancel();
      autoPlayTask = null;
    }
  }

  public void skipAll() {
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        module.progressIndex = module.dialogueNodes.size;
        module.isCompleted = true;
      }
    }
    dialogueQueue.clear();
    hide();
    saveCurrentModule();
    updateContinueButtonVisibility();
  }
}
