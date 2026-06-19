package crystal.ai.type;

import arc.math.Mathf;
import arc.math.geom.Vec2;
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
  /** 目标矿石 */
  public @Nullable Tile ore;
  /** 目标矿石对应的物品 */
  public @Nullable Item targetItem;
  /** 正在协助建造的单位 */
  public @Nullable Unit assistUnit;
  /** 协助建造的目标位置 */
  public @Nullable BuildPlan assistPlan;

  public static boolean canUse(Unit unit) {
    if (!(unit instanceof Corec))
      return false;
    return unit.canMine() || unit.canBuild();
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

  /**
   * 更新建造协助逻辑
   * 
   * @return 是否正在协助建造
   */
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
            // 检查建造位置是否在辅助范围内
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
    // 如果有协助目标
    if (assistUnit != null && assistPlan != null) {
      float planX = assistPlan.x * tilesize + tilesize / 2f;
      float planY = assistPlan.y * tilesize + tilesize / 2f;
      // 转向建造目标
      unit.lookAt(planX, planY);
      // 检查是否在建造范围内
      if (unit.within(planX, planY, unit.type.buildRange)) {
        // 开始协助建造 - 添加相同的建造计划
        if (unit.buildPlan() == null || !unit.buildPlan().samePos(assistPlan)) {
          unit.plans.clear();
          unit.addBuild(
              new BuildPlan(assistPlan.x, assistPlan.y, assistPlan.rotation, assistPlan.block, assistPlan.config));
        }
        unit.updateBuilding = true;
        return true;
      } else {
        // 不在建造范围内，只转向，不建造
        unit.clearBuilding();
        return false;
      }
    }
    unit.clearBuilding();
    return false;
  }

  /**
   * 更新挖矿逻辑
   */
  protected void updateMining(Corec core, float auxRange) {
    if (!unit.canMine()) {
      unit.mineTile(null);
      return;
    }
    // 验证当前挖矿目标
    if (ore != null && !unit.validMine(ore)) {
      unit.mineTile(null);
      ore = null;
    }
    // 定时重新选择目标物品和矿石
    if (timer.get(timerTarget3, 60f) || targetItem == null || ore == null) {
      selectTargetItem(core, auxRange);
      findOre(core, auxRange);
    }
    // 如果有目标矿石
    if (ore != null && targetItem != null) {
      float oreX = ore.worldx();
      float oreY = ore.worldy();
      // 转向矿石
      unit.lookAt(oreX, oreY);
      // 检查是否在挖矿范围内
      if (unit.within(oreX, oreY, unit.type.mineRange) && unit.validMine(ore)) {
        unit.mineTile = ore;
      } else {
        // 不在挖矿范围内，只转向不挖矿
        unit.mineTile = null;
      }
    } else {
      unit.mineTile = null;
    }
  }

  /**
   * 选择目标物品 - 找自身库存中最少的可挖矿物品
   */
  protected void selectTargetItem(Corec core, float auxRange) {
    ItemModule items = core.items();
    if (items == null)
      return;
    // 遍历所有物品，找可挖且库存最少的
    targetItem = null;
    int minCount = Integer.MAX_VALUE;
    for (int i = 0; i < content.items().size; i++) {
      Item item = content.item(i);
      // 检查单位是否能挖这个物品
      if (!unit.canMine(item))
        continue;
      // 检查是否有这种矿石
      boolean hasOreType = false;
      if (unit.type.mineFloor && indexer.hasOre(item))
        hasOreType = true;
      if (unit.type.mineWalls && indexer.hasWallOre(item))
        hasOreType = true;
      if (!hasOreType)
        continue;
      // 检查辅助范围内是否真的有这种矿石（快速检查）
      if (!hasOreInRange(item, auxRange))
        continue;
      int count = items.get(item);
      if (count < minCount) {
        minCount = count;
        targetItem = item;
      }
    }
  }

  /**
   * 快速检查辅助范围内是否有指定物品的矿石
   * 用于过滤掉范围内完全没有的矿石类型
   */
  protected boolean hasOreInRange(Item item, float auxRange) {
    int tileRadius = (int) (auxRange / tilesize) + 1;
    int centerX = unit.tileX();
    int centerY = unit.tileY();
    // 只检查几个采样点，快速判断
    int step = Math.max(1, tileRadius / 4);
    for (int dx = -tileRadius; dx <= tileRadius; dx += step) {
      for (int dy = -tileRadius; dy <= tileRadius; dy += step) {
        Tile tile = world.tile(centerX + dx, centerY + dy);
        if (tile == null)
          continue;
        float dst = Mathf.dst(dx * tilesize, dy * tilesize);
        if (dst > auxRange)
          continue;
        if (isOreTile(tile, item)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 在辅助范围内找最近的目标矿石
   */
  protected void findOre(Corec core, float auxRange) {
    if (targetItem == null) {
      ore = null;
      return;
    }
    Tile best = null;
    float bestDst = Float.MAX_VALUE;
    // 计算辅助范围对应的瓦片半径
    int tileRadius = (int) (auxRange / tilesize) + 1;
    int centerX = unit.tileX();
    int centerY = unit.tileY();
    // 遍历范围内的瓦片
    for (int dx = -tileRadius; dx <= tileRadius; dx++) {
      for (int dy = -tileRadius; dy <= tileRadius; dy++) {
        Tile tile = world.tile(centerX + dx, centerY + dy);
        if (tile == null)
          continue;
        // 检查距离
        float dst = Mathf.dst(dx * tilesize, dy * tilesize);
        if (dst > auxRange)
          continue;
        // 检查是否是目标矿石
        if (isOreTile(tile, targetItem)) {
          if (dst < bestDst) {
            bestDst = dst;
            best = tile;
          }
        }
      }
    }
    ore = best;
  }

  /**
   * 检查瓦片是否是指定物品的矿石
   */
  protected boolean isOreTile(Tile tile, Item item) {
    // 检查地板矿石
    if (unit.type.mineFloor && tile.floor() != null && tile.floor().itemDrop == item) {
      return true;
    }
    // 检查墙壁矿石
    if (unit.type.mineWalls && tile.block() != null && tile.block().itemDrop == item) {
      return true;
    }
    // 检查overlay矿石
    if (unit.type.mineFloor && tile.overlay() != null && tile.overlay().itemDrop == item) {
      return true;
    }
    return false;
  }

  @Override
  public boolean shouldShoot() {
    return !unit.isBuilding() && !unit.mining() && unit.type.canAttack;
  }

  @Override
  public boolean shouldFire() {
    return !(unit.controller() instanceof CommandAI ai) || ai.shouldFire();
  }
}
