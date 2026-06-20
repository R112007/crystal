package crystal.ai.type;

import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import crystal.gen.Corec;
import mindustry.ai.types.CommandAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

public class CoreAuxiliaryAI extends AIController {
  protected static final Vec2 vec = new Vec2();
  // ===== 建造相关 =====
  public @Nullable Unit assistUnit;
  public @Nullable BuildPlan assistPlan;
  // ===== 挖矿相关（仿照DrillTurret） =====
  public @Nullable Tile mineTile;
  public @Nullable Tile ore;
  public @Nullable Item targetItem;
  protected Seq<Tile> proxOres;
  protected Seq<Item> proxItems;
  protected int targetID = -1;
  public float mineTimer = 0f;

  public static boolean canUse(Unit unit) {
    return unit instanceof Corec && unit.canMine() && unit.canBuild();
  }

  @Override
  public void updateMovement() {
    if (!(unit instanceof Corec core))
      return;
    float auxRange = core.auxiliaryRange();
    // 优先检查建造协助
    boolean building = updateBuildAssist(core, auxRange);
    // 如果没有在建造，进行挖矿
    if (!building) {
      updateMining(core, auxRange);
    }
    // 不移动 - 只通过lookAt转向
  }

  // ==================== 建造协助 ====================
  protected boolean updateBuildAssist(Corec core, float auxRange) {
    // 自己不能建造就直接跳过
    if (!unit.canBuild())
      return false;
    // 清除无效的协助目标
    if (assistUnit != null && !assistUnit.isValid()) {
      assistUnit = null;
      assistPlan = null;
    }
    // 定时查找可协助的建造单位
    if (timer.get(timerTarget2, 20f)) {
      assistUnit = null;
      assistPlan = null;
      Units.nearby(unit.team, unit.x, unit.y, auxRange, u -> {
        if (assistUnit != null)
          return;
        if (u.canBuild() && u != unit && u.activelyBuilding()) {
          BuildPlan plan = u.buildPlan();
          if (plan != null) {
            float planX = plan.x * tilesize + tilesize / 2f;
            float planY = plan.y * tilesize + tilesize / 2f;
            if (unit.within(planX, planY, auxRange)) {
              assistUnit = u;
              assistPlan = plan;
            }
          }
        }
      });
    }
    if (assistUnit != null && assistPlan != null) {
      float planX = assistPlan.x * tilesize + tilesize / 2f;
      float planY = assistPlan.y * tilesize + tilesize / 2f;
      unit.lookAt(planX, planY);
      if (unit.within(planX, planY, unit.type.buildRange)) {
        if (unit.buildPlan() == null || !unit.buildPlan().samePos(assistPlan)) {
          unit.plans.clear();
          BuildPlan newPlan = new BuildPlan(assistPlan.x, assistPlan.y, assistPlan.rotation, assistPlan.block,
              assistPlan.config);
          newPlan.breaking = assistPlan.breaking; // 保留拆除状态
          newPlan.progress = assistPlan.progress; // 同步进度
          unit.addBuild(newPlan);
        }
        unit.updateBuilding = true;
        return true;
      } else {
        // 不在范围内，只转向
        unit.clearBuilding();
        return false;
      }
    }
    unit.clearBuilding();
    return false;
  }

  // ==================== 挖矿逻辑（仿照DrillTurret） ====================
  protected void updateMining(Corec core, float auxRange) {
    if (!unit.canMine()) {
      mineTile = null;
      return;
    }
    // 第一次或者缓存失效时重新扫描
    if (proxOres == null) {
      reMap(core, auxRange);
    }
    // 选择目标矿石
    targetMine(core, auxRange);
    if (mineTile == null) {
      mineTimer = 0f;
    }
  }

  /**
   * 扫描范围内所有矿石，建立缓存（仿照DrillTurret.reMap）
   */
  public void reMap(Corec core, float auxRange) {
    proxOres = new Seq<>();
    proxItems = new Seq<>();
    ObjectSet<Item> tempItems = new ObjectSet<>();
    int tileRadius = (int) (auxRange / tilesize + 0.5f);
    Geometry.circle(unit.tileX(), unit.tileY(), tileRadius, (x, y) -> {
      Tile other = world.tile(x, y);
      if (other != null) {
        Item drop = getOreDrop(other);
        if (drop != null && unit.canMine(drop) && !tempItems.contains(drop)) {
          tempItems.add(drop);
          proxItems.add(drop);
          proxOres.add(other);
        }
      }
    });
  }

  /**
   * 重新找某类矿石的位置（仿照DrillTurret.reFind）
   */
  public void reFind(Corec core, float auxRange, int i) {
    Item item = proxItems.get(i);
    int tileRadius = (int) (auxRange / tilesize + 0.5f);
    Geometry.circle(unit.tileX(), unit.tileY(), tileRadius, (x, y) -> {
      Tile other = world.tile(x, y);
      if (other != null) {
        Item drop = getOreDrop(other);
        if (drop == item && isValidMineTile(other)) {
          proxOres.set(i, other);
        }
      }
    });
  }

  /**
   * 获取瓦片的矿石掉落物
   */
  protected @Nullable Item getOreDrop(Tile tile) {
    // 地板矿石
    if (unit.type.mineFloor && tile.floor() != null && tile.floor().itemDrop != null) {
      return tile.floor().itemDrop;
    }
    // 墙壁矿石
    if (unit.type.mineWalls && tile.block() != null && tile.block().itemDrop != null) {
      return tile.block().itemDrop;
    }
    // overlay矿石
    if (unit.type.mineFloor && tile.overlay() != null && tile.overlay().itemDrop != null) {
      return tile.overlay().itemDrop;
    }
    return null;
  }

  /**
   * 检查瓦片是否是有效的可挖矿瓦片
   */
  protected boolean isValidMineTile(Tile tile) {
    return unit.validMine(tile);
  }

  /**
   * 遍历所有矿石类型，选库存最少的（仿照DrillTurret.iterateMap）
   */
  public @Nullable Item iterateMap(Corec core) {
    if (proxOres == null || !proxOres.any())
      return null;
    Item target = null;
    int minStock = Integer.MAX_VALUE;
    ItemModule items = core.items();
    for (int i = 0; i < proxItems.size; i++) {
      Item item = proxItems.get(i);
      if (!unit.canMine(item))
        continue;
      if (items.get(item) >= core.storageCapacity())
        continue;
      int stock = items.get(item);
      if (stock < minStock) {
        Tile oreTile = proxOres.get(i);
        if (isValidMineTile(oreTile)) {
          minStock = stock;
          target = item;
          targetID = i;
        } else {
          // 矿石被挖没了，重新找
          reFind(core, core.auxiliaryRange(), i);
          oreTile = proxOres.get(i);
          if (isValidMineTile(oreTile)) {
            minStock = stock;
            target = item;
            targetID = i;
          }
        }
      }
    }
    return target;
  }

  /**
   * 更新挖矿目标（仿照DrillTurret.targetMine）
   */
  public void targetMine(Corec core, float auxRange) {
    targetItem = iterateMap(core);
    if (targetItem == null || core.items().get(targetItem) >= core.storageCapacity()) {
      mineTile = null;
      ore = null;
    } else {
      if (timer.get(timerTarget3, 600) && targetID > -1) {
        ore = proxOres.get(targetID);
      }
      if (ore != null) {
        float oreX = ore.worldx();
        float oreY = ore.worldy();
        // 转向矿石
        unit.lookAt(oreX, oreY);
        // 在挖矿范围内就开始挖
        if (unit.within(oreX, oreY, unit.type.mineRange) && isValidMineTile(ore)) {
          mineTile = ore;
          unit.mineTile = ore;
        } else {
          mineTile = null;
          unit.mineTile = null;
        }
        // 矿石被挖没了，重置
        if (!isValidMineTile(ore)) {
          if (targetID > -1) {
            reFind(core, auxRange, targetID);
          }
          targetItem = null;
          targetID = -1;
          mineTile = null;
          ore = null;
        }
      }
    }
  }

  @Override
  public boolean shouldShoot() {
    return !unit.isBuilding() && mineTile == null && unit.type.canAttack;
  }

  @Override
  public boolean shouldFire() {
    return !(unit.controller() instanceof CommandAI ai) || ai.shouldFire();
  }
}
