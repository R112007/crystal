package crystal.ui.gal;

public class DialogHistoryEntry {
  /** 角色名称，null为旁白 */
  public final String characterName;
  /** 对话内容 */
  public final String content;
  /** 对话时间戳 */
  public final long timestamp;

  public DialogHistoryEntry(String characterName, String content) {
    this.characterName = characterName;
    this.content = content;
    this.timestamp = System.currentTimeMillis();
  }
}
