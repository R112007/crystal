package crystal.ui;

import arc.Core;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectSet;
import crystal.CVars;
import static mindustry.gen.Icon.icons;

public class WorldStuff {
  public static ObjectSet<WorldStuff> stuffs = new ObjectSet<>();
  public String name, description;
  public TextureRegionDrawable icon;

  public WorldStuff(String name, String description) {
    this.name = name;
    this.description = description;
    this.icon = Core.atlas.getDrawable("crystal-" + name);
    stuffs.add(this);
    icons.put(name, icon);
  }

  public WorldStuff(String name) {
    this(name, "");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof WorldStuff stuff))
      return false;
    return this.name == stuff.name && this.description == stuff.description;
  }
}
