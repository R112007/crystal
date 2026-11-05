package crystal.content;

import crystal.magic.Skill;
import crystal.magic.skills.CoreUnitSkill;

/** Registers the skills exposed by the radial skill HUD. */
public final class Skills {
  public static CoreUnitSkill coreUnit;

  private Skills() {
  }

  public static void load() {
    if (coreUnit != null) {
      return;
    }
    coreUnit = new CoreUnitSkill();
  }

  /** Returns the registered core-unit skill, loading it lazily if necessary. */
  public static CoreUnitSkill coreUnit() {
    if (coreUnit == null) {
      load();
    }
    return coreUnit;
  }

  public static Skill skill(String name) {
    return Skill.all.get(name);
  }
}
