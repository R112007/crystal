package crystal.world.blocks.stroage;

import java.lang.reflect.Field;

import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.struct.Seq;
import crystal.gen.Corec;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

public class MCoreBlock extends CoreBlock {
  private static Field coresField;

  static {
    try {
      coresField = TeamData.class.getField("cores");
      coresField.setAccessible(true);
    } catch (Exception e) {
      throw new RuntimeException("核心注入初始化失败", e);
    }
  }

  private static void recalculateCapacity(Team team) {
    Seq<CoreBuild> cores;
    try {
      cores = (Seq<CoreBuild>) coresField.get(team.data());
    } catch (Exception e) {
      return;
    }
    if (cores.isEmpty())
      return;

    int totalCap = 0;
    for (CoreBuild core : cores) {
      totalCap += core.block.itemCapacity;
    }

    // 同步给所有核心，覆盖原版计算结果
    for (CoreBuild core : cores) {
      core.storageCapacity = totalCap;
    }
  }

  public MCoreBlock(String name) {
    super(name);
  }

  public class MCoreBuild extends CoreBuild {
    public Corec corec;

    @Override
    public void hitbox(Rect out) {
      out.setCentered(corec.x(), corec.y(),
          block.size * mindustry.Vars.tilesize, block.size * mindustry.Vars.tilesize);
    }

    @Override
    public void updateTile() {
      super.updateTile();
      x = corec.x();
      y = corec.y();
    }

    @Override
    public Team team() {
      return corec.team();
    }

    @Override
    public void updateLaunch() {
    }

    @Override
    public boolean isValid() {
      return !corec.dead() && corec.isAdded();
    }

    @Override
    public void requestSpawn(Player player) {
      corec.spawnPlayer(player); // 转到我们自己的重生逻辑
    }

    @Override
    public float x() {
      return corec.x();
    }

    @Override
    public float getX() {
      return corec.x();
    }

    @Override
    public float y() {
      return corec.y();
    }

    @Override
    public float getY() {
      return corec.y();
    }

    @Override
    public ItemModule flowItems() {
      return corec.items();
    }

    @Override
    public float health() {
      return corec.health();
    }

    @Override
    public float maxHealth() {
      return corec.maxHealth();
    }

    @Override
    public boolean dead() {
      return corec.dead();
    }

    @Override
    public boolean isAdded() {
      return true;
    }

    @Override
    public Tile tileOn() {
      return Vars.world.tileWorld(corec.x(), corec.y());
    }

    @Override
    public void onProximityUpdate() {
      super.onProximityUpdate();
      recalculateCapacity(corec.team());
    }

    @Override
    public int pos() {
      return Point2.pack(
          World.toTile(corec.x()),
          World.toTile(corec.y()));
    }
  }
}
