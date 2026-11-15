package crystal.entities.comp;

import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import mindustry.gen.Drawc;
import mindustry.gen.Timedc;
import mindustry.type.ItemStack;

@EntityComponent
public abstract class YiCarryComp implements Drawc, Timedc {
  @Import
  public float x, y;
  public ItemStack[] items;

  @Override
  public void draw() {
  }
}
