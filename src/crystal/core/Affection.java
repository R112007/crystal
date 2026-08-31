package crystal.core;

import arc.Core;

public class Affection {
  public static Affection affection = new Affection();
  private static final String YI_KEY = "crystal-yi-affection";
  private int yiAffection = 0;

  private Affection() {
  }

  public int getYiAffection() {
    return yiAffection;
  }

  public void setYiAffection(int value) {
    yiAffection = value;
    save();
  }

  // 原来的 increaseAffection(int, Boolp) 懒求值重载没有任何实际调用点（调用方全是 () -> true），已删除
  public void increaseAffection(int amount) {
    yiAffection += amount;
    save();
  }

  public void decreaseAffection(int amount) {
    yiAffection -= amount;
    save();
  }

  public void load() {
    // 不能用 getInt：settings 经 JSON 读写后 int 变 Float，Arc 的 getInt 硬强转 (int)
    // 会 ClassCastException。用 Number 同时兼容 Integer（本次会话写入）与 Float（重启后读回）
    Object v = Core.settings.get(YI_KEY, null);
    yiAffection = v instanceof Number ? ((Number) v).intValue() : 0;
  }

  public void save() {
    Core.settings.put(YI_KEY, yiAffection);
    // 原来只 put 不落盘，崩溃/杀进程时好感度会丢；好感度变动只在剧情节点，频率低，直接落盘
    Core.settings.manualSave();
  }
}
