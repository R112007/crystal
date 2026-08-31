package crystal.world.blocks.unit;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.graphics.CDrawf;
import mindustry.ai.UnitCommand;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Building;
import mindustry.gen.BuildingTetherc;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.gen.Segmentc;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.graphics.Shaders;
import mindustry.io.TypeIO;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.blocks.units.UnitBlock;
import mindustry.world.consumers.ConsumeItemDynamic;
import mindustry.world.consumers.ConsumeLiquidsDynamic;
import mindustry.world.meta.Stat;

import static mindustry.Vars.*;

public class MoveUpgradeFactory extends UnitBlock {

    public UnitType droneType = UnitTypes.assemblyDrone;
    public Seq<Upgrade> upgrades = new Seq<>();

    public float range = 50f * tilesize;
    public int droneCount = 4;
    public float droneConstructTime = 60f * 2f;
    public Sound createSound = Sounds.unitCreate;
    public float createSoundVolume = 1f;

    public MoveUpgradeFactory(String name) {
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = true;
        configurable = true;
        clearOnDoubleTap = true;
        outputsPayload = true;
        commandable = true;
        ambientSound = Sounds.loopUnitBuilding;
        ambientSoundVolume = 0.09f;

        config(Integer.class, (MoveUpgradeFactoryBuild build, Integer i) -> {
            if (build.currentPlan == i)
                return;
            build.currentPlan = i < 0 || i >= upgrades.size ? -1 : i;
            build.progress = 0f;
            build.target = null;
        });
    }

    public static class Upgrade {
        public UnitType in;
        public UnitType out;
        public float constructTime = 60f * 2f;
        public ItemStack[] items = ItemStack.empty;
        public LiquidStack[] liquids = new LiquidStack[0];
        public float power = 0f;

        public Upgrade() {
        }

        public Upgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items, LiquidStack[] liquids) {
            this(in, out, constructTime, items, liquids, 0f);
        }

        public Upgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items, LiquidStack[] liquids,
                float power) {
            this.in = in;
            this.out = out;
            this.constructTime = constructTime;
            this.items = items;
            this.liquids = liquids;
            this.power = power;
        }
    }

    public void addUpgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items) {
        addUpgrade(in, out, constructTime, items, new LiquidStack[0], 0f);
    }

    public void addUpgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items, LiquidStack[] liquids) {
        addUpgrade(in, out, constructTime, items, liquids, 0f);
    }

    public void addUpgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items, float power) {
        addUpgrade(in, out, constructTime, items, null, power);
    }

    public void addUpgrade(UnitType in, UnitType out, float constructTime, ItemStack[] items, LiquidStack[] liquids,
            float power) {
        upgrades.add(new Upgrade(in, out, constructTime, items, liquids, power));
    }

    @Override
    public void init() {
        itemCapacity = 10;
        liquidCapacity = 10f;

        updateClipRadius(range + 80f);

        for (int i = 0; i < upgrades.size; i++) {
            Upgrade up = upgrades.get(i);
            if (up.items != null) {
                for (int j = 0; j < up.items.length; j++) {
                    itemCapacity = Math.max(itemCapacity, up.items[j].amount * 2);
                }
            }
            if (up.liquids != null) {
                for (int j = 0; j < up.liquids.length; j++) {
                    liquidCapacity = Math.max(liquidCapacity, up.liquids[j].amount * 2f);
                }
            }
        }

        consume(new ConsumeItemDynamic((MoveUpgradeFactoryBuild e) -> {
            if (e.currentPlan < 0 || e.currentPlan >= upgrades.size)
                return ItemStack.empty;
            ItemStack[] req = upgrades.get(e.currentPlan).items;
            return req == null ? ItemStack.empty : req;
        }));

        consume(new ConsumeLiquidsDynamic((MoveUpgradeFactoryBuild e) -> {
            if (e.currentPlan < 0 || e.currentPlan >= upgrades.size)
                return new LiquidStack[0];
            LiquidStack[] req = upgrades.get(e.currentPlan).liquids;
            return req == null ? new LiquidStack[0] : req;
        }));

        float maxPower = 0f;
        for (int i = 0; i < upgrades.size; i++) {
            maxPower = Math.max(maxPower, upgrades.get(i).power);
        }
        if (maxPower > 0f) {
            consumePower(maxPower);
        }

        super.init();

        for (int i = 0; i < upgrades.size; i++) {
            Upgrade up = upgrades.get(i);
            if (up.liquids != null) {
                for (int j = 0; j < up.liquids.length; j++) {
                    liquidFilter[up.liquids[j].liquid.id] = true;
                }
            }
        }
    }

    @Override
    public void afterPatch() {
        super.afterPatch();
        init();
    }

    @Override
    public void setStats() {
        stats.timePeriod = 60f;
        super.setStats();

        stats.add(Stat.output, table -> {
            table.row();

            for (int i = 0; i < upgrades.size; i++) {
                Upgrade up = upgrades.get(i);
                if (up.in.unlockedNow() && up.out.unlockedNow()) {
                    table.table(Styles.grayPanel, t -> {
                        t.left();

                        t.image(up.in.uiIcon).size(40).pad(10f).scaling(Scaling.fit);
                        t.image(Icon.right).color(Pal.darkishGray).size(40).pad(10f);
                        t.image(up.out.uiIcon).size(40).pad(10f).scaling(Scaling.fit);

                        // 修复：物品/液体展示改成 UnitFactory 紧凑风格
                        t.table(info -> {
                            info.add(up.in.localizedName + " -> " + up.out.localizedName).left();
                            info.row();
                            info.add(Strings.autoFixed(up.constructTime / 60f, 1) + " "
                                    + Core.bundle.get("unit.seconds")).color(Color.lightGray);

                            if (up.items != null && up.items.length > 0) {
                                info.row();
                                info.table(req -> {
                                    req.left();
                                    for (int j = 0; j < up.items.length; j++) {
                                        if (j > 0)
                                            req.add(", ").left();
                                        req.image(up.items[j].item.uiIcon).size(18).padRight(2);
                                        req.add(String.valueOf(up.items[j].amount)).left();
                                    }
                                }).padTop(2).left();
                            }
                            if (up.liquids != null && up.liquids.length > 0) {
                                info.row();
                                info.table(req -> {
                                    req.left();
                                    for (int j = 0; j < up.liquids.length; j++) {
                                        if (j > 0)
                                            req.add(", ").left();
                                        req.image(up.liquids[j].liquid.uiIcon).size(18).padRight(2);
                                        req.add(Strings.autoFixed(up.liquids[j].amount * 60f, 1) + "/s").left();
                                    }
                                }).padTop(2).left();
                            }
                            if (up.power > 0) {
                                info.row();
                                info.add("[lightgray]Power:[] " + Strings.autoFixed(up.power * 60, 2) + "/s")
                                        .color(Color.lightGray);
                            }
                        }).pad(10).left();
                    }).growX().pad(5).row();
                }
            }
        });
    }

    @Override
    public void setBars() {
        super.setBars();

        addBar("progress", (MoveUpgradeFactoryBuild e) -> new Bar("bar.progress", Pal.ammo, e::fraction));
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[] { region, topRegion };
    }

    public class MoveUpgradeFactoryBuild extends UnitBuild {
        public @Nullable UnitCommand command;
        public int currentPlan = -1;
        public Seq<Unit> drones = new Seq<>();
        public @Nullable Unit target;
        public float droneProgress;
        public float droneWarmup;
        public float totalDroneProgress;
        public float warmup;

        public float retargetTimer;

        protected IntSeq readTarget = new IntSeq();
        protected IntSeq readDrones = new IntSeq();

        public float fraction() {
            if (currentPlan < 0 || currentPlan >= upgrades.size)
                return 0f;
            return progress / upgrades.get(currentPlan).constructTime;
        }

        @Override
        public void display(Table table) {
            super.display(table);

            if (team != player.team())
                return;

            table.row();
            table.table(t -> {
                t.left().defaults().left();

                if (currentPlan >= 0 && currentPlan < upgrades.size) {
                    Upgrade up = upgrades.get(currentPlan);

                    t.table(header -> {
                        header.left();
                        header.image(up.in.uiIcon).size(iconMed).padRight(4);
                        header.add("[accent] -> []").padRight(4);
                        header.image(up.out.uiIcon).size(iconMed);
                    }).left().row();

                    if (up.items != null && up.items.length > 0) {
                        t.table(req -> {
                            req.left().defaults().left();
                            req.add("[lightgray]Items:[] ").padRight(4);
                            for (ItemStack stack : up.items) {
                                req.image(stack.item.uiIcon).size(18).padRight(2);
                                req.add(items.get(stack.item) + "/" + stack.amount).color(Color.lightGray).padRight(8);
                            }
                        }).left().padTop(2).row();
                    }

                    if (up.liquids != null && up.liquids.length > 0) {
                        t.table(req -> {
                            req.left().defaults().left();
                            req.add("[lightgray]Liquids:[] ").padRight(4);
                            for (LiquidStack stack : up.liquids) {
                                req.image(stack.liquid.uiIcon).size(18).padRight(2);
                                req.add(Strings.autoFixed(liquids.get(stack.liquid), 1) + "/"
                                        + Strings.autoFixed(stack.amount, 1)).color(Color.lightGray).padRight(8);
                            }
                        }).left().padTop(2).row();
                    }

                    if (up.power > 0) {
                        t.add("[lightgray]Power:[] " + Strings.autoFixed(up.power * 60, 2) + "/s")
                                .color(Color.lightGray).padTop(2).row();
                    }

                    t.row();
                    t.table(barTable -> {
                        barTable.left();
                        barTable.add(new Bar(
                                () -> {
                                    if (up.out == null)
                                        return "[lightgray]" + Iconc.cancel;
                                    String unicode = Fonts.getUnicodeStr(up.out.name);
                                    int count = team.data().countType(up.out);
                                    String cap = up.out.useUnitCap ? Units.getStringCap(team) : "∞";
                                    return Core.bundle.format("bar.unitcap", unicode, count, cap);
                                },
                                () -> Pal.power,
                                () -> {
                                    if (up.out == null || !up.out.useUnitCap)
                                        return 1f;
                                    int cap = Units.getCap(team);
                                    return cap <= 0 ? 0f : (float) team.data().countType(up.out) / cap;
                                })).growX().height(18f);
                    }).growX().padTop(4);

                } else {
                    t.add("[lightgray]No upgrade selected");
                }
            }).pad(4).left();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Drawf.dashCircle(x, y, range, Pal.accent);
            if (target != null && target.isAdded() && !target.dead) {
                Drawf.line(Pal.accent, x, y, target.x, target.y);
            }
            if (currentPlan >= 0 && currentPlan < upgrades.size) {
                drawItemSelection(upgrades.get(currentPlan).in);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            if (upgrades.isEmpty()) {
                deselect();
                return;
            }

            table.background(Styles.black6);

            ButtonGroup<Button> group = new ButtonGroup<>();
            group.setMinCheckCount(0);

            for (int i = 0; i < upgrades.size; i++) {
                final int idx = i;
                Upgrade up = upgrades.get(i);

                Button button = table.button(b -> {
                    b.table(Styles.grayPanel, in -> {
                        in.left();
                        in.image(up.in.uiIcon).size(40).pad(10f).left().scaling(Scaling.fit);
                        in.table(info -> {
                            info.add(up.in.localizedName).left();
                        }).pad(10).left();
                    }).fill().padTop(5).padBottom(5);

                    // 箭头（中）
                    b.table(Styles.grayPanel, arrow -> {
                        arrow.image(Icon.right).color(Pal.darkishGray).size(40).pad(10f);
                    }).fill().padTop(5).padBottom(5);

                    // out 单位（右）
                    b.table(Styles.grayPanel, out -> {
                        out.left();
                        out.image(up.out.uiIcon).size(40).pad(10f).right().scaling(Scaling.fit);
                        out.table(info -> {
                            info.add(up.out.localizedName).right();
                        }).pad(10).right();
                    }).fill().padTop(5).padBottom(5);
                }, Styles.clearTogglei, () -> {
                    if (currentPlan == idx) {
                        configure(-1);
                    } else {
                        configure(idx);
                    }
                    deselect();
                }).group(group).pad(4).growX().get();

                button.update(() -> button.setChecked(currentPlan == idx));

                table.row();
            }
        }

        @Override
        public Object config() {
            return currentPlan;
        }

        public Unit findTarget() {
            if (currentPlan < 0 || currentPlan >= upgrades.size)
                return null;
            Upgrade up = upgrades.get(currentPlan);
            if (up.in == null)
                return null;
            return Units.closest(team, x, y, range, u -> u.type == up.in && u.isAdded() && !u.dead);
        }

        public int countNearbyDrones() {
            if (target == null)
                return 0;
            int count = 0;
            float radius = target.hitSize * 2 * 1.5f + tilesize;
            for (Unit drone : drones) {
                if (drone.within(target, radius))
                    count++;
            }
            return count;
        }

        public boolean canCreateOutUnit() {
            if (currentPlan < 0 || currentPlan >= upgrades.size)
                return false;
            return Units.canCreate(team, upgrades.get(currentPlan).out);
        }

        @Override
        public void updateTile() {
            if (!readTarget.isEmpty()) {
                readTarget.each(id -> {
                    Unit u = Groups.unit.getByID(id);
                    if (u != null)
                        target = u;
                });
                readTarget.clear();
            }

            if (!readDrones.isEmpty()) {
                readDrones.each(id -> {
                    Unit u = Groups.unit.getByID(id);
                    if (u != null)
                        drones.add(u);
                });
                readDrones.clear();
            }

            drones.removeAll(u -> u == null || !u.isAdded() || u.dead);

            if (target != null && (!target.isAdded() || target.dead)) {
                target = null;
                progress = 0f;
            }

            if (target != null && !canCreateOutUnit()) {
                target = null;
                progress = 0f;
            }

            if (target == null && currentPlan >= 0 && enabled && canCreateOutUnit()) {
                target = findTarget();
            }

            if (currentPlan >= 0 && drones.size < droneCount && enabled) {
                droneProgress += delta() / droneConstructTime;
                droneWarmup = Mathf.lerpDelta(droneWarmup, 1f, 0.1f);
                totalDroneProgress += droneWarmup * delta();
                if (droneProgress >= 1f) {
                    Unit drone = droneType.create(team);
                    drone.set(x, y);
                    drone.rotation = 90f;
                    if (drone instanceof BuildingTetherc) {
                        ((BuildingTetherc) drone).building(this);
                    }
                    drone.add();
                    drones.add(drone);
                    droneProgress = 0f;
                    Fx.spawn.at(x, y);
                }
            } else {
                droneWarmup = Mathf.lerpDelta(droneWarmup, 0f, 0.1f);
                droneProgress = 0f;
            }

            if (target != null && currentPlan >= 0 && currentPlan < upgrades.size) {
                Upgrade up = upgrades.get(currentPlan);
                int nearby = countNearbyDrones();
                boolean ready = nearby > 0 && efficiency > 0 && canCreateOutUnit();

                if (ready) {
                    float speedMult = nearby / (float) droneCount;
                    time += edelta() * speedMult * state.rules.unitBuildSpeed(team);
                    progress += edelta() * speedMult * state.rules.unitBuildSpeed(team);
                    speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
                    warmup = Mathf.lerpDelta(warmup, 1f, 0.1f);

                    if (progress >= up.constructTime) {
                        completeUpgrade(up);
                    }
                } else {
                    speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
                    warmup = Mathf.lerpDelta(warmup, 0f, 0.1f);
                }
            } else {
                speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
                warmup = Mathf.lerpDelta(warmup, 0f, 0.1f);
            }

            if (target != null && drones.size > 0) {
                for (int i = 0; i < drones.size; i++) {
                    Unit drone = drones.get(i);
                    float angle = Time.time * 0.3f + (i * 360f / drones.size);
                    float radius = target.hitSize * 2 * 1.5f;
                    float tx = target.x + Angles.trnsx(angle, radius);
                    float ty = target.y + Angles.trnsy(angle, radius);

                    moveDroneSmooth(drone, tx, ty, droneType.speed, true);
                }
            } else if (drones.size > 0) {
                float orbitRadius = size * tilesize / 2f + 16f;
                for (int i = 0; i < drones.size; i++) {
                    Unit drone = drones.get(i);
                    float angle = Time.time * 0.3f + (i * 360f / drones.size);
                    float tx = x + Angles.trnsx(angle, orbitRadius);
                    float ty = y + Angles.trnsy(angle, orbitRadius);

                    moveDroneSmooth(drone, tx, ty, droneType.speed * 0.5f, false);
                }
            }

            if (currentPlan >= 0 && currentPlan < upgrades.size) {
                progress = Mathf.clamp(progress, 0f, upgrades.get(currentPlan).constructTime);
            }
        }

        private void moveDroneSmooth(Unit drone, float tx, float ty, float maxSpeed, boolean lookAtTarget) {
            float angleToTarget = Angles.angle(drone.x, drone.y, tx, ty);
            float dist = Mathf.dst(drone.x, drone.y, tx, ty);
            float speed = Math.min(dist * 0.08f, maxSpeed);

            float targetVx = Angles.trnsx(angleToTarget, speed);
            float targetVy = Angles.trnsy(angleToTarget, speed);

            drone.vel.x = Mathf.lerpDelta(drone.vel.x, targetVx, 0.15f);
            drone.vel.y = Mathf.lerpDelta(drone.vel.y, targetVy, 0.15f);

            if (lookAtTarget) {
                drone.rotation = drone.angleTo(target);
            } else {
                drone.rotation = Angles.angle(drone.x, drone.y, tx, ty);
            }
        }

        public void completeUpgrade(Upgrade up) {
            if (target == null)
                return;
            if (!Units.canCreate(team, up.out))
                return;

            boolean wasPlayer = target.isPlayer();
            Player player = wasPlayer ? (Player) target.controller() : null;

            Unit out = up.out.create(team);
            out.set(target.x, target.y);
            out.rotation = target.rotation;
            out.vel.set(target.vel);

            float healthPercent = target.health / target.type.health;
            out.health = out.type.health * healthPercent;

            out.add();

            if (wasPlayer && player != null) {
                player.unit(out);
            }

            createSound.at(target.x, target.y, 1f + Mathf.range(0.06f), createSoundVolume);
            Effect.shake(2f, 3f, target);
            Fx.producesmoke.at(target.x, target.y);
            Fx.unitAssemble.at(target.x, target.y, target.rotation - 90f, up.out);

            target.remove();
            target = null;

            consume();
            Events.fire(new UnitCreateEvent(out, this));

            progress = 0f;
        }

        @Override
        public void draw() {
            super.draw();

            if (droneWarmup > 0.001f) {
                Draw.draw(Layer.blockOver + 0.2f, () -> {
                    Drawf.construct(this, droneType.fullIcon, Pal.accent, 0f, droneProgress, droneWarmup,
                            totalDroneProgress, 14f);
                });
            }

            if (target != null && currentPlan >= 0 && currentPlan < upgrades.size && progress > 0) {
                Upgrade up = upgrades.get(currentPlan);
                float frac = progress / up.constructTime;
                float outAlpha = Mathf.clamp(frac * frac * (3f - 2f * frac));
                float beamRadius = target.hitSize;
                float a = Mathf.clamp(frac);
                for (int i = 0; i < drones.size; i++) {
                    Unit drone = drones.get(i);
                    float px = drone.x + Angles.trnsx(drone.rotation, drone.type.buildBeamOffset);
                    float py = drone.y + Angles.trnsy(drone.rotation, drone.type.buildBeamOffset);

                    Draw.z(Layer.buildBeam);
                    Draw.color(team.color, Pal.accent, frac);
                    CDrawf.buildBeam(px, py, target.x, target.y, beamRadius);
                    Draw.reset();
                }
                Draw.reset();
                float layer = getUnitLayer(target);
                Draw.z(layer + 1);
                Draw.alpha(1f - a);
                Draw.rect(up.in.fullIcon, target.x, target.y, target.rotation - 90f);
                Draw.reset();

                Draw.z(layer + 2);
                Draw.alpha(a);
                Draw.rect(up.out.fullIcon, target.x, target.y, target.rotation - 90f);
                Draw.reset();
                Draw.z(layer + 0.5f);
                Draw.color(Pal.accent);
                Draw.z(layer + 0.5f);
                float maxRadius = up.out.hitSize * 2f;
                float spawnInterval = 40f;
                float ringDuration = 70f;
                int maxRings = 5;

                for (int i = 0; i < maxRings; i++) {
                    float age = (Time.time - i * spawnInterval) % (maxRings * spawnInterval);
                    if (age < 0)
                        age += maxRings * spawnInterval;

                    if (age < ringDuration) {
                        float life = age / ringDuration;
                        float ringR = maxRadius * (1f - life);

                        Draw.color(Pal.accent, 0.8f);
                        Lines.stroke(target.hitSize / 15f);
                        Lines.circle(target.x, target.y, ringR);
                    }
                }
                // Draw.reset();
                // Lines.stroke(2f * frac);
                // Lines.circle(target.x, target.y, up.out.hitSize * rad);
                // Draw.alpha(frac * 0.5f);
                // Fill.circle(target.x, target.y, up.out.hitSize * rad);
                Draw.reset();

                Draw.draw(layer + 0.6f, () -> {
                    Draw.color(Pal.accent, warmup);
                    Shaders.blockbuild.region = up.out.fullIcon;
                    Shaders.blockbuild.time = Time.time;
                    Shaders.blockbuild.alpha = warmup * frac;
                    Shaders.blockbuild.progress = Mathf.clamp(frac);
                    Draw.rect(up.out.fullIcon, target.x, target.y, target.rotation - 90f);
                    Draw.flush();
                    Draw.color();
                    Shaders.blockbuild.alpha = 1f;
                    Draw.reset();
                });
            }
        }

        public static float getUnitLayer(Unit unit) {
            UnitType type = unit.type;
            boolean isPayload = !unit.isAdded();
            if (isPayload)
                return Draw.z();
            float z;
            if (unit instanceof Segmentc seg) {
                z = type.groundLayer + seg.segmentIndex() / 4000f * Mathf.sign(type.segmentLayerOrder)
                        + (!type.segmentLayerOrder ? 0.01f : 0f);
            } else if (unit.elevation > 0.5f || (type.flying && unit.dead)) {
                z = type.flyingLayer;
            } else {
                z = type.groundLayer + Mathf.clamp(type.hitSize / 4000f, 0, 0.01f);
            }
            return z;
        }

        @Override
        public boolean shouldConsume() {
            return enabled && currentPlan >= 0 && target != null && team.activateUnitFactories();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (currentPlan < 0 || currentPlan >= upgrades.size)
                return false;
            if (items.get(item) >= getMaximumAccepted(item))
                return false;
            ItemStack[] reqs = upgrades.get(currentPlan).items;
            if (reqs == null)
                return false;
            for (int i = 0; i < reqs.length; i++) {
                if (reqs[i].item == item)
                    return true;
            }
            return false;
        }

        @Override
        public int getMaximumAccepted(Item item) {
            if (currentPlan < 0 || currentPlan >= upgrades.size)
                return 0;
            ItemStack[] reqs = upgrades.get(currentPlan).items;
            if (reqs == null)
                return 0;
            for (int i = 0; i < reqs.length; i++) {
                if (reqs[i].item == item)
                    return reqs[i].amount * 2;
            }
            return 0;
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            for (int i = 0; i < drones.size; i++) {
                Unit drone = drones.get(i);
                if (drone != null)
                    drone.remove();
            }
            drones.clear();
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(progress);
            write.s(currentPlan);
            write.i(target == null ? -1 : target.id);
            write.b(drones.size);
            for (int i = 0; i < drones.size; i++) {
                write.i(drones.get(i).id);
            }
            write.f(0f);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            progress = read.f();
            currentPlan = read.s();
            int tid = read.i();
            if (tid != -1)
                readTarget.add(tid);
            int dcount = read.b();
            for (int i = 0; i < dcount; i++) {
                readDrones.add(read.i());
            }
            if (revision >= 2) {
                read.f();
            }
        }
    }
}
