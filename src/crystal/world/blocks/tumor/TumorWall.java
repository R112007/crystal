package crystal.world.blocks.tumor;

import arc.graphics.g2d.Draw;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.tumor.TissueState;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorRender;
import crystal.world.tumor.TumorTissue;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/** Fleshy barrier whose durability grows with the tumor level. */
public class TumorWall extends Wall {
  public TumorWall(String name) {
    super(name);
    size = 1;
    health = 650;
    armor = 3f;
    update = true;
    destructible = true;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorWallBuild extends WallBuild implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public int appliedLevel;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      if (TumorNetwork.updateTissue(this, tissue) || net.client()) return;
      int level = TumorNetwork.levelOf(this, tissue);
      if (level != appliedLevel) {
        appliedLevel = level;
        float ratio = maxHealth <= 0f ? 1f : Math.min(1f, health / maxHealth);
        maxHealth = block.health * (1f + (level - 1) * 0.42f);
        health = maxHealth * ratio;
      }
    }

    @Override
    public void draw() {
      float scale = tissue.visualScale();
      Draw.alpha(tissue.visualAlpha());
      TumorRender.draw(region, x, y, 0f, scale);
      drawTeamTop();
      Draw.reset();
    }

    @Override
    public byte version() {
      return 3;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      tissue.write(write);
      write.i(appliedLevel);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      read.i();
      appliedLevel = 0;
    }
  }
}
