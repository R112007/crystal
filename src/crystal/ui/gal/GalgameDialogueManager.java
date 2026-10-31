package crystal.ui.gal;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Timer;
import crystal.CVars;
import crystal.Crystal;
import crystal.audio.CMusics;
import crystal.core.CSettings;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.game.CEventType.SectorEnterEvent;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType;
import mindustry.game.EventType.StateChangeEvent;

/**
 * Coordinates story playback. Persistence, queues, continue UI and playback state are separate
 * collaborators so this class no longer owns every part of the GAL feature.
 */
public class GalgameDialogueManager {
  public static final GalgameDialogueManager instance = new GalgameDialogueManager();

  public final Seq<DialogueModule> modules = new Seq<>();
  final Seq<DialogueLine> dialogueQueue = new Seq<>();
  public final GalgameDialogueUI ui;
  public final DialogueHistoryUI historyUI;
  public final GalDebugDialog debugDialog;

  private final DialogueQueueController queues = new DialogueQueueController();
  private final DialogueContinueButton continueButton;
  protected final boolean replayManager;

  private DialoguePlaybackState playbackState = DialoguePlaybackState.idle;
  private boolean autoPlay;
  private boolean autoBeforeChoice;
  private Timer.Task autoPlayTask;
  private boolean hostedExternally;
  private Runnable externalClose;

  public float autoPlayInterval = 3.5f;
  public float typingSpeed = 25f;

  public DialogueLine currentLine;
  public String currentModuleId;
  public String lastPlayedCharacterId;

  protected GalgameDialogueManager() {
    this(false);
  }

  protected GalgameDialogueManager(boolean replay) {
    replayManager = replay;
    historyUI = replay ? null : new DialogueHistoryUI();
    debugDialog = replay ? null : new GalDebugDialog();
    continueButton = replay ? null : new DialogueContinueButton(this);
    ui = new GalgameDialogueUI(this);
    if (!replay) registerEvents();

    Core.app.post(() -> {
      loadCurrentModule();
      if (!replay) {
        continueButton.ensure();
        updateContinueButtonVisibility();
      }
    });
  }

  public DialoguePlaybackState playbackState() {
    return playbackState;
  }

  public boolean isShowing() {
    return playbackState.visible();
  }

  public boolean isPlaying() {
    return playbackState.visible();
  }

  public boolean isTyping() {
    return playbackState == DialoguePlaybackState.typing
        || playbackState == DialoguePlaybackState.typingChoice;
  }

  public boolean isAutoPlay() {
    return autoPlay;
  }

  public boolean isSessionActive() {
    return playbackState.sessionActive();
  }

  public boolean isReplayMode() {
    return replayManager;
  }

  public Seq<String> waitingModuleIds() {
    return queues.waiting;
  }

  public Seq<String> conditionalWaitingIds() {
    return queues.conditional;
  }

  public void hostExternally(Runnable closeAction) {
    hostedExternally = true;
    externalClose = closeAction;
  }

  public void externalHostClosed() {
    resetSession();
  }

  void beginTyping() {
    playbackState = playbackState == DialoguePlaybackState.typingChoice
        ? DialoguePlaybackState.typingChoice
        : DialoguePlaybackState.typing;
  }

  void markTypingFinished() {
    if (playbackState == DialoguePlaybackState.typing) {
      playbackState = DialoguePlaybackState.awaitingAdvance;
    } else if (playbackState == DialoguePlaybackState.typingChoice) {
      playbackState = DialoguePlaybackState.awaitingChoice;
    }
  }

  void markWaitingForChoice() {
    playbackState = isTyping()
        ? DialoguePlaybackState.typingChoice
        : DialoguePlaybackState.awaitingChoice;
  }

  protected boolean takeAutoBeforeChoice() {
    boolean result = autoBeforeChoice;
    autoBeforeChoice = false;
    return result;
  }

  void setAutoPlay(boolean value) {
    autoPlay = value;
    ui.updateAutoPlayButton();
  }

  // ==================== Events ====================
  public void registerEvents() {
    Events.on(SectorChangeEvent.class, e -> {
      hide();
      saveCurrentModule();
      queues.save();
    });
    Events.on(StateChangeEvent.class, e -> {
      if (e.to == State.menu) {
        hide();
        saveCurrentModule();
        queues.save();
      }
    });
    Events.on(SectorEnterEvent.class, e -> {
      updateContinueButtonVisibility();
      if (Vars.state.isCampaign() && currentModuleId != null) {
        DialogueModule module = getModule(currentModuleId);
        if (module != null && !module.isCompleted && module.progressIndex > 0) {
          playModule(module);
        }
      }
    });
    Events.on(EventType.DisposeEvent.class, e -> {
      saveCurrentModule();
      queues.save();
    });
    Events.run(EventType.Trigger.update, () -> {
      if (Crystal.timer % 180 == 0) processWaitingQueue();
    });
  }

  // ==================== Continue button ====================
  public void updateContinueButtonVisibility() {
    if (continueButton != null) continueButton.update();
  }

  public void ensureContinueButton() {
    if (continueButton != null) continueButton.ensure();
  }

  public void resumeLatestModule() {
    DialogueModule target = getModule(currentModuleId);
    if (target == null) {
      for (DialogueModule module : modules) {
        if (module != null && !module.isCompleted && module.progressIndex > 0) {
          target = module;
          break;
        }
      }
    }
    if (target != null) playModule(target);
    if (continueButton != null) continueButton.hide();
    updateContinueButtonVisibility();
  }

  // ==================== Queues ====================
  public void processWaitingQueue() {
    if (replayManager || isSessionActive() || !canShowDialogueUI() || !Vars.state.isCampaign()) {
      return;
    }

    while (!queues.waiting.isEmpty()) {
      String nextId = queues.poll();
      DialogueModule module = getModule(nextId);
      queues.save();
      if (module == null) {
        DLog.err("等待队列模块无效，自动跳过：" + nextId);
        continue;
      }
      DLog.info("自动执行等待队列模块：" + nextId);
      playModule(module);
      return;
    }
  }

  public void saveWaitingQueue() {
    queues.save();
  }

  public void loadWaitingQueue() {
    queues.load(id -> getModule(id) != null);
    queues.conditional.removeAll(id -> {
      DialogueModule module = getModule(id);
      return module == null || module.prerequisiteMet(this::getModule);
    });
    queues.save();
  }

  public void clearWaitingQueue() {
    queues.clear();
  }

  // ==================== Catalog ====================
  public void addModules(String modulePrefix, Seq<DialogueModule> moduleList) {
    for (int i = 0; i < moduleList.size; i++) {
      DialogueModule module = moduleList.get(i);
      module.moduleId = modulePrefix + (i + 1);
      module.setNodeIds();
      modules.add(module);
    }
  }

  public DialogueModule getModule(String moduleId) {
    if (moduleId == null) return null;
    return modules.find(module -> moduleId.equals(module.moduleId));
  }

  public void openModuleGallery() {
    new ModuleGalleryDialog().music(CMusics.memoryGallery).looping(true).volume(0.7f).show();
  }

  // ==================== Playback entry points ====================
  public void playModule(String moduleId) {
    DialogueModule module = getModule(moduleId);
    if (module == null) {
      Vars.ui.showErrorMessage(moduleId + " 是无效模块ID");
      return;
    }
    playModule(module);
  }

  public void playModule(DialogueModule module) {
    if (module == null) {
      Vars.ui.showErrorMessage("存在无效模块");
      return;
    }
    if (!module.prerequisiteMet(this::getModule)) {
      queues.awaitPrerequisite(module.moduleId);
      return;
    }
    if (isSessionActive()) {
      queues.queue(module.moduleId);
      return;
    }
    if (!canShowDialogueUI()) {
      queues.queue(module.moduleId);
      return;
    }

    boolean deferred = false;
    try {
      hide();
      playbackState = DialoguePlaybackState.preparing;
      module.loadModuleData();
      if (module.isCompleted && !CSettings.instance.allowReplayCompletedModules()) {
        playbackState = DialoguePlaybackState.idle;
        return;
      }
      if (module.isCompleted) module.resetProgress();

      if (!module.isCompleted && module.progressIndex > 0
          && module.progressIndex <= module.dialogueNodes.size) {
        DialogueLine last = module.dialogueNodes.get(module.progressIndex - 1);
        if (last != null && last.options != null && last.options.length > 0) {
          module.progressIndex--;
        }
      }

      resetRuntime();
      currentModuleId = module.moduleId;
      for (int i = module.progressIndex; i < module.dialogueNodes.size; i++) {
        dialogueQueue.add(module.dialogueNodes.get(i));
      }
      show();
      if (!isShowing()) {
        deferred = true;
        abortPreparedSession(module.moduleId);
        return;
      }
      if (dialogueQueue.isEmpty()) {
        finishCurrentModule();
        hide();
      } else {
        nextLine();
      }
      if (continueButton != null) continueButton.hide();
    } finally {
      if (!deferred && !isSessionActive()) processWaitingQueue();
    }
  }

  public void replayModule(DialogueModule module) {
    arc.util.Log.info("[GAL] replay enter replayManager=" + replayManager
        + ", module=" + (module == null ? "null" : module.moduleId));
    if (module == null) {
      Vars.ui.showErrorMessage("存在无效模块");
      return;
    }
    module.loadModuleData();
    arc.util.Log.info("[GAL] replay check completed=" + module.isCompleted
        + ", nodes=" + module.dialogueNodes.size
        + ", original=" + (module.originalMainNodes == null ? "null" : module.originalMainNodes.size));
    if (!module.isCompleted) {
      Vars.ui.showInfo("该模块尚未完成，无法回放。");
      return;
    }

    if (replayManager) {
      resetRuntime();
    } else {
      hide();
    }
    playbackState = DialoguePlaybackState.preparing;
    currentModuleId = null;
    Seq<DialogueLine> copies = module.createReplayCopies();
    arc.util.Log.info("[GAL] replay copies=" + copies.size);
    if (copies.isEmpty()) {
      playbackState = DialoguePlaybackState.idle;
      Vars.ui.showInfo("该模块没有可回放内容。");
      return;
    }
    dialogueQueue.addAll(copies);
    show();
    if (!isShowing()) {
      playbackState = DialoguePlaybackState.idle;
      dialogueQueue.clear();
      return;
    }
    nextLine();
    if (continueButton != null) continueButton.hide();
  }

  public void play(DialogueLine line) {
    currentModuleId = null;
    startStandalone(Seq.with(line));
  }

  public void playQueue(Seq<DialogueLine> lines) {
    currentModuleId = null;
    startStandalone(lines);
  }

  private void startStandalone(Seq<DialogueLine> lines) {
    hide();
    playbackState = DialoguePlaybackState.preparing;
    resetRuntime();
    dialogueQueue.addAll(lines);
    show();
    if (isShowing()) nextLine();
    else playbackState = DialoguePlaybackState.idle;
  }

  public void appendLine(DialogueLine line) {
    appendLines(Seq.with(line));
  }

  public void appendLines(Seq<DialogueLine> lines) {
    if (lines == null || lines.isEmpty()) return;
    boolean start = !isSessionActive() && dialogueQueue.isEmpty();
    dialogueQueue.addAll(lines);
    DialogueModule module = getModule(currentModuleId);
    if (module != null) {
      int originalSize = module.dialogueNodes.size;
      module.dialogueNodes.addAll(lines);
      for (int i = 0; i < lines.size; i++) {
        DialogueLine line = lines.get(i);
        line.moduleId = module.moduleId;
        line.nodeId = module.moduleId + "-" + (originalSize + i + 1);
      }
      module.isCompleted = false;
    }
    if (start) {
      playbackState = DialoguePlaybackState.preparing;
      show();
      if (isShowing()) nextLine();
    }
    saveCurrentModule();
  }

  // ==================== Visibility ====================
  public void show() {
    if (!canShowDialogueUI() || isShowing()) return;
    if (!hostedExternally) Core.scene.root.addChild(ui);
    if (playbackState == DialoguePlaybackState.preparing) {
      playbackState = DialoguePlaybackState.awaitingAdvance;
    }
    ui.updateVisibility();
    updateContinueButtonVisibility();
  }

  public boolean canShowDialogueUI() {
    // The gallery and its replay host are available from the main menu. A
    // dedicated replay owns the scene and must not be rejected by campaign UI
    // checks; otherwise replayModule() clears its queue and leaves a blank host.
    if (hostedExternally) return true;
    if (Vars.state.isMenu()) return false;
    return CVars.cui == null || CVars.cui.cplanet == null || !CVars.cui.cplanet.isShown();
  }

  public void hide() {
    if (replayManager) {
      arc.util.Log.info("[GAL] replay hide state=" + playbackState);
    }
    if (playbackState == DialoguePlaybackState.idle && ui.getScene() == null) return;
    resetSession();
    if (hostedExternally) {
      if (externalClose != null) externalClose.run();
    } else if (ui.getScene() != null) {
      ui.remove();
    }
  }

  private void resetSession() {
    final boolean wasActive = isSessionActive();
    playbackState = DialoguePlaybackState.idle;
    stopAutoPlay();
    autoPlay = false;
    autoBeforeChoice = false;
    dialogueQueue.clear();
    currentLine = null;
    lastPlayedCharacterId = null;
    if (!hostedExternally && ui.getScene() != null) ui.remove();
    ui.reset();
    updateContinueButtonVisibility();

    if (wasActive && !replayManager) {
      Time.runTask(120f, () -> {
        if (isSessionActive() || queues.waiting.isEmpty() || Vars.state.isMenu()
            || !Vars.state.isCampaign()) return;
        DialogueModule next = getModule(queues.waiting.first());
        if (next == null) return;
        Vars.ui.showConfirm(CVars.plot.formatOrBundle("havewaittingmodule", next.moduleName),
            this::processWaitingQueue);
      });
    }
  }

  // ==================== Line playback ====================
  public void nextLine() {
    if (isTyping()) {
      ui.finishTyping();
      return;
    }
    stopAutoPlay();
    if (dialogueQueue.isEmpty()) {
      if (replayManager) {
        arc.util.Log.info("[GAL] replay queue empty, hiding");
      }
      hide();
      finishCurrentModule();
      processWaitingQueue();
      return;
    }

    currentLine = dialogueQueue.remove(0);
    currentLine.refreshContent();
    DialogueModule module = getModule(currentModuleId);
    if (module != null) module.advanceProgress();

    String currentCharacter = currentLine.characterName;
    boolean sameCharacter = currentCharacter != null && currentCharacter.equals(lastPlayedCharacterId);
    ui.setDialogueLine(currentLine);
    if (module != null) module.appendToHistory(currentLine);
    if (ui.activeSpriteAppeared()) ui.playSpriteEnterAction();

    if (currentLine.spriteAction != null) {
      currentLine.spriteAction.run(ui);
    } else if (sameCharacter && currentLine.spriteActionLegacy != null) {
      currentLine.spriteActionLegacy.run();
    }
    lastPlayedCharacterId = currentCharacter;
    if (currentLine.beforePlay != null) currentLine.beforePlay.run();

    ui.startTyping();
    if (hasOptions(currentLine)) {
      autoBeforeChoice = autoPlay;
      ui.showOptions(currentLine.options);
      markWaitingForChoice();
      setAutoPlay(false);
      saveCurrentModule();
      return;
    }
    if (autoPlay) startAutoPlay();
    saveCurrentModule();
  }

  private boolean hasOptions(DialogueLine line) {
    return line.options != null && line.options.length > 0;
  }

  public void selectOption(DialogueLine.DialogueOption option) {
    if (option == null) return;
    boolean restoreAuto = takeAutoBeforeChoice();
    if (option.branch != null) {
      String selectedId = option.branch.id;
      dialogueQueue.removeAll(line -> line.branchId != null && !line.branchId.equals(selectedId));
      if (replayManager) {
        dialogueQueue.removeAll(line -> line.branchId != null && line.branchId.equals(selectedId));
        Seq<DialogueLine> copies = option.branch.createReplayCopies();
        for (int i = copies.size - 1; i >= 0; i--) dialogueQueue.insert(0, copies.get(i));
      } else if (option.onSelect == null) {
        addBranch(option.branch);
      }
    }
    if (option.onSelect != null) option.onSelect.get(option);
    if (isTyping()) ui.finishTyping();
    Core.app.post(this::nextLine);
    if (restoreAuto) {
      setAutoPlay(true);
      startAutoPlay();
    }
  }

  // ==================== Module completion/persistence ====================
  public void saveCurrentModule() {
    DialogueModule module = getModule(currentModuleId);
    if (module == null) return;
    module.saveModuleData();
    DialoguePersistence.saveCurrentModuleId(currentModuleId);
  }

  protected void finishCurrentModule() {
    DialogueModule module = getModule(currentModuleId);
    if (module == null) return;
    module.progressIndex = module.dialogueNodes.size;
    module.isCompleted = true;
    saveCurrentModule();
    checkConditionalTriggers(currentModuleId);
  }

  public void checkConditionalTriggers(String completedModuleId) {
    Seq<String> released = queues.releaseDependents(completedModuleId, this::getModule);
    for (String id : released) {
      if (isSessionActive()) queues.queue(id);
      else playModule(id);
    }
  }

  public void loadCurrentModule() {
    currentModuleId = DialoguePersistence.loadCurrentModuleId();
  }

  public void addBranch(Branch branch) {
    DialogueModule module = getModule(currentModuleId);
    if (module == null || branch == null) return;
    if (!module.branchIds.contains(branch.id)) {
      module.addBranch(branch);
      dialogueQueue.addAll(branch.nodes);
    }
  }

  // ==================== Rewind/reset ====================
  public void backToNode(String moduleId, int nodeIndex) {
    DialogueModule module = getModule(moduleId);
    if (module == null) return;
    hide();
    module.progressIndex = Math.max(0, Math.min(nodeIndex, module.dialogueNodes.size));
    module.isCompleted = false;
    currentModuleId = moduleId;
    playbackState = DialoguePlaybackState.preparing;
    dialogueQueue.addAll(module.createPlaybackCopies(module.progressIndex));
    show();
    if (isShowing()) nextLine();
    else playbackState = DialoguePlaybackState.idle;
  }

  public void backToLastNode() {
    DialogueModule module = getModule(currentModuleId);
    if (module != null) backToNode(currentModuleId, Math.max(0, module.progressIndex - 1));
  }

  public void backToModuleStart(String moduleId) {
    backToNode(moduleId, 0);
  }

  public void clearAllHistory() {
    for (DialogueModule module : modules) {
      module.history.clear();
      module.playedNodeSet.clear();
    }
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
    hide();
    playbackState = DialoguePlaybackState.idle;
    dialogueQueue.clear();
    currentLine = null;
    currentModuleId = null;
    lastPlayedCharacterId = null;
    for (DialogueModule module : modules) module.resetProgress();
    DialoguePersistence.saveCurrentModuleId(null);
    queues.clear();
  }

  // ==================== Skip/auto ====================
  public void fastSkipMainLine() {
      fastForward();
  }

  public void fastForward() {
    if (!isShowing() || ui.optionTable.visible) return;
    setAutoPlay(false);
    stopAutoPlay();

    // 快速播放整段主线；每个节点仍会经过 finish/next 流程，因此完成回调与历史记录不会丢。
    while (isShowing() && !ui.optionTable.visible) {
      if (isTyping()) ui.finishTyping();
      if (dialogueQueue.isEmpty()) {
        hide();
        finishCurrentModule();
        break;
      }

      boolean nextIsChoice = hasOptions(dialogueQueue.first());
      nextLine();
      if (nextIsChoice) break;
    }
    ui.updateAutoPlayButton();
    saveCurrentModule();
    updateContinueButtonVisibility();
  }

  public void toggleAutoPlay() {
    setAutoPlay(!autoPlay);
    if (autoPlay && !isTyping() && playbackState != DialoguePlaybackState.awaitingChoice) {
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
    String moduleId = currentModuleId;
    dialogueQueue.clear();
    hide();
    DialogueModule module = getModule(moduleId);
    if (module != null) {
      module.progressIndex = module.dialogueNodes.size;
      module.isCompleted = true;
    }
    saveCurrentModule();
    checkConditionalTriggers(moduleId);
    updateContinueButtonVisibility();
  }

  private void resetRuntime() {
    dialogueQueue.clear();
    currentLine = null;
    lastPlayedCharacterId = null;
    stopAutoPlay();
    autoBeforeChoice = false;
    autoPlay = false;
    ui.reset();
  }

  private void abortPreparedSession(String moduleId) {
    playbackState = DialoguePlaybackState.idle;
    dialogueQueue.clear();
    currentLine = null;
    queues.queue(moduleId);
  }
}
