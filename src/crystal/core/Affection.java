package crystal.core;

import arc.Core;
import arc.func.Boolp;

public class Affection {
  public static Affection affection = new Affection();
  private final String yiKey = "crystal-yi-affection";
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

  public void increaseAffection(int amount, Boolp boolp) {
    if (boolp.get()) {
      yiAffection += amount;
      save();
    }
  }

  public void increaseAffection(int amount) {
    increaseAffection(amount, () -> true);
  }

  public void decreaseAffection(int amount, Boolp boolp) {
    if (boolp.get()) {
      yiAffection -= amount;
      save();
    }
  }

  public void decreaseAffection(int amount) {
    decreaseAffection(amount, () -> true);
  }

  public void load() {
    yiAffection = Core.settings.getInt(yiKey, 0);
  }

  public void save() {
    Core.settings.put(yiKey, yiAffection);
  }
}
