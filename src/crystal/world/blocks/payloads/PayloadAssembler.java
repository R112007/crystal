package crystal.world.blocks.payloads;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.Image;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 建筑载荷装配工厂。
 * 输入一种或多种建筑载荷（BuildPayload），并可搭配物品，生成另一种建筑载荷。
 *
 * 设计参考：
 * - PayloadBlock：提供单载荷进出的基础能力（moveOutPayload、payload 动画等）。
 * - UnitAssembler：使用 PayloadSeq 缓冲多个输入载荷，并通过 ConsumePayloadDynamic /
 * ConsumeItemDynamic 消费。
 * - BlockProducer：展示输出建筑时的建造特效。
 *
 * 使用方式：
 * 1. 在 Block 定义中创建 PayloadAssembler 实例。
 * 2. 通过 plans.add(new AssemblerPlan(output, time, payloadReq, itemReq)) 添加配方。
 * 3. 游戏中点击方块选择要生产的建筑配方，输入对应载荷与物品即可开始生产。
 */
public class PayloadAssembler extends PayloadBlock {

    /** 所有生产配方。 */
    public Seq<AssemblerPlan> plans = new Seq<>(4);
    /** 最大可接受的输入载荷尺寸（单位：格）。 */
    public float maxPayloadSize = 4f;
    /** 生产速度倍率，实际耗时 = plan.time / craftSpeed。 */
    public float craftSpeed = 1f;

    protected @Nullable ConsumePayloadDynamic consPayload;
    protected @Nullable ConsumeItemDynamic consItem;

    public PayloadAssembler(String name) {
        super(name);

        update = solid = true;
        rotate = true;
        acceptsPayload = true;
        outputsPayload = true;
        hasItems = true;
        hasPower = true;
        configurable = true;
        clearOnDoubleTap = true;
        group = BlockGroup.payloads;
        regionRotated1 = 1;

        // 配置清除：双击取消当前配方
        configClear((PayloadAssemblerBuild tile) -> tile.recipe = null);

        // 配置选择：玩家点击选择输出建筑
        config(Block.class, (PayloadAssemblerBuild tile, Block block) -> {
            AssemblerPlan plan = findPlan(block);
            if (plan != null) {
                if (tile.recipe != plan)
                    tile.progress = 0f;
                tile.recipe = plan;
            }
        });
    }

    @Override
    public void init() {
        // 动态消费：根据当前选中的配方消费输入载荷
        consume(consPayload = new ConsumePayloadDynamic(
                (PayloadAssemblerBuild build) -> build.recipe != null ? build.recipe.payloadReq : Seq.with()));
        // 动态消费：根据当前选中的配方消费输入物品（可选）
        consume(consItem = new ConsumeItemDynamic(
                (PayloadAssemblerBuild build) -> build.recipe != null && build.recipe.itemReq != null
                        ? build.recipe.itemReq
                        : ItemStack.empty));

        super.init();
        initCapacities();
    }

    @Override
    public void afterPatch() {
        super.afterPatch();
        initCapacities();
    }

    /** 根据物品需求初始化容量。 */
    public void initCapacities() {
        itemCapacity = 10;
        for (AssemblerPlan plan : plans) {
            if (plan.itemReq != null) {
                for (ItemStack stack : plan.itemReq) {
                    itemCapacity = Math.max(itemCapacity, stack.amount * 2);
                }
            }
        }
    }

    @Override
    public void setStats() {
        super.setStats();

        stats.add(Stat.output, table -> {
            table.row();
            for (var plan : plans) {
                table.table(Styles.grayPanel, t -> {
                    t.image(plan.output.uiIcon).scaling(Scaling.fit).size(40).pad(10f).left();
                    t.table(info -> {
                        info.defaults().left();
                        info.add(plan.output.localizedName);
                        info.row();
                        info.add(Strings.autoFixed(plan.time / 60f, 1) + " " + Core.bundle.get("unit.seconds"))
                                .color(Color.lightGray);
                    }).left();

                    t.table(req -> {
                        req.add().grow();

                        boolean hasItems = plan.itemReq != null && plan.itemReq.length > 0;
                        boolean hasPayloads = plan.payloadReq.size > 0;

                        if (hasItems || hasPayloads) {
                            req.table(solid -> {
                                int i = 0;
                                if (hasItems) {
                                    for (ItemStack stack : plan.itemReq) {
                                        if (i % 6 == 0)
                                            solid.row();
                                        solid.add(StatValues.stack(stack)).pad(5);
                                        i++;
                                    }
                                }
                                for (PayloadStack stack : plan.payloadReq) {
                                    if (i % 6 == 0)
                                        solid.row();
                                    solid.add(StatValues.stack(stack)).pad(5);
                                    i++;
                                }
                            }).right();
                        }
                    }).grow().pad(10f);
                }).growX().pad(5);
                table.row();
            }
        });
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("progress", (PayloadAssemblerBuild e) -> new Bar("bar.progress", Pal.ammo,
                () -> e.recipe == null ? 0f : Mathf.clamp(e.progress / e.recipe.time)));
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[] { region, outRegion, topRegion };
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(outRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
        Draw.rect(topRegion, plan.drawx(), plan.drawy());
    }

    public @Nullable AssemblerPlan findPlan(Block output) {
        return plans.find(p -> p.output == output);
    }

    /** 判断某个载荷内容是否被任一配方需要。 */
    public boolean isInputPayload(UnlockableContent content) {
        return plans.contains(p -> p.payloadReq.contains(s -> s.item == content));
    }

    /** 单个生产配方。 */
    public static class AssemblerPlan {
        public Block output;
        public float time;
        public Seq<PayloadStack> payloadReq = new Seq<>();
        public @Nullable ItemStack[] itemReq;

        public AssemblerPlan(Block output, float time, Seq<PayloadStack> payloadReq) {
            this(output, time, payloadReq, null);
        }

        public AssemblerPlan(Block output, float time, Seq<PayloadStack> payloadReq, @Nullable ItemStack[] itemReq) {
            this.output = output;
            this.time = time;
            this.payloadReq = payloadReq;
            this.itemReq = itemReq;
        }
    }

    public class PayloadAssemblerBuild extends PayloadBlockBuild<Payload> {
        /** 当前选中的配方，null 表示未选择。 */
        public @Nullable AssemblerPlan recipe;
        /** 生产进度（帧）。 */
        public float progress;
        /** 输入载荷缓冲区，可累计多个载荷。 */
        public PayloadSeq blocks = new PayloadSeq();
        public float time, heat;

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            // 已有输出载荷时不再接受输入；仅接受建筑/单位载荷，且尺寸符合要求
            if (this.payload != null || !payload.fits(maxPayloadSize) || recipe == null)
                return false;
            UnlockableContent content = payload.content();
            return recipe != null
                    ? recipe.payloadReq.contains(s -> s.item == content)
                            && blocks.get(content) < requiredAmount(content)
                    : isInputPayload(content);
        }

        @Override
        public void handlePayload(Building source, Payload payload) {
            blocks.add(payload.content(), 1);
            // 播放接收特效
            Fx.payloadDeposit.at(payload.x(), payload.y(), rotation);
        }

        @Override
        public @Nullable PayloadSeq getPayloads() {
            return blocks;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return recipe != null && recipe.itemReq != null
                    && items.get(item) < getMaximumAccepted(item)
                    && Structs.contains(recipe.itemReq, stack -> stack.item == item);
        }

        @Override
        public int getMaximumAccepted(Item item) {
            if (recipe == null || recipe.itemReq == null)
                return 0;
            for (ItemStack stack : recipe.itemReq) {
                if (stack.item == item)
                    return stack.amount * 2;
            }
            return 0;
        }

        @Override
        public boolean shouldConsume() {
            return super.shouldConsume() && recipe != null && this.payload == null;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            var recipe = this.recipe;
            boolean canProduce = recipe != null && efficiency > 0 && this.payload == null;

            if (canProduce) {
                progress += craftSpeed * edelta();

                if (progress >= recipe.time) {
                    consume();
                    this.payload = new BuildPayload(recipe.output, team);
                    ((BuildPayload) payload).build.block.placeEffect.at(x, y, payload.size() / tilesize);
                    payVector.setZero();
                    progress %= recipe.time;
                }
            }

            heat = Mathf.lerpDelta(heat, Mathf.num(canProduce), 0.15f);
            time += heat * delta();

            // 输出已完成的建筑载荷
            moveOutPayload();
        }

        @Override
        public void draw() {
            Draw.rect(region, x, y);
            Draw.rect(outRegion, x, y, rotdeg());

            // 绘制输入口
            for (int i = 0; i < 4; i++) {
                if (blends(i) && i != rotation) {
                    Draw.rect(inRegion, x, y, (i * 90) - 180);
                }
            }

            // 绘制当前配方建造特效
            if (recipe != null) {
                float prog = progress / recipe.time;
                Drawf.shadow(x, y, recipe.output.size * tilesize * 2f, prog);

                Draw.draw(Layer.blockBuilding, () -> {
                    Draw.color(Pal.accent);
                    for (TextureRegion region : recipe.output.getGeneratedIcons()) {
                        Shaders.blockbuild.region = region;
                        Shaders.blockbuild.time = time;
                        Shaders.blockbuild.progress = prog;
                        Draw.rect(region, x, y, recipe.output.rotate ? rotdeg() : 0);
                        Draw.flush();
                    }
                    Draw.color();
                });

                Draw.z(Layer.blockBuilding + 1);
                Draw.color(Pal.accent, heat);
                Lines.lineAngleCenter(x + Mathf.sin(time, 10f, tilesize / 2f * recipe.output.size + 1f), y, 90,
                        recipe.output.size * tilesize + 1f);
                Draw.reset();
            }

            // 绘制当前持有的载荷
            drawPayload();

            Draw.z(Layer.blockBuilding + 1.1f);
            Draw.rect(topRegion, x, y);
        }

        @Override
        public void buildConfiguration(Table table) {
            ItemSelection.buildTable(PayloadAssembler.this, table, plans.map(p -> p.output),
                    () -> recipe != null ? recipe.output : null, block -> {
                        configure(block);
                    });
        }

        @Override
        public Object config() {
            return recipe != null ? recipe.output : null;
        }

        @Override
        public void drawSelect() {
            if (recipe != null) {
                float dx = x - size * tilesize / 2f, dy = y + size * tilesize / 2f;
                TextureRegion icon = recipe.output.uiIcon;
                Draw.mixcol(Color.darkGray, 1f);
                Draw.rect(icon, dx - 0.7f, dy - 1f, Draw.scl * Draw.xscl * 24f, Draw.scl * Draw.yscl * 24f);
                Draw.reset();
                Draw.rect(icon, dx, dy, Draw.scl * Draw.xscl * 24f, Draw.scl * Draw.yscl * 24f);
            }
        }

        @Override
        public void display(Table table) {
            table.table(t -> {
                t.left();
                t.add(new Image(block.getDisplayIcon(tile))).scaling(Scaling.fit).size(iconMed);
                t.labelWrap(block.getDisplayName(tile)).left().width(190f).padLeft(5);
            }).growX().left();

            table.row();

            if (team == player.team()) {
                table.table(bars -> {
                    bars.defaults().growX().height(18f).pad(4);
                    displayBars(bars);
                }).growX();
                table.row();
                table.table(this::displayRequirements).growX();
                table.marginBottom(-5);
            }
        }

        public void displayRequirements(Table table) {
            table.left();

            AssemblerPlan[] last = { null };
            float[] payMult = { 1f }, itemMult = { 1f };

            Runnable rebuild = () -> {
                table.clearChildren();
                table.left();

                AssemblerPlan plan = recipe;
                last[0] = plan;
                payMult[0] = consPayload != null ? consPayload.multiplier.get(self()) : 1f;
                itemMult[0] = consItem != null ? consItem.multiplier.get(self()) : 1f;

                if (plan == null) {
                    table.add("@none").color(Color.lightGray);
                    return;
                }

                table.image().update(i -> {
                    i.setDrawable(plan.output.uiIcon);
                    i.setScaling(Scaling.fit);
                    i.setColor(Color.white);
                }).size(iconMed).scaling(Scaling.fit).padRight(4);
                table.label(() -> plan.output.localizedName).padRight(10).color(Color.lightGray);

                boolean hasReqs = false;

                if (plan.itemReq != null && plan.itemReq.length > 0) {
                    for (ItemStack stack : plan.itemReq) {
                        int amount = Mathf.round(stack.amount * itemMult[0]);
                        table.add(new ReqImage(StatValues.stack(stack.item, amount),
                                () -> items.get(stack.item) >= amount)).padRight(6);
                    }
                    hasReqs = true;
                }

                for (PayloadStack stack : plan.payloadReq) {
                    int amount = Mathf.round(stack.amount * payMult[0]);
                    table.add(new ReqImage(StatValues.stack(stack.item, amount),
                            () -> blocks.get(stack.item) >= amount)).padRight(6);
                    hasReqs = true;
                }

                if (!hasReqs) {
                    table.add("@none").color(Color.lightGray);
                }
            };

            table.update(() -> {
                if (last[0] != recipe)
                    rebuild.run();
            });

            rebuild.run();
        }

        @Override
        public double sense(LAccess sensor) {
            if (sensor == LAccess.progress)
                return recipe == null ? 0f : Mathf.clamp(progress / recipe.time);
            return super.sense(sensor);
        }

        @Override
        public double sense(Content content) {
            if (content instanceof Block b && recipe != null && recipe.output == b)
                return 1;
            return Float.NaN;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(progress);
            write.s(recipe == null ? -1 : recipe.output.id);
            blocks.write(write);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            progress = read.f();
            short id = read.s();
            recipe = id == -1 ? null : findPlan(Vars.content.block(id));
            blocks.read(read);
        }

        int requiredAmount(UnlockableContent content) {
            if (recipe == null)
                return 0;
            PayloadStack stack = recipe.payloadReq.find(s -> s.item == content);
            return stack == null ? 0 : stack.amount;
        }
    }
}
