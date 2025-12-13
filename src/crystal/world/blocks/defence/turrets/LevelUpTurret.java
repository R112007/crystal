package crystal.world.blocks.defence.turrets;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.world.interfaces.LevelUpBuilding;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.defense.turrets.Turret;

import static mindustry.Vars.*;

public class LevelUpTurret extends Turret implements LevelUpBuilding {
  public Seq<Block> blocks = new Seq<>();
  public Mode mode = Mode.cannot;

  public LevelUpTurret(String name) {
    super(name);
    solid = true;
    update = true;
    configurable = true;
    blocks.add(Blocks.duo, Blocks.arc);
    config(Block.class, (LevelUpTurretBuild build, Block block) -> {
      if (canProduce(block, this.size) && build.config != block) {
        build.config = block;
      }
    });
    configClear((LevelUpTurretBuild build) -> {
      build.config = null;
    });
  }

  @Override
  public void changeMode(Mode mode) {
    this.mode = mode;
  }

  @Override
  public Seq<Block> getSeq() {
    return blocks;
  }

  @Override
  public boolean canLevelup(Block block) {
    return true;
  }

  public class LevelUpTurretBuild extends TurretBuild {
    public @Nullable Block config;

    @Override
    public @Nullable Block config() {
      return config;
    }

    @Override
    public void buildConfiguration(Table table) {
      ItemSelection.buildTable(LevelUpTurret.this, table,
          content.blocks().select(b -> blocks.contains(b)).<UnlockableContent>as(),
          () -> (UnlockableContent) config(), this::configure, selectionRows, selectionColumns);
    }
  }
}
