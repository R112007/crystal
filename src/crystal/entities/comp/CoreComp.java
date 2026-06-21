package crystal.entities.comp;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.Time;
import crystal.Crystal;
import crystal.content.CUnitCommands;
import crystal.gen.Corec;
import crystal.type.CoreUnit;
import crystal.util.DLog;
import crystal.world.blocks.stroage.CoreInjector;
import crystal.world.blocks.stroage.MoveCoreSystem;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.MethodPriority;
import ent.anno.Annotations.Replace;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.core.UI;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.game.EventType.CoreChangeEvent;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.StatUnit;
import mindustry.world.modules.ItemModule;
import static mindustry.Vars.*;

@EntityComponent
public abstract class CoreComp implements Unitc, Corec {
  public ItemModule items = new ItemModule();
  public int storageCapacity;
  public boolean deployed = false;
  public transient CoreBuild proxy;
  public float suckRange, auxiliaryRange;
  public int unitCapBonus;
  public boolean accept = false; // 存取模式开关：false取货 / true存货
  public transient Unit currentInteractor; // 记录当前点击交互的核心机，给面板取货用
  @Import
  ItemStack stack;
  @Import
  Team team;
  @Import
  @Nullable
  Tile mineTile;
  @Import
  UnitType type;
  @Import
  float mineTimer;
  public float idleTimer = 0f;
  public boolean autoSwitched = false;

  @Override
  public void setType(UnitType type) {
    if (type instanceof CoreUnit c) {
      this.storageCapacity = c.storageCapacity();
      this.suckRange = c.suckRange();
      this.unitCapBonus = c.unitCapBonus();
      this.auxiliaryRange = c.auxiliaryRange();
    } else
      throw new IllegalArgumentException("CoreUnit must use CoreUnitType");
  }

  public @Nullable Item getOreItem(Tile tile) {
    if (type.mineFloor) {
      if (tile.floor() != null && tile.floor().itemDrop != null) {
        return tile.floor().itemDrop;
      }
      if (tile.overlay() != null && tile.overlay().itemDrop != null) {
        return tile.overlay().itemDrop;
      }
    }
    if (type.mineWalls && tile.block() != null && tile.block().itemDrop != null) {
      return tile.block().itemDrop;
    }
    return null;
  }

  public ItemModule flowItems() {
    return items;
  }

  @Override
  public void add() {
    if (!MoveCoreSystem.getCores(team()).contains(this)) {
      CoreInjector.injectCore(team().data(), this);
      Events.fire(new CoreChangeEvent(this.proxy()));
      Fx.upgradeCore.at(this);
      DLog.info("[创建]" + "单位编号" + id() + " " + "核心数量" + Vars.player.team().data().cores.size);
    }
  }

  @Override
  public void afterRead() {
    if (!dead() && !MoveCoreSystem.getCores(team()).contains(this)) {
      CoreInjector.injectCore(team().data(), this);
      Events.fire(new CoreChangeEvent(this.proxy()));
    }
    DLog.info("[读取]" + "单位编号" + id() + " " + "核心数量" + Vars.player.team().data().cores.size);
  }

  @MethodPriority(-999)
  @Override
  public void update() {
    if (self() instanceof Corec core && core.deployed()) {
      speedMultiplier(0f);
      vel().setZero();
      deltaX(0);
      deltaY(0);
    }
    if (proxy != null) {
      proxy.team = team();
      if (proxy.items != null) {
        proxy.items.updateFlow();
      }
    }
    // 同步虚拟核心状态
    if (proxy != null) {
      proxy.team = team();
    }
    if (stack.amount > 0 && items().total() < proxy.storageCapacity) {
      int canStore = Math.min(stack.amount, proxy.storageCapacity - items().total());
      items().add(stack.item, canStore);
      stack.amount -= canStore;
      if (stack.amount <= 0)
        stack.amount = 0;
    }
    if (Crystal.timer % 200 == 0) {
      for (var c : player.team().data().cores) {
        DLog.info("核心" + c.id + "单位容量" + ((CoreBlock) c.block).unitCapModifier);
      }
      DLog.info("当前团队单位上限：" + Units.getCap(team()));
    }
    updateAutoCommand();
  }

  @Override
  public void killed() {
    CoreInjector.removeCore(team().data(), this);
    Fx.explosion.at(self());
  }

  public void spawnPlayer(Player player) {
    UnitType spawnType = mindustry.content.UnitTypes.alpha;
    Fx.spawn.at(this);
    player.set(this);

    if (!net.client()) {
      Unit unit = spawnType.create(team());
      unit.set(this);
      unit.rotation(90f);
      unit.impulse(0f, 3f);
      unit.spawnedByCore(true);
      unit.controller(player);
      unit.add();
    }
  }

  public boolean acceptItem(Building source, Item item) {
    int totalCap = team().data().cores.sum(c -> c.storageCapacity);
    return items.total() < totalCap;
  }

  public void handleItem(Building source, Item item) {
    items.add(item, 1);
  }

  @Replace
  public boolean interact(Player player) {
    Unit interactor = player.unit();
    currentInteractor = interactor;
    if (interactor.stack().amount > 0) {
      if (acceptItem(null, interactor.stack().item)) {
        handleItem(null, interactor.stack().item);
        interactor.stack().amount--;
        return true;
      }
      return false;
    }
    return true;
  }

  @Override
  public void display(Table table) {
    if (team() != Vars.player.team() || proxy() == null)
      return;

    ItemModule items = items();
    String perSecond = " " + StatUnit.perSecond.localized();

    table.row();
    table.table(Styles.grayPanel, panel -> {
      panel.left().top();
      panel.add(Core.bundle.get("stat.items") + (accept ? "（存货模式）" : "（取货模式）"))
          .left().padBottom(4f).row();
      panel.table(grid -> {
        grid.left();
        int col = 0;
        for (Item item : Vars.content.items()) {
          int amount = items.get(item);
          boolean hasFlow = items.hasFlowItem(item);
          if (amount <= 0 && !hasFlow)
            continue;

          grid.table(cell -> {
            cell.left();
            cell.image(item.uiIcon).size(24f).padRight(4f);
            cell.label(() -> UI.formatAmount(items.get(item))).left();
            if (hasFlow) {
              cell.row();
              float rate = items.getFlowRate(item);
              cell.label(() -> (rate >= 0 ? "+" : "") + Strings.fixed(rate, 1) + perSecond)
                  .color(rate >= 0 ? Color.forest : Color.scarlet)
                  .fontScale(0.75f).left();
            }
          }).pad(4f).fillY().left();

          col++;
          if (col >= 4) {
            col = 0;
            grid.row();
          }
        }
      }).left().growX().row();

      // 容量进度条
      panel.add(new Bar(
          () -> Core.bundle.format("bar.capacity", UI.formatAmount(proxy().storageCapacity)),
          () -> Pal.items,
          () -> items.total() / (float) proxy().storageCapacity)).growX().height(18f).padTop(6f);
    }).growX().pad(5f).row();
  }

  public void takeItem(Unitc machine, Item item, int amount) {
    int have = items().get(item);
    if (have < amount)
      amount = have;
    if (amount <= 0)
      return;

    items().remove(item, amount);
    ItemStack stack = machine.stack();

    if (stack.amount <= 0) {
      stack.set(item, amount);
    } else if (stack.item == item) {
      stack.amount += amount;
    }
  }

  public void updateAutoCommand() {
    // 检查是否在移动（速度小于阈值算静止）
    if (vel().len() < 0.1f) {
      idleTimer += Math.min(Time.delta, 10f); // 最多一帧加 10，防止跳帧

      // 5秒 = 300 tick
      if (idleTimer >= 300f && !autoSwitched) {
        // 切换到核心辅助命令
        if (controller() instanceof CommandAI ai) {
          // 只有当前不是核心辅助命令时才切换
          if (ai.command != CUnitCommands.coreAuxiliaryCommand) {
            ai.command = CUnitCommands.coreAuxiliaryCommand;
            autoSwitched = true;
          }
        }
      }
    } else {
      idleTimer = 0f;
      if (autoSwitched && controller() instanceof CommandAI ai) {
        ai.command = UnitCommand.moveCommand;
        autoSwitched = false;
      }
    }
  }

  @Override
  public void draw() {
  }
}
