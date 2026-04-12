package crystal.ui.gal;

import arc.func.Cons;

/**
 * 对话选择支实体
 */
public class DialogChoice {
  /** 选择支显示的文本 */
  public final String text;
  /** 选择后跳转的对话节点ID（可选） */
  public final String targetNodeId;
  /** 选择后执行的自定义回调（可选，用于触发剧情事件） */
  public final Cons<DialogManager> selectCallback;

  /** 基础构造：仅跳转节点 */
  public DialogChoice(String text, String targetNodeId) {
    this(text, targetNodeId, null);
  }

  /** 完整构造：跳转节点+自定义回调 */
  public DialogChoice(String text, String targetNodeId, Cons<DialogManager> selectCallback) {
    this.text = text;
    this.targetNodeId = targetNodeId;
    this.selectCallback = selectCallback;
  }
}
