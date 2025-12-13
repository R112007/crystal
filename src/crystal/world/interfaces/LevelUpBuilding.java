package crystal.world.interfaces;

import arc.func.Prov;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import static mindustry.Vars.*;

public interface LevelUpBuilding {
  public Seq<Block> getSeq();

  public void changeMode(Mode mode);

  public boolean canLevelup(Block block);

  public default boolean canProduce(Block b, int size) {
    return b.isVisible() && b.size < size && !(b instanceof CoreBlock) && !state.rules.isBanned(b)
        && b.environmentBuildable();
  }

  public enum Mode {
    fine, cannot, leveling
  }
}
