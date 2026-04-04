package crystal.type;

import arc.Core;
import arc.func.Cons;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.game.GongFaObjectives.GongFaResearch;
import mindustry.game.Objectives;
import mindustry.type.ItemStack;

public class GongFaTechNode {
  public int depth;
  public @Nullable GongFaTechNode parent;
  public final GongFa gongFa;
  public ItemStack[] requirements;
  public ItemStack[] finishedRequirements;
  public Seq<Objectives.Objective> objectives = new Seq<>();
  public final Seq<GongFaTechNode> children = new Seq<>();
  // UI布局用坐标
  public float x, y;
  public float width, height;
  public boolean visible = true, selectable = true;

  public GongFaTechNode(@Nullable GongFaTechNode parent, GongFa gongFa, ItemStack[] requirements) {
    this.parent = parent;
    this.gongFa = gongFa;
    this.depth = parent == null ? 0 : parent.depth + 1;
    this.width = this.height = 60f;

    if (parent != null) {
      parent.children.add(this);
      this.objectives.add(new GongFaResearch(parent.gongFa));
    }

    setupRequirements(requirements);
  }

  public void setupRequirements(ItemStack[] requirements) {
    this.requirements = requirements;
    this.finishedRequirements = new ItemStack[requirements.length];
    // 加载存档里的已完成进度
    for (int i = 0; i < requirements.length; i++) {
      finishedRequirements[i] = new ItemStack(requirements[i].item,
          Core.settings == null ? 0
              : Core.settings.getInt("gongfa-req-" + gongFa.name + "-" + requirements[i].item.name, 0));
    }
  }

  // 保存解锁进度到存档
  public void save() {
    for (ItemStack stack : finishedRequirements) {
      Core.settings.put("gongfa-req-" + gongFa.name + "-" + stack.item.name, stack.amount);
    }
    Core.settings.manualSave();
  }

  // 重置解锁进度
  public void reset() {
    for (ItemStack stack : finishedRequirements) {
      stack.amount = 0;
    }
    save();
  }

  // 递归遍历所有子节点
  public void each(Cons<GongFaTechNode> consumer) {
    consumer.get(this);
    for (var child : children) {
      child.each(consumer);
    }
  }

  // 检查所有解锁条件是否完成
  public boolean objectivesComplete() {
    return !objectives.contains(o -> !o.complete());
  }

  // 检查是否可解锁（条件完成+未解锁）
  public boolean canUnlock() {
    return !gongFa.unlocked() && objectivesComplete();
  }
}
