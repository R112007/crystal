package crystal.ui.gal;

import arc.Core;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import crystal.util.DLog;
import mindustry.io.JsonIO;

/**
 * 对话模块，用于分组管理对话节点
 */
public class DialogueModule {
  // 模块唯一ID，如"1-1""1-2"
  public String moduleId;
  // 模块显示名称，如"第一章 第一节"
  public String moduleName;
  // 该模块下的所有对话节点
  public Seq<DialogueLine> dialogueNodes = new Seq<>();
  // 模块是否已完成
  public boolean isCompleted = false;
  // 模块已播放到的节点索引
  public int progressIndex = 0;
  // 分支id序列
  public Seq<String> branchIds = new Seq<>();
  // 新增：原始主线备份，构造时初始化，永远不会被运行时修改
  private Seq<DialogueLine> originalMainNodes;
  // 模块对话历史
  public final Seq<DialogueHistory> history = new Seq<>();
  public ObjectSet<String> playedNodeSet = new ObjectSet<>();

  public DialogueModule(String moduleId, String moduleName) {
    this.moduleId = moduleId;
    this.moduleName = moduleName;
    this.originalMainNodes = new Seq<>(this.dialogueNodes);
  }

  public DialogueModule(String moduleId, String moduleName, Seq<DialogueLine> nodes) {
    this.moduleId = moduleId;
    this.moduleName = moduleName;
    this.dialogueNodes = nodes;
    this.originalMainNodes = new Seq<>(this.dialogueNodes);
    setNodeIds();
  }

  public void appendToHistory(DialogueLine line) {
    if (line == null || line.nodeId == null)
      return;
    // 模块级去重：已经加过的节点不再重复记录
    if (playedNodeSet.contains(line.nodeId))
      return;
    history.add(new DialogueHistory(
        this.moduleId,
        line.nodeId,
        line.characterName,
        line.content));
    playedNodeSet.add(line.nodeId);
  }

  /**
   * 用Seq.each批量设置节点ID，格式：moduleId-索引
   * 完全符合需求：无需逐个设置，批量生成
   */
  public void setNodeIds() {
    int size = dialogueNodes.size;
    for (int i = 0; i < size; i++) {
      DialogueLine line = dialogueNodes.get(i);
      line.moduleId = this.moduleId;
      line.nodeId = this.moduleId + "-" + (i + 1);
    }
  }

  /** 添加对话节点，自动刷新ID */
  public DialogueModule addNode(DialogueLine... lines) {
    dialogueNodes.addAll(lines);
    originalMainNodes.set(dialogueNodes);
    setNodeIds();
    return this;
  }

  public DialogueModule addNode(Seq<DialogueLine> lines) {
    dialogueNodes.addAll(lines);
    originalMainNodes.set(dialogueNodes);
    setNodeIds();
    return this;
  }

  /** 获取当前进度的对话节点 */
  public DialogueLine getCurrentNode() {
    if (progressIndex >= dialogueNodes.size)
      return null;
    return dialogueNodes.get(progressIndex);
  }

  /** 推进模块进度 */
  public void advanceProgress() {
    // 只在没播完时+1
    if (progressIndex < dialogueNodes.size) {
      progressIndex++;
    }
    isCompleted = progressIndex >= dialogueNodes.size;
  }

  /** 重置模块进度 */
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
    // 避免重复添加
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
    String branchIdsJson = Core.settings.getString("gal_module_branch_" + this.moduleId, "[]");
    Seq<String> savedBranchIds = JsonIO.json.fromJson(Seq.class, branchIdsJson);
    this.branchIds.set(savedBranchIds);
    rebuildDialogueNodes();
    this.progressIndex = Core.settings.getInt("gal_module_progress_" + this.moduleId, 0);
    this.isCompleted = Core.settings.getBool("gal_module_completed_" + this.moduleId, false);
    this.isCompleted = this.progressIndex >= this.dialogueNodes.size;
    if (isCompleted) {
      for (DialogueLine d : dialogueNodes) {
        if (d.nodeId == null)
          continue;
        history.add(new DialogueHistory(
            moduleId,
            d.nodeId,
            d.characterName,
            d.content));
        playedNodeSet.add(d.nodeId);
      }
    } else {
      for (int i = 0; i < progressIndex; i++) {
        DialogueLine d = dialogueNodes.get(i);
        if (d.nodeId == null)
          continue;
        history.add(new DialogueHistory(
            moduleId,
            d.nodeId,
            d.characterName,
            d.content));
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

  public void addSavedBranch() {
    for (String id : branchIds) {
      addNode(Branch.branchIds.get(id).nodes);
    }
  }
}
