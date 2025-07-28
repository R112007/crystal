package crystal.world.blocks.denfence;

import crystal.world.meta.CStat;
import mindustry.entities.TargetPriority;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Env;
import mindustry.world.meta.StatUnit;

public class HealWall extends Wall {
  public float healpercent;

  public HealWall(String name) {
    super(name);
    healpercent = 1f;
    solid = true;
    destructible = true;
    update = true;
    group = BlockGroup.walls;
    buildCostMultiplier = 6f;
    canOverdrive = false;
    drawDisabled = false;
    crushDamageMultiplier = 5f;
    priority = TargetPriority.wall;
    envEnabled = Env.any;
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.healpercent, this.healpercent * 60f);
  }

  public class HealWallBuild extends WallBuild {
    @Override
    public void updateTile() {
      if (health < maxHealth) {
        heal(healpercent);
      }
    }
  }
}
