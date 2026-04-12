package crystal.content;

import mindustry.game.Schematic;
import mindustry.game.Schematics;

public class CLoadouts {
  public static Schematic jichuhexi;

  public static void load() {
    jichuhexi = Schematics.readBase64(
        "bXNjaAF4nGNgZmBmYWDJS8xNZeB52jH36fLuZwt2PN3fzMCVnJ9XkppX4ptYwMBUXcvAnZJanFyUWVCSmZ/HwMDAlpOYlJpTzMAUHcvIwJtcVFlckpijm5xflGoIlGUEISABAARjHFs=");
  }
}
