package crystal.magic.skills;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import crystal.core.CultivationState;
import crystal.gen.SkillEntity;
import crystal.magic.Skill;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Player;

/**
 * Summons or recalls the mobile core's player unit.
 *
 * <p>This is a stateful continuous skill. A {@link SkillEntity} represents the
 * summoned state so the generic skill runtime, not the HUD, owns its
 * lifecycle.</p>
 */
public final class CoreUnitSkill extends Skill {

  public CoreUnitSkill() {
    super("coreUnit");
    continuous = true;
    duration = 0f;
    iconColor = Color.valueOf("8fd8ff");
  }

  @Override
  public void loadIcon() {
    TextureRegion found = Core.atlas.find("crystal-core1");
    if (!found.found()) {
      found = Core.atlas.find("crystal-core");
    }
    if (!found.found()) {
      found = Icon.units.getRegion();
    }
    setIcon(found);
  }

  @Override
  public boolean visible() {
    return Vars.state != null
        && Vars.state.isPlaying()
        && Vars.state.rules != null
        && !Vars.state.rules.editor
        && Vars.player != null;
  }

  @Override
  public boolean ready() {
    return super.ready()
        && (MobileCoreControlSystem.isPlayerUnitActive()
            || MobileCoreControlSystem.canTogglePlayerUnit());
  }

  @Override
  public boolean canCast(Player player) {
    return MobileCoreControlSystem.canTogglePlayerUnit()
        && (isActive(player) || CultivationState.availableMagicPower > 0f);
  }

  @Override
  public boolean cast(Player player, float x, float y, float angle) {
    caster = player;
    aimX = x;
    aimY = y;
    this.angle = angle;

    if (isActive(player)) {
      endActive();
      MobileCoreControlSystem.togglePlayerUnit();
      return true;
    }
    return super.cast(player, x, y, angle);
  }

  @Override
  public void influence(SkillEntity entity) {
    if (!entity.started) {
      entity.started = true;
      if (!MobileCoreControlSystem.isPlayerUnitActive()) {
        MobileCoreControlSystem.togglePlayerUnit();
      }
      if (!MobileCoreControlSystem.isPlayerUnitActive()) {
        entity.remove();
      }
      return;
    }

    // The U key can recall the unit outside this skill. Keep the backing
    // entity in sync so the radial button immediately returns to idle.
    if (!MobileCoreControlSystem.isPlayerUnitActive()) {
      entity.remove();
    }
  }
}
