package crystal.world.blocks.stroage;

import arc.Events;
import arc.math.geom.Point2;
import arc.struct.Seq;
import crystal.gen.Corec;
import crystal.type.CoreUnitType;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.SectorLoseEvent;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Player;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;

public class CoreInjector {
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

  private static void recalculateUnitCap(Team team) {
    try {
      Seq<CoreBlock.CoreBuild> cores = (Seq<CoreBlock.CoreBuild>) coresField.get(team.data());
      int totalCap = 0;
      for (var core : cores) {
        totalCap += core.block.unitCapModifier;
      }
      // 反射赋值TeamData的unitCap字段
      Field unitCapField = TeamData.class.getDeclaredField("unitCap");
      unitCapField.setAccessible(true);
      unitCapField.setInt(team.data(), totalCap);
    } catch (Exception e) {
      DLog.err("单位上限重计算失败：" + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void injectCore(TeamData data, Corec coreUnit) {
    try {
      Seq<CoreBuild> cores = (Seq<CoreBuild>) coresField.get(data);
      DLog.info("当前核心数量：" + cores.size);
      DLog.info("尝试加入虚拟核心");
      if (coreUnit.proxy() != null && cores.contains(coreUnit.proxy())) {
        DLog.info("核心已存在，跳过注入");
        return;
      }

      // 同时检查 mobileCores
      if (MoveCoreSystem.getCores(coreUnit.team()).contains(coreUnit)) {
        DLog.info("移动核心已注册，跳过注入");
        return;
      }
      CoreUnitType type = (CoreUnitType) coreUnit.type();
      CoreBlock ourCoreBlock = type.core();

      CoreBuild proxy = ourCoreBlock.new CoreBuild() {
        @Override
        public Team team() {
          return coreUnit.team();
        }

        @Override
        public void updateLaunch() {
        }

        @Override
        public boolean isValid() {
          return !coreUnit.dead() && coreUnit.isAdded();
        }

        @Override
        public void requestSpawn(Player player) {
          coreUnit.spawnPlayer(player); // 转到我们自己的重生逻辑
        }

        @Override
        public float x() {
          return coreUnit.x();
        }

        @Override
        public float y() {
          return coreUnit.y();
        }

        @Override
        public ItemModule flowItems() {
          return coreUnit.items();
        }

        @Override
        public float health() {
          return coreUnit.health();
        }

        @Override
        public float maxHealth() {
          return coreUnit.maxHealth();
        }

        @Override
        public boolean dead() {
          return coreUnit.dead();
        }

        @Override
        public boolean isAdded() {
          return true;
        }

        @Override
        public Tile tileOn() {
          return Vars.world.tileWorld(coreUnit.x(), coreUnit.y());
        }

        @Override
        public void onProximityUpdate() {
          super.onProximityUpdate();
          recalculateCapacity(coreUnit.team());
        }

        @Override
        public int pos() {
          return Point2.pack(
              World.toTile(coreUnit.x()),
              World.toTile(coreUnit.y()));
        }
      };

      proxy.create(ourCoreBlock, coreUnit.team());
      proxy.tile = Vars.world.tileWorld(coreUnit.x(), coreUnit.y());
      if (cores.any()) {
        ItemModule sharedItems = cores.first().items;
        proxy.items = sharedItems;
        coreUnit.items(sharedItems);
      } else {
        proxy.items = coreUnit.items();
      }
      cores.add(proxy);
      coreUnit.proxy(proxy);
      for (CoreBuild core : cores) {
        core.onProximityUpdate();
      }
      recalculateCapacity(coreUnit.team());
      recalculateUnitCap(coreUnit.team());
      DLog.info("运行添加完毕");
      DLog.info("当前核心数量：" + cores.size);
      MoveCoreSystem.getCores(coreUnit.team()).add(coreUnit);

    } catch (Exception e) {
      DLog.err("核心注入失败：" + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void removeCore(TeamData data, Corec coreUnit) {
    try {
      Seq<CoreBuild> cores = (Seq<CoreBuild>) coresField.get(data);
      cores.remove(coreUnit.proxy());
      MoveCoreSystem.getCores(coreUnit.team()).remove(coreUnit);
      for (CoreBuild core : cores) {
        core.onProximityUpdate();
      }
      recalculateCapacity(coreUnit.team());
      recalculateUnitCap(coreUnit.team());
      // 移除后判断是否还有核心
      if (cores.isEmpty()) {
        data.destroyToDerelict();
        if (Vars.state.isCampaign()) {
          Sector sector = Vars.state.getSector();
          Events.fire(new SectorLoseEvent(sector));
          sector.info.hasCore = false;
          sector.save = null;
          sector.saveInfo();
        }
        Team winner = coreUnit.team() == Vars.state.rules.defaultTeam
            ? Vars.state.rules.waveTeam
            : Vars.state.rules.defaultTeam;
        Events.fire(new GameOverEvent(winner));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
