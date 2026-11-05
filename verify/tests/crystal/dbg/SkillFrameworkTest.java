package crystal.dbg;

import crystal.core.CultivationState;
import crystal.gen.SkillEntity;
import crystal.magic.Skill;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Player;

/**
 * Headless regression checks for the instant/continuous Skill dispatch and the
 * generated SkillEntity update bridge.
 */
public class SkillFrameworkTest {
  private static boolean ok = true;
  private static int instantCalls;
  private static int continuousCalls;
  private static float seenAimX;

  public static void run(String[] args) {
    try {
      CultivationState.availableMagicPower = 100f;
      Vars.state.set(mindustry.core.GameState.State.playing);

      Skill instant = new Skill("test-instant") {
        @Override
        public void influence() {
          instantCalls++;
          seenAimX = aimX;
        }
      };
      instant.baseCost = 10f;
      instant.unlock();

      Skill continuous = new Skill("test-continuous") {
        @Override
        public void influence(SkillEntity entity) {
          continuousCalls++;
        }
      };
      continuous.continuous = true;
      continuous.duration = 2f;
      continuous.unlock();

      Player player = Player.create();
      player.id = 4242;
      player.team(Team.sharded);
      player.set(12f, 34f);

      check("instant cast succeeds", true, instant.cast(player, 56f, 78f, 90f));
      check("instant uses influence()", 1, instantCalls);
      check("instant receives aim X", 56f, seenAimX);
      check("instant consumes spirit power", 90f, CultivationState.availableMagicPower);

      check("continuous cast succeeds", true, continuous.cast(player, 56f, 78f, 90f));
      check("continuous enters through influence(entity)", 1, continuousCalls);
      check("continuous cast creates an entity", true, continuous.isActive(player));

      SkillEntity entity = findEntity(continuous);
      check("entity stores skill id", continuous.id, entity == null ? -1 : entity.skillId);
      if (entity != null) {
        entity.update();
      }
      check("entity update calls influence(entity)", 2, continuousCalls);
      check("continuous skill cannot stack", false, continuous.cast(player, 0f, 0f, 0f));

      continuous.endActive();
      check("endActive removes the entity", false, continuous.isActive(player));
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }

    System.out.println("[SKILL-FRAMEWORK] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static SkillEntity findEntity(Skill skill) {
    for (var entity : mindustry.gen.Groups.all) {
      if (entity instanceof SkillEntity skillEntity && skillEntity.skillId == skill.id) {
        return skillEntity;
      }
    }
    return null;
  }

  private static void check(String label, Object expected, Object actual) {
    boolean pass = expected == null ? actual == null : expected.equals(actual);
    ok &= pass;
    System.out.println("[SKILL-FRAMEWORK] " + label + ": " + actual + "/" + expected
        + (pass ? " OK" : " FAIL"));
  }
}
