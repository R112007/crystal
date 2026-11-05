package crystal.magic;

import arc.graphics.g2d.TextureRegion;
import arc.util.Nullable;

/**
 * Common unlock contract shared by the standalone magic skills and the skills
 * owned by a ShenWu.
 */
public interface SkillUnlockable {
  /** Stable settings key used to persist the unlock. */
  String unlockKey();

  /** Offline-safe display name. Never null. */
  String displayName();

  /** Optional skill explanation. */
  @Nullable
  String displayDescription();

  /** Icon with a non-null fallback. */
  TextureRegion unlockIcon();

  /** Whether the skill can currently be cast. */
  boolean unlocked();

  /**
   * Permanently unlocks the skill and persists the state.
   *
   * @return whether this call changed the state
   */
  boolean unlock();
}
