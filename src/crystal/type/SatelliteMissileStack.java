package crystal.type;

import arc.math.Mathf;
import arc.struct.Seq;

public class SatelliteMissileStack implements Comparable<SatelliteMissileStack> {
  public static final SatelliteMissileStack[] empty = {};
  public SatelliteMissile missile;
  public int amount;

  public SatelliteMissileStack(SatelliteMissile missile, int amount) {
    this.missile = missile;
    this.amount = amount;
  }

  public SatelliteMissileStack set(SatelliteMissile missile, int amount) {
    this.missile = missile;
    this.amount = amount;
    return this;
  }

  public static SatelliteMissileStack[] mult(SatelliteMissileStack[] stacks, float amount) {
    var copy = new SatelliteMissileStack[stacks.length];
    for (int i = 0; i < copy.length; i++) {
      copy[i] = new SatelliteMissileStack(stacks[i].missile, Mathf.round(stacks[i].amount * amount));
    }
    return copy;
  }

  public static SatelliteMissileStack[] with(Object... missiles) {
    var stacks = new SatelliteMissileStack[missiles.length / 2];
    for (int i = 0; i < missiles.length; i += 2) {
      stacks[i / 2] = new SatelliteMissileStack((SatelliteMissile) missiles[i], ((Number) missiles[i + 1]).intValue());
    }
    return stacks;
  }

  public static Seq<SatelliteMissileStack> list(Object... missiles) {
    Seq<SatelliteMissileStack> stacks = new Seq<>(missiles.length / 2);
    for (int i = 0; i < missiles.length; i += 2) {
      stacks.add(new SatelliteMissileStack((SatelliteMissile) missiles[i], ((Number) missiles[i + 1]).intValue()));
    }
    return stacks;
  }

  public static SatelliteMissileStack[] copy(SatelliteMissileStack[] stacks) {
    var out = new SatelliteMissileStack[stacks.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = stacks[i].copy();
    }
    return out;
  }

  public SatelliteMissileStack copy() {
    return new SatelliteMissileStack(missile, amount);
  }

  public boolean equals(SatelliteMissileStack other) {
    return other != null && other.missile == missile && other.amount == amount;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof SatelliteMissileStack stack && stack.amount == amount && missile == stack.missile);
  }

  @Override
  public String toString() {
    return missile + ": " + amount;
  }

  @Override
  public int compareTo(SatelliteMissileStack stack) {
    return missile.compareTo(stack.missile);
  }
}
