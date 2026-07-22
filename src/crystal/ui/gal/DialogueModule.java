package crystal.ui.gal;

import arc.Core;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import crystal.util.DLog;
import mindustry.io.JsonIO;

/**
 * 对话模块，用于分组管理对话节点。
 * 保存进度、已触发分支、对话历史到本地存档。
 */
public class DialogueModule {
  // 模块唯一ID，如 "1-1" "1-2"
  public String moduleId;
  // 模块显示名称
  public String moduleName;
  // 该模块下的所有对话节点（包含已追加的分支）
  public Seq<DialogueLine> dialogueNodes = new Seq<>();
  // 是否已完成
  public boolean isCompleted = false;
  // 已播放到的节点索引
  public int progressIndex = 0;
  // 已触发分支 ID
  public Seq<String> branchIds = new Seq<>();
  // 原始主线备份，构造时初始化，运行时不会被直接修改
  public Seq<DialogueLine> originalMainNodes;
  // 模块对话历史
  public final Seq<DialogueHistory> history = new Seq<>();
  public final ObjectSet<String> playedNodeSet = new ObjectSet<>();

  public DialogueModule(String moduleId, String moduleName) {
    this.moduleId = moduleId;
    this.moduleName = moduleName;
  }

  public DialogueModule(String moduleId, String moduleName, Seq<DialogueLine> nodes) {
    this.moduleId = moduleId;
    this.moduleName = moduleName;
    this.dialogueNodes = nodes == null ? new Seq<>() : nodes;
    this.originalMainNodes = new Seq<>(this.dialogueNodes);
    setNodeIds();
  }

  /**
   * 生成用于剧情回溯的节点副本队列：文本、角色、立绘保留，回调不拷贝。
   *
   * @param fromIndex 起始节点索引（含）
   */
  public Seq<DialogueLine> createPlaybackCopies(int fromIndex) {
    Seq<DialogueLine> copies = new Seq<>();
    for (int i = fromIndex; i < dialogueNodes.size; i++) {
      DialogueLine original = dialogueNodes.get(i);
      if (original.nodeId != null && original.nodeId.contains("-branch-"))
        continue;
      copies.add(original.copyForHistory());
    }
    return copies;
  }

  /**
   * 生成用于模块回放副本的完整节点副本队列：
   * 文本、角色、立绘、表情、选项、回调与动作均保留，
   * 不影响原模块的进度与完成状态。
   *
   * 已触发分支的节点不会预置进副本，而是保留分支选项 line；
   * 玩家在回放时选择某个分支后，再把对应分支副本动态插入队列，
   * 从而可以体验之前未选择的分支。
   */
  public Seq<DialogueLine> createReplayCopies() {
    Seq<DialogueLine> copies = new Seq<>();
    for (DialogueLine original : dialogueNodes) {
      if (original.nodeId != null && original.nodeId.contains("-branch-"))
        continue;
      copies.add(original.copyForReplay());
    }
    return copies;
  }

  public void appendToHistory(DialogueLine line) {
    if (line == null || line.nodeId == null)
      return;
    if (playedNodeSet.contains(line.nodeId))
      return;
    history.add(new DialogueHistory(
        this.moduleId,
        line.nodeId,
        line.characterName,
        line.content));
    playedNodeSet.add(line.nodeId);
  }

  /** 批量设置节点 ID，格式：moduleId-索引 */
  public void setNodeIds() {
    int size = dialogueNodes.size;
    for (int i = 0; i < size; i++) {
      DialogueLine line = dialogueNodes.get(i);
      if (line == null)
        continue;
      line.moduleId = this.moduleId;
      line.nodeId = this.moduleId + "-" + (i + 1);
    }
  }

  /** 添加对话节点，自动刷新 ID 与原始备份。 */
  public DialogueModule addNode(DialogueLine... lines) {
    dialogueNodes.addAll(lines);
    refreshOriginals();
    setNodeIds();
    return this;
  }

  public DialogueModule addNode(Seq<DialogueLine> lines) {
    if (lines != null) {
      dialogueNodes.addAll(lines);
    }
    refreshOriginals();
    setNodeIds();
    return this;
  }

  private void refreshOriginals() {
    if (originalMainNodes == null) {
      originalMainNodes = new Seq<>();
    }
    originalMainNodes.set(dialogueNodes);
  }

  /** 获取当前进度节点。 */
  public DialogueLine getCurrentNode() {
    if (progressIndex >= dialogueNodes.size)
      return null;
    return dialogueNodes.get(progressIndex);
  }

  /** 推进模块进度。 */
  public void advanceProgress() {
    if (progressIndex < dialogueNodes.size) {
      progressIndex++;
    }
    isCompleted = progressIndex >= dialogueNodes.size;
  }

  /** 重置模块进度。 */
  public void resetProgress() {
    progressIndex = 0;
    isCompleted = false;
    branchIds.clear();
    if (originalMainNodes != null) {
      dialogueNodes.set(originalMainNodes);
      setNodeIds();
    }
    history.clear();
    playedNodeSet.clear();
    saveModuleData();
  }

  public void addBranch(Branch branch) {
    String branchId = branch.id;
    if (!branchIds.contains(branchId)) {
      branchIds.add(branchId);
      dialogueNodes.addAll(branch.nodes);
      setNodeIds();
    }
  }

  public void saveModuleData() {
    String branchIdsJson = JsonIO.json.toJson(this.branchIds);
    Core.settings.put("gal_module_branch_" + this.moduleId, branchIdsJson);
    Core.settings.put("gal_module_progress_" + this.moduleId, this.progressIndex);
    Core.settings.put("gal_module_completed_" + this.moduleId, this.isCompleted);
    Core.settings.forceSave();
  }

  public void loadModuleData() {
    history.clear();
    playedNodeSet.clear();
    String branchIdsJson = Core.settings.getString("gal_module_branch_" + this.moduleId, "[]");
    try {
      Seq<String> savedBranchIds = JsonIO.json.fromJson(Seq.class, branchIdsJson);
      this.branchIds.set(savedBranchIds);
    } catch (Exception e) {
      DLog.err("加载分支失败: " + this.moduleId, e);
      this.branchIds.clear();
    }
    rebuildDialogueNodes();
    this.progressIndex = Core.settings.getInt("gal_module_progress_" + this.moduleId, 0);
    this.isCompleted = Core.settings.getBool("gal_module_completed_" + this.moduleId, false);
    this.isCompleted = this.progressIndex >= this.dialogueNodes.size;

    if (isCompleted) {
      for (DialogueLine d : dialogueNodes) {
        if (d == null || d.nodeId == null)
          continue;
        history.add(new DialogueHistory(moduleId, d.nodeId, d.characterName, d.content));
        playedNodeSet.add(d.nodeId);
      }
    } else {
      for (int i = 0; i < progressIndex; i++) {
        DialogueLine d = dialogueNodes.get(i);
        if (d == null || d.nodeId == null)
          continue;
        history.add(new DialogueHistory(moduleId, d.nodeId, d.characterName, d.content));
        playedNodeSet.add(d.nodeId);
      }
    }
  }

  public void rebuildDialogueNodes() {
    if (originalMainNodes == null) {
      originalMainNodes = new Seq<>(this.dialogueNodes);
    }
    this.dialogueNodes.set(originalMainNodes);
    for (String branchId : this.branchIds) {
      Branch targetBranch = Branch.branchIds.get(branchId);
      if (targetBranch != null) {
        this.dialogueNodes.addAll(targetBranch.nodes);
      }
    }
    setNodeIds();
  }
}
