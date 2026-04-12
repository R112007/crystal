package crystal.ui.gal;

import arc.struct.Seq;

/**
 * 对话节点实体
 * 单条对话的完整配置，包含角色、文本、表情、选择支、跳转逻辑
 */
public class DialogNode {
  /** 节点唯一ID，用于分支跳转 */
  public final String id;
  /** 说话的角色，null表示旁白/无角色 */
  public final DialogCharacter character;
  /** 对话文本内容 */
  public final String content;
  /** 角色表情名，对应DialogCharacter中的表情key */
  public final String expression;
  /** 对话结束后自动跳转的下一个节点ID（无选择支时生效） */
  public final String nextNodeId;
  /** 该节点的选择支列表，不为空时对话结束后显示选择按钮 */
  public final Seq<DialogChoice> choices;
  /** 是否为剧情结束节点 */
  public final boolean isEnd;

  /** 旁白/无角色对话节点 */
  public static DialogNode narration(String id, String content, String nextNodeId) {
    return new DialogNode(id, null, content, "normal", nextNodeId, Seq.with(), false);
  }

  /** 基础角色对话节点 */
  public DialogNode(String id, DialogCharacter character, String content, String expression, String nextNodeId) {
    this(id, character, content, expression, nextNodeId, Seq.with(), false);
  }

  /** 带选择支的对话节点 */
  public DialogNode(String id, DialogCharacter character, String content, String expression,
      Seq<DialogChoice> choices) {
    this(id, character, content, expression, null, choices, false);
  }

  /** 完整构造函数 */
  public DialogNode(String id, DialogCharacter character, String content, String expression, String nextNodeId,
      Seq<DialogChoice> choices, boolean isEnd) {
    this.id = id;
    this.character = character;
    this.content = content;
    this.expression = expression;
    this.nextNodeId = nextNodeId;
    this.choices = choices;
    this.isEnd = isEnd;
  }

  /** 剧情结束节点 */
  public static DialogNode end(String id, String content) {
    return new DialogNode(id, null, content, "normal", null, Seq.with(), true);
  }
}
