package crystal.game;

import arc.Core;
import arc.func.Boolp;
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
}
