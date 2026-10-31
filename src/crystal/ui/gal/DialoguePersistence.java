package crystal.ui.gal;

import arc.Core;
import arc.struct.Seq;
import crystal.util.DLog;
import mindustry.io.JsonIO;

import java.util.function.Predicate;

/** Centralizes JSON/settings persistence for story modules and runtime queues. */
public final class DialoguePersistence {
  private static final String PREFIX = "gal_module_";
  private static final String CURRENT_MODULE = "gal_current_module_id";
  private static final String WAITING_QUEUE = "gal_waiting_module_queue";
  private static final String CONDITIONAL_QUEUE = "gal_conditional_queue";

  private DialoguePersistence() {}

  public static SavedModule loadModule(String moduleId) {
    SavedModule result = new SavedModule();
    String branchIdsJson = Core.settings.getString(PREFIX + "branch_" + moduleId, "[]");
    try {
      Seq<String> saved = JsonIO.json.fromJson(Seq.class, branchIdsJson);
      if (saved != null) result.branchIds.addAll(saved);
    } catch (Exception e) {
      DLog.err("加载分支失败: " + moduleId, e);
    }
    result.progressIndex = Core.settings.getInt(PREFIX + "progress_" + moduleId, 0);
    result.completed = Core.settings.getBool(PREFIX + "completed_" + moduleId, false);
    return result;
  }

  public static void saveModule(DialogueModule module) {
    Core.settings.put(PREFIX + "branch_" + module.moduleId, JsonIO.json.toJson(module.branchIds));
    Core.settings.put(PREFIX + "progress_" + module.moduleId, module.progressIndex);
    Core.settings.put(PREFIX + "completed_" + module.moduleId, module.isCompleted);
    Core.settings.autosave();
  }

  public static QueueState loadQueues(Predicate<String> moduleExists) {
    QueueState state = new QueueState();
    Seq<String> waiting = readStringSeq(WAITING_QUEUE, "等待队列");
    for (String id : waiting) {
      if (moduleExists.test(id)) state.waiting.add(id);
    }

    Seq<String> conditional = readStringSeq(CONDITIONAL_QUEUE, "条件队列");
    for (String id : conditional) {
      if (moduleExists.test(id)) state.conditional.add(id);
    }
    return state;
  }

  public static void saveQueues(Seq<String> waiting, Seq<String> conditional) {
    if (waiting.isEmpty()) Core.settings.remove(WAITING_QUEUE);
    else Core.settings.put(WAITING_QUEUE, JsonIO.json.toJson(waiting));

    if (conditional.isEmpty()) Core.settings.remove(CONDITIONAL_QUEUE);
    else Core.settings.put(CONDITIONAL_QUEUE, JsonIO.json.toJson(conditional));
    Core.settings.autosave();
  }

  public static String loadCurrentModuleId() {
    return Core.settings.getString(CURRENT_MODULE, null);
  }

  public static void saveCurrentModuleId(String moduleId) {
    if (moduleId == null) Core.settings.remove(CURRENT_MODULE);
    else Core.settings.put(CURRENT_MODULE, moduleId);
  }

  private static Seq<String> readStringSeq(String key, String label) {
    String json = Core.settings.getString(key, "[]");
    try {
      Seq<String> values = JsonIO.json.fromJson(Seq.class, json);
      return values == null ? new Seq<>() : values;
    } catch (Exception e) {
      DLog.err(label + "恢复失败", e);
      return new Seq<>();
    }
  }

  public static class SavedModule {
    public final Seq<String> branchIds = new Seq<>();
    public int progressIndex;
    public boolean completed;
  }

  public static class QueueState {
    public final Seq<String> waiting = new Seq<>();
    public final Seq<String> conditional = new Seq<>();
  }
}
