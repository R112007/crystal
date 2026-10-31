package crystal.ui.gal;

import arc.struct.Seq;

import java.util.function.Predicate;

/** Controls normal playback waits and prerequisite-gated waits independently of UI playback. */
public class DialogueQueueController {
  public final Seq<String> waiting = new Seq<>();
  public final Seq<String> conditional = new Seq<>();

  public void load(Predicate<String> moduleExists) {
    DialoguePersistence.QueueState persisted = DialoguePersistence.loadQueues(moduleExists);
    waiting.set(persisted.waiting);
    conditional.set(persisted.conditional);
  }

  public void save() {
    DialoguePersistence.saveQueues(waiting, conditional);
  }

  public boolean queue(String moduleId) {
    if (moduleId == null || waiting.contains(moduleId)) return false;
    waiting.add(moduleId);
    save();
    return true;
  }

  public boolean awaitPrerequisite(String moduleId) {
    if (moduleId == null || conditional.contains(moduleId)) return false;
    conditional.add(moduleId);
    save();
    return true;
  }

  public String poll() {
    return waiting.isEmpty() ? null : waiting.remove(0);
  }

  public Seq<String> releaseDependents(String completedModuleId,
      java.util.function.Function<String, DialogueModule> lookup) {
    Seq<String> released = new Seq<>();
    for (String id : conditional) {
      DialogueModule module = lookup.apply(id);
      if (module != null && completedModuleId.equals(module.prerequisiteModuleId)) {
        released.add(id);
      }
    }
    if (released.isEmpty()) return released;
    conditional.removeAll(released);
    save();
    return released;
  }

  public void clear() {
    waiting.clear();
    conditional.clear();
    save();
  }
}
