package crystal.world.blocks.stroage;

import arc.Events;
import arc.math.geom.Point2;
import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import crystal.gen.Corec;
import crystal.type.CoreUnit;
import crystal.world.blocks.stroage.MCoreBlock.MCoreBuild;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.SectorLoseEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.type.Sector;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BlockFlag;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;

public class CoreInjector {
  private static Field coresField;
  private static Field xField, yField;

  static {
    try {
      coresField = TeamData.class.getField("cores");
      coresField.setAccessible(true);
    } catch (Exception e) {
      throw new RuntimeException("核心注入初始化失败", e);
    }
    xField = findField(CoreBuild.class, "x");
    yField = findField(CoreBuild.class, "y");
  }

  private static Field findField(Class<?> clazz, String name) {
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    return null;
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
      e.printStackTrace();
    }
  }

  /**
   * 同步 proxy 的 x/y 字段。
   * proxy 覆写了 x()/y() 方法，但 BuildingComp.hitbox() 等方法直接使用 x/y 字段，
   * 不同步字段会导致 QuadTree 空间查询、hitbox() 等返回错误位置。
   */
  public static void syncProxyPos(CoreBuild proxy, float x, float y) {
    try {
      if (xField != null)
        xField.setFloat(proxy, x);
      if (yField != null)
        yField.setFloat(proxy, y);
    } catch (Exception e) {
      // ignore
    }
  }

  /**
   * 重建队伍的 buildingTree（QuadTree）。
   * 因为移动核心的位置不断变化，QuadTree 中的旧位置会过期，
   * 需要定期重建以让 findEnemyTile() 等空间查询能正确找到 proxy。
   */
  public static void rebuildBuildingTree(Team team) {
    try {
      TeamData data = team.data();
      if (data.buildingTree == null) {
        if (Vars.world != null && Vars.world.unitWidth() > 0) {
          data.buildingTree = new QuadTree<>(new Rect(0, 0, Vars.world.unitWidth(), Vars.world.unitHeight()));
        } else {
          return;
        }
      }
      data.buildingTree.clear();
      for (Building b : data.buildings) {
        // 不使用 b.isValid() — proxy CoreBuild 的 tile.build != this（从未通过正常建造流程放置），
        // isValid() 永远返回 false，会导致所有 proxy 被排除出树。
        // data.buildings 由我们手动维护，removeCore 时已移除死掉的 proxy，
        // 所以这里只检查非 null 即可。
        if (b != null) {
          data.buildingTree.insert(b);
        }
      }
    } catch (Exception e) {
      // ignore
    }
  }

  /**
   * 确保 proxy 在所有索引结构中。
   * 移动核心的 proxy 可能因各种原因从索引中丢失（如 updateTeamStats 清空），
   * 需要定期重新注册。
   */
  public static void reindexCore(Corec coreUnit) {
    try {
      CoreBuild proxy = coreUnit.proxy();
      if (proxy == null || coreUnit.dead() || !coreUnit.isAdded())
        return;

      Team team = coreUnit.team();
      TeamData data = team.data();

      // 1. flagMap
      if (Vars.indexer != null) {
        Seq<Building> flagged = Vars.indexer.getFlagged(team, BlockFlag.core);
        if (flagged != null && !flagged.contains(proxy, true)) {
          flagged.add(proxy);
        }
      }

      // 2. TeamData.buildings
      if (!data.buildings.contains(proxy, true)) {
        data.buildings.add(proxy);
      }

      // 3. TeamData.buildingTypes
      Seq<Building> blockList = data.getBuildings(proxy.block);
      if (!blockList.contains(proxy, true)) {
        blockList.add(proxy);
      }

      // 4. 重建 buildingTree（proxy 位置可能已变化）
      rebuildBuildingTree(team);
    } catch (Exception e) {
      // ignore
    }
  }

  public static void injectCore(TeamData data, Corec coreUnit) {
    try {
      Seq<CoreBuild> cores = (Seq<CoreBuild>) coresField.get(data);

      CoreUnit type = (CoreUnit) coreUnit.type();
      MCoreBlock ourCoreBlock = type.core();
      MCoreBuild proxy = ourCoreBlock.new MCoreBuild() {
        @Override
        public void updateTile() {
          super.updateTile();
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

        /**
         * 覆写 hitbox()，使其返回单位当前位置而非 x/y 字段的陈旧值。
         * QuadTree.insert() 和 remove() 都通过 hitbox() 确定位置。
         */
        @Override
        public void hitbox(Rect out) {
          out.setCentered(corec.x(), corec.y(),
              block.size * mindustry.Vars.tilesize, block.size * mindustry.Vars.tilesize);
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
      };
      proxy.corec = coreUnit;
      proxy.create(ourCoreBlock, coreUnit.team());
      proxy.tile = Vars.world.tileWorld(coreUnit.x(), coreUnit.y());
      // 同步 x/y 字段，让 hitbox() 等基于字段的方法也能返回正确位置
      syncProxyPos(proxy, coreUnit.x(), coreUnit.y());
      if (cores.any()) {
        ItemModule sharedItems = cores.first().items;
        proxy.items = sharedItems;
        coreUnit.items(sharedItems);
      } else {
        proxy.items = coreUnit.items();
      }
      coreUnit.proxy(proxy);

      // === 注册到 TeamData.cores + 更新 active/coreEnemies ===
      if (Vars.state != null && Vars.state.teams != null) {
        Vars.state.teams.registerCore(proxy);
      } else {
        cores.add(proxy);
      }

      if (Vars.indexer != null) {
        // 1. flagMap[team][core] — getEnemy() 使用
        Vars.indexer.getFlagged(coreUnit.team(), BlockFlag.core).add(proxy);

        // 2. TeamData.buildings — presentFlag / active() 判定
        if (!data.buildings.contains(proxy)) {
          data.buildings.add(proxy);
        }

        // 3. TeamData.buildingTypes — 按方块类型查询
        Seq<Building> blockList = data.getBuildings(ourCoreBlock);
        if (!blockList.contains(proxy)) {
          blockList.add(proxy);
        }

        // 4. TeamData.buildingTree (QuadTree) — findEnemyTile() 空间范围查询
        if (data.buildingTree == null && Vars.world != null && Vars.world.unitWidth() > 0) {
          data.buildingTree = new QuadTree<>(new Rect(0, 0, Vars.world.unitWidth(), Vars.world.unitHeight()));
        }
        if (data.buildingTree != null) {
          data.buildingTree.insert(proxy);
        }
      }

      for (CoreBuild core : cores) {
        core.onProximityUpdate();
      }
      recalculateCapacity(coreUnit.team());
      recalculateUnitCap(coreUnit.team());
      MoveCoreSystem.getCores(coreUnit.team()).add(coreUnit);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void removeCore(TeamData data, Corec coreUnit) {
    try {
      Seq<CoreBuild> cores = (Seq<CoreBuild>) coresField.get(data);
      CoreBuild proxy = coreUnit.proxy();
      if (proxy == null) {
        MoveCoreSystem.getCores(coreUnit.team()).remove(coreUnit);
        return;
      }

      Block block = proxy.block;

      // 1. 从 flagMap 移除
      if (Vars.indexer != null) {
        Vars.indexer.getFlagged(coreUnit.team(), BlockFlag.core).remove(proxy);
      }

      // 2. 先从 TeamData.buildings 移除（必须在 unregisterCore 之前，
      // 否则 active() 会因 buildings 仍有 proxy 而返回 true，跳过 updateEnemies）
      data.buildings.remove(proxy);

      // 3. 从 TeamData.buildingTypes 移除
      if (block != null) {
        data.getBuildings(block).remove(proxy);
      }

      // 4. 从 TeamData.cores 移除 + 更新 active/coreEnemies
      // 此时 buildings 已清空 proxy，active() 能正确判断团队是否仍然活跃
      if (Vars.state != null && Vars.state.teams != null) {
        Vars.state.teams.unregisterCore(proxy);
      } else {
        cores.remove(proxy);
      }

      // 5. 重建 buildingTree — 不能只调用 remove()，因为 proxy 在移动过程中
      // 被 ensureProxyIndexed 重复插入到 QuadTree 的不同位置节点，
      // remove() 只能移除当前所在节点的一个实例，旧位置的实例不会被移除。
      // 重建树可以从 data.buildings 中干净地重建，排除已移除的 proxy。
      rebuildBuildingTree(coreUnit.team());

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
