package crystal.dbg;

import arc.Core;
import arc.Events;
import crystal.core.CSettings;
import crystal.core.ShenWuSystem;
import crystal.core.ScrollSystem;
import crystal.game.CEventType.ScrollObtainedEvent;
import crystal.game.CEventType.ScrollUsedEvent;
import crystal.magic.Scroll;
import crystal.magic.ShenWu;
import crystal.magic.Skill;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Player;

/**
 * Headless regression for persisted skill unlocks, scroll inventory semantics,
 * and events used by the notification / skill-detail UI.
 */
public class UnlockScrollSystemTest {
  private static boolean ok = true;
  private static int obtainedEvents;
  private static int usedEvents;

  public static void run(String[] args) {
    try {
      ShenWuSystem.init();
      ScrollSystem.init();
      CultivationPower.set100();
      Vars.state.set(mindustry.core.GameState.State.playing);

      Events.on(ScrollObtainedEvent.class, event -> obtainedEvents++);
      Events.on(ScrollUsedEvent.class, event -> usedEvents++);

      testStandaloneSkillScroll();
      testShenWuSkillScroll();
      testPersistedUnlockKeys();
      testHudToggles();
    } catch (Throwable error) {
      error.printStackTrace();
      ok = false;
    }

    System.out.println("[UNLOCK-SCROLL] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void testStandaloneSkillScroll() {
    int[] casts = {0};
    Skill skill = new Skill("unlock-test-magic") {
      @Override
      public void influence() {
        casts[0]++;
      }
    };
    skill.lock();
    Scroll scroll = new Scroll("unlock-test-magic-scroll", "测试卷轴", skill);

    Player player = Player.create();
    player.id = 9001;
    player.team(Team.sharded);
    player.set(0f, 0f);

    check("standalone starts locked", false, skill.unlocked());
    check("locked standalone cannot cast", false, skill.cast(player, 0f, 0f, 90f));

    ScrollSystem.give(scroll, 2);
    check("give stores both scrolls", 2, ScrollSystem.count(scroll));
    check("give emits obtained event", true, obtainedEvents > 0);
    check("scroll icon has fallback", true, scroll.uiIcon() != null);

    check("use unlocks standalone skill", true, ScrollSystem.use(scroll));
    check("standalone skill unlocked", true, skill.unlocked());
    check("one scroll consumed", 1, ScrollSystem.count(scroll));
    check("use emits scroll event", true, usedEvents > 0);
    check("unlocked standalone can cast", true, skill.cast(player, 0f, 0f, 90f));
    check("influence ran once", 1, casts[0]);

    check("duplicate use does not consume", false, ScrollSystem.use(scroll));
    check("duplicate use keeps inventory", 1, ScrollSystem.count(scroll));
  }

  private static void testShenWuSkillScroll() {
    ShenWu shenWu = new ShenWu("unlock-test-shenwu") {
      {
        addSkill(new Skill("unlock-test-shenwu-skill") {
          @Override
          public boolean instance(ShenWu owner, float x, float y) {
            return true;
          }
        });
      }
    };

    ShenWu.Skill skill = shenWu.skills.first();
    skill.markLocked();
    Scroll scroll = new Scroll("unlock-test-shenwu-scroll", "测试神武卷轴", skill);

    check("shenwu starts locked at level zero", 0, skill.level);
    check("shenwu requires unlock", false, skill.unlocked());
    ScrollSystem.give(scroll, 1);
    check("shenwu scroll unlocks", true, ScrollSystem.use(scroll));
    check("shenwu skill unlocked", true, skill.unlocked());
    check("shenwu unlock sets level one", 1, skill.level);
    check("shenwu scroll consumed", 0, ScrollSystem.count(scroll));
  }

  private static void testPersistedUnlockKeys() {
    Core.settings.put("skill.unlock-test-persisted-unlocked", true);
    Skill persisted = new Skill("unlock-test-persisted") {
    };
    check("standalone unlock key is loaded", true, persisted.unlocked());

    ShenWu shenWu = new ShenWu("unlock-test-persisted-shenwu") {
      {
        addSkill(new Skill("unlock-test-persisted-shenwu-skill") {
          @Override
          public boolean instance(ShenWu owner, float x, float y) {
            return true;
          }
        });
      }
    };
    ShenWu.Skill skill = shenWu.skills.first();
    ShenWuSystem.unlockSkill(skill);
    skill.markLocked();
    ShenWuSystem.load();
    check("shenwu unlock survives reload", true, skill.unlocked());
    check("shenwu level survives reload", true, skill.level > 0);
  }

  private static void testHudToggles() {
    CSettings.setShowSkillHUD(false);
    CSettings.setShowFaBaoHUD(false);
    check("skill HUD toggle persists false", false, CSettings.showSkillHUD());
    check("fabao HUD toggle persists false", false, CSettings.showFaBaoHUD());
    CSettings.setShowSkillHUD(true);
    CSettings.setShowFaBaoHUD(true);
  }

  private static void check(String label, Object expected, Object actual) {
    boolean pass = expected == null ? actual == null : expected.equals(actual);
    ok &= pass;
    System.out.println("[UNLOCK-SCROLL] " + label + ": " + actual + "/" + expected
        + (pass ? " OK" : " FAIL"));
  }

  private static final class CultivationPower {
    private static void set100() {
      crystal.core.CultivationState.availableMagicPower = 100f;
      crystal.core.CultivationState.playerMagicPower = 100f;
    }
  }
}
