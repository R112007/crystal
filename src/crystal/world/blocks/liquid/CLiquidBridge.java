package crystal.world.blocks.liquid;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.graphics.Drawf;
import mindustry.world.blocks.liquid.LiquidBridge;

public class CLiquidBridge extends LiquidBridge {
  public TextureRegion liquidRegion;
  public TextureRegion topRegion;
  public TextureRegion bottomRegion;

  public CLiquidBridge(String name) {
    super(name);
  }

  @Override
  public void load() {
    super.load();
    liquidRegion = Core.atlas.find(name + "-liquid");
    topRegion = Core.atlas.find(name + "-top");
    bottomRegion = Core.atlas.find(name + "-bottom");
  }

  @Override
  public TextureRegion[] icons() {
    return new TextureRegion[] { bottomRegion, region, topRegion };
  }

  public class CLiquidBridgeBuild extends LiquidBridgeBuild {
    @Override
    public void draw() {
      Draw.rect(bottomRegion, x, y, rotation);
      super.draw();
      float rotation = rotate ? rotdeg() : 0;

      if (liquids.currentAmount() > 0.001f) {
        Drawf.liquid(liquidRegion, x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
      }

      Draw.rect(region, x, y, rotation);
      Draw.rect(topRegion, x, y, rotation);
    }

  }
}
