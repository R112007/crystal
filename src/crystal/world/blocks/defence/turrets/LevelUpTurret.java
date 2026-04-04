package crystal.world.blocks.defence.turrets;

import arc.Events;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Timer;
import crystal.game.CEventType.GenerateBuild;
import crystal.world.interfaces.LevelUpBuilding;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.ui.Styles;
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
    blocks.add(Blocks.duo, Blocks.arc, Blocks.graphitePress);
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
      for (var b : blocks) {
        TextureRegionDrawable reg = new TextureRegionDrawable();
        reg.set(b.uiIcon);
        table.button(reg, Styles.cleari, () -> {
          Events.fire(new GenerateBuild(b, this.tile, this.team, (byte) this.rotation));
          kill();
        }).size(40f);
      }
    }
  }
}
