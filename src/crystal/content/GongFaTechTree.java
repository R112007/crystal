package crystal.content;

import arc.struct.Seq;
import arc.util.Nullable;
import crystal.type.GongFa;
import crystal.type.GongFaTechNode;
import mindustry.game.Objectives;
import mindustry.type.ItemStack;

public class GongFaTechTree {
  private static GongFaTechNode context = null;
  public static Seq<GongFaTechNode> all = new Seq<>();
  public static Seq<GongFaTechNode> roots = new Seq<>();

  // 构建根节点
  public static GongFaTechNode nodeRoot(GongFa gongFa, Runnable children) {
    return nodeRoot(gongFa, gongFa.researchRequirements, children);
  }

  public static GongFaTechNode nodeRoot(GongFa gongFa, ItemStack[] requirements, Runnable children) {
    var root = node(gongFa, requirements, null, children);
    roots.add(root);
    return root;
  }

  // 构建普通子节点
  public static GongFaTechNode node(GongFa gongFa, Runnable children) {
    return node(gongFa, gongFa.researchRequirements, null, children);
  }

  public static GongFaTechNode node(GongFa gongFa, ItemStack[] requirements, Runnable children) {
    return node(gongFa, requirements, null, children);
  }

  public static GongFaTechNode node(GongFa gongFa, Seq<Objectives.Objective> objectives, Runnable children) {
    return node(gongFa, gongFa.researchRequirements, objectives, children);
  }

  // 全参数节点构建
  public static GongFaTechNode node(GongFa gongFa, ItemStack[] requirements,
      @Nullable Seq<Objectives.Objective> objectives, Runnable children) {
    GongFaTechNode node = new GongFaTechNode(context, gongFa, requirements);
    if (objectives != null) {
      node.objectives.addAll(objectives);
    }

    GongFaTechNode prev = context;
    context = node;
    children.run();
    context = prev;

    all.add(node);
    return node;
  }

  public static @Nullable GongFaTechNode context() {
    return context;
  }
}
