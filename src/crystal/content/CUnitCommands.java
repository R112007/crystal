package crystal.content;

import crystal.ai.type.CoreAuxiliaryAI;
import crystal.ai.type.RammingAI;
import mindustry.ai.UnitCommand;

public class CUnitCommands {
  public static UnitCommand coreAuxiliaryCommand, rammingCommand;

  public static void load() {
    coreAuxiliaryCommand = new UnitCommand("core-auxiliary", "production", u -> {
      if (CoreAuxiliaryAI.canUse(u)) {
        return new CoreAuxiliaryAI();
      }
      return null;
    });
    rammingCommand = new UnitCommand("ramming", "book", u -> {
      if (CoreAuxiliaryAI.canUse(u)) {
        return new RammingAI();
      }
      return null;
    });
  }

}
