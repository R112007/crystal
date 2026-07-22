package crystal.ui.gal;

import arc.struct.ObjectMap;
import arc.struct.Seq;

/**
 * 剧情分支。分支拥有全局 ID，可被任意模块触发并追加到当前模块的节点队列中。
 */
public class Branch {
    /** 全局分支注册表，用于存档恢复。 */
    public static final ObjectMap<String, Branch> branchIds = new ObjectMap<>();

    /** 分支唯一 ID。 */
    public final String id;
    /** 该分支的对话节点队列。 */
    public final Seq<DialogueLine> nodes = new Seq<>();

    public Branch(String id) {
        this.id = id;
        branchIds.put(id, this);
    }

    /** 链式添加单个节点。 */
    public Branch addNode(DialogueLine node) {
        nodes.add(node);
        return this;
    }

    /** 批量添加节点。 */
    public Branch addAll(Seq<DialogueLine> nodes) {
        this.nodes.addAll(nodes);
        return this;
    }

    /** 兼容数组的批量添加。 */
    public Branch addAll(DialogueLine... nodes) {
        this.nodes.addAll(nodes);
        return this;
    }

    /**
     * 创建用于模块副本回放的分支节点副本：保留文本、角色、立绘、表情、选项与回调。
     */
    public Seq<DialogueLine> createReplayCopies() {
        Seq<DialogueLine> copies = new Seq<>();
        for (DialogueLine line : nodes) {
            copies.add(line.copyForReplay());
        }
        return copies;
    }
}
