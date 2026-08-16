package crystal.aviation.input;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.GestureDetector;
import arc.input.GestureDetector.GestureListener;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Scaling;
import arc.util.Tmp;
import mindustry.input.Binding;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.content.CIcons;
import crystal.type.SatelliteMissile;
import crystal.ui.style.CircleButton;
import mindustry.content.Fx;
import mindustry.core.GameState.State;
import mindustry.entities.Effect;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.type.Sector;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.fragments.PlacementFragment;

import static mindustry.Vars.*;

/**
 * 卫星导弹打击专用输入管理器。
 * 继承 InputHandler，通过 control.setInput() 接管输入，进入绑定区块后自动激活。
 * 激活后：隐藏建筑列表、显示中央红点瞄准镜、右侧圆形开火按钮、右下角导弹选择器。
 */
public class SatelliteMissileInputHandler extends InputHandler {
    private static SatelliteMissileInputHandler instance;

    private boolean active = false;
    private InputHandler previousInput;
    private SatelliteMissile selected;
    private Image selectedIcon;
    private Label selectedLabel;
    private Label statusLabel;
    private float lastPinchDistance = -1f;
    /** 开火冷却时间（tick，0.5 秒 ≈ 30 tick @60fps） */
    private static final float fireCooldown = 30f;
    private float lastFireTime = -fireCooldown;
    /** 连续发射冷却时间（tick，10 秒 ≈ 600 tick @60fps） */
    private static final float consecutiveCooldown = 600f;
    /** 连续发射间隔（tick，0.05 秒 ≈ 3 tick @60fps） */
    private static final float consecutiveInterval = 3f;
    private static final int consecutiveCount = 10;
    private float lastConsecutiveTime = -consecutiveCooldown;
    /** 下次发射是否从左侧炮管发出，实现左右交替 */
    private boolean fireLeftSideNext = true;
    /** 轨道打击模式下允许的最大缩放倍率（限制缩得太小/太大） */
    private static final float strikeMaxZoom = 8f;
    private float savedMaxZoomInGame = -1f;
    private float savedMinZoomInGame = -1f;

    /** 等待进入目标区块后自动启动导弹模式的卫星 ID */
    public static int pendingSatelliteId = -1;
    /** 当前正处于导弹打击模式所操作的卫星 ID */
    public static int activeSatelliteId = -1;
    /** 标记是否处于轨道打击流程中（用于暂停/锁屏恢复以及退出后返回卫星） */
    public static boolean orbitalStrikeMode = false;

    static {
        // 区块加载完成后，若存在等待中的轨道打击请求，则自动切换到导弹输入模式
        arc.Events.on(WorldLoadEvent.class, e -> Core.app.post(() -> onWorldLoaded()));
        // 兜底：当游戏状态切换到 playing 时再次检查，防止 WorldLoadEvent 时机不对导致漏判
        arc.Events.on(StateChangeEvent.class, e -> {
            if (pendingSatelliteId >= 0 && e.to == State.playing) {
                Core.app.post(() -> onWorldLoaded());
            }
            // 暂停/锁屏恢复后 control.input 可能被重置，若仍处于轨道打击流程则重新接管输入
            if (e.to == State.playing && orbitalStrikeMode && !isCurrentInput()) {
                scheduleReenter(1);
                scheduleReenter(8);
                scheduleReenter(20);
            }
        });
        // 应用从后台恢复（锁屏/切后台）时，状态可能未变化，需要额外监听 resume。
        // 某些运行时把 ApplicationListener 的方法当作抽象方法，因此全部显式实现，避免 AbstractMethodError。
        Core.app.addListener(new arc.ApplicationListener() {
            @Override
            public void init() {
            }

            @Override
            public void resize(int width, int height) {
            }

            @Override
            public void update() {
            }

            @Override
            public void pause() {
            }

            @Override
            public void resume() {
                if (orbitalStrikeMode && !isCurrentInput()) {
                    scheduleReenter(1);
                    scheduleReenter(10);
                }
            }

            @Override
            public void dispose() {
            }

            @Override
            public void exit() {
            }

            @Override
            public void fileDropped(arc.files.Fi file) {
            }
        });
    }

    private SatelliteMissileInputHandler() {
    }

    private static SatelliteMissileInputHandler instance() {
        if (instance == null) {
            instance = new SatelliteMissileInputHandler();
        }
        return instance;
    }

    /** 是否处于导弹打击模式 */
    public static boolean active() {
        return orbitalStrikeMode;
    }

    /** 当前 input handler 是否就是本类实例 */
    public static boolean isCurrentInput() {
        return instance != null && control.input == instance;
    }

    /** 当前正在操作的卫星，若不在导弹模式则返回 null */
    public static @Nullable Satellite activeSatellite() {
        if (activeSatelliteId < 0)
            return null;
        return SatelliteManager.get(activeSatelliteId);
    }

    /** 从指定卫星启动轨道打击（会先切换到该卫星绑定的区块） */
    public static void enterForSatellite(Satellite s) {
        if (s == null || !s.boundToSector || s.targetSectorId < 0) {
            ui.showInfoFade("卫星未绑定区块");
            return;
        }
        if (s.planet == null || s.planet.sectors == null) {
            ui.showInfoFade("卫星所属星球无效");
            return;
        }

        Sector target = null;
        for (Sector sec : s.planet.sectors) {
            if (sec.id == s.targetSectorId) {
                target = sec;
                break;
            }
        }
        if (target == null) {
            ui.showInfoFade("绑定区块不存在");
            return;
        }

        // 已经在目标区块中，直接启动导弹模式
        if (state.isGame() && state.rules.sector == target) {
            activeSatelliteId = s.id;
            enter();
            return;
        }

        // 如果在卫星地图中，先保存并退出
        if (SatelliteManager.currentSatelliteId >= 0) {
            Satellite current = SatelliteManager.get(SatelliteManager.currentSatelliteId);
            if (current != null) {
                current.mapData.captureFromWorld();
                SatelliteManager.save();
            }
            SatelliteManager.currentSatelliteId = -1;
            SatelliteManager.lastSector = null;
        }

        pendingSatelliteId = s.id;
        activeSatelliteId = -1;
        // 标记正在退出卫星地图，防止 logic.reset() 触发的 StateChangeEvent 清空 pendingSatelliteId
        SatelliteManager.setExitingSatellite(true);
        try {
            control.playSector(target);
        } catch (Throwable t) {
            pendingSatelliteId = -1;
            SatelliteManager.setExitingSatellite(false);
            ui.showInfoFade("进入绑定区块失败");
        }
    }

    /** 区块加载完成后的回调 */
    public static void onWorldLoaded() {
        if (pendingSatelliteId < 0)
            return;
        Satellite s = SatelliteManager.get(pendingSatelliteId);
        if (s == null) {
            pendingSatelliteId = -1;
            SatelliteManager.setExitingSatellite(false);
            return;
        }
        if (state.rules.sector != null
                && state.rules.sector.id == s.targetSectorId
                && (state.rules.sector.planet == s.planet
                        || (s.planet != null && state.rules.sector.planet != null
                                && s.planet.name.equals(state.rules.sector.planet.name)))) {
            activeSatelliteId = pendingSatelliteId;
            pendingSatelliteId = -1;
            enter();
        } else {
        }
    }

    /** 进入导弹打击模式 */
    public static void enter() {
        // 已进入目标区块或尝试进入，清除退出标记
        SatelliteManager.setExitingSatellite(false);
        if (SatelliteMissile.map.size == 0) {
            ui.showInfoFade("没有可用的卫星导弹类型");
            return;
        }
        if (activeSatelliteId < 0) {
            ui.showInfoFade("未指定操作卫星");
            return;
        }

        SatelliteMissileInputHandler handler = instance();
        handler.previousInput = control.input;
        handler.active = true;
        Satellite s = activeSatellite();
        if (s != null && s.selectedMissile != null) {
            handler.selected = s.selectedMissile;
        } else {
            handler.selected = SatelliteMissile.basic != null ? SatelliteMissile.basic
                    : SatelliteMissile.map.values().next();
        }
        // 进入轨道打击模式时保存并限制最大缩放
        if (handler.savedMaxZoomInGame < 0f) {
            handler.savedMaxZoomInGame = renderer.maxZoomInGame;
            handler.savedMinZoomInGame = renderer.minZoomInGame;
        }
        renderer.maxZoomInGame = strikeMaxZoom;
        renderer.minZoomInGame = 0.5f;
        renderer.clampScale();
        control.setInput(handler);
        orbitalStrikeMode = true;

        // 进入时立即隐藏原版 HUD，防止一帧闪现
        Core.app.post(() -> setPlacementVisible(false));
    }

    /**
     * 退出导弹打击输入模式，但保留轨道打击状态（activeSatelliteId/orbitalStrikeMode）。
     * 用于暂停、锁屏或游戏临时重置输入后能够恢复。
     */
    public static void exit() {
        SatelliteMissileInputHandler handler = instance;
        if (handler != null && handler.active) {
            handler.active = false;
            if (handler.previousInput != null) {
                control.setInput(handler.previousInput);
            }
            // 恢复原始最大缩放
            if (handler.savedMaxZoomInGame >= 0f) {
                renderer.maxZoomInGame = handler.savedMaxZoomInGame;
                renderer.minZoomInGame = handler.savedMinZoomInGame;
                handler.savedMaxZoomInGame = -1f;
                handler.savedMinZoomInGame = -1f;
                renderer.clampScale();
            }
            setPlacementVisible(true);
        }
    }

    // 直接退出轨道打击模式（返回到星球/区块界面，不回到卫星地图）。
    public static void exitToMenu() {
        exit();

        pendingSatelliteId = -1;
        orbitalStrikeMode = false;

        activeSatelliteId = -1;
    }

    /** 退出导弹打击模式并返回到发起轨道打击的卫星地图。 */
    public static void exitToSatellite() {
        int returningSatelliteId = activeSatelliteId;

        exit();

        pendingSatelliteId = -1;
        orbitalStrikeMode = false;

        if (returningSatelliteId >= 0) {
            Satellite s = SatelliteManager.get(returningSatelliteId);
            if (s != null) {
                SatelliteManager.enterSatelliteMap(s);
            } else {
                ui.planet.show();
            }
        }

        activeSatelliteId = -1;
    }

    /** 完全重置导弹打击状态（用于返回菜单等彻底退出场景）。 */
    public static void resets() {
        exit();
        pendingSatelliteId = -1;
        activeSatelliteId = -1;
        orbitalStrikeMode = false;
    }

    /** 延迟若干帧后尝试重新接管轨道打击输入 */
    private static void scheduleReenter(int frames) {
        Core.app.post(() -> {
            if (frames <= 1) {
                tryReenter();
            } else {
                scheduleReenter(frames - 1);
            }
        });
    }

    /** 尝试重新进入轨道打击输入模式（带前置校验与日志） */
    private static void tryReenter() {
        if (!orbitalStrikeMode) {
            return;
        }
        if (activeSatelliteId < 0) {
            return;
        }
        if (isCurrentInput()) {
            return;
        }
        if (!state.isGame()) {
            return;
        }
        enter();
    }

    @Override
    public void add() {
        // 插入独立手势检测器到最前面，专门处理导弹模式下的视角移动/缩放。
        // 由于 InputHandler 的 pan/zoom 在 159.7+ 是 final，无法子类覆盖，
        // 这里用一个非 InputHandler 的 GestureListener 绕过该限制。
        Core.input.getInputProcessors().insert(0,
                new GestureDetector(20, 0.5f, 0.3f, 0.15f, new MissileGestureListener()));
        super.add();
    }

    @Override
    public void remove() {
        // 清理本类添加的自定义手势检测器
        Core.input.getInputProcessors().removeAll(p -> p instanceof GestureDetector
                && ((GestureDetector) p).getListener() instanceof MissileGestureListener);
        super.remove();
    }

    @Override
    public void update() {
        if (!active)
            return;

        // 先清空建筑/命令/射击状态，再让基类更新，确保玩家.selectedBlock 等不会残留
        block = null;
        commandMode = false;
        commandRect = false;
        selectedUnits.clear();
        commandBuildings.clear();
        player.shooting = false;

        // 使用基类更新（处理建筑计划、 spectating 等），不再委托 previousInput，
        // 避免 MobileInput.updateMovement() 把玩家单位拽走或重置相机。
        super.update();

        // 在基类更新后再强制关闭单位建造，确保本帧不会开始新建造
        if (!player.dead() && player.unit() != null) {
            player.unit().updateBuilding(false);
        }

        // 键盘/手柄移动相机（桌面端或接键鼠时可用）
        if (!Core.scene.hasKeyboard() && !locked()) {
            float camSpeed = 6f * Time.delta;
            Vec2 delta = Tmp.v1.setZero().add(Core.input.axis(Binding.moveX), Core.input.axis(Binding.moveY)).nor()
                    .scl(camSpeed);
            if (!delta.isZero()) {
                Core.camera.position.add(delta);
                spectating = null;
            }
        }

        // 滚轮缩放（桌面端）
        if (!Core.scene.hasDialog() && !Core.scene.hasKeyboard()) {
            float zoomAxis = Core.input.axisTap(Binding.zoom);
            if (Math.abs(zoomAxis) > 0.001f) {
                renderer.scaleCamera(zoomAxis);
            }
            // 限制最大缩放（由 maxZoomInGame 控制，clampScale 已自动处理）
            renderer.clampScale();
        }

        // 持续隐藏建筑列表
        setPlacementVisible(false);
        // 实时刷新导弹数量显示
        updateSelectedDisplay();

        // 相机边界限制
        Core.camera.position.clamp(-Core.camera.width / 4f, -Core.camera.height / 4f,
                world.unitWidth() + Core.camera.width / 4f, world.unitHeight() + Core.camera.height / 4f);
    }

    @Override
    public void buildUI(Group group) {
        if (!active)
            return;

        Color panelDark = Color.valueOf("4a3b2e");
        Color panelLight = Color.valueOf("6b5643");
        Color holeColor = Color.valueOf("2e241c");
        Color accentYellow = Color.valueOf("f5c542");

        // 中央 COD 风格瞄准具：大圆环+十字刻度
        group.fill(t -> {
            t.setFillParent(true);
            t.touchable = Touchable.disabled;
            t.rect((x, y, w, h) -> drawCrosshair(x + w / 2f, y + h / 2f)).grow();
        });

        // 左侧炮管面板：导弹信息 + 退出按钮
        group.fill(t -> {
            t.touchable = Touchable.childrenOnly;
            t.left();
            Stack stack = new Stack();

            // 背景：带炮管孔的金属板
            stack.add(new Element() {
                @Override
                public void draw() {
                    // drawTubePanel(0, 0, getWidth(), getHeight(), panelDark, panelLight,
                    // holeColor, true);
                }
            });

            // 按钮层
            Table btns = new Table();
            btns.left().marginLeft(14f).marginTop(80f).marginBottom(40f);
            btns.defaults().pad(18f);

            // 大号导弹/弹药按钮：显示当前导弹与数量，点击执行连续发射
            CircleButton missileBtn = newCircleButton(CIcons.timesfire, 88f, panelLight, Color.white);
            selectedIcon = missileBtn.getImage();
            selectedIcon.setScaling(Scaling.fit);
            missileBtn.clicked(this::consecutive);

            // 冷却遮罩：连续发射后覆盖阴影，以时钟旋转方式逐渐消失
            Element cooldownOverlay = new Element() {
                @Override
                public void draw() {
                    float elapsed = Time.time - lastConsecutiveTime;
                    if (elapsed >= consecutiveCooldown)
                        return;
                    float ratio = Mathf.clamp(elapsed / consecutiveCooldown, 0f, 1f);
                    float cx = x + width / 2f;
                    float cy = y + height / 2f;
                    float radius = width / 2f;
                    float fraction = 1f - ratio;

                    Draw.color(Color.black, 0.55f);
                    Fill.arc(cx, cy, radius, fraction, 90f);
                }
            };
            cooldownOverlay.touchable = Touchable.disabled;

            Stack missileStack = new Stack();
            missileStack.add(missileBtn);
            missileStack.add(cooldownOverlay);
            btns.add(missileStack).size(88f).row();

            selectedLabel = new Label("");
            selectedLabel.setColor(Color.white);
            selectedLabel.setFontScale(0.8f);
            btns.add(selectedLabel).padTop(4f).row();

            // 退出按钮（主动退出时返回发起轨道打击的卫星）
            CircleButton exitBtn = newCircleButton(Icon.cancel, 58f, panelLight, Color.lightGray);
            exitBtn.clicked(SatelliteMissileInputHandler::exitToSatellite);
            btns.add(exitBtn).size(58f).padTop(24f);

            stack.add(btns);
            t.add(stack).width(110f).fillY();
        });

        // 右侧炮管面板：武器切换 + 开火
        group.fill(t -> {
            t.touchable = Touchable.childrenOnly;
            t.right();
            Stack stack = new Stack();

            stack.add(new Element() {
                @Override
                public void draw() {
                    // drawTubePanel(0, 0, getWidth(), getHeight(), panelDark, panelLight,
                    // holeColor, false);
                }
            });

            Table btns = new Table();
            btns.right().marginRight(14f).marginTop(80f).marginBottom(40f);
            btns.defaults().pad(18f);

            // 导弹切换按钮：仅在轨道打击模式下生效，不影响自动攻击导弹
            CircleButton selectBtn = newCircleButton(Icon.units, 72f, panelLight, Color.white);
            selectBtn.clicked(this::showSelector);
            btns.add(selectBtn).size(72f).row();

            // 下方大号开火按钮
            CircleButton fireBtn = newCircleButton(CIcons.onefire, 96f, Color.valueOf("5c2a24"), Color.white);
            fireBtn.strokeColor = Color.valueOf("ff6b5c");
            fireBtn.clicked(this::fire);
            btns.add(fireBtn).size(96f).padTop(18f).row();

            stack.add(btns);
            t.add(stack).width(110f).fillY();
        });

        // 底部状态条：装填/就绪进度
        group.fill(t -> {
            t.touchable = Touchable.disabled;
            t.bottom().marginBottom(28f);
            Table bar = new Table();
            bar.defaults().padBottom(4f);
            statusLabel = new Label("就绪");
            statusLabel.setColor(accentYellow);
            statusLabel.setFontScale(0.85f);
            bar.add(statusLabel).row();
            bar.rect((x, y, w, h) -> {
                float ratio = Mathf.clamp((Time.time - lastFireTime) / fireCooldown, 0f, 1f);
                float cx = x + w / 2f;
                float cy = y + h / 2f;
                // 背景（Fill.rect 以中心定位）
                Draw.color(panelDark);
                Fill.rect(cx, cy, w, h);
                // 进度填充（左对齐，以中心定位）
                Draw.color(accentYellow);
                Fill.rect(x + w * ratio / 2f, cy, w * ratio, h);
                // 边框（Lines.rect 以左下角定位）
                Draw.color(Color.gray);
                Lines.stroke(2f);
                Lines.rect(x, y, w, h);
                Draw.color();
            }).size(220f, 10f);
            t.add(bar);
        });

        updateSelectedDisplay();
    }

    @Override
    public void buildPlacementUI(Table table) {
        // 导弹模式下不显示建筑放置 UI
    }

    // InputHandler 在 Mindustry 159.7+ 将 GestureListener/InputProcessor 相关方法声明为
    // final，
    // 子类无法覆盖 tap/longPress/pan/zoom/pinch/mouseMoved/scrolled 等。视角控制改在 update()
    // 中手动处理。

    @Override
    public void useSchematic(Schematic schem, boolean checkHidden) {
        // 导弹模式不使用蓝图
    }

    /** 绘制 COD 风格中央瞄准具：大圆环、刻度、中心点 */
    private void drawCrosshair(float cx, float cy) {
        float time = Time.time;
        float base = 110f;
        float pulse = 1f + Mathf.sin(time * 0.008f) * 0.04f;
        float rot = time * 0.4f;
        Color yellow = Color.valueOf("f5c542");
        Color white = Color.white;

        // 外层大圆环（带轻微呼吸）
        Draw.color(yellow, 0.85f);
        Lines.stroke(8f);
        Lines.circle(cx, cy, base * pulse);

        // 旋转方位刻度
        Draw.color(yellow, 0.6f);
        Lines.stroke(5.5f);
        for (int i = 0; i < 12; i++) {
            float angle = i * 30f + rot;
            float r1 = base - 10f;
            float r2 = base + (i % 3 == 0 ? 10f : 4f);
            float a1 = Mathf.cosDeg(angle);
            float b1 = Mathf.sinDeg(angle);
            Lines.line(cx + a1 * r1, cy + b1 * r1, cx + a1 * r2, cy + b1 * r2);
        }

        // 内圈虚线圆
        Draw.color(yellow, 0.4f);
        Lines.stroke(4.5f);
        Lines.dashCircle(cx, cy, base * 0.55f);

        // 十字线（带缺口）
        float len = base * 0.85f;
        float gap = 14f;
        Draw.color(white, 0.9f);
        Lines.stroke(6f);
        Lines.line(cx - len, cy, cx - gap, cy);
        Lines.line(cx + gap, cy, cx + len, cy);
        Lines.line(cx, cy - len, cx, cy - gap);
        Lines.line(cx, cy + gap, cx, cy + len);

        // 四向距离标尺
        Draw.color(yellow, 0.5f);
        Lines.stroke(4.5f);
        for (int dir = 0; dir < 4; dir++) {
            float angle = dir * 90f;
            for (int j = 1; j <= 3; j++) {
                float r = gap + j * 16f;
                float tick = j == 3 ? 8f : 4f;
                float ax = Mathf.cosDeg(angle);
                float ay = Mathf.sinDeg(angle);
                float px = cx + ax * r;
                float py = cy + ay * r;
                Lines.line(px - ay * tick, py + ax * tick, px + ay * tick, py - ax * tick);
            }
        }

        // 中心点
        Draw.color(yellow);
        Fill.circle(cx, cy, 5f);
        Draw.color(white, 0.8f);
        Fill.circle(cx, cy, 2.5f);

        Draw.color();
    }

    /** 创建统一风格的圆形按钮 */
    private CircleButton newCircleButton(Drawable icon, float size, Color bgColor, Color iconColor) {
        ImageButtonStyle style = new ImageButtonStyle(Styles.clearNonei);
        style.imageUp = icon;
        CircleButton btn = new CircleButton(icon, style);
        btn.backgroundColor = bgColor;
        btn.strokeColor = Color.valueOf("5a5048");
        btn.setColor(iconColor);
        return btn;
    }

    /** 绘制两侧导弹发射管面板背景 */
    private void drawTubePanel(float x, float y, float w, float h, Color bg, Color edge, Color hole, boolean left) {
        // 主体背景
        Draw.color(bg);
        Fill.rect(x, y, w, h);
        // 侧边金属亮边
        Draw.color(edge);
        float edgeW = 6f;
        if (left) {
            Fill.rect(x + w - edgeW, y, edgeW, h);
        } else {
            Fill.rect(x, y, edgeW, h);
        }

        // 炮管孔：垂直排列的多行小圆
        Draw.color(hole);
        int rows = 8;
        int cols = 2;
        float marginX = 18f;
        float marginY = 160f;
        float availH = h - marginY * 2f;
        float stepY = availH / (rows - 1);
        float holeR = 5f;
        for (int r = 0; r < rows; r++) {
            float py = y + marginY + r * stepY;
            for (int c = 0; c < cols; c++) {
                float px = left
                        ? x + marginX + c * (w - marginX * 2f - holeR) / (cols - 1)
                        : x + w - marginX - c * (w - marginX * 2f - holeR) / (cols - 1);
                Fill.circle(px, py, holeR);
                // 孔内暗部：不用纯黑，改用 holeColor 加深，避免生硬黑块
                Draw.color(hole.cpy().mul(0.55f), 0.5f);
                Fill.circle(px + 1f, py - 1f, holeR * 0.5f);
                Draw.color(hole);
            }
        }

        Draw.color();
    }

    private void updateSelectedDisplay() {
        if (selected == null)
            return;
        if (selectedIcon != null) {
            // 使用导弹自身纹理作为图标，不同导弹显示不同图标；若纹理未加载则回退到默认单位图标
            selectedIcon.setDrawable(
                    selected.region != null && selected.region.found() ? selected.region : Icon.units.getRegion());
        }
        if (selectedLabel != null) {
            int amount = currentAmount();
            selectedLabel.setText("[accent]" + selected.name + "[]\n[lightgray]" + amount + " 发");
        }
        if (statusLabel != null) {
            boolean ready = Time.time - lastFireTime >= fireCooldown;
            statusLabel.setText(ready ? "就绪" : "装填中");
            statusLabel.setColor(ready ? Color.valueOf("f5c542") : Color.lightGray);
        }
    }

    private int currentAmount() {
        Satellite s = activeSatellite();
        if (s == null || s.missileModule == null)
            return 0;
        return s.missileModule.get(selected);
    }

    private void showSelector() {
        Satellite s = activeSatellite();
        if (s == null || s.missileModule == null) {
            ui.showInfoFade("未进入有效卫星打击模式");
            return;
        }

        BaseDialog dialog = new BaseDialog("选择卫星导弹");
        dialog.cont.defaults().pad(6f);
        for (SatelliteMissile missile : SatelliteMissile.map.values()) {
            int amount = s.missileModule.get(missile);
            dialog.cont.button(missile.name + " [lightgray](" + amount + ")", Styles.flatt, () -> {
                selected = missile;
                updateSelectedDisplay();
                dialog.hide();
            }).size(240f, 58f).row();
        }

        dialog.addCloseButton();
        dialog.show();
    }

    private static Vec2 origin = new Vec2();

    /** 连续发射 10 枚导弹，每枚间隔 0.05 秒；发射后进入 10 秒冷却。 */
    private void consecutive() {
        Satellite s = activeSatellite();
        if (s == null || s.missileModule == null) {
            ui.showInfoFade("未进入有效卫星打击模式");
            return;
        }
        if (selected == null) {
            ui.showInfoFade("请先选择导弹");
            return;
        }
        if (!s.missileModule.has(selected, consecutiveCount)) {
            ui.showInfoFade("导弹数量不足（需要 " + consecutiveCount + " 发）");
            return;
        }
        if (Time.time - lastConsecutiveTime < consecutiveCooldown) {
            ui.showInfoFade("连续发射冷却中");
            return;
        }

        lastConsecutiveTime = Time.time;

        for (int i = 0; i < consecutiveCount; i++) {
            int index = i;
            Time.run(index * consecutiveInterval, () -> {
                if (!orbitalStrikeMode || !active)
                    return;
                Satellite sat = activeSatellite();
                if (sat == null || sat.missileModule == null)
                    return;
                if (!sat.missileModule.has(selected, 1))
                    return;
                fireOne(sat, selected);
            });
        }
    }

    /** 发射单枚导弹（不检查冷却，供 fire() 与 consecutive() 复用）。 */
    private void fireOne(Satellite s, SatelliteMissile missile) {
        if (s == null || s.missileModule == null || !s.missileModule.has(missile, 1))
            return;

        float tx = Core.camera.position.x;
        float ty = Core.camera.position.y;

        s.missileModule.remove(missile, 1);
        updateSelectedDisplay();

        // 导弹从相机视口两侧飞出，向准星位置飞去。
        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        float sx = fireLeftSideNext
                ? cx - (Core.camera.width / 2f + 8f)
                : cx + (Core.camera.width / 2f + 8f);
        float sy = cy + Mathf.random(-24f, 24f);

        launchMissile(s, sx, sy, tx, ty, missile);
        fireLeftSideNext = !fireLeftSideNext;

        // 发射时屏幕轻微震动
        Effect.shake(10f, 40f, tx, ty);
    }

    private void fire() {
        Satellite s = activeSatellite();
        if (s == null || s.missileModule == null) {
            ui.showInfoFade("未进入有效卫星打击模式");
            return;
        }
        if (selected == null) {
            ui.showInfoFade("请先选择导弹");
            return;
        }
        if (!s.missileModule.has(selected, 1)) {
            ui.showInfoFade("导弹数量不足");
            return;
        }
        // 0.5 秒冷却
        if (Time.time - lastFireTime < fireCooldown) {
            ui.showInfoFade("导弹装填中");
            return;
        }

        fireOne(s, selected);
        lastFireTime = Time.time;
    }

    /** 从指定位置向目标点发射一枚导弹，生命周期刚好让它在目标点命中 */
    private void launchMissile(Satellite s, float sx, float sy, float tx, float ty, SatelliteMissile type) {
        // 使用 SatelliteMissile.create 统一创建，仿 BulletType.create
        crystal.gen.SMissile m = type.create(player.unit(), player.team(), sx, sy, tx, ty);
    }

    /**
     * 自定义手势监听器：处理导弹模式下的单指平移与双指缩放。
     * 由于 InputHandler 的 pan/zoom 被声明为 final，无法直接覆盖，
     * 这里以一个独立的 GestureListener 注册到 Core.input 最前端来绕过限制。
     */
    private class MissileGestureListener implements GestureListener {
        private float lastZoom = -1f;
        private @Nullable Element gestureStartTarget;

        @Override
        public boolean touchDown(float x, float y, int pointer, KeyCode button) {
            Vec2 v = Core.scene.screenToStageCoordinates(Tmp.v1.set(x, y));
            gestureStartTarget = Core.scene.hit(v.x, v.y, true);
            lastZoom = -1f;
            return false;
        }

        @Override
        public boolean tap(float x, float y, int count, KeyCode button) {
            return false;
        }

        @Override
        public boolean longPress(float x, float y) {
            return false;
        }

        @Override
        public boolean fling(float velocityX, float velocityY, KeyCode button) {
            return false;
        }

        @Override
        public boolean pan(float x, float y, float deltaX, float deltaY) {
            if (!active || Core.scene.hasDialog())
                return false;
            // 若手指起始于 UI 元素（按钮等），不拖动相机
            if (gestureStartTarget != null)
                return false;

            float scale = Core.camera.width / Core.graphics.getWidth();
            Core.camera.position.x -= deltaX * scale;
            Core.camera.position.y -= deltaY * scale;
            spectating = null;
            Core.camera.position.clamp(-Core.camera.width / 4f, -Core.camera.height / 4f,
                    world.unitWidth() + Core.camera.width / 4f, world.unitHeight() + Core.camera.height / 4f);
            return true;
        }

        @Override
        public boolean panStop(float x, float y, int pointer, KeyCode button) {
            return false;
        }

        @Override
        public boolean zoom(float initialDistance, float distance) {
            if (!active || Core.scene.hasDialog())
                return false;
            if (gestureStartTarget != null)
                return false;
            if (lastZoom < 0f)
                lastZoom = renderer.getScale();

            renderer.setScale(Mathf.clamp(distance / initialDistance * lastZoom, 0.5f, strikeMaxZoom));
            return true;
        }

        @Override
        public boolean pinch(Vec2 initialPointer1, Vec2 initialPointer2, Vec2 pointer1, Vec2 pointer2) {
            return false;
        }

        @Override
        public void pinchStop() {
        }
    }

    /** 隐藏/显示轨道打击模式下不应出现的原版 HUD（建筑列表、命令按钮等）。 */
    private static void setPlacementVisible(boolean visible) {
        // 1) 通过名称查找 PlacementFragment 根容器（比反射更可靠，Android 兼容性好）
        Element placement = Core.scene.find("placement-toggler");
        if (placement instanceof Table pt) {
            pt.visibility = visible ? () -> ui.hudfrag.shown : () -> false;
            pt.touchable = visible ? Touchable.childrenOnly : Touchable.disabled;
            pt.validate();
        }

        // 2) 通过名称查找移动端左下角命令按钮表
        Element cmdTable = Core.scene.find("mobile-command-table");
        if (cmdTable instanceof Table ct) {
            ct.visible(() -> visible);
            ct.touchable = visible ? Touchable.childrenOnly : Touchable.disabled;
            ct.validate();
        }

        // 3) 兜底：反射隐藏 PlacementFragment 内部子面板
        try {
            java.lang.reflect.Field togglerField = PlacementFragment.class.getDeclaredField("toggler");
            togglerField.setAccessible(true);
            Table toggler = (Table) togglerField.get(ui.hudfrag.blockfrag);
            if (toggler != null) {
                toggler.visibility = visible ? () -> ui.hudfrag.shown : () -> false;
                toggler.touchable = visible ? Touchable.childrenOnly : Touchable.disabled;
                toggler.validate();
            }

            String[] names = { "mainStack", "blockCatTable", "commandTable" };
            for (String name : names) {
                try {
                    java.lang.reflect.Field f = PlacementFragment.class.getDeclaredField(name);
                    f.setAccessible(true);
                    Table t = (Table) f.get(ui.hudfrag.blockfrag);
                    if (t != null) {
                        t.visible(() -> visible);
                        t.touchable = visible ? Touchable.childrenOnly : Touchable.disabled;
                        t.validate();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {

        }
    }

    public static Vec2 getVec2InRound(float x, float y, float radius) {
        float angle = Mathf.random() * 2f * Mathf.PI;
        float distance = Mathf.sqrt(Mathf.random()) * radius;
        float px = x + distance * Mathf.cos(angle);
        float py = y + distance * Mathf.sin(angle);
        return new Vec2(px, py);
    }
}
