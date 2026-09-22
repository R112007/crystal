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
import crystal.core.CSettings;
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
  /** 条件等待队列：前置模块未完成的模块ID，前置完成后自动按顺序触发 */
  public final Seq<String> conditionalWaitingIds = new Seq<>();
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
    // 先创建 historyUI/debugDialog，再创建 UI，避免 UI 初始化时读到 null 而隐藏按钮
    this.historyUI = replay ? null : new DialogueHistoryUI();
    this.debugDialog = replay ? null : new GalDebugDialog();
    this.ui = new GalgameDialogueUI(this);
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
      buttonContainer.button(CVars.plot.getOrBundle("contineStory"), Styles.flatt, () -> {
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
    if (isPlayingModule || isShowing || waitingModuleIds.isEmpty() || !canShowDialogueUI() || !Vars.state.isCampaign()) {
      return;
    }
    String nextModuleId = waitingModuleIds.remove(0);
    DialogueModule nextModule = getModule(nextModuleId);
    // 不再跳过已完成模块：playModule 内部会重置进度后重播
    if (nextModule == null) {
      DLog.err("等待队列模块无效，自动跳过：" + nextModuleId);
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
    // 保存条件等待队列
    if (conditionalWaitingIds.isEmpty()) {
      Core.settings.remove("gal_conditional_queue");
    } else {
      String condJson = JsonIO.json.toJson(conditionalWaitingIds);
      Core.settings.put("gal_conditional_queue", condJson);
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
    // 恢复条件等待队列
    conditionalWaitingIds.clear();
    String condJson = Core.settings.getString("gal_conditional_queue", "[]");
    try {
      Seq<String> savedCondIds = JsonIO.json.fromJson(Seq.class, condJson);
      if (savedCondIds != null && !savedCondIds.isEmpty()) {
        savedCondIds.each(id -> {
          DialogueModule m = getModule(id);
          // 只保留前置仍未完成的（已完成的会立即触发）
          if (m != null && !m.prerequisiteMet())
            conditionalWaitingIds.add(id);
        });
        DLog.info("恢复条件队列，共" + conditionalWaitingIds.size + "个模块");
      }
    } catch (Exception e) {
      DLog.err("条件队列恢复失败", e);
      conditionalWaitingIds.clear();
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

    // 【前置依赖检查】前置未完成则加入条件队列，等前置完成后再自动触发
    if (!module.prerequisiteMet()) {
      if (!conditionalWaitingIds.contains(module.moduleId)) {
        conditionalWaitingIds.add(module.moduleId);
        DLog.info("模块 " + module.moduleId + " 前置 [" + module.prerequisiteModuleId + "] 未完成，加入条件队列");
      }
      return;
    }

    if (isPlayingModule) {
      // 已完成模块也允许排队等待再次播放，仅按 ID 去重
      if (waitingModuleIds.contains(module.moduleId))
        return;
      waitingModuleIds.add(module.moduleId);
      saveWaitingQueue();
      return;
    }
    // 界面无法显示时（菜单 / 星球界面）不要在这里静默把剧情跑完：进度被消耗、玩家看不到，
    // 而且 isPlayingModule 会一直卡住，之后所有剧情都进不来。
    if (!canShowDialogueUI()) {
      DLog.info("模块 " + module.moduleId + " 当前界面无法显示对话面板，转入等待队列");
      if (!waitingModuleIds.contains(module.moduleId))
        waitingModuleIds.add(module.moduleId);
      saveWaitingQueue();
      return;
    }
    boolean deferred = false;
    try {
      hide();
      isPlayingModule = true;
      module.loadModuleData();
      // 设置项：是否允许重复播放已完成的模块
      if (module.isCompleted && !CSettings.instance.allowReplayCompletedModules()) {
        DLog.info("模块 " + module.moduleId + " 已完成，且设置不允许重复播放，跳过");
        isPlayingModule = false;
        return;
      }
      // 【修改】播放完的模块可以再次播放：重置进度、恢复原始主线节点并清空已触发分支，
      // 之后从头播放（分支可重新选择）。重置会写存档，播完会重新标记完成。
      if (module.isCompleted) {
        module.resetProgress();
      }
      // 上次是在"选项节点"处退出的：进度已经越过该节点但分支还没选，退回一句把选项重新给玩家
      if (!module.isCompleted && module.progressIndex > 0 && module.progressIndex <= module.dialogueNodes.size) {
        DialogueLine last = module.dialogueNodes.get(module.progressIndex - 1);
        if (last != null && last.options != null && last.options.length > 0) {
          module.progressIndex--;
        }
      }
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
      if (!isShowing) {
        // show() 被菜单/星球界面挡掉了：回滚状态，等界面允许时再播
        DLog.info("模块 " + module.moduleId + " 对话面板未能显示，转入等待队列");
        isPlayingModule = false;
        deferred = true;
        dialogueQueue.clear();
        currentLine = null;
        if (!waitingModuleIds.contains(module.moduleId))
          waitingModuleIds.add(module.moduleId);
        saveWaitingQueue();
        return;
      }
      if (!dialogueQueue.isEmpty()) {
        nextLine();
      } else {
        isPlayingModule = false;
        hide();
        finishCurrentModule();
      }
      if (continuePlayTable != null)
        continuePlayTable.visible = false;
    } finally {
      // 刚转进等待队列时不能立刻处理队列，否则会在同一个调用里反复重入
      if (!deferred)
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
      if (!isShowing) {
        isPlayingModule = false;
        dialogueQueue.clear();
        currentLine = null;
        return;
      }
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
    // 即兴播放的临时对话不属于任何模块，清掉 currentModuleId，
    // 否则会推进上一个模块的进度、把临时台词写进它的历史
    currentModuleId = null;
    dialogueQueue.clear();
    dialogueQueue.add(line);
    show();
    nextLine();
  }

  public void playQueue(Seq<DialogueLine> lines) {
    currentModuleId = null;
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
    if (!canShowDialogueUI())
      return;
    if (isShowing)
      return;
    isShowing = true;
    Core.scene.root.addChild(ui);
    ui.updateVisibility();
    updateContinueButtonVisibility();
  }

  /** 当前界面是否有条件显示对话面板（菜单、星球界面下不能显示）。 */
  public boolean canShowDialogueUI() {
    if (Vars.state.isMenu())
      return false;
    return CVars.cui == null || CVars.cui.cplanet == null || !CVars.cui.cplanet.isShown();
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
      Vars.ui.showConfirm(CVars.plot.formatOrBundle("havewaittingmodule", getModule(waitingModuleIds.first()).moduleName),
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
      // 先收起面板让 isPlayingModule 归位，再标记完成并触发条件队列，
      // 这样"前置完成后该自动播的模块"能立刻播，而不是干等定时轮询
      hide();
      finishCurrentModule();
      processWaitingQueue();
      return;
    }

    currentLine = dialogueQueue.first();
    currentLine.refreshContent();

    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        // 只推进进度，完成状态等队列真的空了再判定：
        // 选项节点后面还会追加分支节点，用 progressIndex >= size 判断会在选项处就误判为已完成
        module.advanceProgress();
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

  /**
   * 把当前模块标记为完成，并触发依赖它的条件等待模块。
   * 只有模块的对话队列真的播空（或玩家跳过）时才该调用。
   */
  protected void finishCurrentModule() {
    if (currentModuleId == null)
      return;
    DialogueModule module = getModule(currentModuleId);
    if (module == null)
      return;
    module.progressIndex = module.dialogueNodes.size;
    module.isCompleted = true;
    saveCurrentModule();
    checkConditionalTriggers(currentModuleId);
  }

  /**
   * 检查条件等待队列：当指定模块完成时，触发所有依赖它的等待中模块。
   * 保证模块按前置依赖顺序播放，避免"开头结尾解锁、中间没解锁"的混沌状态。
   */
  public void checkConditionalTriggers(String completedModuleId) {
    if (conditionalWaitingIds.isEmpty()) return;
    Seq<String> toTrigger = new Seq<>();
    for (String id : conditionalWaitingIds) {
      DialogueModule m = getModule(id);
      if (m != null && completedModuleId.equals(m.prerequisiteModuleId)) {
        toTrigger.add(id);
      }
    }
    if (toTrigger.isEmpty()) return;
    conditionalWaitingIds.removeAll(toTrigger);
    DLog.info("前置模块 [" + completedModuleId + "] 已完成，触发条件队列：" + toTrigger.size + " 个");
    for (String id : toTrigger) {
      DialogueModule m = getModule(id);
      if (m != null) {
        // 如果当前正在播放其他模块，加入普通等待队列
        if (isPlayingModule) {
          if (!waitingModuleIds.contains(id)) {
            waitingModuleIds.add(id);
            saveWaitingQueue();
          }
        } else {
          playModule(m);
        }
      }
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
    hide();
    isShowing = false;
    isPlaying = false;
    isPlayingModule = false;
    isTyping = false;
    isAutoPlay = false;
    cachedAutoPlayBeforeOption = false;
    stopAutoPlay();
    dialogueQueue.clear();
    currentLine = null;
    lastPlayedCharacterId = null;
    modules.each(DialogueModule::resetProgress);
    currentModuleId = null;
    Core.settings.remove(KEY_CURRENT_MODULE);
    clearWaitingQueue();
    conditionalWaitingIds.clear();
    Core.settings.remove("gal_conditional_queue");
  }

  // ==================== 快速跳过 ====================
  public void fastSkipMainLine() {
    if (!isShowing || (dialogueQueue.isEmpty() && currentLine == null) || ui.optionTable.visible)
      return;
    isTyping = false;
    isAutoPlay = false;
    stopAutoPlay();
    ui.finishTyping();
    while (isShowing) {
      // 先收掉当前句：nextLine() 遇到"正在打字"只会补完文字不推进，
      // 不先收尾就会出现"停在选项前却没把选项显示出来"的情况
      if (isTyping) {
        ui.finishTyping();
      }
      if (dialogueQueue.isEmpty()) {
        hide();
        // 队列空了 = 这一段播完：必须走完成收尾，否则依赖它的条件队列永远不触发
        finishCurrentModule();
        break;
      }
      DialogueLine nextLine = dialogueQueue.first();
      if (nextLine.options != null && nextLine.options.length > 0) {
        nextLine();
        break;
      }
      nextLine();
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
    String moduleId = currentModuleId;
    dialogueQueue.clear();
    hide(); // 先收起面板：isPlayingModule 归位后，条件队列里的模块才能立刻接着播
    if (moduleId != null) {
      DialogueModule module = getModule(moduleId);
      if (module != null) {
        module.progressIndex = module.dialogueNodes.size;
        module.isCompleted = true;
      }
    }
    saveCurrentModule();
    checkConditionalTriggers(moduleId);
    updateContinueButtonVisibility();
  }
}
