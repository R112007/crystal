package crystal.ui.fragments;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Prov;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.ClickListener;
import arc.scene.event.HandCursorListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.util.Align;
import arc.util.Time;
import crystal.gen.Corec;
import mindustry.core.UI;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Tex;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.modules.ItemModule;
import static mindustry.Vars.*;
import java.util.Arrays;

public class CoreInventoryFragment {
  private static final float holdWithdraw = 20f;
  private static final float holdShrink = 120f;
  private static final float followSpeed = 0.15f;
  private final Table table = new Table();
  private Corec core;
  private float holdTime = 0f, emptyTime;
  private boolean holding, held;
  private float[] shrinkHoldTimes = new float[content.items().size];
  private Item lastItem;
  private final Vec2 targetPos = new Vec2();
  {
    Events.on(WorldLoadEvent.class, e -> hide());
  }

  public void build(Group parent) {
    table.name = "core-inventory";
    table.setTransform(true);
    parent.setTransform(true);
    parent.addChild(table);
  }

  public void showFor(Corec coreUnit) {
    if (this.core == coreUnit) {
      hide();
      return;
    }
    this.core = coreUnit;
    if (core == null || !core.isValid() || core.items() == null || core.items().total() == 0) {
      return;
    }
    rebuild(true);
  }

  public void hide() {
    if (table == null)
      return;
    table.actions(
        Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out),
        Actions.run(() -> {
          table.clearChildren();
          table.clearListeners();
          table.update(null);
        }),
        Actions.visible(false));
    table.touchable = Touchable.disabled;
    core = null;
  }

  private void takeItem(int requested) {
    if (core == null || !core.isValid() || lastItem == null)
      return;
    ItemModule items = core.items();
    if (items == null || !items.has(lastItem))
      return;
    int amount = Math.min(requested, player.unit().maxAccepted(lastItem));
    amount = Math.min(amount, items.get(lastItem));
    if (amount > 0) {
      items.remove(lastItem, amount);
      for (int i = 0; i < amount; i++) {
        player.unit().addItem(lastItem);
      }
      holding = false;
      holdTime = 0f;
      held = true;
    }
  }

  private void rebuild(boolean actions) {
    IntSet container = new IntSet();
    Arrays.fill(shrinkHoldTimes, 0);
    holdTime = emptyTime = 0f;
    table.clearChildren();
    table.clearActions();
    table.background(null);
    table.touchable = Touchable.enabled;
    updateTargetPos();
    table.update(() -> {
      if (state.isMenu() || core == null || !core.isValid() || emptyTime >= holdShrink) {
        hide();
      } else {
        ItemModule items = core.items();
        if (items == null || items.total() == 0) {
          emptyTime += Time.delta;
        } else {
          emptyTime = 0f;
        }
        // 长按连续取物
        if (holding && lastItem != null && (holdTime += Time.delta) >= holdWithdraw) {
          holdTime = 0f;
          takeItem(1);
        }
        updateTablePosition();
        if (items != null) {
          boolean dirty = false;
          if (shrinkHoldTimes.length != content.items().size) {
            shrinkHoldTimes = new float[content.items().size];
          }
          for (int i = 0; i < content.items().size; i++) {
            boolean has = items.has(content.item(i));
            boolean had = container.contains(i);
            if (has) {
              shrinkHoldTimes[i] = 0f;
              dirty |= !had;
            } else if (had) {
              shrinkHoldTimes[i] += Time.delta;
              dirty |= shrinkHoldTimes[i] >= holdShrink;
            }
          }
          if (dirty)
            rebuild(false);
        }
        if (table.getChildren().isEmpty()) {
          hide();
        }
      }
    });

    // ===== 顶部控制栏 =====
    Table topBar = new Table();
    topBar.marginBottom(4f);

    // 接收模式切换按钮
    Button acceptBtn = new Button(Styles.flatToggleMenut);
    acceptBtn.margin(6f, 10f, 6f, 10f);
    acceptBtn.label(() -> core != null && core.accept() ? "[green]接收中[]" : "[scarlet]已关闭[]");
    acceptBtn.clicked(() -> {
      if (core != null) {
        core.accept(!core.accept());
      }
    });
    acceptBtn.update(() -> acceptBtn.setChecked(core != null && core.accept()));
    topBar.add(acceptBtn).left().growX();

    // 新增：重生按钮
    Button spawnBtn = new Button(Styles.none);
    spawnBtn.margin(6f, 10f, 6f, 10f);
    spawnBtn.label(() -> "重生");
    spawnBtn.clicked(() -> {
      if (core != null && core.isValid() && !player.dead()) {
        core.spawnPlayer(player);
        hide(); // 重生后自动关闭面板
      }
    });
    spawnBtn.update(() -> spawnBtn.setDisabled(core == null || !core.isValid() || player.dead()));
    topBar.add(spawnBtn).right().padLeft(8f);

    table.add(topBar).growX().padBottom(4f).row();

    int cols = 4;
    int row = 0;
    table.margin(6f);
    Table itemsTable = new Table();
    itemsTable.defaults().size(8 * 5).pad(4f);
    ItemModule items = core.items();
    if (items != null) {
      for (int i = 0; i < content.items().size; i++) {
        Item item = content.item(i);
        if (!items.has(item))
          continue;
        container.add(i);
        Boolp canPick = () -> !player.dead() && player.unit().acceptsItem(item) &&
            !state.isPaused() && player.within(core, itemTransferRange);
        HandCursorListener l = new HandCursorListener();
        l.enabled = canPick;
        Element image = itemImage(item.uiIcon, () -> {
          if (core == null || !core.isValid() || core.items() == null) {
            return "";
          }
          return round(core.items().get(item));
        });
        image.addListener(l);
        Boolp validClick = () -> canPick.get() && core != null && core.isValid() &&
            core.items() != null && core.items().has(item);
        image.addListener(new ClickListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            held = false;
            if (validClick.get()) {
              lastItem = item;
              holding = true;
            }
            return super.touchDown(event, x, y, pointer, button);
          }

          @Override
          public void clicked(InputEvent event, float x, float y) {
            if (!validClick.get() || held)
              return;
            lastItem = item;
            takeItem(core.items().get(item));
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
            super.touchUp(event, x, y, pointer, button);
            holding = false;
            lastItem = null;
          }
        });
        itemsTable.add(image);
        if (row++ % cols == cols - 1)
          itemsTable.row();
      }
    }

    // 新增：滚动面板包裹物品列表，限制最大高度为屏幕高度的60%
    ScrollPane scroll = new ScrollPane(itemsTable, Styles.smallPane);
    scroll.setScrollingDisabled(true, false);
    scroll.setOverscroll(false, false);
    table.add(scroll).growX().maxHeight(Core.graphics.getHeight() * 0.6f);

    if (row == 0) {
      table.setSize(0f, 0f);
    }
    table.pack();
    table.setPosition(targetPos.x, targetPos.y, Align.topLeft);
    table.visible = true;
    if (actions) {
      table.setScale(0f, 1f);
      table.actions(Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));
    } else {
      table.setScale(1f, 1f);
    }
  }

  private String round(float f) {
    f = (int) f;
    if (f >= 1000000) {
      return (int) (f / 1000000f) + "[gray]" + UI.millions;
    } else if (f >= 1000) {
      return (int) (f / 1000) + UI.thousands;
    } else {
      return (int) f + "";
    }
  }

  private void updateTargetPos() {
    if (core == null)
      return;
    float offset = core.hitSize() / 2f;
    Vec2 v = Core.input.mouseScreen(core.x() + offset, core.y() + offset);
    targetPos.set(v.x, v.y);
  }

  private void updateTablePosition() {
    updateTargetPos();
    float currentX = table.getX(Align.topLeft);
    float currentY = table.getY(Align.topLeft);
    float newX = Mathf.lerp(currentX, targetPos.x, followSpeed);
    float newY = Mathf.lerp(currentY, targetPos.y, followSpeed);
    table.setPosition(newX, newY, Align.topLeft);
  }

  private Element itemImage(TextureRegion region, Prov<CharSequence> text) {
    Stack stack = new Stack();
    Table t = new Table().left().bottom();
    t.label(text);
    stack.add(new Image(region));
    stack.add(t);
    return stack;
  }

  public boolean isShown() {
    return core != null && table.visible == true;
  }

  public Corec currentCore() {
    return core;
  }
}
