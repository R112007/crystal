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
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import crystal.world.meta.CStat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;
import crystal.world.meta.CBuildVisibility;

/**
 * 地面物品接收台。
 *
 * 放置在星球区块中，向绑定到该区块的卫星请求指定物品。
 * 每隔一段时间从卫星库存转运一批物品到建筑自身库存。
 * 仿照 LandingPad 加入等待队列与着陆特效，防止同种物品多个接收台同时拉取，
 * 并在游戏重置时清空静态队列，避免旧状态污染新游戏。
 */
public class ItemReceivePad extends Block {

    /** 等待队列：按物品分组，用于协调同种物品的多个接收台。 */
    static ObjectMap<Item, Seq<ItemReceivePadBuild>> waiting = new ObjectMap<>();
    static long lastUpdateId = -1;

    static {
        Events.on(EventType.ResetEvent.class, e -> {
            waiting.clear();
            lastUpdateId = -1;
        });
    }

    /** 单次最多请求数量。 */
    public int maxRequestAmount = 100;
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

    public ItemReceivePad(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        hasItems = true;
        itemCapacity = 200;
        requirements(Category.effect, new ItemStack[] {
                new ItemStack(mindustry.content.Items.silicon, 80),
                new ItemStack(mindustry.content.Items.copper, 120),
                new ItemStack(mindustry.content.Items.lead, 80)
        });

        config(Item.class, (ItemReceivePadBuild build, Item item) -> build.requestItem = item);
        config(Integer.class, (ItemReceivePadBuild build,
                Integer value) -> build.requestAmount = Math.max(1, Math.min(maxRequestAmount, value)));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(CStat.requestAmount, maxRequestAmount, StatUnit.items);
        stats.add(CStat.transferInterval, requestInterval, StatUnit.seconds);
    }

    @Override
    public void setBars() {
        super.setBars();
        // 自己管理物品 Bar，避免与原 Block 默认 Bar 重复
        barMap.remove("items");
    }

    public class ItemReceivePadBuild extends Building {
        /** 请求物品 */
        public @Nullable Item requestItem = null;
        /** 每次请求数量 */
        public int requestAmount = 50;
        /** 请求计时器（秒） */
        public float requestTimer = 0f;

        /** 队列中选中了本建筑，但还没开始动画的待接收数量 */
        public int pendingAmount = 0;
        /** 来源卫星（待接收阶段） */
        public @Nullable Satellite pendingSatellite = null;

        /** 当前正在降落的物品 */
        public @Nullable Item arriving = null;
        /** 正在降落的数量 */
        public int arrivingAmount = 0;
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
            // 持续尝试把缓冲区的物品输出到相邻建筑/传送带
            dumpItems();

            // 每帧只处理一次等待队列，协调同种物品的多个接收台
            if (lastUpdateId != state.updateId) {
                lastUpdateId = state.updateId;
                waiting.each((item, pads) -> {
                    pads.removeAll(p -> p.requestItem != item || !p.isValid() || p.dead);
                    if (pads.size > 0) {
                        pads.sort(p -> p.priority);
                        ItemReceivePadBuild first = pads.first();
                        ItemReceivePadBuild head = pads.peek();
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
                    items.add(arriving, arrivingAmount);
                    if (arrivingSatellite != null) {
                        SatelliteSectorInfoManager.recordItemInject(state.rules.sector, arrivingSatellite, arriving,
                                arrivingAmount);
                    }
                    dump(arriving);
                    arriving = null;
                    arrivingTimer = 0f;
                    arrivingAmount = 0;
                    arrivingSatellite = null;
                    cooldown = 1f;
                }
            }

            // 冷却衰减
            if (cooldown > 0f && arriving == null) {
                cooldown -= delta() / cooldownTime;
                cooldown = Mathf.clamp(cooldown);
            }

            if (requestItem == null || arriving != null || pendingAmount > 0)
                return;
            requestTimer += Time.delta / 60f;
            if (requestTimer < requestInterval)
                return;
            requestTimer = 0f;

            Satellite s = findBoundSatellite();
            if (s == null)
                return;

            int space = itemCapacity - items.get(requestItem);
            int amount = Math.min(requestAmount, space);
            if (amount <= 0)
                return;

            int available = s.items.get(requestItem);
            int actual = Math.min(amount, available);
            if (actual <= 0)
                return;

            // 先记录待接收，等队列选中后再真正从卫星扣除并播放动画
            pendingSatellite = s;
            pendingAmount = actual;
            waiting.get(requestItem, Seq::new).add(this);
        }

        /** 被队列选中，开始播放降落动画。 */
        public void handleReceive() {
            if (pendingSatellite == null || pendingAmount <= 0)
                return;
            // 再次检查卫星库存，防止队列处理前被其他建筑取走
            int actual = Math.min(pendingAmount, pendingSatellite.items.get(requestItem));
            if (actual <= 0) {
                pendingSatellite = null;
                pendingAmount = 0;
                return;
            }
            pendingSatellite.items.remove(requestItem, actual);
            arriving = requestItem;
            arrivingAmount = actual;
            arrivingSatellite = pendingSatellite;
            arrivingTimer = 0f;
            landSound.at(x, y, 1f, landSoundVolume);
            pendingSatellite = null;
            pendingAmount = 0;
        }

        /** 尝试输出缓冲区中的所有物品。 */
        void dumpItems() {
            if (items.total() <= 0)
                return;
            for (Item item : content.items()) {
                if (items.get(item) > 0) {
                    dump(item);
                }
            }
        }

        /** 查找绑定到当前区块的卫星。 */
        Satellite findBoundSatellite() {
            if (state.rules.sector == null)
                return null;
            mindustry.type.Sector sector = state.rules.sector;
            for (Satellite s : SatelliteManager.satellites.values()) {
                if (s.planet == sector.planet && s.boundToSector && s.targetSectorId == sector.id) {
                    return s;
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
            table.add("[accent]物品接收配置[]").row();

            table.add("物品: ").style(Styles.outlineLabel).row();
            ItemSelection.buildTable(ItemReceivePad.this, table, content.items(), () -> requestItem, item -> {
                requestItem = item;
                configure(item);
                rebuild(table);
            }, selectionRows, selectionColumns);
            table.row();

            table.label(() -> "数量: " + requestAmount).style(Styles.outlineLabel).row();
            Table amountTable = new Table();
            amountTable.defaults().size(40f, 30f).pad(2f);
            amountTable.button("-10", Styles.flatt, () -> configureAmount(requestAmount - 10));
            amountTable.button("-1", Styles.flatt, () -> configureAmount(requestAmount - 1));
            amountTable.button("+1", Styles.flatt, () -> configureAmount(requestAmount + 1));
            amountTable.button("+10", Styles.flatt, () -> configureAmount(requestAmount + 10));
            table.add(amountTable).row();

            Table infoTable = new Table();
            infoTable.update(() -> {
                infoTable.clearChildren();
                Satellite s = findBoundSatellite();
                if (s == null) {
                    infoTable.add("[scarlet]当前区块未绑定卫星[]").style(Styles.outlineLabel).row();
                } else {
                    infoTable.add("绑定卫星: " + s.name).style(Styles.outlineLabel).row();
                    if (requestItem != null) {
                        infoTable.add("卫星库存: " + s.items.get(requestItem)).style(Styles.outlineLabel).row();
                    }
                }
            });
            table.add(infoTable).row();
        }

        void rebuild(Table table) {
            table.clear();
            buildConfiguration(table);
        }

        void configureAmount(int amount) {
            requestAmount = Math.max(1, Math.min(maxRequestAmount, amount));
            configure(requestAmount);
        }

        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            if (items == null)
                return;
            table.add(new Bar(() -> "物品容量: " + items.total() + "/" + itemCapacity,
                    () -> Pal.items, () -> items.total() / (float) itemCapacity))
                    .growX().height(18f).row();
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value) {
            if (value instanceof Item) {
                requestItem = (Item) value;
            } else if (value instanceof Integer) {
                requestAmount = Math.max(1, Math.min(maxRequestAmount, (Integer) value));
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
            write.i(requestItem == null ? -1 : requestItem.id);
            write.i(requestAmount);
            write.f(requestTimer);
            write.i(arriving == null ? -1 : arriving.id);
            write.i(arrivingAmount);
            write.i(arrivingSatellite == null ? -1 : arrivingSatellite.id);
            write.f(arrivingTimer);
            write.bool(pendingAmount > 0);
            write.i(pendingSatellite == null ? -1 : pendingSatellite.id);
            write.i(pendingAmount);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int itemId = read.i();
            requestItem = itemId < 0 ? null : content.item(itemId);
            requestAmount = read.i();
            requestTimer = read.f();
            int arrivingId = read.i();
            arriving = arrivingId < 0 ? null : content.item(arrivingId);
            arrivingAmount = read.i();
            int arrivingSatId = read.i();
            arrivingSatellite = (arrivingSatId >= 0) ? SatelliteManager.get(arrivingSatId) : null;
            arrivingTimer = read.f();
            boolean hasPending = read.bool();
            int pendingSatId = read.i();
            pendingAmount = read.i();
            pendingSatellite = (hasPending && pendingSatId >= 0) ? SatelliteManager.get(pendingSatId) : null;
        }
    }
}
