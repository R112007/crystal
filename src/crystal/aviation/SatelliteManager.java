package crystal.aviation;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.serialization.*;
import crystal.aviation.ui.SatelliteAccessDialog;
import mindustry.core.GameState.State;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.world.*;

import java.io.*;

import static mindustry.Vars.*;

/**
 * 管理所有已发射的人造卫星。
 * 负责：创建、查询、更新、存档/读档。
 */
public class SatelliteManager {
    private static final String settingsKey = "crystal-aviation-satellites-v2";
    private static final String settingsKeyLegacy = "crystal-aviation-satellites";
    private static final String chunkName = "crystal-aviation";
    private static final byte revision = 9;

    /** 所有卫星 */
    public static final ObjectMap<Integer, Satellite> satellites = new ObjectMap<>();
    /** 当前玩家所在的卫星ID（-1表示不在卫星上） */
    public static int currentSatelliteId = -1;
    /** 进入卫星前所在的星球区块，用于退出时返回 */
    public static @Nullable Sector lastSector = null;
    /** 每颗星球最多允许的卫星数量 */
    public static final int maxSatellitesPerPlanet = 8;
    /** 防止 captureFromWorld 触发 SaveWriteEvent 导致递归 */
    private static boolean capturingWorld = false;
    /** 标记是否正在进入卫星地图（用于跳过 logic.reset 产生的 ResetEvent/StateChangeEvent） */
    private static boolean enteringSatellite = false;
    /** 标记是否正在退出卫星地图（防止退出过程中 ResetEvent/StateChangeEvent 再次捕获空世界） */
    private static boolean exitingSatellite = false;

    /** 自动保存计时器（单位：tick，1 秒 ≈ 60 tick） */
    private static float autoSaveTimer = 0f;
    /** 自动保存间隔：30 秒 */
    private static final float autoSaveInterval = 30f * 60f;

    public static boolean isEnteringSatellite() {
        return enteringSatellite;
    }

    public static boolean isExitingSatellite() {
        return exitingSatellite;
    }

    public static void setExitingSatellite(boolean value) {
        exitingSatellite = value;
    }

    public static boolean isCapturingWorld() {
        return capturingWorld;
    }

    public static void setCapturingWorld(boolean value) {
        capturingWorld = value;
    }

    public static void load() {
        satellites.clear();
        boolean migrated = false;
        byte[] bytes = Core.settings.getBytes(settingsKey, null);
        if (bytes == null || bytes.length == 0) {
            String data = Core.settings.getString(settingsKeyLegacy, "");
            if (data != null && !data.isEmpty()) {
                try {
                    bytes = Base64Coder.decode(data);
                    migrated = true;
                    Log.info("[CrystalAviation] Migrated satellite data from legacy Base64 key.");
                } catch (Throwable t) {
                    Log.err("[CrystalAviation] Failed to decode legacy satellite Base64", t);
                }
            }
        }
        if (bytes != null && bytes.length > 0) {
            try {
                loadBytes(bytes);
            } catch (Throwable t) {
                Log.err("[CrystalAviation] Failed to load satellites", t);
            }
        }
        if (migrated) {
            save();
        }
    }

    public static void save() {
        try {
            byte[] data = saveBytes();
            Core.settings.put(settingsKey, data);
            Core.settings.remove(settingsKeyLegacy);
            Core.settings.forceSave();
            Log.info("[CrystalAviation] Satellites persisted to settings (binary, @ bytes).", data.length);
        } catch (Throwable t) {
            Log.err("[CrystalAviation] Failed to save satellites", t);
        }
    }

    public static byte[] saveBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            Writes write = new Writes(dos);
            write.b(revision);
            write.i(satellites.size);
            for (Satellite s : satellites.values()) {
                s.write(write);
            }
            // 当前所在卫星是运行时状态，不应跨会话持久化；写入 -1 保持格式兼容
            write.i(-1);
            write.close();
            return baos.toByteArray();
        } catch (Throwable t) {
            Log.err("[CrystalAviation] Failed to save satellites", t);
            return new byte[0];
        }
    }

    public static void loadBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            Reads read = new Reads(dis);
            byte dataRevision = read.b();
            int count = read.i();
            satellites.clear();
            for (int i = 0; i < count; i++) {
                Satellite s = new Satellite();
                s.read(read, dataRevision);
                satellites.put(s.id, s);
            }
            if (dataRevision >= 4) {
                read.i(); // 旧格式可能保存了运行时卫星 ID，忽略不用
            }
            // 当前所在卫星是运行时状态，读档后默认不在任何卫星中
            currentSatelliteId = -1;
            lastSector = null;
            read.close();
        } catch (Throwable t) {
            Log.err("[CrystalAviation] Failed to load satellites", t);
        }
    }

    public static void registerSaveChunk() {
        SaveVersion.addCustomChunk(chunkName, new SaveFileReader.CustomChunk() {
            @Override
            public void write(DataOutput stream) throws IOException {
                byte[] data = saveBytes();
                stream.writeInt(data.length);
                stream.write(data);
            }

            @Override
            public void read(DataInput stream) throws IOException {
                int length = stream.readInt();
                // 该 chunk 不再作为权威数据源（统一走 Core.settings），旧存档中的数据直接丢弃，
                // 防止加载旧地图存档时覆盖当前 settings 里的最新卫星数据。
                if (length > 0) {
                    stream.skipBytes(length);
                }
            }

            /**
             * 显式实现带长度的 read，避免 Android D8/R8 丢失默认方法实现导致 AbstractMethodError。
             * 外层 length 由 SaveVersion 读取后传入，实际数据仍按内部长度前缀解析。
             */
            @Override
            public void read(DataInput stream, int length) throws IOException {
                read(stream);
            }

            @Override
            public boolean shouldWrite() {
                // 卫星数据统一走 Core.settings 保存，不再写入地图存档 chunk。
                // 避免从卫星退回普通区块时，普通区块存档里的旧卫星数据覆盖当前内存中的最新数据。
                return false;
            }
        });
    }

    /** 任意游戏存档写入前调用。若当前在卫星地图中，先把世界状态捕获到 saveData，再保存 settings。 */
    public static void onSaveWrite() {
        if (capturingWorld || exitingSatellite || currentSatelliteId < 0)
            return;
        Satellite current = satellites.get(currentSatelliteId);
        if (current != null) {
            try {
                current.mapData.captureFromWorld();
                save();
                Log.info("[CrystalAviation] Captured satellite @ before save write.", currentSatelliteId);
            } catch (Throwable t) {
                Log.err("[CrystalAviation] Failed to capture satellite world before save write", t);
            }
        }
    }

    /** 创建并注册一颗新卫星 */
    public static @Nullable Satellite launch(Planet planet, String name) {
        return launch(planet, name, null);
    }

    /** 创建并注册一颗新卫星，可指定自定义地图文件 */
    public static @Nullable Satellite launch(Planet planet, String name, @Nullable Fi mapFile) {
        if (!canLaunchOn(planet)) {
            Log.warn("[CrystalAviation] Satellite limit reached for planet @", planet.name);
            return null;
        }
        Satellite s = new Satellite(planet, name, mapFile);
        s.id = nextSatelliteId();
        satellites.put(s.id, s);
        save();
        Events.fire(new SatelliteLaunchEvent(s));
        return s;
    }

    /** 生成下一个不会与已有卫星冲突的 ID。不依赖静态 idCounter，避免类重载或读档顺序导致重复。 */
    private static int nextSatelliteId(){
        int max = 0;
        for(Satellite s : satellites.values()){
            if(s.id > max) max = s.id;
        }
        return max + 1;
    }

    public static Satellite get(int id) {
        return satellites.get(id);
    }

    public static int countForPlanet(Planet planet) {
        int count = 0;
        for (Satellite s : satellites.values()) {
            if (s.planet == planet)
                count++;
        }
        return count;
    }

    public static boolean canLaunchOn(Planet planet) {
        return countForPlanet(planet) < maxSatellitesPerPlanet;
    }

    public static int countPlanetsWithSatellites() {
        ObjectSet<Planet> set = new ObjectSet<>();
        for (Satellite s : satellites.values()) {
            if (s.planet != null)
                set.add(s.planet);
        }
        return Math.max(set.size, 1);
    }

    public static void rename(int id, String newName) {
        Satellite s = get(id);
        if (s != null) {
            s.rename(newName);
            save();
        }
    }

    /** 将一颗卫星从记录中移除（退役/销毁） */
    public static boolean retire(int id) {
        Satellite s = satellites.get(id);
        if (s == null)
            return false;

        // 先解除对接关系
        undock(id);

        // 如果当前正在这颗卫星的地图中，先退出
        if (currentSatelliteId == id) {
            SatelliteAccessDialog.exitToSector();
        }

        satellites.remove(id);
        save();
        return true;
    }

    /** 解除某颗卫星的对接关系 */
    public static void undock(int id) {
        Satellite s = satellites.get(id);
        if (s == null)
            return;

        // 移除其他卫星记录中的本卫星
        for (int dockId : s.dockedSatellites.items) {
            Satellite other = satellites.get(dockId);
            if (other != null) {
                other.dockedSatellites.removeValue(id);
                if (other.dockMaster == id)
                    other.dockMaster = -1;
            }
        }

        // 如果本卫星是从属，通知主体
        if (s.dockMaster >= 0) {
            Satellite master = satellites.get(s.dockMaster);
            if (master != null) {
                master.dockedSatellites.removeValue(id);
                master.visualScale = Math.max(1f, master.visualScale - 0.3f);
            }
        }

        s.undockAll();
        save();
    }

    public static void update() {
        for (Satellite s : satellites.values()) {
            if (s.isDockMaster())
                s.update();
        }

        // 30 秒自动保存：仅在当前处于卫星地图中且未在捕获世界时触发
        if (currentSatelliteId >= 0) {
            autoSaveTimer += Time.delta;
            if (autoSaveTimer >= autoSaveInterval) {
                autoSaveTimer = 0f;
                onAutoSave();
            }
        } else {
            autoSaveTimer = 0f;
        }
    }

    /** 自动保存回调。 */
    public static void onAutoSave() {
        if (capturingWorld || exitingSatellite || currentSatelliteId < 0)
            return;
        Satellite current = satellites.get(currentSatelliteId);
        if (current != null) {
            try {
                current.mapData.captureFromWorld();
                save();
                Log.info("[CrystalAviation] Auto-saved satellite @.", currentSatelliteId);
            } catch (Throwable t) {
                Log.err("[CrystalAviation] Failed to auto-save satellite world", t);
            }
        }
    }

    /** 打开暂停对话框时保存。 */
    public static void onPauseDialogOpen() {
        if (capturingWorld || exitingSatellite || currentSatelliteId < 0)
            return;
        Satellite current = satellites.get(currentSatelliteId);
        if (current != null) {
            try {
                current.mapData.captureFromWorld();
                save();
                Log.info("[CrystalAviation] Saved satellite @ on pause dialog open.", currentSatelliteId);
            } catch (Throwable t) {
                Log.err("[CrystalAviation] Failed to save satellite world on pause dialog open", t);
            }
        }
    }

    /** 重置运行时卫星状态。退出到菜单或返回主界面时应调用，避免把旧的运行时状态带入下一场游戏。 */
    public static void resetRuntimeState() {
        resetRuntimeState(false);
    }

    /**
     * 重置运行时卫星状态。
     * @param capture 是否在重置前把当前世界捕获到卫星 saveData；应在世界仍完整时（如 ResetEvent）使用 true，
     *                在世界已被清空后（如 StateChangeEvent playing→menu）使用 false，避免保存空地图。
     */
    public static void resetRuntimeState(boolean capture) {
        if (currentSatelliteId >= 0) {
            Satellite current = satellites.get(currentSatelliteId);
            if (current != null && capture && !exitingSatellite) {
                try {
                    current.mapData.captureFromWorld();
                    save();
                    Log.info("[CrystalAviation] Captured satellite @ before reset.", currentSatelliteId);
                } catch (Throwable t) {
                    Log.err("[CrystalAviation] Failed to capture satellite world during reset", t);
                }
            }
            Log.info("[CrystalAviation] Reset runtime satellite state (was @).", currentSatelliteId);
        }
        currentSatelliteId = -1;
        lastSector = null;
        // 退出流程结束，清除标记
        exitingSatellite = false;
    }

    public static void onWorldLoaded() {
        // 读档或某些情况下运行时状态会丢失，但地图标签中保留了卫星 ID，从中恢复
        if (currentSatelliteId < 0 && state.map != null) {
            String tag = state.map.tags.get("crystal-aviation-satellite");
            if (tag != null && !tag.isEmpty()) {
                try {
                    currentSatelliteId = Integer.parseInt(tag);
                    Log.info("[CrystalAviation] Restored current satellite id from map tag: @", currentSatelliteId);
                } catch (NumberFormatException e) {
                    Log.err("[CrystalAviation] Invalid satellite tag: @", tag);
                }
            }
        }

        // 同时恢复退出时要返回的区块
        if (currentSatelliteId >= 0 && state.map != null && lastSector == null) {
            Satellite s = get(currentSatelliteId);
            String lastSectorTag = state.map.tags.get("crystal-aviation-last-sector");
            if (s != null && s.planet != null && lastSectorTag != null && !lastSectorTag.isEmpty()) {
                try {
                    String[] parts = lastSectorTag.split(":");
                    if (parts.length == 2 && s.planet.name.equals(parts[0])) {
                        int sectorId = Integer.parseInt(parts[1]);
                        for (Sector sec : s.planet.sectors) {
                            if (sec.id == sectorId) {
                                lastSector = sec;
                                Log.info("[CrystalAviation] Restored last sector from map tag: @", lastSector.id);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.err("[CrystalAviation] Failed to restore last sector: @", lastSectorTag);
                }
            }
        }

        // 如果当前世界是卫星地图，重新绑定控制/对接建筑引用
        if (currentSatelliteId >= 0) {
            Satellite s = get(currentSatelliteId);
            if (s != null) {
                s.mapData.rebindBuildings();
            }
        }
    }

    /** 进入指定卫星的内部地图。返回 true 表示已开始尝试进入（异步加载），返回 false 表示被跳过或参数无效。 */
    public static boolean enterSatelliteMap(Satellite s) {
        if (s == null)
            return false;

        // 如果已经在目标卫星中，无需重复加载
        if (currentSatelliteId == s.id) {
            Log.info("[CrystalAviation] Already in satellite @, skipping reload.", s.id);
            return false;
        }

        // 记录进入卫星前所在的区块，以便退出时返回
        if (state.isCampaign() && state.rules.sector != null) {
            lastSector = state.rules.sector;
            Log.info("[CrystalAviation] Remember last sector: @", lastSector.id);
        }

        // 如果已经在某颗卫星地图中，先保存当前世界
        if (currentSatelliteId >= 0) {
            Satellite current = satellites.get(currentSatelliteId);
            if (current != null) {
                current.mapData.captureFromWorld();
                save();
            }
        } else if (state.isGame() && control.saves.getCurrent() != null) {
            // 在普通区块中：保存当前区块，避免数据丢失
            try {
                control.saves.getCurrent().save();
                Log.info("[CrystalAviation] Saved current sector before entering satellite.");
            } catch (Throwable t) {
                Log.warn("[CrystalAviation] Failed to save current sector before entering satellite: @",
                        t.getMessage());
            }
        }

        final int targetId = s.id;
        final Satellite target = s;

        enteringSatellite = true;
        ui.loadAnd(() -> {
            try {
                currentSatelliteId = targetId;

                // 重置逻辑状态，准备进入新地图
                logic.reset();

                // 设置地图元数据（SaveIO.load 会恢复这些 tags；若使用 tiles 回退则保留此处设置）
                StringMap tags = new StringMap();
                tags.put("name", target.name);
                tags.put("author", "Crystal Aviation");
                tags.put("crystal-aviation-satellite", String.valueOf(targetId));
                tags.put("crystal-aviation-last-sector", lastSector != null ? lastSector.planet.name + ":" + lastSector.id : "");
                state.map = new Map(tags);

                // 加载卫星世界数据：优先使用 saveData（.msav），否则回退到 transient tiles
                target.mapData.applyToWorld();

                // 补全/覆盖地图元数据，确保宽高与名称正确
                state.map.width = target.mapData.width;
                state.map.height = target.mapData.height;
                state.map.tags.put("name", target.name);
                state.map.tags.put("author", "Crystal Aviation");
                state.map.tags.put("crystal-aviation-satellite", String.valueOf(targetId));

                // 进入游戏状态
                state.set(State.playing);
                // 卫星地图跳过 logic.play()，避免其清空核心库存并重新添加 loadout
                Events.fire(new PlayEvent());

                // 断开当前存档槽位，防止在卫星内部时自动保存覆盖原星球区块存档
                control.saves.resetSave();

                Log.info("[CrystalAviation] Entered satellite @ (@)", target.id, target.name);
            } catch (Throwable t) {
                Log.err("[CrystalAviation] Failed to enter satellite @", targetId);
                Log.err(t);
                currentSatelliteId = -1;
                // 出错时安全返回之前的区块或星球界面
                Core.app.post(() -> {
                    if (lastSector != null) {
                        try {
                            control.playSector(lastSector);
                        } catch (Throwable t2) {
                            Log.err(t2);
                            ui.planet.show();
                        }
                    } else {
                        ui.planet.show();
                    }
                });
            } finally {
                enteringSatellite = false;
            }
        });
        return true;
    }

    public static class SatelliteLaunchEvent {
        public final Satellite satellite;

        public SatelliteLaunchEvent(Satellite satellite) {
            this.satellite = satellite;
        }
    }
}
