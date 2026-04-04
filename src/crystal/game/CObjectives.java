package crystal.game;

import arc.Core;
import arc.func.Boolp;
import crystal.CVars;
import crystal.entities.units.UnitEnum.XiuWei;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives.Objective;

public class CObjectives {
  public static class Bool implements Objective {
    public Boolp boolp;
    public UnlockableContent content;

    public Bool(Boolp bool, UnlockableContent content) {
      this.boolp = bool;
      this.content = content;
    }

    protected Bool() {
    }

    @Override
    public boolean complete() {
      return boolp.get();
    }

    @Override
    public String display() {
      return Core.bundle.format("ondamagefloor", content.emoji());
    }
  }

  public static class ArchieveXiuWei implements Objective {
    public XiuWei xiuWei;

    public ArchieveXiuWei(XiuWei xiuWei) {
      this.xiuWei = xiuWei;
    }

    @Override
    public boolean complete() {
      return CVars.playerXiuWei.ordinal() >= this.xiuWei.ordinal();
    }

    @Override
    public String display() {
      return Core.bundle.get("xiuweiarchieve") + ": " + xiuWei.str;
    }

    @Override
    public String toString() {
      return "xiuWei: " + xiuWei;
    }
  }
}
