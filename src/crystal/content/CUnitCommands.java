package crystal.content;

import crystal.ai.type.CoreAuxiliaryAI;
import crystal.gen.Corec;
import mindustry.ai.UnitCommand;
import mindustry.gen.Unit;

public class CUnitCommands {
  public static UnitCommand coreAuxiliaryCommand;

  public static void load() {
    coreAuxiliaryCommand = new UnitCommand("core-auxiliary", "production", u -> {
      if (CoreAuxiliaryAI.canUse(u)) {
        return new CoreAuxiliaryAI();
      }
      return null;
    }) {
      {
        switchToMove = false;
        drawTarget = false;
        resetTarget = false;
      }
    };
  }

}
