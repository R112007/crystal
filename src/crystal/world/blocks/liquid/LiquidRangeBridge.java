package crystal.world.blocks.liquid;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.liquid.LiquidBridge;

public class LiquidRangeBridge extends LiquidBridge {
  public LiquidRangeBridge(String name) {
    super(name);
  }

  @Override
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    Drawf.dashCircle(x * Vars.tilesize, y * Vars.tilesize, range * Vars.tilesize, Pal.accent);
  }

  @Override
  public boolean linkValid(Tile tile, Tile other, boolean checkDouble) {
    if (other == null || tile == null || other == tile)
      return false;
    return ((other.block() == tile.block() && tile.block() == this)
        || (!(tile.block() instanceof ItemBridge) && other.block() == this))
        && (other.team() == tile.team() || tile.block() != this)
        && (!checkDouble || ((LiquidRangeBridgeBuild) other.build).link != tile.pos());
  }

  public class LiquidRangeBridgeBuild extends LiquidBridgeBuild {
    @Override
    public void updateTile() {
      Building other = Vars.world.build(this.link);
      if (other != null) {
        if (!((LiquidRangeBridge) block).linkValid(this.tile, other.tile)) {
          this.link = -1;
        }
        super.updateTile();
      }
    }

    @Override
    public void drawConfigure() {
      float sin = Mathf.absin(Time.time, 6, 1);
      Draw.color(Pal.accent);
      Lines.stroke(1);
      Drawf.circles(this.x, this.y, (block.size / 2 + 1) * Vars.tilesize + sin - 2, Pal.accent);
      Building other = Vars.world.build(this.link);
      if (other != null) {
        Drawf.circles(other.x, other.y, (block.size / 3 + 1) * Vars.tilesize + sin - 2, Pal.place);
        Drawf.arrow(this.x, this.y, other.x, other.y, block.size * Vars.tilesize + sin, 4 + sin, Pal.accent);
      }
      Drawf.dashCircle(this.x, this.y, range * Vars.tilesize, Pal.accent);
    }

    @Override
    public void draw() {
      Draw.rect(block.region, this.x, this.y);
      Draw.z(Layer.power);

      Building other = Vars.world.build(this.link);
      if (other == null)
        return;
      float op = Core.settings.getInt("bridgeopacity") / 100f;
      if (Mathf.zero(op))
        return;
      Draw.color((this.liquids.currentAmount() > 0 ? this.liquids.current().color : Color.white));
      Draw.alpha(Math.max(this.power.status, 0.25f) * op);

      Draw.rect(((LiquidRangeBridge) block).endRegion, this.x, this.y);
      Draw.rect(((LiquidRangeBridge) block).endRegion, other.x, other.y);

      Lines.stroke(8);

      Tmp.v1.set(this.x, this.y).sub(other.x, other.y).setLength(Vars.tilesize / 2).scl(-1);

      Lines.line(((LiquidRangeBridge) block).bridgeRegion, this.x, this.y, other.x, other.y, false);
      Draw.reset();
    }

    @Override
    public boolean acceptLiquid(Building source, Liquid liquid) {
      if (this.team != source.team || !block.hasLiquids)
        return false;
      return this.liquids.currentAmount() < block.liquidCapacity;
    }
    /*
     * @Override
     * public boolean checkDump(Building to) {
     * return true;
     * }
     */
  }
}
