package crystal.ui.gal;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.struct.ObjectSet;
import arc.util.Time;
import arc.util.Timer;
import crystal.CVars;
import crystal.Crystal;
import crystal.core.CSettings;
import crystal.game.CEventType.SectorChangeEvent;
import crystal.game.CEventType.SectorEnterEvent;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.gen.Tex;
import mindustry.io.SaveVersion;
import mindustry.ui.Styles;
import mindustry.io.JsonIO;

public class GalgameDialogueManager {
  // 单例实例
  public static final GalgameDialogueManager instance = new GalgameDialogueManager();
  private static final String KEY_CURRENT_MODULE = "gal_current_module_id";
  // 等待执行的模块ID队列（持久化用，不存对象，重启不丢失）
  public final Seq<String> waitingModuleIds = new Seq<>();
  // 队列持久化key
  private static final String KEY_WAITING_QUEUE = "gal_waiting_module_queue";
  // 缓存选项前的自动播放状态
  public boolean cachedAutoPlayBeforeOption = false;
  // 对话UI面板
  public final GalgameDialogueUI ui;
  // 历史记录UI面板
  public final DialogueHistoryUI historyUI;
  // 对话队列
  public final Seq<DialogueLine> dialogueQueue = new Seq<>();
  // 对话模块列表
  public Seq<DialogueModule> modules = new Seq<>();
  // 当前播放的模块ID
  public String currentModuleId;
  // 上一个播放的角色ID，用于相邻角色动画判断
  public String lastPlayedCharacterId;
  // 状态控制
  public boolean isShowing = false;
  public boolean isPlaying = false;
  public boolean isAutoPlay = false;
  public boolean isTyping = false;
  private boolean isPlayingModule = false;
  // 自动播放间隔（秒）
  public float autoPlayInterval = 3.5f;
  // 逐字打印速度（字符/秒）
  public float typingSpeed = 25f;
  // 当前播放的对话
  public DialogueLine currentLine;
  public Timer.Task autoPlayTask;
  // 继续剧情按钮容器（解决重复创建问题）
  public Table continuePlayTable;
  // 调试窗口实例
  public final GalDebugDialog debugDialog;

  public GalgameDialogueManager() {
    this.ui = new GalgameDialogueUI();
    this.historyUI = new DialogueHistoryUI();
    this.debugDialog = new GalDebugDialog();
    registerEvents();
    Core.app.post(() -> {
      loadCurrentModule();
      ensureContinueButton();
      updateContinueButtonVisibility();
    });
  }

  // ==================== 事件注册 ====================
  /** 注册Mindustry事件，处理区块进出的保存/加载 */
  public void registerEvents() {
    // 退出区块：关闭对话框，保存进度
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

    // 加载区块：加载进度，继续播放未完成对话
    Events.on(SectorEnterEvent.class, e -> {
      updateContinueButtonVisibility();
    });
    // 游戏退出：保存数据
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

  // GalgameDialogueManager.java → updateContinueButtonVisibility
  public void updateContinueButtonVisibility() {
    if (continuePlayTable == null)
      return;
    boolean showSwitch = Core.settings.getBool("showContinueButton", true);
    boolean hasUnfinished = false;

    for (DialogueModule m : modules) {
      if (m == null)
        continue;
      // 修复：必须同时满足 未完成+有效进度+有剩余节点
      if (!m.isCompleted
          && m.progressIndex > 0
          && m.progressIndex < m.dialogueNodes.size
          && m.dialogueNodes.size > 0) {
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
        // 1. 优先使用已保存的 currentModuleId
        DialogueModule target = getModule(currentModuleId);

        // 2. 兜底：如果为空，自动遍历找到第一个未完成的模块
        if (target == null) {
          for (DialogueModule m : modules) {
            if (m != null && !m.isCompleted && m.progressIndex > 0) {
              target = m;
              currentModuleId = m.moduleId;
              break;
            }
          }
        }

        // 3. 找到就播放
        if (target != null) {
          playModule(target.moduleId);
        }
        continuePlayTable.visible = false;
        updateContinueButtonVisibility();
        // 4. 点击后隐藏按钮
        continuePlayTable.visible = false;
      }).size(120f, 60f);

      continuePlayTable.add(buttonContainer).size(120f, 60f).pad(60f);
      Vars.ui.hudGroup.addChild(continuePlayTable);
    }
  }

  public void processWaitingQueue() {
    if (isPlayingModule || isShowing || waitingModuleIds.isEmpty() || Vars.state.isMenu() || !Vars.state.isCampaign()) {
      return;
    }
    String nextModuleId = waitingModuleIds.remove(0);
    DialogueModule nextModule = getModule(nextModuleId);

    // 二次校验：已完成则跳过
    if (nextModule == null || nextModule.isCompleted) {
      DLog.err("等待队列模块无效或已完成，自动跳过：" + nextModuleId);
      saveWaitingQueue();
      processWaitingQueue();
      return;
    }

    DLog.info("自动执行等待队列模块：" + nextModuleId);
    playModule(nextModuleId);
    saveWaitingQueue();
  }

  /**
   * 持久化保存等待队列到本地存档
   */
  public void saveWaitingQueue() {
    if (waitingModuleIds.isEmpty()) {
      Core.settings.remove(KEY_WAITING_QUEUE);
    } else {
      String queueJson = JsonIO.json.toJson(waitingModuleIds);
      Core.settings.put(KEY_WAITING_QUEUE, queueJson);
    }
    Core.settings.autosave();
  }

  /**
   * 从本地存档恢复等待队列
   * 游戏启动/模块注册后调用
   */
  public void loadWaitingQueue() {
    waitingModuleIds.clear();
    String queueJson = Core.settings.getString(KEY_WAITING_QUEUE, "[]");
    try {
      Seq<String> savedIds = JsonIO.json.fromJson(Seq.class, queueJson);
      if (savedIds != null && !savedIds.isEmpty()) {
        // 过滤无效模块ID
        savedIds.each(id -> {
          if (getModule(id) != null) {
            waitingModuleIds.add(id);
          }
        });
        DLog.info("恢复等待队列，共" + waitingModuleIds.size + "个模块");
      }
    } catch (Exception e) {
      DLog.err("等待队列恢复失败", e);
      waitingModuleIds.clear();
    }
  }

  /**
   * 清空等待队列
   */
  public void clearWaitingQueue() {
    waitingModuleIds.clear();
    saveWaitingQueue();
  }

  // ==================== 模块管理 ====================
  /**
   * 批量添加模块，用Seq.each自动设置模块ID
   * 完全符合需求：无需逐个设置，批量生成
   * 
   * @param modulePrefix 模块前缀，如"1-"，生成1-1、1-2...
   * @param moduleList   模块列表
   */
  public void addModules(String modulePrefix, Seq<DialogueModule> moduleList) {
    for (int i = 0; i < moduleList.size; i++) {
      DialogueModule module = moduleList.get(i);
      module.moduleId = modulePrefix + (i + 1);
      module.setNodeIds();
      modules.add(module);
    }
  }

  /** 获取指定ID的模块 */
  public DialogueModule getModule(String moduleId) {
    return modules.find(m -> m.moduleId.equals(moduleId));
  }

  /** 播放指定模块的对话，从当前进度开始 */
  public void playModule(String moduleId) {
    DialogueModule module = getModule(moduleId);
    if (module == null) {
      Vars.ui.showErrorMessage(moduleId + " 是无效模块ID");
      return;
    }
    DLog.info("即将尝试播放模块" + moduleId);
    // ====================== 核心修复：三重过滤，禁止重叠播放 ======================
    // 1. 正在播放 → 不加入
    if (isPlayingModule) {
      DLog.info("尝试播放模块" + moduleId + "但是正在播放模块");
      if (module.isCompleted) {
        DLog.info("模块已完成，拒绝加入队列：" + moduleId);
        return;
      }
      if (waitingModuleIds.contains(moduleId)) {
        DLog.info("已存在队列，拒绝重复加入：" + moduleId);
        return;
      }
      waitingModuleIds.add(moduleId);
      DLog.info("当前正在播放模块，加入等待队列：" + moduleId);
      saveWaitingQueue();
      return;
    }
    // 2. 模块已完成 → 不加入
    // 3. 队列已存在 → 不加入（去重）
    // ==========================================================================
    try {
      hide();
      isPlayingModule = true;
      DLog.info("尝试播放模块" + moduleId + "成功");
      // ========== 核心BUG修复：删除module.resetProgress()，不再强制重置进度 ==========
      // 新增：加载模块本地存档进度，确保重启后进度不丢失
      module.loadModuleData();
      currentLine = null;
      lastPlayedCharacterId = null;
      isAutoPlay = false;
      isTyping = false;
      ui.reset();
      dialogueQueue.clear();
      currentModuleId = moduleId;
      // ========== 修复：从当前进度progressIndex开始加载队列，而非从头开始 ==========
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
    // 加入播放队列
    dialogueQueue.add(line);

    // 安全追加到模块主干，仅修改新增内容，不动原有主线数据
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        // 1. 仅把新节点追加到主干末尾
        module.dialogueNodes.add(line);
        // 2. 仅给当前新增节点设置ID，原有主线节点完全不动，杜绝ID重排错乱
        line.moduleId = module.moduleId;
        line.nodeId = module.moduleId + "-" + module.dialogueNodes.size;
        // 3. 标记模块未完成，禁止提前结束
        module.isCompleted = false;
        // 4. 彻底移除advanceProgress()！进度仅在对话播放完成后推进
      }
    }

    // 空队列自动启动播放
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
    // 1. 一次性把整个分支加入播放队列，避免多次操作队列
    dialogueQueue.addAll(branchLines);

    // 2. 安全追加到模块主干，一次性处理，仅修改新增节点
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        // 记录追加前的主干长度，用于批量设置ID
        int originalSize = module.dialogueNodes.size;
        // 一次性把整个分支加入主干队列
        module.dialogueNodes.addAll(branchLines);
        // 仅给新增的分支节点设置ID，原有主线节点完全不动
        for (int i = 0; i < branchLines.size; i++) {
          DialogueLine line = branchLines.get(i);
          line.moduleId = module.moduleId;
          line.nodeId = module.moduleId + "-" + (originalSize + i + 1);
        }
        // 标记模块未完成
        module.isCompleted = false;
      }
    }

    // 3. 空队列自动启动播放
    if (wasQueueEmpty && !isPlaying && !isTyping) {
      show();
      nextLine();
    }
    // 4. 统一持久化进度
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

  /** 推进到下一句对话（核心逻辑） */
  public void nextLine() {
    // 1. 正在打字 → 直接显示完，不推进
    if (isTyping) {
      ui.finishTyping();
      return;
    }

    stopAutoPlay();

    // 2. 队列为空 → 结束
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

    // ====================== 核心修复：严格时序 ======================
    // 3. 先取当前行（不移除）
    currentLine = dialogueQueue.first();
    currentLine.refreshContent();

    // 4. ✅ 先推进进度（最关键修复）
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        module.advanceProgress();
        module.isCompleted = module.progressIndex >= module.dialogueNodes.size;
      }
    }

    // 5. 再移除队列
    dialogueQueue.remove(0);

    // 6. 再播放、记录历史、动画
    isPlaying = true;
    String currentCharacterId = currentLine.character != null ? currentLine.character.characterId : null;
    boolean sameChar = currentCharacterId != null && currentCharacterId.equals(lastPlayedCharacterId);
    ui.characterSprite.clearActions();

    if (!sameChar)
      ui.playSpriteExitAction();
    ui.setDialogueLine(currentLine);

    // 记录历史
    if (currentModuleId != null) {
      DialogueModule module = getModule(currentModuleId);
      if (module != null) {
        module.appendToHistory(currentLine);
      }
    }

    // 动画逻辑
    if (!sameChar) {
      ui.playSpriteEnterAction();
    } else if (currentLine.spriteAction != null) {
      currentLine.spriteAction.run();
    }
    lastPlayedCharacterId = currentCharacterId;

    // 开始打字
    ui.startTyping();
    if (currentLine.onComplete != null)
      currentLine.onComplete.run();

    // 7. 处理选项
    boolean hasOptions = currentLine.options != null && currentLine.options.length > 0;
    if (hasOptions) {
      cachedAutoPlayBeforeOption = isAutoPlay;
      ui.showOptions(currentLine.options);
      isAutoPlay = false;
      ui.updateAutoPlayButton();
      saveCurrentModule();
      return;
    }

    // 8. 自动播放
    if (isAutoPlay)
      startAutoPlay();
    saveCurrentModule();
  }

  public void saveCurrentModule() {
    if (getModule(currentModuleId) != null) {
      getModule(currentModuleId).saveModuleData();
      Core.settings.put(KEY_CURRENT_MODULE, currentModuleId);
      Core.settings.autosave();
    }
  }

  public void loadCurrentModule() {
    currentModuleId = Core.settings.getString(KEY_CURRENT_MODULE, null);
  }

  public void addBranch(Branch branch) {
    DialogueModule module = getModule(currentModuleId);
    if (module == null)
      return;
    if (!module.branchIds.contains(branch.id)) {
      module.addBranch(branch); // 加入模块保存
      dialogueQueue.addAll(branch.nodes); // 加入播放队列（正确）
    }
  }

  // ==================== 剧情回溯功能 ====================
  /** 回溯到指定模块的指定节点 */
  public void backToNode(String moduleId, int nodeIndex) {
    DialogueModule module = getModule(moduleId);
    if (module == null)
      return;

    module.progressIndex = nodeIndex;
    module.isCompleted = false;
    currentModuleId = moduleId;

    dialogueQueue.clear();
    for (int i = nodeIndex; i < module.dialogueNodes.size; i++) {
      dialogueQueue.add(module.dialogueNodes.get(i));
    }

    show();
    nextLine();
  }

  /** 回溯到上一条对话 */
  public void backToLastNode() {
    if (currentModuleId == null)
      return;
    DialogueModule module = getModule(currentModuleId);
    if (module == null)
      return;

    int targetIndex = Math.max(0, module.progressIndex - 1);
    backToNode(currentModuleId, targetIndex);
  }

  /** 回溯到模块开头 */
  public void backToModuleStart(String moduleId) {
    backToNode(moduleId, 0);
  }

  // ==================== 清除/重置功能 ====================
  public void clearAllHistory() {
    modules.each(m -> m.history.clear());
    modules.each(m -> m.playedNodeSet.clear());
  }

  public void clearModuleHistory(String moduleId) {
    if (getModule(moduleId) == null) {
      Vars.ui.showErrorMessage(moduleId + " is a unkown id");
      return;
    }
    getModule(moduleId).history.clear();
    getModule(moduleId).playedNodeSet.clear();
  }

  public void resetAllProgress() {
    modules.each(DialogueModule::resetProgress);
    currentModuleId = null;
    lastPlayedCharacterId = null;
  }

  public void fastSkipMainLine() {
    // 前置校验：无播放内容、正在显示选项时，不执行
    if (!isShowing || dialogueQueue.isEmpty() && currentLine == null || ui.optionTable.visible) {
      return;
    }

    // 先终止当前打字、自动播放，避免时序冲突
    isTyping = false;
    isAutoPlay = false;
    stopAutoPlay();
    ui.finishTyping();

    // 核心循环：逐句执行主线，直到遇到选项/队列空
    while (true) {
      // 1. 队列空，直接退出循环，关闭面板
      if (dialogueQueue.isEmpty()) {
        hide();
        break;
      }

      // 2. 取出下一句对话，先判断是否带选项
      DialogueLine nextLine = dialogueQueue.first();
      // 遇到带选项的对话，立刻停止循环，显示当前句，等待玩家操作
      if (nextLine.options != null && nextLine.options.length > 0) {
        // 执行当前句的播放，显示选项
        nextLine();
        break;
      }

      // 3. 无选项，正常执行这句对话的全量逻辑
      nextLine();

      // 4. 安全兜底：如果执行后面板被隐藏/状态异常，立刻退出
      if (!isShowing) {
        break;
      }
    }

    // 执行完成后，同步更新按钮状态和进度
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

  // ==================== 自动播放相关 ====================
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
