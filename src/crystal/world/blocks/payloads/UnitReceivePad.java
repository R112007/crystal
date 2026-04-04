package crystal.world.blocks.payloads;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Eachable;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.game.UnitInfo;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.Effect;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.io.TypeIO;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.blocks.payloads.UnitPayload;
import static mindustry.Vars.*;

public class UnitReceivePad extends PayloadBlock {
    static ObjectMap<UnitType, Seq<UnitReceivePadBuild>> waiting = new ObjectMap<>();
    static long lastUpdateId = -1;
    static {
        Events.on(ResetEvent.class, e -> {
            waiting.clear();
            lastUpdateId = -1;
        });
    }
    public float reload = 600f;
    public float payloadsCapacity = 100f;
    public float arrivalDuration = 120f;
    public float landSoundVolume = 0.75f;
    public Sound landSound = Sounds.padLand;
    public boolean firstTimeInstant = false;

    public float landingRadius = 5f * tilesize;
    // 爆炸持续时间（单位：帧）
    public float explosionDuration = 30f;
    // 爆炸特效
    public Effect explodeEffect = Fx.blastExplosion;
    // 爆炸音效
    public Sound explodeSound = Sounds.explosion;
    // 爆炸屏幕震动强度
    public float shakeStrength = 5f;
    // 爆炸震动时长
    public float shakeDuration = 5f;

    public UnitReceivePad(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = false;
        configurable = true;
        clipSize = 180f;
        payloadSpeed = 1.2f;
        payloadRotateSpeed = 6f;
        outputsPayload = true;
        acceptsPayload = false;
        rotate = true;
        commandable = true;
        regionRotated1 = 1;
        acceptsUnitPayloads = false;
        noUpdateDisabled = true;
        clearOnDoubleTap = true;
        configClear((UnitReceivePadBuild build) -> {
            if (!build.accessible())
                return;
            build.resetAllState();
        });
        config(UnitType.class, (UnitReceivePadBuild build, UnitType unit) -> {
            if (!build.accessible())
                return;
            if (canProduce(unit) && build.config != unit) {
                build.resetAllState();
                build.config = unit;
                if (firstTimeInstant) {
                    build.handleArrival();
                } else {
                    build.cooldown = 1f;
                }
            }
        });
    }

    @Override
    public void getPlanConfigs(Seq<UnlockableContent> options) {
        options.add(content.units().select(this::canProduce));
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

    @Override
    public void setBars() {
        super.setBars();
        addBar("cooldown",
                (UnitReceivePadBuild build) -> new Bar(
                        "bar.cooldown",
                        Pal.lightOrange,
                        () -> 1f - build.cooldown));
        addBar("batch-progress",
                (UnitReceivePadBuild build) -> new Bar(
                        "批次进度",
                        Pal.accent,
                        () -> build.arrivingUnit == null ? 0f
                                : (float) build.currentAmount / Math.max(build.maxCapacity, 1)));
    }

    public boolean canProduce(UnitType t) {
        return !t.isHidden() && !t.isBanned() && t.supportsEnv(Vars.state.rules.env);
    }

    public class UnitReceivePadBuild extends PayloadBlockBuild<Payload> {
        public @Nullable UnitType config;
        public @Nullable UnitType arrivingUnit;
        public @Nullable Vec2 commandPos;
        public float cooldown = 0f;
        public float arrivingTimer = 0f;
        public float scl = 0f;
        public int priority = Mathf.rand.nextInt();
        public int currentAmount = 0;
        public int maxCapacity = 0;
        public boolean inWaiting = false;

        // 随机降落目标坐标
        public float targetX, targetY;
        // 爆炸计时器
        public float explosionTimer = 0f;
        // 是否已触发爆炸
        public boolean exploded = false;

        public void resetAllState() {
            config = null;
            arrivingUnit = null;
            payload = null;
            scl = 0f;
            cooldown = 0f;
            arrivingTimer = 0f;
            currentAmount = 0;
            maxCapacity = 0;
            inWaiting = false;
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
        public Object config() {
            return config;
        }

        public boolean accessible() {
            return state.rules.editor || state.rules.allowEditWorldProcessors || state.isCampaign()
                    || state.rules.infiniteResources
                    || (team != state.rules.defaultTeam && !state.rules.pvp && team != Team.derelict);
        }

        public boolean isFake() {
            return team != state.rules.defaultTeam || !state.isCampaign();
        }

        public int getMaxCapacity(UnitType unit) {
            if (unit == null)
                return 0;
            int count = 0;
            float remaining = payloadsCapacity;
            while (remaining >= unit.hitSize) {
                remaining -= unit.hitSize;
                count++;
            }
            return Math.max(count, 1);
        }

        public void produceUnit(UnitType unitType) {
            if (!canProduce(unitType))
                return;
            Unit unit = unitType.create(team);
            payload = new UnitPayload(unit);
            if (commandPos != null && unit.isCommandable()) {
                unit.command().commandPosition(commandPos);
            }
            Events.fire(new UnitCreateEvent(unit, this));
            currentAmount++;
            payVector.setZero();
            payRotation = rotdeg();
        }

        public void handleArrival() {
            if (config == null)
                return;
            arrivingUnit = config;
            cooldown = 1f;
            arrivingTimer = 0f;
            scl = 0f;
            maxCapacity = getMaxCapacity(config);
            currentAmount = 0;
            inWaiting = false;
            landSound.at(x, y, 1f, landSoundVolume);
        }

        public void updateGlobalWaiting() {
            if (lastUpdateId == state.updateId)
                return;
            lastUpdateId = state.updateId;
            UnitInfo campaignInfo = state.isCampaign() ? UnitInfo.get(state.rules.sector) : null;
            waiting.each((unitType, pads) -> {
                if (pads.isEmpty() || unitType == null) {
                    pads.clear();
                    return;
                }
                pads.sort(p -> p.priority);
                for (UnitReceivePadBuild pad : pads) {
                    if (pad.config != unitType || pad.cooldown > 0f || pad.arrivingUnit != null
                            || pad.payload != null) {
                        continue;
                    }
                    boolean isFake = pad.isFake();
                    boolean hasStock = isFake
                            || (campaignInfo != null && campaignInfo.getPossessedUnitStack(unitType) != null
                                    && campaignInfo.getPossessedUnitStack(unitType).amount > 0);
                    if (!hasStock) {
                        continue;
                    }
                    pad.handleArrival();
                    pad.priority = Integer.MAX_VALUE - Mathf.rand.nextInt(10000);
                }
                pads.each(pad -> pad.inWaiting = false);
                pads.clear();
            });
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            return false;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            updateGlobalWaiting();
            if (arrivingUnit != null) {
                arrivingTimer += Time.delta / arrivalDuration;
                arrivingTimer = Mathf.clamp(arrivingTimer);
                scl = Mathf.lerpDelta(scl, 1f, 0.12f);
                UnitInfo campaignInfo = state.isCampaign() ? UnitInfo.get(state.rules.sector) : null;
                boolean isFake = isFake();
                boolean hasStock = isFake
                        || (campaignInfo != null && campaignInfo.getPossessedUnitStack(arrivingUnit) != null
                                && campaignInfo.getPossessedUnitStack(arrivingUnit).amount > 0);
                if (arrivingTimer >= 1f) {
                    if (payload == null && hasStock && currentAmount < maxCapacity) {
                        produceUnit(arrivingUnit);
                        if (!isFake && campaignInfo != null) {
                            campaignInfo.getPossessedUnitStack(arrivingUnit).amount--;
                        }
                    }
                    if (payload != null) {
                        moveOutPayload();
                    }
                    if ((!hasStock && payload == null) || (currentAmount >= maxCapacity && payload == null)) {
                        arrivingUnit = null;
                        arrivingTimer = 0f;
                    }
                }
            } else {
                if (config != null && cooldown > 0f) {
                    cooldown -= Time.delta / reload;
                    cooldown = Mathf.clamp(cooldown);
                }
                if (config != null && cooldown <= 0f && payload == null && accessible() && !inWaiting) {
                    boolean canJoin = isFake() ||
                            (state.isCampaign() && UnitInfo.get(state.rules.sector) != null
                                    && UnitInfo.get(state.rules.sector).getPossessedUnitStack(config) != null
                                    && UnitInfo.get(state.rules.sector).getPossessedUnitStack(config).amount > 0);
                    if (canJoin) {
                        waiting.get(config, Seq::new).add(this);
                        inWaiting = true;
                    }
                }
            }
        }

        @Override
        public void draw() {
            Draw.rect(region, x, y);
            Draw.rect(outRegion, x, y, rotdeg());
            Draw.rect(topRegion, x, y);
            if (arrivingUnit != null && arrivingTimer < 1f) {
                float fin = arrivingTimer;
                float fout = 1f - fin;
                float alpha = Interp.pow5Out.apply(fin);
                float scale = (1f - alpha) * 1.5f + 1f;
                float cx = x;
                float cy = y + Interp.pow4In.apply(fout) * 120f;
                float targetRot = rotdeg();
                float rotation = targetRot + fout * 1080f;
                Draw.z(Layer.effect + 0.001f);
                Draw.color(Pal.engine);
                float rad = 0.2f + Interp.pow5Out.apply(Mathf.slope(fin));
                Fill.light(cx, cy, 12, 30f * (rad + scale - 1f), Tmp.c2.set(Pal.engine).a(alpha),
                        Tmp.c1.set(Pal.engine).a(0f));
                Draw.alpha(alpha);
                for (int i = 0; i < 4; i++) {
                    Drawf.tri(cx, cy, 8f, 50f * (rad + scale - 1f), i * 90f + rotation);
                }
                Draw.color();
                Draw.z(Layer.flyingUnit + 1);
                Draw.alpha(alpha);
                Draw.scl(scale);
                Draw.rect(arrivingUnit.fullIcon, cx, cy, rotation);
                Draw.scl();
                Draw.reset();
            }
            Draw.scl(scl);
            drawPayload();
            Draw.reset();
        }

        @Override
        public void buildConfiguration(Table table) {
            if (!accessible())
                return;
            mindustry.world.blocks.ItemSelection.buildTable(UnitReceivePad.this, table,
                    Vars.content.units().select(UnitReceivePad.this::canProduce).as(),
                    () -> config,
                    this::configure,
                    selectionRows, selectionColumns);
        }

        @Override
        public void display(Table table) {
            super.display(table);
            if (config != null && !config.supportsEnv(state.rules.env)) {
                table.row();
                table.label(() -> Core.bundle.get("unsupportenv") + config.localizedName).color(Pal.remove);
            }
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(config == null ? -1 : config.id);
            write.f(cooldown);
            write.s(arrivingUnit == null ? -1 : arrivingUnit.id);
            write.f(arrivingTimer);
            write.i(priority);
            write.i(currentAmount);
            write.i(maxCapacity);
            TypeIO.writeVecNullable(write, commandPos);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            config = Vars.content.unit(read.s());
            cooldown = read.f();
            if (revision >= 1) {
                arrivingUnit = Vars.content.unit(read.s());
                arrivingTimer = read.f();
                priority = read.i();
                currentAmount = read.i();
                maxCapacity = read.i();
            }
            if (revision >= 2) {
                commandPos = TypeIO.readVecNullable(read);
            }
            inWaiting = false;
        }
    }
}
