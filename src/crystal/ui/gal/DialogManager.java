package crystal.ui.gal;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.scene.actions.Actions;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import crystal.game.CEventType.SectorChangeEvent;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.io.JsonIO;
import mindustry.type.Sector;

/**
 * Galgame对话系统核心管理器（单例）
 * 负责对话生命周期、进度管理、持久化、自动播放等核心逻辑
 */
public class DialogManager {
  public static final DialogManager instance = new DialogManager();

  // ========== 配置常量 ==========
  /** 自动播放模式下，单句对话的停留时间（秒） */
  public float autoPlayInterval = 3.5f;
  /** 文字逐字显示的速度（字符/秒） */
  public float textSpeed = 25f;
  /** 立绘动画时长（秒） */
  public float animationDuration = 0.3f;

  // ========== 状态数据 ==========
  /** 已注册的对话剧情：key=剧情ID，value=对话节点列表 */
  private final ObjectMap<String, ObjectMap<String, DialogNode>> storyMap = new ObjectMap<>();
  /** 对话历史记录 */
  public final Seq<DialogHistoryEntry> history = new Seq<>();
  /** 当前正在播放的剧情ID */
  private String currentStoryId;
  /** 当前正在播放的对话节点 */
  private DialogNode currentNode;
  /** 上一个说话的角色，用于优化相邻对话动画 */
  private DialogCharacter lastCharacter;
  /** 当前剧情的播放进度索引（用于持久化） */
  private int currentProgressIndex;
  /** 对话是否正在播放中 */
  public boolean isPlaying = false;
  /** 当前文字是否已完全显示 */
  public boolean textFullyShown = false;
  /** 自动播放模式是否开启 */
  public boolean autoPlayEnabled = false;
  /** 自动播放计时器 */
  private float autoPlayTimer = 0f;
  /** 当前文字逐字显示的计时器 */
  private float textTimer = 0f;
  /** 当前显示的文字长度 */
  private int displayedTextLength = 0;
  /** 剧情是否已暂停 */
  public boolean isPaused = false;

  // ========== UI引用 ==========
  public DialogPanel dialogPanel;
  public DialogHistoryPanel historyPanel;

  private DialogManager() {
  }

  // ========== 初始化 ==========
  /**
   * 初始化对话系统，在Mod的ClientLoadEvent中调用
   */
  public void init() {
    // 初始化UI
    dialogPanel = new DialogPanel();
    historyPanel = new DialogHistoryPanel();
    dialogPanel.build(Vars.ui.hudGroup);

    // 注册游戏事件监听
    registerEvents();

    // 加载持久化数据
    loadPersistentData();
  }

  /**
   * 注册游戏生命周期事件
   */
  private void registerEvents() {
    // 战役区块加载事件：恢复剧情进度
    Events.on(SectorChangeEvent.class, e -> {
      String saveKey = getSectorProgressKey(e.sector);
      String savedStoryId = Core.settings.getString(saveKey + "_story", "");
      int savedProgress = Core.settings.getInt(saveKey + "_progress", 0);

      if (!savedStoryId.isEmpty() && savedProgress >= 0) {
        // 恢复剧情进度
        resumeStory(savedStoryId, savedProgress);
      }
    });

    // 游戏状态切换事件：退出到菜单时保存进度、关闭对话框
    Events.on(StateChangeEvent.class, e -> {
      if (e.to == State.menu && isPlaying) {
        // 保存当前进度
        saveCurrentProgress();
        // 关闭对话框
        stopDialog();
      }
    });

    // 游戏帧更新事件：处理逐字显示、自动播放
    Events.run(EventType.Trigger.update, () -> {
      if (!isPlaying || isPaused)
        return;
      float delta = Time.delta;

      // 处理文字逐字显示
      if (!textFullyShown) {
        textTimer += delta;
        int targetLength = Math.min((int) (textTimer * textSpeed), currentNode.content.length());
        if (targetLength > displayedTextLength) {
          displayedTextLength = targetLength;
          dialogPanel.updateDisplayedText();
        }
        if (displayedTextLength >= currentNode.content.length()) {
          textFullyShown = true;
        }
      }

      // 处理自动播放
      if (autoPlayEnabled && textFullyShown && currentNode.choices.isEmpty()) {
        autoPlayTimer += delta;
        if (autoPlayTimer >= autoPlayInterval) {
          autoPlayTimer = 0f;
          next();
        }
      }
    });
  }

  // ========== 剧情注册 ==========
  /**
   * 注册一段完整的剧情
   * 
   * @param storyId 剧情唯一ID
   * @param nodes   剧情的所有对话节点
   */
  public void registerStory(String storyId, Seq<DialogNode> nodes) {
    ObjectMap<String, DialogNode> nodeMap = new ObjectMap<>();
    nodes.each(node -> nodeMap.put(node.id, node));
    storyMap.put(storyId, nodeMap);
  }

  // ========== 对话播放控制 ==========
  /**
   * 开始播放指定剧情
   * 
   * @param storyId 剧情ID
   */
  public void startStory(String storyId) {
    if (!storyMap.containsKey(storyId)) {
      throw new IllegalArgumentException("剧情ID不存在: " + storyId);
    }

    ObjectMap<String, DialogNode> nodeMap = storyMap.get(storyId);
    // 找到剧情的第一个节点（ID以start开头的节点，或第一个节点）
    DialogNode startNode = nodeMap.values().toSeq().find(n -> n.id.startsWith("start"));
    if (startNode == null)
      startNode = nodeMap.values().toSeq().first();

    currentStoryId = storyId;
    currentProgressIndex = 0;
    isPlaying = true;
    isPaused = false;
    lastCharacter = null;
    history.clear();

    playNode(startNode);
  }

  /**
   * 从指定进度恢复剧情
   * 
   * @param storyId       剧情ID
   * @param progressIndex 进度索引
   */
  public void resumeStory(String storyId, int progressIndex) {
    if (!storyMap.containsKey(storyId))
      return;

    currentStoryId = storyId;
    currentProgressIndex = progressIndex;
    isPlaying = true;
    isPaused = false;
    lastCharacter = null;

    // 跳转到对应节点
    ObjectMap<String, DialogNode> nodeMap = storyMap.get(storyId);
    DialogNode targetNode = nodeMap.values().toSeq().get(progressIndex);
    if (targetNode != null) {
      playNode(targetNode);
    }
  }

  /**
   * 播放指定的对话节点
   */
  private void playNode(DialogNode node) {
    if (node == null) {
      stopDialog();
      return;
    }

    currentNode = node;
    textFullyShown = false;
    displayedTextLength = 0;
    textTimer = 0f;
    autoPlayTimer = 0f;

    // 记录历史
    history.add(new DialogHistoryEntry(
        node.character == null ? "旁白" : node.character.name,
        node.content));

    // 更新UI
    dialogPanel.show();
    dialogPanel.updateForNode(node);

    // 处理立绘动画
    handleCharacterAnimation(node);

    // 处理结束节点
    if (node.isEnd) {
      // 清除该区块的进度保存
      clearSectorProgress(Vars.state.getSector());
    }
  }

  /**
   * 处理角色立绘动画，同角色相邻对话不重复播放进入/离开动画
   */
  private void handleCharacterAnimation(DialogNode node) {
    DialogCharacter currentChar = node.character;

    // 情况1：当前无角色，上一个有角色 → 播放上一个角色的离开动画
    if (currentChar == null && lastCharacter != null) {
      dialogPanel.playCharacterExitAnimation();
      lastCharacter = null;
    }
    // 情况2：当前有角色，和上一个不同 → 上一个离开，当前进入
    else if (currentChar != null && !currentChar.equals(lastCharacter)) {
      if (lastCharacter != null) {
        dialogPanel.playCharacterExitAnimation();
      }
      dialogPanel.playCharacterEnterAnimation(currentChar, node.expression);
      lastCharacter = currentChar;
    }
    // 情况3：当前有角色，和上一个相同 → 仅切换表情，不播放进入/离开动画
    else if (currentChar != null && currentChar.equals(lastCharacter)) {
      dialogPanel.updateCharacterExpression(currentChar, node.expression);
    }
  }

  /**
   * 推进到下一句对话/完成当前文字显示
   * 点击对话框时调用
   */
  public void next() {
    if (!isPlaying || isPaused)
      return;

    // 文字未完全显示 → 直接显示完整文字
    if (!textFullyShown) {
      displayedTextLength = currentNode.content.length();
      textFullyShown = true;
      dialogPanel.updateDisplayedText();
      return;
    }

    // 有选择支 → 不推进，等待用户选择
    if (!currentNode.choices.isEmpty())
      return;

    // 结束节点 → 停止对话
    if (currentNode.isEnd) {
      stopDialog();
      return;
    }

    // 推进到下一个节点
    if (currentNode.nextNodeId != null && storyMap.containsKey(currentStoryId)) {
      DialogNode nextNode = storyMap.get(currentStoryId).get(currentNode.nextNodeId);
      currentProgressIndex++;
      playNode(nextNode);
    } else {
      stopDialog();
    }
  }

  /**
   * 选择支点击回调
   * 点击选择按钮时调用，不会触发下一句对话
   */
  public void onChoiceSelect(DialogChoice choice) {
    if (!isPlaying || choice == null)
      return;

    // 执行自定义回调
    if (choice.selectCallback != null) {
      choice.selectCallback.get(this);
    }

    // 跳转到目标节点
    if (choice.targetNodeId != null && storyMap.containsKey(currentStoryId)) {
      DialogNode targetNode = storyMap.get(currentStoryId).get(choice.targetNodeId);
      if (targetNode != null) {
        currentProgressIndex++;
        playNode(targetNode);
      }
    }
  }

  /**
   * 停止对话，关闭对话框
   */
  public void stopDialog() {
    isPlaying = false;
    currentNode = null;
    currentStoryId = null;
    lastCharacter = null;
    autoPlayEnabled = false;
    autoPlayTimer = 0f;
    dialogPanel.hide();
  }

  /**
   * 切换自动播放模式
   */
  public void toggleAutoPlay() {
    autoPlayEnabled = !autoPlayEnabled;
    autoPlayTimer = 0f;
  }

  // ========== 持久化相关 ==========
  /**
   * 获取当前区块的进度存储Key
   */
  private String getSectorProgressKey(Sector sector) {
    if (sector == null)
      return "dialog_global";
    return "dialog_sector_" + sector.id;
  }

  /**
   * 保存当前剧情进度
   */
  public void saveCurrentProgress() {
    if (currentStoryId == null || Vars.state.getSector() == null)
      return;
    String key = getSectorProgressKey(Vars.state.getSector());

    Core.settings.put(key + "_story", currentStoryId);
    Core.settings.put(key + "_progress", currentProgressIndex);
    Core.settings.manualSave();
  }

  /**
   * 清除指定区块的剧情进度
   */
  public void clearSectorProgress(Sector sector) {
    String key = getSectorProgressKey(sector);
    Core.settings.remove(key + "_story");
    Core.settings.remove(key + "_progress");
    Core.settings.manualSave();
  }

  /**
   * 清除所有剧情进度和历史记录
   */
  /*
   * public void clearAllData() {
   * history.clear();
   * Core.settings.getKeys().toSeq().filter(k ->
   * k.startsWith("dialog_")).each(Core.settings::remove);
   * Core.settings.manualSave();
   * }
   */

  /**
   * 加载持久化数据
   */
  private void loadPersistentData() {
    // 加载历史记录
    String historyJson = Core.settings.getString("dialog_history", "[]");
    try {
      DialogHistoryEntry[] entries = JsonIO.json.fromJson(DialogHistoryEntry[].class, historyJson);
      history.clear();
      history.addAll(entries);
    } catch (Exception ignored) {
    }

    // 保存历史记录的事件
    Events.on(StateChangeEvent.class, e -> {
      if (e.to == State.menu) {
        Core.settings.put("dialog_history", JsonIO.json.toJson(history.toArray(DialogHistoryEntry.class)));
        Core.settings.manualSave();
      }
    });
  }

  // ========== 工具方法 ==========
  public String getCurrentDisplayedText() {
    if (currentNode == null)
      return "";
    return currentNode.content.substring(0, displayedTextLength);
  }

  public DialogNode getCurrentNode() {
    return currentNode;
  }
}
