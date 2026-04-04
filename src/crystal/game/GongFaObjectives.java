package crystal.game;

import arc.Core;
import crystal.type.GongFa;
import mindustry.game.Objectives;

public class GongFaObjectives {
  // 功法解锁条件：必须解锁指定功法
  public static class GongFaResearch implements Objectives.Objective {
    public GongFa gongFa;

    public GongFaResearch(GongFa gongFa) {
      this.gongFa = gongFa;
    }

    @Override
    public boolean complete() {
      return gongFa.unlockedHost();
    }

    @Override
    public String display() {
      return Core.bundle.format("requirement.gongfa.research",
          gongFa.unlockedHost() ? gongFa.localizedName : "???");
    }
  }

  public static class GongFaRealmReach implements Objectives.Objective {
    public int realmId;
    public String realmName;

    public GongFaRealmReach(int realmId, String realmName) {
      this.realmId = realmId;
      this.realmName = realmName;
    }

    public GongFaRealmReach(int realmId) {
      this(realmId, realmId + "阶");
    }

    @Override
    public boolean complete() {
      // 只要同ID的任意一个功法解锁，就算达到该境界
      return GongFa.gongFas.values().toSeq()
          .find(g -> g.id == realmId && g.unlockedHost()) != null;
    }

    @Override
    public String display() {
      return Core.bundle.format("requirement.gongfa.realm", realmName);
    }
  }

  // 新增：平行分支互斥（解锁了A就不能解锁B，二选一）
  public static class GongFaExclusive implements Objectives.Objective {
    public GongFa excludeGongFa;

    public GongFaExclusive(GongFa excludeGongFa) {
      this.excludeGongFa = excludeGongFa;
    }

    @Override
    public boolean complete() {
      // 互斥功法未解锁，才能解锁当前功法
      return !excludeGongFa.unlockedHost();
    }

    @Override
    public String display() {
      return Core.bundle.format("requirement.gongfa.exclusive", excludeGongFa.localizedName);
    }
  }
}
