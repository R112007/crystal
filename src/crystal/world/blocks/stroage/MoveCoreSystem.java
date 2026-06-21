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
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.world.Tile;
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

    // 短按仅处理物品栏，触发过长按则不打开
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

  public static Seq<Corec> getCores(Team team) {
    return mobileCores.get(team, Seq::new);
  }
}
