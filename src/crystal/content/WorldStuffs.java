package crystal.content;

import crystal.ui.WorldStuff;

public class WorldStuffs {
  public static WorldStuff lightline, pao;

  public static void load() {
    lightline = new WorldStuff("lightline") {
      {
        this.description = "背负着无法动摇的爱";
      }
    };
    pao = new WorldStuff("pao") {
      {
        description = "开炮！";
      }
    };
  }
}
