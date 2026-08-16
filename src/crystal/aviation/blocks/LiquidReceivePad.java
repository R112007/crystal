package crystal.aviation.blocks;

import arc.Events;
import arc.audio.Sound;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.aviation.SatelliteSectorInfoManager;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import crystal.world.meta.CStat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 地面液体接收台。
 *
 * 放置在星球区块中，向绑定到该区块的卫星请求指定液体。
 * 每隔一段时间从卫星液体库存转运一批液体到建筑自身液体缓存。
 * 仿照 LandingPad 加入等待队列与着陆特效，防止同种液体多个接收台同时拉取，
 * 并在游戏重置时清空静态队列，避免旧状态污染新游戏。
 */
public class LiquidReceivePad extends Block {

    /** 等待队列：按液体分组，用于协调同种液体的多个接收台。 */
    static ObjectMap<Liquid, Seq<LiquidReceivePadBuild>> waiting = new ObjectMap<>();
    static long lastUpdateId = -1;

    static {
        Events.on(EventType.ResetEvent.class, e -> {
            waiting.clear();
            lastUpdateId = -1;
        });
    }

    /** 单次最多请求液体数量。 */
    public float maxRequestAmount = 200f;
    /** 请求间隔（秒）。 */
    public float requestInterval = 3f;
    /** 降落动画时长（tick）。 */
    public float arrivalDuration = 150f;
    /** 冷却时长（tick）。 */
    public float cooldownTime = 150f;
    /** 着陆特效。 */
    public Effect landEffect = Fx.podLandShockwave;
    /** 着陆音效。 */
    public Sound landSound = Sounds.padLand;
    /** 着陆音效音量。 */
    public float landSoundVolume = 0.75f;

    public LiquidReceivePad(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasLiquids = true;
        liquidCapacity = 500f;
        requirements(Category.effect, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.metaglass, 100),
                new ItemStack(mindustry.content.Items.titanium, 60)
        });

        config(Liquid.class, (LiquidReceivePadBuild build, Liquid liquid) -> build.requestLiquid = liquid);
        config(Integer.class, (LiquidReceivePadBuild build,
                Integer value) -> build.requestAmount = Math.max(1f, Math.min(maxRequestAmount, value)));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.requestAmount, StatValues.number(maxRequestAmount, StatUnit.liquidUnits));
        stats.add(CStat.transferInterval, requestInterval, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理液体 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("liquid");
    }

    public class LiquidReceivePadBuild extends Building {
        /** 请求液体 */
        public @Nullable Liquid requestLiquid = null;
        /** 每次请求数量 */
        public float requestAmount = 100f;
        /** 请求计时器（秒） */
        public float requestTimer = 0f;

        /** 队列中选中了本建筑，但还没开始动画的待接收数量 */
        public float pendingAmount = 0f;
        /** 来源卫星（待接收阶段） */
        public @Nullable Satellite pendingSatellite = null;

        /** 当前正在降落的液体 */
        public @Nullable Liquid arriving = null;
        /** 正在降落的数量 */
        public float arrivingAmount = 0f;
        /** 来源卫星（动画阶段） */
        public @Nullable Satellite arrivingSatellite = null;
        /** 降落动画进度 */
        public float arrivingTimer = 0f;
        /** 着陆后冷却 */
        public float cooldown = 0f;
        /** 地面扬尘计时器 */
        public float landParticleTimer = 0f;
        /** 队列优先级 */
        public int priority = Mathf.rand.nextInt();

        @Override
        public void updateTile() {
            // 持续尝试把缓冲区的液体输出到相邻管道/建筑
            dumpLiquids();

            // 每帧只处理一次等待队列，协调同种液体的多个接收台
            if (lastUpdateId != state.updateId) {
                lastUpdateId = state.updateId;
                waiting.each((liquid, pads) -> {
                    pads.removeAll(p -> p.requestLiquid != liquid || !p.isValid() || p.dead);
                    if (pads.size > 0) {
                        pads.sort(p -> p.priority);
                        LiquidReceivePadBuild first = pads.first();
                        LiquidReceivePadBuild head = pads.peek();
                        first.handleReceive();
                        // 轮换优先级，让下一次轮到别的接收台
                        int tmp = first.priority;
                        first.priority = head.priority;
                        head.priority = tmp;
                        pads.clear();
                    }
                });
            }

            // 正在降落：播放扬尘并推进动画
            if (arriving != null) {
                if (!headless) {
                    float fin = arrivingTimer;
                    float tsize = Interp.pow5Out.apply(fin);
                    landParticleTimer += tsize * Time.delta / 2f;
                    if (landParticleTimer >= 1f) {
                        tile.getLinkedTiles(t -> {
                            if (Mathf.chance(0.1f)) {
                                Fx.podLandDust.at(t.worldx(), t.worldy(),
                                        angleTo(t.worldx(), t.worldy()) + Mathf.range(30f),
                                        Tmp.c1.set(t.floor().mapColor).mul(1.5f + Mathf.range(0.15f)));
                            }
                        });
                        landParticleTimer = 0f;
                    }
                }

                arrivingTimer += Time.delta / arrivalDuration;
                if (arrivingTimer >= 1f) {
                    // 着陆完成
                    landEffect.at(this);
                    Effect.shake(3f, 3f, this);
                    liquids.add(arriving, arrivingAmount);
                    if (arrivingSatellite != null) {
                        SatelliteSectorInfoManager.recordLiquidInject(state.rules.sector, arrivingSatellite, arriving,
                                arrivingAmount);
                    }
                    dumpLiquid(arriving);
                    arriving = null;
                    arrivingTimer = 0f;
                    arrivingAmount = 0f;
                    arrivingSatellite = null;
                    cooldown = 1f;
                }
            }

            // 冷却衰减
            if (cooldown > 0f && arriving == null) {
                cooldown -= delta() / cooldownTime;
                cooldown = Mathf.clamp(cooldown);
            }

            if (requestLiquid == null || arriving != null || pendingAmount > 0.001f)
                return;
            requestTimer += Time.delta / 60f;
            if (requestTimer < requestInterval)
                return;
            requestTimer = 0f;

            Satellite s = findBoundSatellite();
            if (s == null)
                return;

            float space = liquidCapacity - liquids.get(requestLiquid);
            float amount = Math.min(requestAmount, space);
            if (amount <= 0.001f)
                return;

            float available = s.getLiquid(requestLiquid);
            float actual = Math.min(amount, available);
            if (actual <= 0.001f)
                return;

            // 先记录待接收，等队列选中后再真正从卫星扣除并播放动画
            pendingSatellite = s;
            pendingAmount = actual;
            waiting.get(requestLiquid, Seq::new).add(this);
        }

        /** 被队列选中，开始播放降落动画。 */
        public void handleReceive() {
            if (pendingSatellite == null || pendingAmount <= 0.001f)
                return;
            // 再次检查卫星库存，防止队列处理前被其他建筑取走
            float actual = Math.min(pendingAmount, pendingSatellite.getLiquid(requestLiquid));
            if (actual <= 0.001f) {
                pendingSatellite = null;
                pendingAmount = 0f;
                return;
            }
            pendingSatellite.removeLiquid(requestLiquid, actual);
            arriving = requestLiquid;
            arrivingAmount = actual;
            arrivingSatellite = pendingSatellite;
            arrivingTimer = 0f;
            landSound.at(x, y, 1f, landSoundVolume);
            pendingSatellite = null;
            pendingAmount = 0f;
        }

        /** 尝试输出缓冲区中的所有液体。 */
        void dumpLiquids() {
            for (Liquid liquid : content.liquids()) {
                if (liquids.get(liquid) > 0.001f) {
                    dumpLiquid(liquid);
                }
            }
        }

        /** 查找绑定到当前区块的卫星。 */
        Satellite findBoundSatellite() {
            if (state.rules.sector == null)
                return null;
            mindustry.type.Sector sector = state.rules.sector;
            for (Satellite sat : SatelliteManager.satellites.values()) {
                if (sat.planet == sector.planet && sat.boundToSector && sat.targetSectorId == sector.id) {
                    return sat;
                }
            }
            return null;
        }

        @Override
        public void draw() {
            super.draw();
            if (arriving != null) {
                float fin = Mathf.clamp(arrivingTimer), fout = 1f - fin;
                float alpha = Interp.pow5Out.apply(fin);
                float scale = (1f - alpha) * 1.3f + 1f;
                float cx = x;
                float cy = y + Interp.pow4In.apply(fout) * (50f + Mathf.randomSeedRange(id() + 2, 15f));
                float rotation = fout * (90f + Mathf.randomSeedRange(id(), 50f));

                Draw.z(Layer.effect + 0.001f);
                Draw.color(Pal.engine);
                float rad = 0.15f + Interp.pow5Out.apply(Mathf.slope(fin));
                Fill.light(cx, cy, 10, 15f * (rad + scale - 1f), Tmp.c2.set(Pal.engine).a(alpha),
                        Tmp.c1.set(Pal.engine).a(0f));
                Draw.alpha(alpha);
                for (int i = 0; i < 4; i++) {
                    Drawf.tri(cx, cy, 4f, 25f * (rad + scale - 1f), i * 90f + rotation);
                }

                Draw.color();
                if (arriving.fullIcon != null) {
                    Draw.z(Layer.flyingUnit + 1);
                    float s = 8f * scale;
                    Draw.rect(arriving.fullIcon, cx, cy, s, s, rotation);
                }
                Draw.reset();
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().pad(2f);
            table.add("[accent]液体接收配置[]").row();
            Table selectorTable = new Table();
            table.add("液体: ").style(Styles.outlineLabel).row();
            ItemSelection.buildTable(LiquidReceivePad.this, selectorTable, content.liquids(), () -> requestLiquid,
                    liquid -> {
                        requestLiquid = liquid;
                        configure(liquid);
                        rebuild(table);
                    }, selectionRows, selectionColumns);
            table.add(selectorTable).size(200, 50).top().left();

            table.label(() -> "数量: " + (int) requestAmount).style(Styles.outlineLabel).row();
            Table amountTable = new Table();
            amountTable.defaults().size(40f, 30f).pad(2f);
            amountTable.button("-50", Styles.flatt, () -> configureAmount(requestAmount - 50f));
            amountTable.button("-10", Styles.flatt, () -> configureAmount(requestAmount - 10f));
            amountTable.button("+10", Styles.flatt, () -> configureAmount(requestAmount + 10f));
            amountTable.button("+50", Styles.flatt, () -> configureAmount(requestAmount + 50f));
            table.add(amountTable).row();

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = findBoundSatellite();
                if (s == null) {
                    infoTable.add("[scarlet]当前区块未绑定卫星[]").style(Styles.outlineLabel).row();
                } else {
                    infoTable.add("绑定卫星: " + s.name).style(Styles.outlineLabel).row();
                    if (requestLiquid != null) {
                        infoTable.add("卫星库存: " + Strings.fixed(s.getLiquid(requestLiquid), 1))
                                .style(Styles.outlineLabel).row();
                    }
                }
            });
            table.add(infoTable).row();
        }

        void rebuild(Table table) {
            table.clear();
            buildConfiguration(table);
        }

        void configureAmount(float amount) {
            requestAmount = Math.max(1f, Math.min(maxRequestAmount, amount));
            configure((int) requestAmount);
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            if (requestLiquid == null)
                return;
            table.add(
                    new Bar(() -> "液体容量: " + Strings.fixed(liquids.get(requestLiquid), 1) + "/" + (int) liquidCapacity,
                            () -> requestLiquid.color, () -> liquids.get(requestLiquid) / liquidCapacity))
                    .growX().height(18f).row();
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value) {
            if (value instanceof Liquid) {
                requestLiquid = (Liquid) value;
            } else if (value instanceof Integer) {
                requestAmount = Math.max(1f, Math.min(maxRequestAmount, (Integer) value));
            }
        }

        @Override
        public Object config() {
            // 多配置项不通过单一 config() 同步；持久化由 write/read 处理
            return null;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(requestLiquid == null ? -1 : requestLiquid.id);
            write.f(requestAmount);
            write.f(requestTimer);
            write.i(arriving == null ? -1 : arriving.id);
            write.f(arrivingAmount);
            write.i(arrivingSatellite == null ? -1 : arrivingSatellite.id);
            write.f(arrivingTimer);
            write.bool(pendingAmount > 0.001f);
            write.i(pendingSatellite == null ? -1 : pendingSatellite.id);
            write.f(pendingAmount);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int liquidId = read.i();
            requestLiquid = liquidId < 0 ? null : content.liquid(liquidId);
            requestAmount = read.f();
            requestTimer = read.f();
            int arrivingId = read.i();
            arriving = arrivingId < 0 ? null : content.liquid(arrivingId);
            arrivingAmount = read.f();
            int arrivingSatId = read.i();
            arrivingSatellite = (arrivingSatId >= 0) ? SatelliteManager.get(arrivingSatId) : null;
            arrivingTimer = read.f();
            boolean hasPending = read.bool();
            int pendingSatId = read.i();
            pendingAmount = read.f();
            pendingSatellite = (hasPending && pendingSatId >= 0) ? SatelliteManager.get(pendingSatId) : null;
        }
    }
}
