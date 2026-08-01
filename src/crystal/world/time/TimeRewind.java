package crystal.world.time;

import arc.Core;
import arc.Events;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.EntityGroup;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static mindustry.Vars.*;

/**
 * 时间回溯系统（模组可用版本）。
 *
 * 不修改游戏源码、不改动输入键位。模组主类实例化本类后，
 * 在 {@link mindustry.mod.Mod#update()} 中调用 {@link #update()} 即可驱动。
 *
 * 触发方式：
 * 
 * <pre>
 * Events.fire(new TimeRewindEvent(5f)); // 回溯 5 秒
 * Events.fire(TimeRewindEvent.interrupt()); // 打断当前回溯
 * </pre>
 */
public class TimeRewind {
    /** 最大回溯时长（秒），按有效 30fps 采样估算 */
    public static final int maxHistorySeconds = 20;
    /** 最大保存帧数（每 2 帧采样一次，30fps * 20s） */
    public static final int maxHistoryFrames = 30 * maxHistorySeconds;
    /** 捕获间隔：每 N 帧捕获一次（降低内存占用） */
    public static final int captureInterval = 2;
    /** 回溯时每帧跳过的帧数，越大倒流越快 */
    public static final int rewindSpeed = 2;

    /** 单帧数据上限（字节），超过则跳过该帧以控制内存 */
    public static final int maxFrameBytes = 8 * 1024 * 1024;
    /** 单个建筑状态上限（字节），超过则丢弃该建筑 */
    public static final int maxBuildingStateBytes = 256 * 1024;

    /** 回溯前特效帧数 */
    public static final int windupDuration = 30;

    /** 当前是否处于回溯模式（包含预备和正式回溯） */
    private boolean active = false;
    /** 是否已结束预备、开始正式回溯 */
    private boolean rewinding = false;
    /** 剩余预备帧数 */
    private int windupFrames = 0;
    /** 本次请求的回溯时长（秒） */
    private float pendingDuration = 0f;
    /** 环形缓冲区 */
    private final Seq<TimeRewindFrame> history = new Seq<>(maxHistoryFrames);
    /** 下一帧写入位置 */
    private int head = 0;
    /** 当前缓冲区有效帧数 */
    private int size = 0;
    /** 当前已经回退的帧数 */
    private int rewindOffset = 0;
    /** 本次回溯目标回退帧数，<=0 表示回退到最早帧 */
    private int targetOffset = 0;
    /** 捕获计数器，用于支持 captureInterval */
    private int captureCounter = 0;

    /** 回溯期间锁定的波次倒计时，防止 Logic 继续倒计时 */
    private float lockWaveTime = 0f;
    /** 回溯前 state.rules.waveTimer 的原值，停止时恢复 */
    private boolean wasWaveTimer = false;

    /** 用于实体序列化的临时输出流 */
    private final ReusableByteOutStream entityStream = new ReusableByteOutStream(65536);
    private final DataOutputStream entityDataOut = new DataOutputStream(entityStream);
    private final Writes entityWrites = new Writes(entityDataOut);

    /** 用于建筑序列化的临时输出流 */
    private final ReusableByteOutStream buildStream = new ReusableByteOutStream(65536);
    private final DataOutputStream buildDataOut = new DataOutputStream(buildStream);
    private final Writes buildWrites = new Writes(buildDataOut);

    /** 用于单个建筑状态序列化的临时输出流，防止部分写入导致整帧数据错位 */
    private final ReusableByteOutStream perBuildStream = new ReusableByteOutStream(4096);
    private final DataOutputStream perBuildDataOut = new DataOutputStream(perBuildStream);
    private final Writes perBuildWrites = new Writes(perBuildDataOut);

    public TimeRewind() {
        Events.on(EventType.WorldLoadEvent.class, e -> clearHistory());
        Events.on(EventType.ResetEvent.class, e -> clearHistory());
        Events.on(TimeRewindEvent.class, this::onEvent);
        Events.run(EventType.Trigger.draw, this::drawWindupEffect);
    }

    /** 接收 TimeRewindEvent，启动或打断回溯。 */
    private void onEvent(TimeRewindEvent event) {
        if (event.interrupt) {
            stopRewind();
            return;
        }
        if (active) {
            pendingDuration = event.duration;
        } else {
            startRewind(event.duration);
        }
    }

    /** 是否正在倒流（含预备阶段） */
    public boolean active() {
        return active;
    }

    /** 当前回溯进度，0~1；预备阶段显示预备进度。 */
    public float progress() {
        if (!active)
            return 0f;
        if (windupFrames > 0) {
            return 1f - (float) windupFrames / windupDuration;
        }
        if (targetOffset <= 0)
            return 0f;
        return Mathf.clamp((float) rewindOffset / targetOffset);
    }

    /** 开始倒流，duration 为秒；<=0 表示回退到最早可用帧。 */
    public void startRewind(float duration) {
        if (size < 2 || active)
            return;
        active = true;
        rewinding = false;
        windupFrames = windupDuration;
        pendingDuration = duration;

        // 锁定波次倒计时：先保持为最新历史帧的值，避免 windup 期间 Logic 继续扣减
        TimeRewindFrame newest = history.get((head - 1 + maxHistoryFrames) % maxHistoryFrames);
        lockWaveTime = newest.waveTime;
        if (lockWaveTime <= 0f)
            lockWaveTime = 0.001f;
        wasWaveTimer = state.rules.waveTimer;
        state.rules.waveTimer = false;

        Log.info("[CrystalRewind] 开始时间回溯预备，历史帧数: @，目标: @s", size, duration);
    }

    /** 打断并停止倒流，从当前恢复的状态继续正向模拟。 */
    public void stopRewind() {
        if (!active)
            return;
        active = false;
        rewinding = false;
        windupFrames = 0;
        pendingDuration = 0f;

        // 恢复波次计时器
        state.rules.waveTimer = wasWaveTimer;
        lockWaveTime = 0f;

        int keep = Math.max(1, size - rewindOffset);
        Seq<TimeRewindFrame> retained = new Seq<>(keep);
        for (int i = 0; i < keep; i++) {
            int index = (head - keep + i + maxHistoryFrames) % maxHistoryFrames;
            retained.add(history.get(index));
        }
        history.clear();
        history.addAll(retained);
        size = keep;
        head = size % maxHistoryFrames;
        rewindOffset = 0;
        targetOffset = 0;
        Log.info("[CrystalRewind] 回溯被打断/停止，保留帧数: @", size);
    }

    /** 清空历史 */
    public void clearHistory() {
        if (active) {
            state.rules.waveTimer = wasWaveTimer;
        }
        history.clear();
        head = 0;
        size = 0;
        rewindOffset = 0;
        targetOffset = 0;
        windupFrames = 0;
        rewinding = false;
        pendingDuration = 0f;
        lockWaveTime = 0f;
        active = false;
    }

    /** 每帧由模组主类的 update() 调用。正向时捕获快照；回溯时恢复更早的快照。 */
    public void update() {
        if (active) {
            // 强制覆盖 Logic.update 可能做过的 wavetime 修改，确保 windup/rewind 期间倒计时冻结在当前历史值
            state.wavetime = lockWaveTime;

            if (windupFrames > 0) {
                windupFrames--;
            } else if (!rewinding) {
                rewinding = true;
                rewindOffset = 0;
                int frames = pendingDuration <= 0 ? size - 1
                        : Math.min((int) (pendingDuration * 60f / captureInterval), size - 1);
                targetOffset = Math.max(1, frames);
                Log.info("[CrystalRewind] 预备结束，开始倒流，目标: @s", pendingDuration);
            } else {
                stepRewind();
            }
        } else {
            captureFrame();
        }
    }

    /** 绘制回溯前的时钟倒流特效。 */
    private void drawWindupEffect() {
        if (!active || windupFrames <= 0)
            return;

        float progress = 1f - (float) windupFrames / windupDuration;
        float x = Core.camera.position.x;
        float y = Core.camera.position.y;
        float radius = 60f;
        float alpha = 0.6f * (1f - progress);

        Draw.color(Color.white, alpha);
        Lines.stroke(3f);
        Lines.circle(x, y, radius);

        // 刻度
        Draw.color(Color.lightGray, alpha);
        Lines.stroke(1f);
        for (int i = 0; i < 12; i++) {
            float angle = i * 30f;
            float r1 = radius * 0.85f;
            float r2 = radius * 0.95f;
            float x1 = x + Mathf.cosDeg(angle) * r1;
            float y1 = y + Mathf.sinDeg(angle) * r1;
            float x2 = x + Mathf.cosDeg(angle) * r2;
            float y2 = y + Mathf.sinDeg(angle) * r2;
            Lines.line(x1, y1, x2, y2);
        }

        // 两根指针向反方向旋转
        float secondAngle = -progress * 360f * 3f;
        float minuteAngle = -progress * 360f * 0.5f;

        Draw.color(Color.cyan, alpha);
        Lines.stroke(2f);
        Lines.line(x, y,
                x + Mathf.cosDeg(secondAngle) * radius * 0.8f,
                y + Mathf.sinDeg(secondAngle) * radius * 0.8f);

        Draw.color(Color.sky, alpha);
        Lines.stroke(3f);
        Lines.line(x, y,
                x + Mathf.cosDeg(minuteAngle) * radius * 0.5f,
                y + Mathf.sinDeg(minuteAngle) * radius * 0.5f);

        Draw.reset();
    }

    /** 执行一次回溯步进 */
    private void stepRewind() {
        if (size <= 1) {
            stopRewind();
            return;
        }

        for (int i = 0; i < rewindSpeed; i++) {
            if (rewindOffset >= size - 1 || rewindOffset >= targetOffset)
                break;
            rewindOffset++;
            int index = (head - rewindOffset + maxHistoryFrames) % maxHistoryFrames;
            restoreFrame(history.get(index));
        }

        if (rewindOffset >= size - 1 || rewindOffset >= targetOffset) {
            stopRewind();
        }
    }

    /** 捕获当前世界状态 */
    private void captureFrame() {
        if (world == null || world.tiles == null)
            return;

        captureCounter++;
        if (captureCounter % captureInterval != 0)
            return;

        TimeRewindFrame frame = new TimeRewindFrame();
        frame.tick = (long) state.tick;
        frame.time = Time.time;
        frame.delta = Time.delta;
        frame.waveTime = state.wavetime;
        frame.wave = state.wave;
        frame.enemies = state.enemies;
        frame.gameOver = state.gameOver;
        frame.randSeed0 = Mathf.rand.seed0;
        frame.randSeed1 = Mathf.rand.seed1;

        try {
            frame.entityData = captureEntities();
            frame.buildingData = captureBuildings();
        } catch (Exception e) {
            Log.err("[CrystalRewind] 捕获快照失败", e);
            return;
        }

        int frameSize = frame.approxSize();
        if (frameSize > maxFrameBytes) {
            Log.warn("[CrystalRewind] 单帧数据过大(@MB)，跳过该帧以控制内存。", frameSize / (1024 * 1024));
            return;
        }

        if (history.size < maxHistoryFrames) {
            history.add(frame);
        } else {
            history.set(head, frame);
        }
        head = (head + 1) % maxHistoryFrames;
        if (size < maxHistoryFrames)
            size++;
    }

    /** 序列化所有动态实体，返回字节数组 */
    private byte[] captureEntities() throws IOException {
        entityStream.reset();
        IntSet written = new IntSet();
        int count = 0;
        entityDataOut.writeInt(0);

        count += writeEntityGroup(Groups.unit, written);
        count += writeEntityGroup(Groups.bullet, written);
        count += writeEntityGroup(Groups.all, written);
        count += writeEntityGroup(Groups.player, written);

        byte[] bytes = entityStream.toByteArray();
        bytes[0] = (byte) (count >> 24);
        bytes[1] = (byte) (count >> 16);
        bytes[2] = (byte) (count >> 8);
        bytes[3] = (byte) count;
        return bytes;
    }

    /** 把一个组中可序列化的实体写入流，按 id 去重 */
    private int writeEntityGroup(EntityGroup<? extends Entityc> group, IntSet written) {
        if (group == null)
            return 0;
        int added = 0;
        for (Entityc entity : group) {
            if (entity == null || !entity.serialize() || !written.add(entity.id()))
                continue;
            // 排除玩家控制的单位，避免其位置和状态被回溯
            if (entity instanceof Unit && ((Unit) entity).isPlayer())
                continue;
            try {
                entityDataOut.writeByte(entity.classId() & 0xFF);
                entityDataOut.writeInt(entity.id());
                entity.beforeWrite();
                entity.write(entityWrites);
            } catch (IOException e) {
                Log.warn("[CrystalRewind] 实体序列化失败: classId=@ id=@", entity.classId(), entity.id());
            }
            added++;
        }
        return added;
    }

    /** 序列化所有建筑，返回字节数组。额外保存 team/rotation，并记录状态字节长度。 */
    private byte[] captureBuildings() throws IOException {
        buildStream.reset();
        buildDataOut.writeInt(0);
        int count = 0;

        if (world.tiles != null) {
            for (int i = 0; i < world.tiles.width * world.tiles.height; i++) {
                Tile tile = world.tiles.geti(i);
                if (tile == null || tile.build == null || !tile.isCenter())
                    continue;

                Building build = tile.build;
                try {
                    // 先把单个建筑状态写到独立流，防止部分写入导致整帧错位
                    perBuildStream.reset();
                    build.writeAll(perBuildWrites);
                    int len = perBuildStream.size();
                    if (len > maxBuildingStateBytes) {
                        Log.warn("[CrystalRewind] 建筑状态过大(@B)，跳过: @ @", len, build.block.name, tile.pos());
                        continue;
                    }

                    buildDataOut.writeInt(tile.pos());
                    buildDataOut.writeShort(build.block.id);
                    buildDataOut.writeByte(build.team().id);
                    buildDataOut.writeByte(build.rotation);
                    buildDataOut.writeByte(build.version());
                    buildDataOut.writeInt(len);
                    buildDataOut.write(perBuildStream.getBytes(), 0, len);
                    count++;
                } catch (Exception e) {
                    Log.warn("[CrystalRewind] 建筑序列化失败: @ @", build.block.name, tile.pos());
                }
            }
        }

        byte[] bytes = buildStream.toByteArray();
        bytes[0] = (byte) (count >> 24);
        bytes[1] = (byte) (count >> 16);
        bytes[2] = (byte) (count >> 8);
        bytes[3] = (byte) count;
        return bytes;
    }

    /** 恢复一帧 */
    private void restoreFrame(TimeRewindFrame frame) {
        if (frame == null)
            return;

        state.tick = frame.tick;
        Time.time = frame.time;
        Time.delta = frame.delta;
        state.wavetime = frame.waveTime;
        state.wave = frame.wave;
        state.enemies = frame.enemies;
        state.gameOver = frame.gameOver;
        Mathf.rand.seed0 = frame.randSeed0;
        Mathf.rand.seed1 = frame.randSeed1;

        // 记录本次恢复帧的波次倒计时，后续 update() 会用它覆盖 Logic 的修改
        lockWaveTime = frame.waveTime;
        if (lockWaveTime <= 0f)
            lockWaveTime = 0.001f;

        try {
            restoreEntities(frame.entityData);
            restoreBuildings(frame.buildingData);
            state.teams.updateTeamStats();
            // 等建筑和队伍统计恢复后再修复玩家单位，确保核心已就位
            fixPlayerUnit();
        } catch (Exception e) {
            Log.err("[CrystalRewind] 恢复快照失败", e);
            active = false;
        }
    }

    /** 恢复动态实体 */
    private void restoreEntities(byte[] data) throws IOException {
        if (data == null || data.length < 4)
            return;

        // 备份当前本地玩家控制的单位，避免被 clearDynamicGroups 误删且不被回溯
        Unit retainedPlayerUnit = null;
        boolean retainedPlayerUnitAdded = false;
        if (Vars.player != null) {
            retainedPlayerUnit = Vars.player.unit();
            retainedPlayerUnitAdded = retainedPlayerUnit != null && retainedPlayerUnit.isAdded();
        }

        clearDynamicGroups();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        Reads read = new Reads(in);
        int count = in.readInt();
        if (count < 0 || count > 100000) {
            Log.warn("[CrystalRewind] 实体快照数量异常(@)，放弃恢复。", count);
            return;
        }
        final Entityc[] newPlayer = { null };
        Seq<Player> restoredPlayers = new Seq<>();

        for (int i = 0; i < count; i++) {
            int classId = in.readUnsignedByte();
            int id = in.readInt();

            Prov<? extends Entityc> prov = EntityMapping.map(classId);
            if (prov == null) {
                Log.warn("[CrystalRewind] 未知实体 classId: @", classId);
                continue;
            }

            Entityc entity = prov.get();
            EntityGroup.checkNextId(id);
            entity.id(id);
            entity.read(read);
            entity.add();

            if (entity instanceof Player) {
                Player p = (Player) entity;
                restoredPlayers.add(p);
                if (Vars.player != null && p.id() == Vars.player.id()) {
                    newPlayer[0] = p;
                }
            }
        }

        // 优先按 id 匹配；若 id 不同（玩家曾重生等），按 uuid 回退匹配
        if (newPlayer[0] == null && Vars.player != null) {
            String localUuid = Vars.player.uuid();
            for (Player p : restoredPlayers) {
                if (p.uuid() != null && p.uuid().equals(localUuid)) {
                    newPlayer[0] = p;
                    break;
                }
            }
            // 单人模式下只有一个玩家时，直接采用
            if (newPlayer[0] == null && restoredPlayers.size == 1) {
                newPlayer[0] = restoredPlayers.first();
            }
        }

        if (newPlayer[0] != null) {
            Vars.player = (Player) newPlayer[0];
        }

        // 确保所有动态组都执行 afterReadAll，以正确解析单位与玩家之间的引用
        Groups.all.each(Entityc::afterReadAll);
        Groups.player.each(Entityc::afterReadAll);
        Groups.unit.each(Entityc::afterReadAll);
        Groups.bullet.each(Entityc::afterReadAll);
        Groups.build.each(Entityc::afterReadAll);

        Groups.unit.updatePhysics();
        Groups.bullet.updatePhysics();

        // 恢复被排除的玩家单位绑定，使其保持回溯前的状态
        if (retainedPlayerUnitAdded && retainedPlayerUnit != null && Vars.player != null) {
            Vars.player.unit(retainedPlayerUnit);
            if (!retainedPlayerUnit.isAdded())
                retainedPlayerUnit.add();
        }
    }

    /** 确保回溯后本地玩家有有效单位，否则尝试从核心强制重生。 */
    private void fixPlayerUnit() {
        if (Vars.player == null) {
            Log.warn("[CrystalRewind] fixPlayerUnit: Vars.player 为 null。");
            return;
        }

        Unit u = Vars.player.unit();
        Log.info("[CrystalRewind] fixPlayerUnit: player.id=@, unit=@, added=@", Vars.player.id(),
                u == null ? "null" : u.id(), u == null ? "n/a" : u.isAdded());

        if (u != null && !u.isAdded()) {
            Log.info("[CrystalRewind] fixPlayerUnit: 单位引用失效，清空。");
            Vars.player.unit((Unit) null);
            u = null;
        }

        if (u == null) {
            for (Unit unit : Groups.unit) {
                if (unit.isPlayer()) {
                    Vars.player.unit(unit);
                    u = unit;
                    Log.info("[CrystalRewind] fixPlayerUnit: 在 Groups.unit 中找到归属单位 @ 并绑定。", u.id());
                    break;
                }
            }
        }

        if (u == null) {
            Teams.TeamData data = state.teams.get(Vars.player.team());
            Log.info("[CrystalRewind] fixPlayerUnit: player.team=@, cores.size=@", Vars.player.team(),
                    data == null ? "null" : data.cores.size);
            if (data != null && data.cores.size > 0) {
                CoreBlock.CoreBuild core = data.cores.first();
                if (core != null && core.tile != null) {
                    Log.info("[CrystalRewind] fixPlayerUnit: 尝试从核心 @ 强制重生。", core.tile.pos());
                    CoreBlock.playerSpawn(core.tile, Vars.player);
                    Log.info("[CrystalRewind] fixPlayerUnit: playerSpawn 调用完成，重生后 unit=@",
                            Vars.player.unit() == null ? "null" : Vars.player.unit().id());
                } else {
                    Log.warn("[CrystalRewind] fixPlayerUnit: 核心或核心瓦片为空。");
                }
            } else {
                Log.warn("[CrystalRewind] fixPlayerUnit: 未找到己方核心，无法强制重生。");
            }
        }
    }

    /** 清空所有动态实体组（保留建筑和电力图更新器） */
    private void clearDynamicGroups() {
        Groups.all.clear();
        Groups.player.clear();
        Groups.unit.clear();
        Groups.bullet.clear();
        Groups.sync.clear();
        Groups.draw.clear();
        Groups.fire.clear();
        Groups.puddle.clear();
        Groups.weather.clear();
        Groups.label.clear();
    }

    /** 恢复建筑状态，包括创建/删除建筑以匹配快照。 */
    private void restoreBuildings(byte[] data) throws IOException {
        if (data == null || data.length < 4)
            return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int count = in.readInt();
        if (count < 0 || count > 1000000) {
            Log.warn("[CrystalRewind] 建筑快照数量异常(@)，放弃恢复。", count);
            return;
        }

        final int headerSize = 4 + 2 + 1 + 1 + 1 + 4; // pos + blockId + team + rotation + version + len

        class Snap {
            int pos;
            short blockId;
            byte teamId;
            byte rotation;
            byte version;
            byte[] state;
        }

        Seq<Snap> snaps = new Seq<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            if (in.available() < headerSize) {
                Log.warn("[CrystalRewind] 建筑快照数据不足，终止读取（已读@/@）。", i, count);
                break;
            }

            Snap s = new Snap();
            s.pos = in.readInt();
            s.blockId = in.readShort();
            s.teamId = in.readByte();
            s.rotation = in.readByte();
            s.version = in.readByte();
            int len = in.readInt();
            if (len < 0 || len > maxBuildingStateBytes || len > in.available()) {
                Log.warn("[CrystalRewind] 建筑状态长度异常(@B)，停止恢复以避免 OOM。", len);
                break;
            }
            s.state = new byte[len];
            in.readFully(s.state);
            snaps.add(s);
        }

        // 当前世界中所有中心建筑
        IntMap<Building> current = new IntMap<>();
        if (world.tiles != null) {
            for (int i = 0; i < world.tiles.width * world.tiles.height; i++) {
                Tile tile = world.tiles.geti(i);
                if (tile != null && tile.build != null && tile.isCenter()) {
                    current.put(tile.pos(), tile.build);
                }
            }
        }

        // 快照中存在的建筑位置集合
        IntSet snapPos = new IntSet(snaps.size);
        for (Snap s : snaps)
            snapPos.add(s.pos);

        // 1. 删除快照中没有的建筑
        for (IntMap.Entry<Building> entry : current) {
            if (!snapPos.contains(entry.key)) {
                Tile tile = world.tile(entry.key);
                if (tile != null)
                    tile.setBlock(Blocks.air);
            }
        }

        // 2. 创建或更新快照中的建筑
        for (Snap s : snaps) {
            Tile tile = world.tile(s.pos);
            if (tile == null)
                continue;

            Building build = tile.build;
            if (build == null || build.block.id != s.blockId) {
                Block block = content.block(s.blockId);
                if (block == null) {
                    Log.warn("[CrystalRewind] 未知方块 id: @", s.blockId);
                    continue;
                }
                Team team = Team.get(s.teamId);
                tile.setBlock(block, team, s.rotation);
                build = tile.build;
            }

            if (build != null && build.block.id == s.blockId) {
                try {
                    Reads stateRead = new Reads(new DataInputStream(new ByteArrayInputStream(s.state)));
                    build.readAll(stateRead, s.version);
                } catch (Exception e) {
                    Log.warn("[CrystalRewind] 建筑恢复失败: @ @", build.block.name, s.pos);
                }
            }
        }
    }
}
