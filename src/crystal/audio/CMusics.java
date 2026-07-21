package crystal.audio;

import arc.Core;
import arc.audio.Music;
import arc.util.Log;
import mindustry.Vars;

public class CMusics {
  public static Music memoryGallery;

  public static void load() {
    memoryGallery = Vars.tree.loadMusic("memoryGallery");
  }
}
