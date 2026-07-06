package crystal.type;

import arc.func.Boolp;

public class DuJieCondition {
  /** 渡劫目标说明，展示给玩家 */
  public final String str;
  /** 成功条件，返回true则渡劫成功 */
  public final Boolp success;
  /** 失败条件，返回true则渡劫失败，优先级高于成功 */
  public final Boolp fail;

  public DuJieCondition(String str, Boolp success, Boolp fail) {
    this.str = str;
    this.success = success;
    this.fail = fail;
  }
}
