package crystal.ui.gal;

import arc.struct.ObjectMap;
import arc.struct.Seq;

public class Branch {
  public static ObjectMap<String, Branch> branchIds = new ObjectMap<>();
  // 分支ID（用于存档/判断是否已触发）
  public final String id;
  // 该分支自己的对话节点队列
  public final Seq<DialogueLine> nodes = new Seq<>();

  public Branch(String id) {
    this.id = id;
    this.branchIds.put(id, this);
  }

  // 链式添加节点，写起来更顺手
  public Branch addNode(DialogueLine node) {
    nodes.add(node);
    return this;
  }

  // 批量添加节点
  public Branch addAll(Seq<DialogueLine> nodes) {
    this.nodes.addAll(nodes);
    return this;
  }
}
