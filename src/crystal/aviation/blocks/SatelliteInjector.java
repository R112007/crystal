package crystal.aviation.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteContentFilter;
import crystal.aviation.SatelliteManager;
import crystal.content.CFx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import crystal.world.meta.CStat;
import mindustry.world.meta.StatUnit;
import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

public class SatelliteInjector extends Block {

    public int defaultAmount = 50;
    public TextureRegion pad, left1, left2, right1, right2;

    /** 展开动画时长（tick） */
    public float openDuration = 180f;
    /** 注入（侧臂收回 + pad 缩到 padEndScale）动画时长（tick） */
    public float injectDuration = 140f;
    /** 闭合（pad 继续缩到 0 + 淡出）动画时长（tick） */
    public float closeDuration = 120f;
    /** pad 淡入/淡出时长（tick） */
    public float padFadeDuration = 90f;
    /** 两个注入轮回之间的间隔（tick） */
    public float intervalDuration = 3 * 60f;

    /** 侧臂 1（right1 / left1）移动距离 */
    public float moveDist1 = 12f;
    /** 侧臂 2（right2 / left2）移动距离 */
    public float moveDist2 = 18f;

    /** pad 初始缩放（展开后等待注入时的大小） */
    public float padStartScale = 2.1f;
    /** 侧臂完全闭合时 pad 的缩放（之后继续缩到 0） */
    public float padEndScale = 1.4f;
    public float shake = 1.3f;
    public Effect injectEffect = new Effect(120f, 300f, e -> {
        e.color = Pal.lancerLaser;
        float scl = 2f;
        float fin = e.fin();
        float fout = 1f - fin;

        Color base = Tmp.c1.set(e.color);
        Color bright = Tmp.c2.set(base).lerp(Color.white, 0.55f);
        Color glow = Tmp.c3.set(base).lerp(Color.white, 0.85f);

        float converge = Mathf.curve(fin, 0f, 0.35f);
        float compress = Mathf.curve(fin, 0.25f, 0.45f);
        float burst = Mathf.curve(fin, 0.38f, 0.55f);
        float expand = Mathf.curve(fin, 0.45f, 1.0f);
        float decay = Mathf.curve(fin, 0.75f, 1.0f);

        // === 1. 六条弧形螺旋臂 ===
        int arms = 6;
        int armSegments = 14;
        float maxRadius = 100f * scl;
        float spiralAngle = 55f;

        for (int i = 0; i < arms; i++) {
            float angleBase = i * 60f + e.rotation;
            int dir = (i % 2 == 0) ? 1 : -1;
            float rotOffset = fin * 480f * dir;
            float currentSpiral = spiralAngle * (1f - compress * 0.3f);
            float currentMaxR = maxRadius * (1f - converge * 0.9f);

            for (int seg = 0; seg < armSegments; seg++) {
                float t1 = seg / (float) armSegments;
                float t2 = (seg + 1) / (float) armSegments;
                float r1 = currentMaxR * (1f - Mathf.pow(t1, 0.85f) * converge);
                float r2 = currentMaxR * (1f - Mathf.pow(t2, 0.85f) * converge);
                float bend1 = currentSpiral * Mathf.pow(t1, 0.7f);
                float bend2 = currentSpiral * Mathf.pow(t2, 0.7f);
                float a1 = angleBase + rotOffset + bend1 * dir;
                float a2 = angleBase + rotOffset + bend2 * dir;

                float x1 = e.x + Angles.trnsx(a1, r1);
                float y1 = e.y + Angles.trnsy(a1, r1);
                float x2 = e.x + Angles.trnsx(a2, r2);
                float y2 = e.y + Angles.trnsy(a2, r2);

                float widthBase = 1.8f + t1 * 1.6f;
                float width = widthBase * (1f + compress * 0.8f) * fout * scl;

                Color segColor = t1 > 0.6f ? bright : base;
                Draw.color(segColor);
                Draw.alpha((0.5f + t1 * 0.4f + compress * 0.3f) * (1f - decay) * fout);
                Lines.stroke(width);
                Lines.line(x1, y1, x2, y2);

                if (seg % 3 == 0 && t1 > 0.3f) {
                    Draw.color(glow);
                    Draw.alpha((0.4f + compress * 0.5f) * (1f - t1 * 0.3f) * (1f - decay) * fout);
                    float nodeSize = (1.2f + t1 * 1.6f) * (1f + compress * 0.5f) * fout * scl;
                    Fill.circle(x1, y1, nodeSize);
                }
            }

            float tipT = converge * 0.95f;
            float tipR = currentMaxR * (1f - tipT);
            float tipBend = currentSpiral * tipT;
            float tipAngle = angleBase + rotOffset + tipBend * dir;
            float tipX = e.x + Angles.trnsx(tipAngle, tipR);
            float tipY = e.y + Angles.trnsy(tipAngle, tipR);

            Draw.color(glow);
            Draw.alpha(compress * 0.9f * (1f - decay) * fout);
            Fill.circle(tipX, tipY, (2.4f + compress * 3.2f) * fout * scl);
        }

        // === 2. 中心能量压缩 ===
        float corePulse = compress * (1f - burst * 0.6f);
        float coreSize = (corePulse * 10f + burst * 20f * expand) * fout * scl;

        Draw.color(glow);
        Draw.alpha((compress * 0.85f + burst * 0.35f) * (1f - decay) * fout);
        Fill.circle(e.x, e.y, coreSize);

        Draw.color(bright);
        Draw.alpha(compress * 0.75f * (1f - burst * 0.4f) * fout);
        Fill.circle(e.x, e.y, coreSize * 0.45f);

        // === 3. 多层弧形冲击波 ===
        int shockLayers = 4;
        for (int i = 0; i < shockLayers; i++) {
            float delay = i * 0.05f;
            float layerFin = Mathf.curve(fin, 0.40f + delay, 0.95f);
            float layerFout = 1f - layerFin;
            if (layerFin <= 0)
                continue;

            float speed = 1f + i * 0.3f;
            float radius = layerFin * (40f + i * 36f) * speed * scl;

            int ringSegs = 24;
            for (int s = 0; s < ringSegs; s++) {
                float rt1 = s / (float) ringSegs;
                float rt2 = (s + 1) / (float) ringSegs;
                float baseA = rt1 * 360f + e.rotation + fin * 60f * ((i % 2 == 0) ? 1 : -1);
                float nextA = rt2 * 360f + e.rotation + fin * 60f * ((i % 2 == 0) ? 1 : -1);

                float wave = Mathf.sin(rt1 * 6f + fin * 4f) * 4f * layerFin;
                float r1 = radius + wave;
                float r2 = radius + Mathf.sin(rt2 * 6f + fin * 4f) * 4f * layerFin;

                float sx1 = e.x + Angles.trnsx(baseA, r1);
                float sy1 = e.y + Angles.trnsy(baseA, r1);
                float sx2 = e.x + Angles.trnsx(nextA, r2);
                float sy2 = e.y + Angles.trnsy(nextA, r2);

                Draw.color(i % 2 == 0 ? bright : base);
                Draw.alpha(layerFout * 0.65f * (1f - decay) * fout);
                Lines.stroke((4f - i * 0.6f) * layerFout * fout * scl);
                Lines.line(sx1, sy1, sx2, sy2);
            }

            for (int j = 0; j < arms; j++) {
                float nodeAngle = j * 60f + e.rotation + fin * 45f * ((j % 2 == 0) ? 1 : -1);
                float wave = Mathf.sin(j + fin * 3f) * 3f * layerFin;
                float nx = e.x + Angles.trnsx(nodeAngle, radius + wave);
                float ny = e.y + Angles.trnsy(nodeAngle, radius + wave);

                Draw.color(bright);
                Draw.alpha(layerFout * 0.7f * (1f - decay) * fout);
                Fill.circle(nx, ny, (2.8f - i * 0.35f) * layerFout * fout * scl);
            }
        }

        // === 4. 爆发射线（直线，无弧形偏移）===
        int rays = 12;
        for (int i = 0; i < rays; i++) {
            float rayAngle = i * 30f + e.rotation;
            float rayFin = Mathf.curve(fin, 0.42f, 0.78f);
            float rayFout = 1f - rayFin;
            if (rayFin <= 0)
                continue;

            float rayLen = rayFin * (32f + (i % 3) * 20f) * scl;
            float startR = 6f * compress * scl;
            float endR = startR + rayLen;

            float x1 = e.x + Angles.trnsx(rayAngle, startR);
            float y1 = e.y + Angles.trnsy(rayAngle, startR);
            float x2 = e.x + Angles.trnsx(rayAngle, endR);
            float y2 = e.y + Angles.trnsy(rayAngle, endR);

            Draw.color(i % 2 == 0 ? glow : bright);
            Draw.alpha(0.8f * rayFout * (1f - decay) * fout);
            Lines.stroke(2.8f * rayFout * fout * scl);
            Lines.line(x1, y1, x2, y2);
        }

        // === 5. 飞散粒子（纯圆点，无拖尾线条）===
        Angles.randLenVectors(e.id, 20, burst * 88f * scl, e.rotation + 180f, 100f, (x, y) -> {
            float dist = Mathf.dst(x, y);
            float maxDist = 88f * scl;
            float life = 1f - dist / maxDist;

            Draw.color(base);
            Draw.alpha(fout * 0.9f * life * (1f - decay));
            float size = 3f * fout * life * (1f - decay) * scl;
            Fill.circle(e.x + x, e.y + y, Math.max(size, 0));
        });

        Draw.color(base);
        Draw.alpha(0.16f * burst * expand * fout * fout);
        Fill.light(e.x, e.y, 20, 110f * burst * expand * fout * scl, bright, Color.clear);

        Draw.reset();
    });

    public SatelliteInjector(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        requirements(Category.effect, CBuildVisibility.satelliteOnly, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.copper, 100),
                new ItemStack(mindustry.content.Items.lead, 80)
        });
        config(Item.class, (SatelliteInjectorBuild build, Item item) -> {
            build.injectItem = item;
            build.registerConfig();
        });
    }

    @Override
    public void load() {
        super.load();
        pad = Core.atlas.find(name + "-pad");
        left1 = Core.atlas.find(name + "-left1");
        left2 = Core.atlas.find(name + "-left2");
        right1 = Core.atlas.find(name + "-right1");
        right2 = Core.atlas.find(name + "-right2");
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.injectAmount, defaultAmount, StatUnit.items);
        stats.add(CStat.transferInterval, Satellite.injectInterval, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        barMap.remove("items");
    }

    public class SatelliteInjectorBuild extends Building {
        public int satelliteId = -1;
        public @Nullable Item injectItem = null;
        public int injectAmount = defaultAmount;

        private enum State {
            IDLE, OPENING, WAITING, INJECTING, CLOSING, INTERVAL
        }

        private State state = State.IDLE;
        private float animProgress = 0f;
        private float lastInjectTimer = 0f;

        /** pad 淡入/淡出进度 0~1 */
        private float padFadeProgress = 0f;
        /** 进入 CLOSING 时 pad 的初始透明度 */
        private float closingPadStartAlpha = 0f;
        /** 进入 CLOSING 时 pad 的初始缩放 */
        private float closingPadStartScale = 1f;
        /** 侧臂展开进度 0~1（CLOSING 时从此值收到 0） */
        private float armOpenProgress = 0f;
        /** 间隔等待进度 */
        private float intervalProgress = 0f;

        private static final float ROTATE_ANGLE = 45f;

        @Override
        public void created() {
            super.created();
            if (SatelliteManager.currentSatelliteId >= 0 && satelliteId < 0) {
                satelliteId = SatelliteManager.currentSatelliteId;
            }
            registerConfig();
            resetToIdle();
        }

        @Override
        public void updateTile() {
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null) {
                if (state != State.IDLE)
                    resetToIdle();
                return;
            }
            if (injectItem != null && s.injectProgress() >= 1 - 0.01f) {
                Effect.shake(shake, 90, this);
                injectEffect.at(this);
            }
            boolean injectMode = s.injectMode;
            float currentInjectTimer = s.injectTimer;
            SatelliteInjector self = (SatelliteInjector) block;

            // 中途取消
            if ((!injectMode || injectItem == null)
                    && state != State.IDLE
                    && state != State.CLOSING
                    && state != State.INTERVAL) {

                closingPadStartAlpha = switch (state) {
                    case OPENING -> 0f;
                    case WAITING -> Math.min(1f, padFadeProgress);
                    case INJECTING -> 1f;
                    default -> 0f;
                };

                closingPadStartScale = switch (state) {
                    case OPENING, WAITING -> self.padStartScale;
                    case INJECTING -> self.padStartScale - (self.padStartScale - self.padEndScale) * animProgress;
                    default -> self.padStartScale;
                };

                state = State.CLOSING;
                animProgress = 0f;
                padFadeProgress = 0f;
                // armOpenProgress 保持当前值（INJECTING 里已更新）
                return;
            }

            switch (state) {
                case IDLE -> {
                    if (injectMode && injectItem != null) {
                        state = State.OPENING;
                        animProgress = 0f;
                        padFadeProgress = 0f;
                        armOpenProgress = 0f;
                        lastInjectTimer = currentInjectTimer;
                    }
                }

                case OPENING -> {
                    animProgress += Time.delta / self.openDuration;
                    armOpenProgress = Mathf.clamp(animProgress);
                    if (animProgress >= 1f) {
                        animProgress = 1f;
                        armOpenProgress = 1f;
                        state = State.WAITING;
                        padFadeProgress = 0f;
                        lastInjectTimer = currentInjectTimer;
                    }
                }

                case WAITING -> {
                    armOpenProgress = 1f;
                    padFadeProgress += Time.delta / self.padFadeDuration;
                    if (padFadeProgress > 1f)
                        padFadeProgress = 1f;

                    // 关键修改：injectProgress 达到 0.9 即开始注入动画，保留 injectTimer 重置兜底防止跳帧
                    if (s.injectProgress() >= 0.9f || currentInjectTimer < lastInjectTimer) {
                        state = State.INJECTING;
                        animProgress = 0f;
                    }
                    lastInjectTimer = currentInjectTimer;
                }

                case INJECTING -> {
                    // 关键修复：注入时同步收回侧臂，进入 CLOSING 后不再重复收
                    armOpenProgress = 1f - animProgress;
                    animProgress += Time.delta / self.injectDuration;
                    if (animProgress >= 1f) {
                        animProgress = 1f;
                        armOpenProgress = 0f;
                        state = State.CLOSING;
                        closingPadStartAlpha = 1f;
                        closingPadStartScale = self.padEndScale;
                        animProgress = 0f;
                        padFadeProgress = 0f;
                    }
                }

                case CLOSING -> {
                    animProgress += Time.delta / self.closeDuration;
                    padFadeProgress += Time.delta / self.padFadeDuration;

                    if (animProgress >= 1f && padFadeProgress >= 1f) {
                        animProgress = 1f;
                        padFadeProgress = 1f;
                        state = State.INTERVAL;
                        intervalProgress = 0f;
                    }
                }

                case INTERVAL -> {
                    intervalProgress += Time.delta;
                    if (intervalProgress >= self.intervalDuration) {
                        if (injectMode && injectItem != null) {
                            state = State.OPENING;
                            animProgress = 0f;
                            padFadeProgress = 0f;
                            armOpenProgress = 0f;
                            lastInjectTimer = currentInjectTimer;
                        } else {
                            state = State.IDLE;
                            resetToIdle();
                        }
                    }
                }
            }
        }

        private void resetToIdle() {
            state = State.IDLE;
            animProgress = 0f;
            padFadeProgress = 0f;
            closingPadStartAlpha = 0f;
            closingPadStartScale = ((SatelliteInjector) block).padStartScale;
            armOpenProgress = 0f;
            intervalProgress = 0f;
        }

        @Override
        public void draw() {
            SatelliteInjector block = (SatelliteInjector) this.block;
            if (block == null)
                return;

            Draw.rect(block.region, x, y);

            float dist1 = block.moveDist1;
            float dist2 = block.moveDist2;
            float rotAngle = -ROTATE_ANGLE;
            float startScale = block.padStartScale;
            float endScale = block.padEndScale;

            float r1x, r1rot, r2x, r2rot;
            float l1x, l1rot, l2x, l2rot;
            float padAlpha, padScale, padOffY;

            switch (state) {
                case IDLE, INTERVAL -> {
                    r1x = r1rot = r2x = r2rot = 0f;
                    l1x = l1rot = l2x = l2rot = 0f;
                    padAlpha = 0f;
                    padScale = startScale;
                    padOffY = 0f;
                }

                case OPENING -> {
                    float moveProg = Mathf.clamp(armOpenProgress / 0.5f);
                    float rotProg = Mathf.clamp((armOpenProgress - 0.5f) / 0.3f);
                    float extra = Mathf.clamp((moveProg - 0.5f) * 2f);
                    r1x = dist1 * moveProg;
                    r1rot = rotAngle * rotProg;
                    r2x = dist1 * moveProg + (dist2 - dist1) * extra;
                    r2rot = rotAngle * rotProg;
                    l1x = -dist1 * moveProg;
                    l1rot = rotAngle * rotProg;
                    l2x = -dist1 * moveProg - (dist2 - dist1) * extra;
                    l2rot = rotAngle * rotProg;
                    padAlpha = 0f;
                    padScale = startScale;
                    padOffY = 0f;
                }

                case WAITING -> {
                    r1x = dist1;
                    r1rot = rotAngle;
                    r2x = dist2;
                    r2rot = rotAngle;
                    l1x = -dist1;
                    l1rot = rotAngle;
                    l2x = -dist2;
                    l2rot = rotAngle;
                    padAlpha = Math.min(1f, padFadeProgress);
                    padScale = startScale;
                    padOffY = 0f;
                }

                case INJECTING -> {
                    // 侧臂从 armOpenProgress（此时由 updateTile 同步为 1-animProgress）计算
                    float currentArm = armOpenProgress;
                    float moveProg = Mathf.clamp(currentArm / 0.5f);
                    float rotProg = Mathf.clamp((currentArm - 0.5f) / 0.3f);
                    float extra = Mathf.clamp((moveProg - 0.5f) * 2f);
                    r1x = dist1 * moveProg;
                    r1rot = rotAngle * rotProg;
                    r2x = dist1 * moveProg + (dist2 - dist1) * extra;
                    r2rot = rotAngle * rotProg;
                    l1x = -dist1 * moveProg;
                    l1rot = rotAngle * rotProg;
                    l2x = -dist1 * moveProg - (dist2 - dist1) * extra;
                    l2rot = rotAngle * rotProg;

                    padAlpha = 1f;
                    padScale = startScale - (startScale - endScale) * animProgress;
                    padOffY = 0f;
                }

                case CLOSING -> {
                    // 侧臂从 armOpenProgress（INJECTING 结束时为 0）收到 0
                    float currentArm = armOpenProgress * Math.max(0f, 1f - Mathf.clamp(animProgress));
                    float moveProg = Mathf.clamp(currentArm / 0.5f);
                    float rotProg = Mathf.clamp((currentArm - 0.5f) / 0.3f);
                    float extra = Mathf.clamp((moveProg - 0.5f) * 2f);
                    r1x = dist1 * moveProg;
                    r1rot = rotAngle * rotProg;
                    r2x = dist1 * moveProg + (dist2 - dist1) * extra;
                    r2rot = rotAngle * rotProg;
                    l1x = -dist1 * moveProg;
                    l1rot = rotAngle * rotProg;
                    l2x = -dist1 * moveProg - (dist2 - dist1) * extra;
                    l2rot = rotAngle * rotProg;

                    padAlpha = closingPadStartAlpha * Math.max(0f, 1f - Mathf.clamp(padFadeProgress));

                    // 正常完成（从 endScale 进入）则继续缩到 0；中途取消则保持当前缩放
                    boolean isNormalClose = Math.abs(closingPadStartScale - block.padEndScale) < 0.001f;
                    if (isNormalClose) {
                        padScale = closingPadStartScale * Math.max(0f, 1f - Mathf.clamp(animProgress));
                    } else {
                        padScale = closingPadStartScale;
                    }
                    padOffY = 0f;
                }

                default -> {
                    r1x = r1rot = r2x = r2rot = 0f;
                    l1x = l1rot = l2x = l2rot = 0f;
                    padAlpha = 0f;
                    padScale = startScale;
                    padOffY = 0f;
                }
            }

            // 绘制 pad
            if (padAlpha > 0.001f && block.pad != null && block.pad.found()) {
                Draw.color(1f, 1f, 1f, padAlpha);
                float pw = block.pad.width / 8f * padScale;
                float ph = block.pad.height / 8f * padScale;
                Draw.rect(block.pad, x, y + padOffY, pw, ph);
                Draw.color();
            }

            // 绘制四个部件
            if (block.right1 != null && block.right1.found())
                Draw.rect(block.right1, x + r1x, y, r1rot);
            if (block.right2 != null && block.right2.found())
                Draw.rect(block.right2, x + r2x, y, r2rot);
            if (block.left1 != null && block.left1.found())
                Draw.rect(block.left1, x + l1x, y, l1rot);
            if (block.left2 != null && block.left2.found())
                Draw.rect(block.left2, x + l2x, y, l2rot);
        }

        void registerConfig() {
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                s.setInjectorConfig(id, injectItem, defaultAmount);
                SatelliteManager.save();
            }
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            Satellite s = SatelliteManager.get(satelliteId);
            if (s != null) {
                s.removeInjectorConfig(id);
                SatelliteManager.save();
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(2f);
            Satellite s = SatelliteManager.get(satelliteId);
            if (s == null) {
                table.add("未绑定卫星").row();
                return;
            }
            table.add("[accent]注入配置[]").row();
            table.add("物品: ").style(Styles.outlineLabel).row();
            ItemSelection.buildTable(SatelliteInjector.this, table, SatelliteContentFilter.items(), () -> injectItem,
                    item -> {
                        injectItem = item;
                        configure(item);
                        rebuild(table);
                    }, selectionRows, selectionColumns);
            table.row();
            table.add("[gray]每次注入: " + defaultAmount + " 物品 / " + Satellite.injectInterval + " 秒[]")
                    .style(Styles.outlineLabel).row();
        }

        void rebuild(Table table) {
            table.clear();
            buildConfiguration(table);
        }

        @Override
        public void display(Table table) {
            Satellite s = SatelliteManager.get(satelliteId);
            super.display(table);
            table.row();
            table.row();
            table.add(new Bar(() -> "发射间隔",
                    () -> Pal.accent, () -> s.injectProgress()))
                    .growX().height(18f).row();
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value) {
            if (value instanceof Item) {
                injectItem = (Item) value;
            }
            registerConfig();
        }

        @Override
        public Object config() {
            return injectItem == null ? -1 : injectItem.id;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(0xCAFEBABE);
            write.b((byte) 3);
            write.i(satelliteId);
            write.i(injectItem == null ? -1 : injectItem.id);
            write.i(injectAmount);
            write.b((byte) state.ordinal());
            write.f(animProgress);
            write.f(padFadeProgress);
            write.f(armOpenProgress);
            write.f(intervalProgress);
            write.f(closingPadStartAlpha);
            write.f(closingPadStartScale);
            write.f(lastInjectTimer);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int magic = read.i();
            if (magic == 0xCAFEBABE) {
                byte version = read.b();
                satelliteId = read.i();
                int itemId = read.i();
                injectItem = itemId < 0 ? null : content.item(itemId);
                injectAmount = read.i();
                byte stateOrd = read.b();
                animProgress = read.f();

                if (version >= 2) {
                    padFadeProgress = read.f();
                    armOpenProgress = read.f();
                    intervalProgress = read.f();
                    closingPadStartAlpha = read.f();
                    if (version >= 3) {
                        closingPadStartScale = read.f();
                    } else {
                        SatelliteInjector sb = (SatelliteInjector) block;
                        closingPadStartScale = (stateOrd == State.INJECTING.ordinal()
                                || stateOrd == State.CLOSING.ordinal())
                                        ? sb.padEndScale
                                        : sb.padStartScale;
                    }
                    lastInjectTimer = read.f();
                } else {
                    lastInjectTimer = read.f();
                    padFadeProgress = 0f;
                    intervalProgress = 0f;
                    closingPadStartAlpha = 0f;
                    closingPadStartScale = ((SatelliteInjector) block).padStartScale;
                }

                State[] values = State.values();
                if (stateOrd >= 0 && stateOrd < values.length) {
                    state = values[stateOrd];
                } else {
                    state = State.IDLE;
                }

                if (version < 2) {
                    armOpenProgress = switch (state) {
                        case OPENING -> animProgress;
                        case WAITING, INJECTING -> 1f;
                        case CLOSING -> 1f;
                        default -> 0f;
                    };
                    if (state == State.CLOSING) {
                        padFadeProgress = 1f;
                        closingPadStartAlpha = 0f;
                    }
                }
            } else {
                satelliteId = magic;
                int itemId = read.i();
                injectItem = itemId < 0 ? null : content.item(itemId);
                injectAmount = read.i();
                state = State.IDLE;
                animProgress = 0f;
                lastInjectTimer = 0f;
                padFadeProgress = 0f;
                armOpenProgress = 0f;
                intervalProgress = 0f;
                closingPadStartAlpha = 0f;
                closingPadStartScale = ((SatelliteInjector) block).padStartScale;
            }

            injectAmount = defaultAmount;
            if (state == State.IDLE)
                resetToIdle();
        }
    }
}
