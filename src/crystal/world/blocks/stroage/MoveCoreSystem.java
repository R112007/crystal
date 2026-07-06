package crystal.world.blocks.stroage;

import arc.Core;
import arc.Events;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import crystal.gen.Corec;
import crystal.net.CCall;
import crystal.ui.fragments.CoreInventoryFragment;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.game.EventType.*;
import static mindustry.Vars.*;

public class MoveCoreSystem {
  public static final ObjectMap<Team, Seq<Corec>> mobileCores = new ObjectMap<>();
  public static final CoreInventoryFragment coreInventory = new CoreInventoryFragment();
  private static final float tapRange = 2.5f;

  // ===== 长按判定参数 =====
  private static final long longPressMs = 300; // 长按触发时长（毫秒）
  private static final float moveCancelPx = 8f; // 移动超过该像素取消长按
  private static long pressStartTime = 0;
  private static final Vec2 pressPos = new Vec2();
  private static boolean longPressTriggered = false;
  private static Corec pressTarget = null; // 记录按下时选中的核心

  public static void init() {
    Events.on(ClientLoadEvent.class, e -> {
      coreInventory.build(Vars.ui.hudGroup);

      // 每帧检测输入状态
      Events.run(Trigger.update, () -> {
        // 刚按下的瞬间：记录时间、位置和选中的目标
        if (Core.input.justTouched()) {
          pressStartTime = Time.millis();
          pressPos.set(Core.input.mouseX(), Core.input.mouseY());
          longPressTriggered = false;
          pressTarget = null;

          // 按下时就判定是否点在核心上
          Tile tile = Vars.world.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
          if (tile != null) {
            Unit unit = Units.closest(player.team(), tile.worldx(), tile.worldy(),
                tapRange * tilesize, u -> !u.dead && u instanceof Corec && u instanceof Legsc);
            if (unit instanceof Corec core) {
              pressTarget = core;
            }
          }
        }

        // 按住过程中
        if (Core.input.isTouched() && !longPressTriggered && pressTarget != null) {
          // 移动超距则取消
          float dx = Core.input.mouseX() - pressPos.x;
          float dy = Core.input.mouseY() - pressPos.y;
          if (dx * dx + dy * dy > moveCancelPx * moveCancelPx) {
            longPressTriggered = true;
            pressTarget = null;
            return;
          }

          // 达到时长立刻触发，无需抬手
          long duration = Time.millis() - pressStartTime;
          if (duration >= longPressMs) {
            CCall.setUnitDeployed(pressTarget, !pressTarget.deployed());
            Fx.upgradeCore.at(pressTarget);
            longPressTriggered = true;
          }
        }
      });
    });

    Events.on(TapEvent.class, e -> {
      if (longPressTriggered)
        return;

      Unit unit = Units.closest(e.player.team(), e.tile.worldx(), e.tile.worldy(),
          tapRange * tilesize, u -> !u.dead && u instanceof Corec);

      if (unit instanceof Corec core) {
        DLog.info("点击核心单位: " + core.type().name);
        coreInventory.showFor(core);
      } else {
        if (coreInventory.isShown()) {
          coreInventory.hide();
        }
      }
    });

    Events.on(UnitDestroyEvent.class, e -> {
      if (e.unit instanceof Corec core) {
        CoreInjector.removeCore(core.team().data(), core);
        if (Vars.state.isCampaign() && core.team().data().cores.isEmpty()) {
          mindustry.type.Sector sector = Vars.state.getSector();
          Events.fire(new SectorLoseEvent(sector));
          sector.info.hasCore = false;
          sector.save = null;
          sector.saveInfo();
        }
      }
    });

    Events.on(WorldLoadEvent.class, e -> {
      mobileCores.clear();
    });
  }

  public static void showCoreInventory(Corec core) {
    coreInventory.showFor(core);
  }

  public static void hideCoreInventory() {
    coreInventory.hide();
  }

  // MoveCoreSystem.java - 修改 removeAllCorecs 方法
  public static void removeAllCorecs(Team team) {
    // 收集所有移动核心单位
    Seq<Unit> toRemove = new Seq<>();
    for (Unit unit : Groups.unit) {
      if (unit instanceof Corec && unit.team == team) {
        toRemove.add(unit);
      }
    }

    // 第一阶段：从 CoreInjector 中移除（幂等操作）
    for (Unit unit : toRemove) {
      Corec core = (Corec) unit;
      try {
        if (core.proxy() != null) {
          CoreInjector.removeCore(team.data(), core);
        }
      } catch (Exception e) {
        DLog.err("移除核心注入失败: " + e.getMessage());
      }
      // 从 mobileCores map 中移除
      getCores(team).remove(core);
    }

    // 第二阶段：从 Groups 中移除（直接 remove，不走 kill 的异步流程）
    for (Unit unit : toRemove) {
      // 标记为死亡，防止后续逻辑问题
      unit.dead = true;
      // 直接移除，触发 remove() 中的清理
      unit.remove();
    }

    // 第三阶段：清理 team.cores 中残留的虚拟核心
    // 使用反射访问 TeamData 的 cores 字段
    try {
      java.lang.reflect.Field coresField = team.data().getClass().getDeclaredField("cores");
      coresField.setAccessible(true);
      Seq<CoreBlock.CoreBuild> cores = (Seq<CoreBlock.CoreBuild>) coresField.get(team.data());

      Seq<CoreBlock.CoreBuild> orphaned = new Seq<>();
      for (CoreBlock.CoreBuild core : cores) {
        // 检查是否还有对应的存活单位
        boolean hasLivingUnit = false;
        for (Unit unit : Groups.unit) {
          if (unit instanceof Corec c && c.proxy() == core && !unit.dead) {
            hasLivingUnit = true;
            break;
          }
        }
        // 如果是 CoreUnitType 创建的虚拟核心，且没有对应单位，标记为孤儿
        if (!hasLivingUnit && core.block instanceof CoreBlock) {
          orphaned.add(core);
        }
      }

      // 移除孤儿核心
      for (CoreBlock.CoreBuild core : orphaned) {
        cores.remove(core);
        // 清理 tile 引用
        if (core.tile != null) {
          // 安全清理 tile.build
          if (core.tile.build == core) {
            try {
              java.lang.reflect.Field buildField = Tile.class.getDeclaredField("build");
              buildField.setAccessible(true);
              buildField.set(core.tile, null);
            } catch (Exception ex) {
              DLog.err("清理 tile.build 失败: " + ex.getMessage());
            }
          }
          core.tile = null;
        }
      }
    } catch (Exception e) {
      DLog.err("清理孤儿核心失败: " + e.getMessage());
      e.printStackTrace();
    }

    // 清空 mobileCores map
    mobileCores.remove(team);
  }

  public static Seq<Corec> getCores(Team team) {
    return mobileCores.get(team, Seq::new);
  }
}
