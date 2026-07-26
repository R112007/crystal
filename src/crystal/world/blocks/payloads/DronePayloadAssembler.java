package crystal.world.blocks.payloads;

import java.util.*;
import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.Image;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 无人机建筑载荷装配工厂。
 * 输入一种或多种建筑载荷（BuildPayload），并可搭配物品，由无人机在指定方向的虚线框中心建造出另一种建筑载荷。
 *
 * 设计参考：
 * - UnitAssembler：无人机建造、虚线框、建造区域占用检测。
 * - PayloadBlock：单载荷进出与绘制。
 *
 * 使用方式：
 * 1. 在 Block 定义中创建 DronePayloadAssembler 实例。
 * 2. 通过 plans.add(new AssemblerPlan(output, time, payloadReq, itemReq)) 添加配方。
 * 3. 游戏中点击方块选择要生产的建筑配方，输入对应载荷与物品，无人机即会开始建造。
 */
public class DronePayloadAssembler extends PayloadBlock {
    public TextureRegion sideRegion1;
    public TextureRegion sideRegion2;

    /** 建造区域边长（单位：格）。 */
    public int areaSize = 5;
    /** 建造无人机类型，必须带有 AssemblerAI 与 BuildingTetherComp。 */
    public UnitType droneType = UnitTypes.assemblyDrone;
    /** 同时存在的无人机数量。 */
    public int dronesCreated = 4;
    /** 生成一架无人机所需时间（帧）。 */
    public float droneConstructTime = 60f * 4f;
    /** 最大可接受的输入载荷尺寸（单位：格）。 */
    public float maxPayloadSize = 4f;

    /** 所有生产配方。 */
    public Seq<AssemblerPlan> plans = new Seq<>(4);
    public int[] capacities = {};

    public Sound createSound = Sounds.unitCreateBig;
    public float createSoundVolume = 1f;

    protected @Nullable ConsumePayloadDynamic consPayload;
    protected @Nullable ConsumeItemDynamic consItem;

    public DronePayloadAssembler(String name) {
        super(name);

        update = solid = true;
        rotate = true;
        rotateDraw = false;
        acceptsPayload = hasItems = true;
        hasPower = true;
        configurable = true;
        clearOnDoubleTap = true;
        regionRotated1 = 1;
        sync = true;
        group = BlockGroup.payloads;
        quickRotate = false;
        ambientSound = Sounds.loopUnitBuilding;
        ambientSoundVolume = 0.13f;

        configClear((DronePayloadAssemblerBuild tile) -> tile.recipe = null);

        config(Block.class, (DronePayloadAssemblerBuild tile, Block block) -> {
            AssemblerPlan plan = findPlan(block);
            if (plan != null) {
                if (tile.recipe != plan)
                    tile.progress = 0f;
                tile.recipe = plan;
            }
        });
    }

    @Override
    public void load() {
        super.load();
        sideRegion1 = Core.atlas.find(name + "-side1");
        sideRegion2 = Core.atlas.find(name + "-side2");
    }

    /** 获取指定方向上的建造区域矩形。 */
    public Rect getRect(Rect rect, float x, float y, int rotation) {
        rect.setCentered(x, y, areaSize * tilesize);
        float len = tilesize * (areaSize + size) / 2f;
        rect.x += Geometry.d4x(rotation) * len;
        rect.y += Geometry.d4y(rotation) * len;
        return rect;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);

        x *= tilesize;
        y *= tilesize;
        x += offset;
        y += offset;

        Drawf.dashRect(valid ? Pal.accent : Pal.remove, getRect(Tmp.r1, x, y, rotation));
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        // 防止同队多个建造区域重叠
        Rect rect = getRect(Tmp.r1, tile.worldx() + offset, tile.worldy() + offset, rotation).grow(0.1f);
        return !indexer.getFlagged(team, BlockFlag.unitAssembler)
                .contains(b -> b != tile.build && b.block instanceof DronePayloadAssembler assembler
                        && assembler.getRect(Tmp.r2, b.x, b.y, b.rotation).overlaps(rect));
    }

    @Override
    public void init() {
        updateClipRadius((areaSize + 1) * tilesize);

        consume(consPayload = new ConsumePayloadDynamic(
                (DronePayloadAssemblerBuild build) -> build.recipe != null ? build.recipe.payloadReq : Seq.with()));
        consume(consItem = new ConsumeItemDynamic(
                (DronePayloadAssemblerBuild build) -> build.recipe != null && build.recipe.itemReq != null
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

    /** 根据物品需求初始化容量与消耗倍率。 */
    public void initCapacities() {
        consumeBuilder.each(c -> c.multiplier = b -> state.rules.unitCost(b.team));

        itemCapacity = 10;
        capacities = new int[Vars.content.items().size];
        for (AssemblerPlan plan : plans) {
            if (plan.itemReq != null) {
                for (ItemStack stack : plan.itemReq) {
                    capacities[stack.item.id] = Math.max(capacities[stack.item.id], stack.amount * 2);
                    itemCapacity = Math.max(itemCapacity, stack.amount * 2);
                }
            }
        }
    }

    @Override
    public void checkContentArrayCapacity(int items, int liquids) {
        super.checkContentArrayCapacity(items, liquids);
        if (capacities.length != items)
            capacities = Arrays.copyOf(capacities, items);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("progress", (DronePayloadAssemblerBuild e) -> new Bar("bar.progress", Pal.ammo,
                () -> e.recipe == null ? 0f : Mathf.clamp(e.progress)));
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
    public TextureRegion[] icons() {
        return new TextureRegion[] { region, sideRegion1, topRegion };
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(plan.rotation >= 2 ? sideRegion2 : sideRegion1, plan.drawx(), plan.drawy(), plan.rotation * 90);
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

    public class DronePayloadAssemblerBuild extends PayloadBlockBuild<Payload> {
        protected IntSeq readUnits = new IntSeq();

        public @Nullable Vec2 commandPos;
        public Seq<Unit> units = new Seq<>();
        /** 输入载荷缓冲区，可累计多个载荷。 */
        public PayloadSeq blocks = new PayloadSeq();

        /** 当前选中的配方，null 表示未选择。 */
        public @Nullable AssemblerPlan recipe;
        /** 建造进度（0~1）。 */
        public float progress;
        public float warmup, droneWarmup, powerWarmup, invalidWarmup;
        public float droneProgress, totalDroneProgress;
        /** 建造区域是否被占用。 */
        public boolean wasOccupied = false;

        /** 获取输出载荷的生成中心。 */
        public Vec2 getPayloadSpawn() {
            float len = tilesize * (areaSize + size) / 2f;
            return Tmp.v4.set(x + Geometry.d4x(rotation) * len, y + Geometry.d4y(rotation) * len);
        }

        @Override
        public boolean shouldConsume() {
            return enabled && !wasOccupied && recipe != null && consPayload.efficiency(this) > 0
                    && consItem.efficiency(this) > 0;
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            if (payload == null || !payload.fits(maxPayloadSize) || recipe == null)
                return false;
            UnlockableContent content = payload.content();
            if (recipe != null) {
                PayloadStack stack = recipe.payloadReq.find(s -> s.item == content);
                return stack != null && blocks.get(content) < Mathf.round(stack.amount * state.rules.unitCost(team));
            }
            return isInputPayload(content);
        }

        @Override
        public void handlePayload(Building source, Payload payload) {
            blocks.add(payload.content(), 1);
            Fx.payloadDeposit.at(payload.x(), payload.y(), rotation);
        }

        @Override
        public @Nullable PayloadSeq getPayloads() {
            return blocks;
        }

        @Override
        public int getMaximumAccepted(Item item) {
            return Mathf.round(capacities[item.id] * state.rules.unitCost(team));
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return recipe != null && recipe.itemReq != null && items.get(item) < getMaximumAccepted(item) &&
                    Structs.contains(recipe.itemReq, stack -> stack.item == item);
        }

        @Override
        public void updateTile() {
            if (!readUnits.isEmpty()) {
                units.clear();
                readUnits.each(i -> {
                    var unit = Groups.unit.getByID(i);
                    if (unit != null)
                        units.add(unit);
                });
                readUnits.clear();
            }

            units.removeAll(u -> !u.isAdded() || u.dead || !(u.controller() instanceof AssemblerAI));

            if (!allowUpdate()) {
                progress = 0f;
                units.each(Unit::kill);
                units.clear();
            }

            float powerStatus = !enabled ? 0f : power == null ? 1f : power.status;
            powerWarmup = Mathf.lerpDelta(powerWarmup, powerStatus > 0.0001f ? 1f : 0f, 0.1f);
            droneWarmup = Mathf.lerpDelta(droneWarmup, units.size < dronesCreated ? powerStatus : 0f, 0.1f);
            totalDroneProgress += droneWarmup * delta();

            // 生成无人机（仅服务端/单机执行，客户端通过同步接收）
            if (!net.client() && units.size < dronesCreated && enabled && (droneProgress += delta()
                    * state.rules.unitBuildSpeed(team) * powerStatus / droneConstructTime) >= 1f) {
                var unit = droneType.create(team);
                if (unit.controller() instanceof AssemblerAI) {
                    if (unit instanceof BuildingTetherc bt) {
                        bt.building(this);
                    }
                    unit.set(x, y);
                    unit.rotation = 90f;
                    unit.add();
                    units.add(unit);
                    droneSpawned();
                } else {
                    droneProgress = 0f;
                }
            }

            if (units.size >= dronesCreated) {
                droneProgress = 0f;
            }

            Vec2 spawn = getPayloadSpawn();

            // 吸入落在方块上的载荷
            if (moveInPayload() && !wasOccupied && payload != null) {
                blocks.add(payload.content(), 1);
                Fx.payloadDeposit.at(payload.x(), payload.y(), rotation);
                payload = null;
            }

            // 安排无人机在建造区域四周就位
            for (int i = 0; i < units.size; i++) {
                var unit = units.get(i);
                var ai = (AssemblerAI) unit.controller();
                ai.targetPos.trns(i * 90f + 45f, areaSize / 2f * Mathf.sqrt2 * tilesize).add(spawn);
                ai.targetAngle = i * 90f + 45f + 180f;
            }

            wasOccupied = checkSolid(spawn);
            invalidWarmup = Mathf.lerpDelta(invalidWarmup, wasOccupied ? 1f : 0f, 0.1f);

            // 建造进度（仅服务端/单机执行放置）
            if (!net.client() && recipe != null && !wasOccupied && efficiency > 0) {
                warmup = Mathf.lerpDelta(warmup, efficiency, 0.1f);
                float eff = units.count(u -> ((AssemblerAI) u.controller()).inPosition()) / (float) dronesCreated;
                if ((progress += edelta() * state.rules.unitBuildSpeed(team) * eff / recipe.time) >= 1f) {
                    spawned();
                }
            } else {
                warmup = Mathf.lerpDelta(warmup, 0f, 0.1f);
            }
        }

        public void droneSpawned() {
            Fx.spawn.at(x, y);
            droneProgress = 0f;
        }

        /** 完成建造，在虚线框中心放置输出建筑。 */
        public void spawned() {
            if (recipe == null)
                return;
            Vec2 spawn = getPayloadSpawn();
            consume();

            Tile tile = world.tileWorld(spawn.x, spawn.y);
            if (tile != null && Build.validPlace(recipe.output, team, tile.x, tile.y, rotation, false)) {
                var payload = new BuildPayload(recipe.output, team);
                payload.set(spawn.x, spawn.y, rotdeg());
                payload.place(tile, rotation);
            }

            createSound.at(spawn.x, spawn.y, 1f + Mathf.range(0.06f), createSoundVolume);
            Fx.unitAssemble.at(spawn.x, spawn.y, rotdeg() - 90f, recipe.output);
            progress = 0f;
            blocks.clear();
        }

        /** 检测建造区域是否被占用。 */
        public boolean checkSolid(Vec2 v) {
            if (recipe == null)
                return true;
            Block output = recipe.output;
            int tx = World.toTile(v.x), ty = World.toTile(v.y);
            float hsize = output.size * tilesize * 1.4f;
            return !Build.validPlace(output, team, tx, ty, rotation, false) ||
                    Units.anyEntities(v.x - hsize / 2f, v.y - hsize / 2f, hsize, hsize, u -> !u.spawnedByCore);
        }

        @Override
        public void draw() {
            Draw.rect(region, x, y);

            // 绘制输入口
            for (int i = 0; i < 4; i++) {
                if (blends(i) && i != rotation) {
                    Draw.rect(inRegion, x, y, (i * 90) - 180);
                }
            }

            Draw.rect(rotation >= 2 ? sideRegion2 : sideRegion1, x, y, rotdeg());

            Draw.z(Layer.blockOver);
            payRotation = rotdeg();
            drawPayload();

            Draw.z(Layer.blockOver + 0.1f);
            Draw.rect(topRegion, x, y);

            if (isPayload())
                return;

            // 绘制无人机生成特效
            if (droneWarmup > 0.001f) {
                Draw.draw(Layer.blockOver + 0.2f, () -> {
                    Drawf.construct(this, droneType.fullIcon, Pal.accent, 0f, droneProgress, droneWarmup,
                            totalDroneProgress, 14f);
                });
            }

            Vec2 spawn = getPayloadSpawn();
            float sx = spawn.x, sy = spawn.y;

            if (recipe != null) {
                var plan = recipe;

                // 绘制建筑建造特效
                Draw.draw(Layer.blockBuilding, () -> {
                    Draw.color(Pal.accent, warmup);
                    for (TextureRegion region : plan.output.getGeneratedIcons()) {
                        Shaders.blockbuild.region = region;
                        Shaders.blockbuild.time = Time.time;
                        Shaders.blockbuild.alpha = warmup;
                        Shaders.blockbuild.progress = Mathf.clamp(progress + 0.05f);
                        Draw.rect(region, sx, sy, plan.output.rotate ? rotdeg() : 0);
                        Draw.flush();
                    }
                    Draw.color();
                    Shaders.blockbuild.alpha = 1f;
                });

                Draw.reset();
                Draw.z(Layer.buildBeam);

                // 绘制建筑剪影
                Draw.mixcol(Tmp.c1.set(Pal.accent).lerp(Pal.remove, invalidWarmup), 1f);
                Draw.alpha(Math.min(powerWarmup, 1f - invalidWarmup));
                Draw.rect(plan.output.fullIcon, sx, sy, rotdeg() - 90f);

                // 绘制无人机建造光束
                Draw.alpha(Math.min(1f - invalidWarmup, warmup));
                for (var unit : units) {
                    if (!((AssemblerAI) unit.controller()).inPosition())
                        continue;
                    float px = unit.x + Angles.trnsx(unit.rotation, unit.type.buildBeamOffset);
                    float py = unit.y + Angles.trnsy(unit.rotation, unit.type.buildBeamOffset);
                    Drawf.buildBeam(px, py, sx, sy, plan.output.size * tilesize / 2f);
                }

                Fill.square(sx, sy, plan.output.size * tilesize / 2f);
                Draw.reset();
            }

            // 绘制虚线框建造区域
            Draw.z(Layer.buildBeam);
            float fulls = areaSize * tilesize / 2f;
            Lines.stroke(2f, Pal.accent);
            Draw.alpha(powerWarmup);
            Drawf.dashRectBasic(spawn.x - fulls, spawn.y - fulls, fulls * 2f, fulls * 2f);
            Draw.reset();

            // 区域被占用时绘制红色小框
            if (recipe != null && invalidWarmup > 0) {
                float outSize = recipe.output.size * tilesize + 9f;
                Lines.stroke(2f, Tmp.c3.set(Pal.accent).lerp(Pal.remove, invalidWarmup).a(invalidWarmup));
                Drawf.dashSquareBasic(sx, sy, outSize);
            }

            Draw.reset();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Drawf.dashRect(Tmp.c1.set(Pal.accent).lerp(Pal.remove, invalidWarmup), getRect(Tmp.r1, x, y, rotation));
        }

        @Override
        public void buildConfiguration(Table table) {
            ItemSelection.buildTable(DronePayloadAssembler.this, table, plans.map(p -> p.output),
                    () -> recipe != null ? recipe.output : null, block -> {
                        configure(block);
                    });
        }

        @Override
        public Object config() {
            return recipe != null ? recipe.output : null;
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
                return recipe == null ? 0f : Mathf.clamp(progress);
            return super.sense(sensor);
        }

        @Override
        public double sense(Content content) {
            if (content instanceof Block b && recipe != null && recipe.output == b)
                return 1;
            return Float.NaN;
        }

        @Override
        public Vec2 getCommandPosition() {
            return commandPos;
        }

        @Override
        public void onCommand(Vec2 target) {
            commandPos = target;
        }

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(progress);
            write.s(recipe == null ? -1 : recipe.output.id);
            write.b(units.size);
            for (var unit : units) {
                write.i(unit.id);
            }
            blocks.write(write);
            TypeIO.writeVecNullable(write, commandPos);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            progress = read.f();
            short id = read.s();
            recipe = id == -1 ? null : findPlan(Vars.content.block(id));
            int count = read.b();
            readUnits.clear();
            for (int i = 0; i < count; i++) {
                readUnits.add(read.i());
            }
            blocks.read(read);
            if (revision >= 1) {
                commandPos = TypeIO.readVecNullable(read);
            }
        }

        int requiredAmount(UnlockableContent content) {
            if (recipe == null)
                return 0;
            PayloadStack stack = recipe.payloadReq.find(s -> s.item == content);
            return stack == null ? 0 : stack.amount;
        }
    }
}
