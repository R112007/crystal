package crystal.entities.comp;

import arc.Core;
import arc.Events;
import arc.Graphics.Cursor;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Bits;
import arc.util.Nullable;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import crystal.Crystal;
import crystal.gen.Corec;
import crystal.type.CoreUnit;
import crystal.util.DLog;
import crystal.world.blocks.stroage.CoreInjector;
import crystal.world.blocks.stroage.MoveCoreSystem;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.Replace;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.core.UI;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.game.EventType.CoreChangeEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.StatUnit;
import mindustry.world.modules.ItemModule;
import static arc.Core.*;
import static mindustry.Vars.*;

@EntityComponent
public abstract class CoreComp implements Unitc, Corec {
  public ItemModule items = new ItemModule();
  public int storageCapacity;
  public boolean deployed = true;
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

  @Override
  public void setType(UnitType type) {
    if (type instanceof CoreUnit c) {
      this.storageCapacity = c.storageCapacity();
      this.suckRange = c.suckRange();
      this.unitCapBonus = c.unitCapBonus();
    } else
      throw new IllegalArgumentException("CoreUnit must use CoreUnitType");
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

  @Override
  public void update() {
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
    if (mineTile != null) {
      Item mineItem = getMineResult(mineTile);
      if (mineItem != null && validMine(mineTile)) {
        // 挖矿计时器
        mineTimer += Time.delta * type.mineSpeed * state.rules.unitMineSpeed(team());
        int tickThreshold = 50 + (type.mineHardnessScaling ? mineItem.hardness * 15 : 15);
        if (mineTimer >= tickThreshold) {
          mineTimer = 0;
          // 直接存入核心items，不进stack
          if (items.total() < proxy.storageCapacity) {
            items.add(mineItem, 1);
            Fx.pulverizeSmall.at(mineTile.worldx(), mineTile.worldy(), 0, mineItem.color);
            // 战役产出统计
            if (state.rules.sector != null && team() == state.rules.defaultTeam) {
              state.rules.sector.info.handleProduction(mineItem, 1);
            }
          }
        }
      }
    }
    if (Crystal.timer % 200 == 0) {
      for (var c : player.team().data().cores) {
        DLog.info("核心" + c.id + "单位容量" + ((CoreBlock) c.block).unitCapModifier);
      }
      DLog.info("当前团队单位上限：" + Units.getCap(team()));
    }
    // 每2秒自动吸取范围内物品
    /*
     * if (Crystal.timer % 120 == 0) {
     * indexer.eachBlock(team(), x(), y(), suckRange,
     * b -> b.block.hasItems && b.items.total() > 0,
     * build -> {
     * build.items.each((item, amount) -> {
     * if (acceptItem(build, item)) {
     * int transfer = Math.min(amount, 10);
     * build.items.remove(item, transfer);
     * items.add(item, transfer);
     * Fx.itemTransfer.at(build.x(), build.y(), 0, item.color, this);
     * }
     * });
     * });
     * }
     */

    // 部署状态：禁止移动
    speedMultiplier(deployed ? 0f : 1f);
  }

  @Override
  public void killed() {
    // 从团队核心列表注销
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

  @Override
  public void draw() {
    Draw.color(team().color);
    Draw.alpha(0.3f + Mathf.absin(30f, 0.1f));
    Fill.circle(x(), y(), hitSize() * 1.2f);
    Draw.reset();
  }
}
