package crystal.ui;

import arc.Core;
import arc.Events;
import arc.func.Floatp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Time;
import arc.util.Tmp;
import crystal.CVars;
import crystal.core.FaBaoSystem;
import crystal.magic.FaBao;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.meta.StatValue;

import java.lang.reflect.Field;

public class FaBaoHUD {
  private static final float SLOWMO_SCALE = 0.2f;
  private static final long TAP_MAX_MS = 400L;
  private static final float PICKUP_MOVE_TOLERANCE = 24f;
  private static final Floatp VANILLA_DELTA = () -> Math.min(Core.graphics.getDeltaTime() * 60f, 3f);

  /* ========== 尺寸参数 ========== */
  private static final float PANEL_WIDTH = 110f;
  private static final float PANEL_HEIGHT = 360f;
  private static final float TRIGGER_WIDTH = 24f;
  private static final float TRIGGER_HEIGHT = 72f;

  /* ========== 滑出参数（独立控制） ========== */
  /** 收起时：面板向右滑出多少（正值，越大越彻底离屏） */
  private static final float SLIDE_OUT_RIGHT = 300f;
  /** 展开时：面板从右边缘向左缩进多少（正值，贴 trigger 建议 = TRIGGER_WIDTH） */
  private static final float SLIDE_IN_LEFT = 24f;
  /** 滑入滑出动画速度 */
  private static final float ANIM_SPEED = 0.22f;

  /* ========== 位置偏移（自己调） ========== */
  /** trigger 垂直偏移（0 = 垂直居中，正值 = 向上） */
  private static final float TRIGGER_OFFSET_Y = 500f;
  /** bar 垂直偏移（建议跟 trigger 保持一致） */
  private static final float BAR_OFFSET_Y = 0f;

  private static Table bar;
  private static Table barContent;
  private static Table trigger;
  private static Table aimCatcher;
  private static Image dragGhost;
  /** 屏幕上方法宝信息面板（瞄准时显示） */
  private static Table infoTable;

  private static FaBao aiming;
  private static boolean aimingCancel;
  private static float aimStageX, aimStageY;
  private static boolean aimPlaced;
  private static int aimPointer = -1;
  private static FaBao pendingPickup;
  private static long pressTime;
  private static float pressStageX, pressStageY;
  private static int pressPointer = -1;
  private static Floatp savedDelta;
  private static Field deltaField;
  private static boolean initialized = false;

  private static boolean panelOpen = false;
  private static float panelX = SLIDE_OUT_RIGHT;

  public static void init() {
    if (initialized)
      return;
    initialized = true;

    Events.on(ClientLoadEvent.class, e -> buildUI());
    Events.run(Trigger.draw, FaBaoHUD::drawAimOverlay);
    Events.on(StateChangeEvent.class, e -> stopAiming(false));
    Events.on(WorldLoadEvent.class, e -> stopAiming(false));
  }

  private static void buildUI() {
    // ========== 1. 法宝栏面板（先添加，在底层） ==========
    Vars.ui.hudGroup.fill(null, table -> {
      table.table(null, t -> {
        bar = new Table(Tex.pane);
        rebuildBar();
        t.add(bar).width(PANEL_WIDTH).height(PANEL_HEIGHT);
      }).size(PANEL_WIDTH, PANEL_HEIGHT);
      table.center().right().update(() -> {
        float target = panelOpen ? -SLIDE_IN_LEFT : SLIDE_OUT_RIGHT;
        panelX = Mathf.lerpDelta(panelX, target, ANIM_SPEED);
        table.translation.set(panelX, BAR_OFFSET_Y);
        bar.touchable = (panelX < SLIDE_OUT_RIGHT * 0.5f) ? Touchable.enabled : Touchable.disabled;
      });
    });

    // ========== 2. 触发条（后添加，在最上层，钉死最右边缘） ==========
    Vars.ui.hudGroup.fill(null, table -> {
      table.table(null, t -> {
        trigger = new Table(((TextureRegionDrawable) Tex.whiteui).tint(Color.white));
        trigger.touchable = Touchable.enabled;
        trigger.addListener(new InputListener() {
          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            return true;
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
            togglePanel();
          }
        });
        trigger.add().size(16f).pad(4f);
        t.add(trigger).width(TRIGGER_WIDTH).height(TRIGGER_HEIGHT);
      }).size(TRIGGER_WIDTH, TRIGGER_HEIGHT);
      table.center().right().update(() -> {
        table.translation.set(0, TRIGGER_OFFSET_Y);
      });
    });

    // ========== 3. 屏幕上方法宝信息面板（瞄准时显示） ==========
    Vars.ui.hudGroup.fill(null, t -> {
      infoTable = new Table(Tex.pane);
      infoTable.visible = false;
      t.add().top().padTop(30f);
      t.add(infoTable);
    });

    // ========== 4. 拖动幽灵图标 ==========
    dragGhost = new Image();
    dragGhost.touchable = Touchable.disabled;
    dragGhost.visible = false;
    dragGhost.setSize(64f, 64f);
    Vars.ui.hudGroup.addChild(dragGhost);

    // ========== 5. 全屏触摸层 ==========
    Vars.ui.hudGroup.fill(null, t -> {
      aimCatcher = t;
      aimCatcher.touchable = Touchable.disabled;
      aimCatcher.addListener(new InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (aiming == null || aimPointer != -1)
            return false;
          aimPointer = pointer;
          aimPlaced = true;
          updateAim(event.stageX, event.stageY);
          return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
          if (aiming != null && aimPlaced && pointer == aimPointer)
            updateAim(event.stageX, event.stageY);
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (aiming == null || !aimPlaced || pointer != aimPointer)
            return;
          aimPointer = -1;
          updateAim(event.stageX, event.stageY);
          stopAiming(!overBar(event.stageX, event.stageY));
        }
      });
    });
  }

  private static void togglePanel() {
    panelOpen = !panelOpen;
    if (panelOpen)
      rebuildBar();
  }

  private static void rebuildBar() {
    if (bar == null)
      return;
    bar.clear();
    barContent = new Table();

    var owned = FaBaoSystem.owned();
    if (owned.isEmpty()) {
      barContent.add(Core.bundle.get("fabao.none", "尚无法宝")).color(Color.darkGray).pad(10f);
    } else {
      for (FaBao f : owned) {
        Table entry = new Table();
        Image img = new Image(f.uiIcon());
        img.setScaling(Scaling.fit);
        img.touchable = Touchable.enabled;
        if (f.usingFallbackIcon())
          img.setColor(f.rangeColor);
        attachDrag(img, f);
        entry.add(img).size(48f).padBottom(2f).row();
        entry.add(f.localizedName).get().setFontScale(0.75f);
        entry.row();
        entry.add("×" + FaBaoSystem.count(f)).color(Color.lightGray).get().setFontScale(0.7f);
        barContent.add(entry).pad(5f).padTop(8f).row();
      }
    }

    if (CVars.debug) {
      barContent.button("debug:+1", () -> {
        for (FaBao f : FaBao.list)
          FaBaoSystem.give(f, 1);
        rebuildBar();
      }).pad(5f).row();
    }

    if (!owned.isEmpty()) {
      barContent.add(Core.bundle.get("fabao.hint", "点图标拾取\n再点栏外释放"))
          .color(Color.darkGray).get().setFontScale(0.6f);
      barContent.row();
    }

    ScrollPane sp = new ScrollPane(barContent, Styles.smallPane);
    sp.setScrollingDisabled(true, false);
    sp.setCancelTouchFocus(false);
    sp.setOverscroll(false, false);
    bar.add(sp).grow().pad(6f);
  }

  private static void attachDrag(Image img, FaBao fabao) {
    img.addListener(new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
        if (FaBaoSystem.count(fabao) <= 0 || aiming != null || pendingPickup != null)
          return false;
        pendingPickup = fabao;
        pressTime = Time.millis();
        pressStageX = event.stageX;
        pressStageY = event.stageY;
        pressPointer = pointer;
        return true;
      }

      @Override
      public void touchDragged(InputEvent event, float x, float y, int pointer) {
        if (pendingPickup == fabao && pointer == pressPointer
            && Mathf.len(event.stageX - pressStageX, event.stageY - pressStageY) > PICKUP_MOVE_TOLERANCE) {
          pendingPickup = null;
          pressPointer = -1;
        }
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
        if (pendingPickup != fabao || pointer != pressPointer)
          return;
        pendingPickup = null;
        pressPointer = -1;
        if (Time.millis() - pressTime <= TAP_MAX_MS) {
          dragGhost.setDrawable(new TextureRegionDrawable(fabao.uiIcon()));
          startAiming(fabao);
        }
      }
    });
  }

  private static void updateAim(float stageX, float stageY) {
    aimStageX = stageX;
    aimStageY = stageY;
    aimingCancel = overBar(stageX, stageY);
    dragGhost.visible = true;
    dragGhost.setPosition(stageX - dragGhost.getWidth() / 2f, stageY - dragGhost.getHeight() / 2f);
    dragGhost.setColor(aimingCancel ? Color.scarlet : Color.white);
  }

  /** 构建屏幕上方信息面板 */
  /** 构建屏幕上方信息面板 */
  private static void buildInfo(FaBao f) {
    if (infoTable == null)
      return;
    infoTable.clear();
    infoTable.defaults().pad(4f);

    // 标题行：图标 + 名称
    infoTable.add(new Image(f.uiIcon())).size(32f).padRight(8f);
    infoTable.add(f.localizedName).color(Color.gold).get().setFontScale(1.1f);
    infoTable.row();

    // 简介
    if (f.description != null) {
      infoTable.add(f.description).color(Color.lightGray).colspan(2).wrap().width(400f);
      infoTable.row();
    }

    // stats：平铺，不显示分类标题
    if (f.stats != null) {
      var map = f.stats.toMap();
      for (var catEntry : map.values()) { // 跳过 StatCat，直接取内层 Map
        for (var statEntry : catEntry.entries()) { // Stat → Seq<StatValue>
          for (var val : statEntry.value) { // 遍历 Seq<StatValue>
            infoTable.add(statEntry.key.name + ": ").color(Color.gray).padRight(4f);
            val.display(infoTable);
            infoTable.row();
          }
        }
      }
    }
  }

  private static void startAiming(FaBao f) {
    aiming = f;
    aimingCancel = false;
    aimPlaced = false;
    aimCatcher.touchable = Touchable.enabled;
    dragGhost.visible = false;

    buildInfo(f);
    if (infoTable != null)
      infoTable.visible = true;

    if (!Vars.net.active()) {
      savedDelta = currentDeltaProvider();
      Time.setDeltaProvider(
          () -> Math.min(Core.graphics.getDeltaTime() * 60f * SLOWMO_SCALE, 3f * SLOWMO_SCALE));
    }
  }

  private static void stopAiming(boolean release) {
    pendingPickup = null;
    pressPointer = -1;
    aimPointer = -1;
    if (aiming == null)
      return;
    FaBao f = aiming;
    boolean cancel = aimingCancel;
    aiming = null;
    aimingCancel = false;
    aimPlaced = false;
    if (aimCatcher != null)
      aimCatcher.touchable = Touchable.disabled;
    dragGhost.visible = false;

    if (infoTable != null)
      infoTable.visible = false;

    Time.setDeltaProvider(savedDelta != null ? savedDelta : VANILLA_DELTA);
    savedDelta = null;

    if (release && !cancel && Vars.state.isGame()) {
      Vec2 w = Core.camera.unproject(aimStageX, aimStageY);
      if (FaBaoSystem.consume(f)) {
        f.release(w.x, w.y);
        rebuildBar();
      }
    }
  }

  private static Floatp currentDeltaProvider() {
    try {
      if (deltaField == null) {
        deltaField = Time.class.getDeclaredField("deltaimpl");
        deltaField.setAccessible(true);
      }
      Object v = deltaField.get(null);
      return v instanceof Floatp ? (Floatp) v : null;
    } catch (Throwable t) {
      return null;
    }
  }

  private static void drawAimOverlay() {
    if (aiming == null || !aimPlaced || !Vars.state.isGame())
      return;
    Vec2 w = Core.camera.unproject(aimStageX, aimStageY);
    aiming.drawBeforeRelease(w.x, w.y);
    if (aimingCancel) {
      Draw.z(Layer.flyingUnit + 1.1f);
      Lines.stroke(3f, Color.scarlet);
      Lines.circle(w.x, w.y, aiming.range + 4f);
      Draw.reset();
    }
  }

  private static boolean inRect(Element actor, float stageX, float stageY) {
    if (actor == null || !actor.visible || actor.getWidth() <= 0)
      return false;
    Vec2 v = actor.localToStageCoordinates(Tmp.v1.set(0, 0));
    return stageX >= v.x && stageX <= v.x + actor.getWidth()
        && stageY >= v.y && stageY <= v.y + actor.getHeight();
  }

  private static boolean overBar(float stageX, float stageY) {
    return inRect(bar, stageX, stageY);
  }
}
