package crystal.world.tumor;

import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;

/**
 * Serialized topology and lifecycle data shared by all tumor buildings.
 *
 * <p>The parent is intentionally stored as a tile position instead of a live object. Buildings can
 * be replaced, saved and loaded independently while the network still knows which tendril feeds
 * them.</p>
 */
public class TissueState {
  public static final float witherDuration = 110f;
  public static final float maturationTime = 180f;

  public int coreX = -1, coreY = -1;
  public int parentX = -1, parentY = -1;
  public int depth;
  public boolean withering;
  public boolean defended;
  public boolean transport;
  public boolean mature = true;
  public float witherElapsed;
  public float linkTimer;
  public float growthTimer;
  public float maturation = maturationTime;

  public void copyFrom(TissueState other) {
    coreX = other.coreX;
    coreY = other.coreY;
    parentX = other.parentX;
    parentY = other.parentY;
    depth = other.depth;
    withering = other.withering;
    defended = other.defended;
    transport = other.transport;
    mature = other.mature;
    witherElapsed = other.witherElapsed;
    linkTimer = other.linkTimer;
    growthTimer = other.growthTimer;
    maturation = other.maturation;
  }

  public void beginWither(float delay) {
    if (withering) return;
    withering = true;
    witherElapsed = -Math.max(delay, 0f);
  }

  /** @return true once the tissue has fully necrotized and should be removed. */
  public boolean update(float delta) {
    if (!withering) {
      linkTimer += delta;
      return false;
    }

    witherElapsed += delta;
    return witherElapsed >= witherDuration;
  }

  public float visualScale() {
    if (!withering || witherElapsed <= 0f) return 1f;
    float t = Mathf.clamp(witherElapsed / witherDuration);
    return Mathf.clamp(1f - t * 0.82f);
  }

  public float visualAlpha() {
    if (!withering || witherElapsed <= 0f) return 1f;
    return Mathf.clamp(1f - witherElapsed / witherDuration);
  }

  public void write(Writes write) {
    write.i(coreX);
    write.i(coreY);
    write.i(parentX);
    write.i(parentY);
    write.i(depth);
    write.bool(withering);
    write.bool(defended);
    write.f(witherElapsed);
    write.f(linkTimer);
    write.bool(transport);
    write.bool(mature);
    write.f(growthTimer);
    write.f(maturation);
  }

  public void read(Reads read, byte revision) {
    coreX = read.i();
    coreY = read.i();
    parentX = read.i();
    parentY = read.i();
    depth = read.i();
    withering = read.bool();
    defended = read.bool();
    witherElapsed = read.f();
    linkTimer = read.f();
    transport = revision >= 2 && read.bool();
    if (revision >= 3) {
      mature = read.bool();
      growthTimer = read.f();
      maturation = read.f();
    }
  }
}
