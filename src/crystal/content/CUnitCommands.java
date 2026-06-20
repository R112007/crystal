package crystal.content;

import crystal.ai.type.CoreAuxiliaryAI;
import mindustry.ai.UnitCommand;

public class CUnitCommands {
  public static UnitCommand coreAuxiliaryCommand;

  public static void load() {
    coreAuxiliaryCommand = new UnitCommand("core-auxiliary", "production", u -> {
      if (CoreAuxiliaryAI.canUse(u)) {
        return new CoreAuxiliaryAI();
      }
      return null;
    });
  }

}
