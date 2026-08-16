package crystal.aviation;

import arc.*;
import arc.files.*;
import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.serialization.*;
import crystal.aviation.input.SatelliteMissileInputHandler;
import crystal.aviation.ui.SatelliteAccessDialog;
import crystal.ui.fragments.SatellitePlacementFragment;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.ui.fragments.PlacementFragment;
import mindustry.world.*;
import mindustry.world.modules.ItemModule;

import java.io.*;
import java.lang.reflect.Field;

import static mindustry.Vars.*;

/**
 * 管理所有已发射的人造卫星。
 * 负责：创建、查询、更新、存档/读档。
 */
public class SatelliteManager {
    private static final String settingsKey = "crystal-aviation-satellites-v2";
    private static final String settingsKeyLegacy = "crystal-aviation-satellites";
    private static final String chunkName = "crystal-aviation";
    private static final byte revision = 17;

    /** 所有卫星 */
    public static final ObjectMap<Integer, Satellite> satellites = new ObjectMap<>();
    /** 当前玩家所在的卫星ID（-1表示不在卫星上） */
    public static int currentSatelliteId = -1;
    /** 进入卫星前所在的星球区块，用于退出时返回 */
    public static @Nullable Sector lastSector = null;
    /** 每颗星球最多允许的卫星数量 */
    public static final int maxSatellitesPerPlanet = 20;
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

    /** 卫星地图专用建筑列表实例。 */
    private static @Nullable SatellitePlacementFragment satelliteFragment;
    /** 当前是否正在使用卫星建筑列表。 */
    private static boolean satelliteFragmentInstalled = false;
    /** 保存的原版 PlacementFragment，用于退出卫星地图时恢复。 */
    private static @Nullable PlacementFragment originalFragment;
    /** 用于“禁用”原版 fragment 事件回调的占位容器。 */
    private static @Nullable Group dummyFragmentParent;

    /** PlacementFragment.toggler 字段，反射缓存。 */
    private static @Nullable Field placementTogglerField;

    private static @Nullable Field getPlacementTogglerField() {
        if (placementTogglerField == null) {
            // 1) 先按源码字段名查找（开发/未混淆环境）
            try {
                Field f = PlacementFragment.class.getDeclaredField("toggler");
                f.setAccessible(true);
                placementTogglerField = f;
                return placementTogglerField;
            } catch (Exception ignored) {
            }

            // 2) release 版字段被混淆：toggler 是唯一的、不是其它 Table 字段后代的 Table
            try {
                if (ui != null && ui.hudfrag != null && ui.hudfrag.blockfrag != null) {
                    PlacementFragment current = ui.hudfrag.blockfrag;
                    Field[] fields = PlacementFragment.class.getDeclaredFields();
                    Seq<Field> tableFields = new Seq<>();
                    for (Field f : fields) {
                        if (f.getType() == Table.class) {
                            f.setAccessible(true);
                            tableFields.add(f);
                        }
                    }

                    for (Field f : tableFields) {
                        Table t = (Table) f.get(current);
                        if (t == null)
                            continue;
                        boolean descendant = false;
                        for (Field other : tableFields) {
                            if (other == f)
                                continue;
                            Table ot = (Table) other.get(current);
                            if (ot == null)
                                continue;
                            if (isDescendantOf(t, ot)) {
                                descendant = true;
                                break;
                            }
                        }
                        if (!descendant) {
                            placementTogglerField = f;
                            return placementTogglerField;
                        }
                    }
                }
            } catch (Exception e) {
                Log.warn("[CrystalAviation] Failed to locate PlacementFragment.toggler via fallback: @",
                        e.getMessage());
            }
        }
        return placementTogglerField;
    }

    private static boolean isDescendantOf(Element child, Group ancestor) {
        Element current = child;
        while (current != null) {
            Group parent = current.parent;
            if (parent == ancestor)
                return true;
            current = parent;
        }
        return false;
    }

    /**
     * 确保当前 blockfrag 是安全的（SafePlacementFragment 或 SatellitePlacementFragment），防止原版
     * PlacementFragment 在 WorldLoadEvent/UnlockEvent 中崩溃。
     */
    public static void ensureSafePlacementFragment() {
        if (ui == null || ui.hudfrag == null)
            return;
        PlacementFragment current = ui.hudfrag.blockfrag;
        if (current == null)
            return;
        // 已经是安全实例或卫星实例，无需处理
        if (current instanceof SafePlacementFragment || current instanceof SatellitePlacementFragment)
            return;

        replacePlacementFragment(new SafePlacementFragment());
    }

    /** 切换为卫星地图专用的建筑列表（仅显示已解锁建筑）。 */
    public static void installSatellitePlacementFragment() {
        if (ui == null || ui.hudfrag == null)
            return;

        // 如果当前 blockfrag 已经是卫星版，同步状态并直接返回
        if (ui.hudfrag.blockfrag instanceof SatellitePlacementFragment) {
            satelliteFragment = (SatellitePlacementFragment) ui.hudfrag.blockfrag;
            satelliteFragmentInstalled = true;
            return;
        }

        // 已经安装且 toggler 仍在，直接返回
        Field togglerField = getPlacementTogglerField();
        if (satelliteFragmentInstalled && satelliteFragment != null && togglerField != null) {
            try {
                Table t = (Table) togglerField.get(satelliteFragment);
                if (t != null && t.parent != null)
                    return;
            } catch (Exception ignored) {
                return;
            }
            // toggler 已失效，需要重新安装
            satelliteFragmentInstalled = false;
        }

        if (satelliteFragment == null) {
            satelliteFragment = new SatellitePlacementFragment();
        }

        // 先确保当前不是原版 PlacementFragment（原版缺少空指针防护）
        ensureSafePlacementFragment();

        replacePlacementFragment(satelliteFragment);
        satelliteFragmentInstalled = true;
    }

    /** 恢复默认建筑列表。 */
    public static void restoreDefaultPlacementFragment() {
        if (ui == null || ui.hudfrag == null)
            return;
        if (!satelliteFragmentInstalled)
            return;

        try {
            replacePlacementFragment(new SafePlacementFragment());
        } catch (Exception e) {
            Log.err("[CrystalAviation] Failed to restore default placement fragment", e);
        } finally {
            satelliteFragmentInstalled = false;
            satelliteFragment = null;
        }
    }

    /**
     * 将 ui.hudfrag.blockfrag 替换为 newFragment，并把旧实例隔离到 dummyFragmentParent。
     * 旧实例的 WorldLoadEvent / UnlockEvent 监听仍然保留在 Events 中，
     * 但只要它的 toggler 还在 dummy parent 里就不会空指针崩溃。
     */
    private static void replacePlacementFragment(PlacementFragment newFragment) {
        if (ui == null || ui.hudfrag == null)
            return;
        PlacementFragment current = ui.hudfrag.blockfrag;
        if (current == null || current == newFragment)
            return;

        try {
            Field togglerField = getPlacementTogglerField();
            Table currentToggler = null;
            Group parent = null;
            if (togglerField != null) {
                currentToggler = (Table) togglerField.get(current);
                parent = currentToggler != null ? currentToggler.parent : null;
            }

            if (parent == null) {
                Log.warn("[CrystalAviation] Cannot replace placement fragment: current toggler has no parent");
                return;
            }

            // 先把旧实例隔离到 dummy parent，确保它残留的事件监听不会崩溃
            isolateOldFragment(current);

            // 从真实 UI 中移除旧 toggler
            if (currentToggler != null) {
                currentToggler.remove();
            }

            // 安装新 fragment
            newFragment.build(parent);
            ui.hudfrag.blockfrag = newFragment;
            originalFragment = newFragment;
        } catch (Exception e) {
            Log.err("[CrystalAviation] Failed to replace placement fragment", e);
        }
    }

    /** 把旧 fragment 隔离到一个不显示的 dummy parent 中，让它残留的监听触发时不会空指针。 */
    private static void isolateOldFragment(PlacementFragment old) {
        if (dummyFragmentParent == null) {
            dummyFragmentParent = new WidgetGroup();
        }

        // 1) 先用反射给 toggler 设置一个带父节点的占位 Table（轻量、可靠）
        try {
            ensureDummyToggler(old);
        } catch (Exception e) {
            Log.warn("[CrystalAviation] ensureDummyToggler failed: @", e.getMessage());
        }

        // 2) 再调用 build(dummyParent) 作为兜底：即使反射失败/字段被混淆，
        // 也会让 toggler 指向一个 parent 非 null 的 Table
        try {
            old.build(dummyFragmentParent);
        } catch (Exception e) {
            Log.warn("[CrystalAviation] Failed to build old fragment into dummy parent: @", e.getMessage());
        }
    }

    /** 为指定 fragment 设置一个带父节点的占位 toggler，防止 rebuild() 时 parent 为 null。 */
    private static void ensureDummyToggler(PlacementFragment fragment) throws IllegalAccessException {
        if (dummyFragmentParent == null) {
            dummyFragmentParent = new WidgetGroup();
        }
        Field togglerField = getPlacementTogglerField();
        if (togglerField == null)
            return;
        Table dummyToggler = new Table();
        dummyFragmentParent.addChild(dummyToggler);
        togglerField.set(fragment, dummyToggler);
    }

    /**
     * 带空指针防护的 PlacementFragment 包装。
     * 不单独建文件，直接作为内部类放在 SatelliteManager 里。
     */
    public static class SafePlacementFragment extends PlacementFragment {
        public SafePlacementFragment() {
            super();
        }

        @Override
        public void rebuild() {
            try {
                super.rebuild();
            } catch (Throwable t) {
                Log.warn("[SafePlacementFragment] rebuild() suppressed error: @", t.getMessage());
            }
        }

        @Override
        public void build(Group parent) {
            if (parent == null) {
                Log.warn("[SafePlacementFragment] build() called with null parent, skipping");
                return;
            }
            try {
                super.build(parent);
            } catch (Throwable t) {
                Log.err("[SafePlacementFragment] build() error", t);
            }
        }
    }

    /**
     * 运行时检查并恢复 currentSatelliteId（从当前地图 tag）。
     * 仅在真正处于卫星地图时恢复：游戏运行中、当前不是普通星球区块（rules.sector == null）、
     * 且不在进入/退出卫星的流程中。避免在星球界面或普通区块被旧 tag 误恢复。
     */
    public static void recoverCurrentSatelliteId() {
        if (currentSatelliteId >= 0)
            return; // 已有值，不处理
        if (state.map == null || !state.isGame())
            return; // 不在游戏地图中
        if (state.rules.sector != null)
            return; // 当前是普通星球区块，不是卫星地图
        if (enteringSatellite || exitingSatellite)
            return; // 正在进入/退出卫星，不干预
        String tag = state.map.tags.get("crystal-aviation-satellite");
        if (tag != null && !tag.isEmpty()) {
            try {
                int id = Integer.parseInt(tag);
                Satellite s = satellites.get(id);
                if (s != null) {
                    currentSatelliteId = id;
                    s.mapData.rebindBuildings();
                    installSatellitePlacementFragment();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static boolean isCapturingWorld() {
        return capturingWorld;
    }

    public static void setCapturingWorld(boolean value) {
        capturingWorld = value;
    }

    public static void load() {
        satellites.clear();
        // 区块统计由 SatelliteSectorInfoManager.init() 统一加载，避免重复
        boolean migrated = false;
        byte[] bytes = Core.settings.getBytes(settingsKey, null);
        if (bytes == null || bytes.length == 0) {
            String data = Core.settings.getString(settingsKeyLegacy, "");
            if (data != null && !data.isEmpty()) {
                try {
                    bytes = Base64Coder.decode(data);
                    migrated = true;
                } catch (Throwable t) {
                }
            }
        }
        if (bytes != null && bytes.length > 0) {
            try {
                loadBytes(bytes);
            } catch (Throwable t) {
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
            // 同时保存区块卫星统计
            SatelliteSectorInfoManager.save();
        } catch (Throwable t) {
            Log.err("[SatelliteManager] save 失败", t);
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
                SatelliteSectorInfoManager.save();
            } catch (Throwable t) {
            }
        }
    }

    /** 创建并注册一颗新卫星 */
    public static @Nullable Satellite launch(Planet planet, String name) {
        return launch(planet, name, null, -1f, -1f);
    }

    /** 创建并注册一颗新卫星，可指定自定义地图文件 */
    public static @Nullable Satellite launch(Planet planet, String name, @Nullable Fi mapFile) {
        return launch(planet, name, mapFile, -1f, -1f);
    }

    /**
     * 创建并注册一颗新卫星，可指定自定义地图文件、轨道高度与初始角度。
     * 
     * @param orbitRadius   轨道半径（相对于星球半径的倍数），<=0 时使用随机默认值
     * @param orbitAngleDeg 初始轨道角度（度），<0 时使用随机默认值
     */
    public static @Nullable Satellite launch(Planet planet, String name, @Nullable Fi mapFile, float orbitRadius,
            float orbitAngleDeg) {
        if (!canLaunchOn(planet)) {
            return null;
        }
        Satellite s = new Satellite(planet, name, mapFile, orbitRadius, orbitAngleDeg);
        s.id = nextSatelliteId();
        satellites.put(s.id, s);
        save();
        Events.fire(new SatelliteLaunchEvent(s));
        return s;
    }

    /** 生成下一个不会与已有卫星冲突的 ID。不依赖静态 idCounter，避免类重载或读档顺序导致重复。 */
    private static int nextSatelliteId() {
        int max = 0;
        for (Satellite s : satellites.values()) {
            if (s.id > max)
                max = s.id;
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

    /** 直接统计当前世界中指定建筑类型的实体数量。 */
    public static int countCurrentWorldBuildings(Block block) {
        if (block == null)
            return 0;
        int[] count = { 0 };
        Groups.build.each(b -> {
            if (b != null && b.isValid() && b.block == block) {
                count[0]++;
            }
        });
        return count[0];
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
        if (Vars.state.isPaused())
            return;
        recoverCurrentSatelliteId();
        SatelliteSectorInfoManager.update();
        for (Satellite s : satellites.values()) {
            if (s.isDockMaster())
                s.update();
        }

        // 当前在卫星地图中：刷新核心容量
        if (currentSatelliteId >= 0) {
            Satellite current = satellites.get(currentSatelliteId);
            if (current != null) {
                current.refreshCoreCapacity();
            }

            // 30 秒自动保存：仅在当前处于卫星地图中且未在捕获世界时触发
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
                SatelliteSectorInfoManager.save();
            } catch (Throwable t) {
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
            } catch (Throwable t) {
            }
        }
    }

    /** 重置运行时卫星状态。退出到菜单或返回主界面时应调用，避免把旧的运行时状态带入下一场游戏。 */
    public static void resetRuntimeState() {
        resetRuntimeState(false);
    }

    /**
     * 重置运行时卫星状态。
     * 
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
                    SatelliteSectorInfoManager.save();
                } catch (Throwable t) {
                }
            }
        }
        // 离开卫星地图时恢复默认建筑列表
        restoreDefaultPlacementFragment();
        currentSatelliteId = -1;
        lastSector = null;
        // 退出流程结束，清除标记
        exitingSatellite = false;
        // 轨道打击模式由 SatelliteMissileInputHandler 自己管理，不在此处重置，避免暂停/锁屏时丢失 activeSatelliteId
        if (!SatelliteMissileInputHandler.orbitalStrikeMode) {
            SatelliteMissileInputHandler.resets();
        }
    }

    public static void onWorldLoaded() {
        // 仅在实际处于卫星地图时恢复状态：游戏运行中、当前不是普通星球区块、且不在进入/退出流程中。
        // 普通区块加载时可能残留卫星 tag，必须跳过，否则会把 sector 世界误判为卫星地图。
        if (state.map == null || !state.isGame())
            return;
        if (state.rules.sector != null)
            return;
        if (enteringSatellite || exitingSatellite)
            return;

        boolean recovered = false;

        // 读档或某些情况下运行时状态会丢失，但地图标签中保留了卫星 ID，从中恢复
        if (currentSatelliteId < 0) {
            String tag = state.map.tags.get("crystal-aviation-satellite");
            if (tag != null && !tag.isEmpty()) {
                try {
                    int id = Integer.parseInt(tag);
                    Satellite s = satellites.get(id);
                    if (s != null) {
                        currentSatelliteId = id;
                        recovered = true;
                    }
                } catch (NumberFormatException e) {
                }
            }
        }

        // 同时恢复退出时要返回的区块
        if (currentSatelliteId >= 0 && lastSector == null) {
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
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        // 如果当前世界是卫星地图，重新绑定控制/对接建筑引用，并安装卫星专用建筑列表。
        // 通过替换原版 PlacementFragment 为 SatellitePlacementFragment 实现，不修改 mindustry 包文件。
        if (currentSatelliteId >= 0) {
            Satellite s = get(currentSatelliteId);
            if (s != null) {
                installSatellitePlacementFragment();
                s.mapData.rebindBuildings();
                // 读档后根据世界中实际建筑重新计算扩容仓/液体仓数量与容量，防止旧存档中的错误计数残留
                s.recalcStorageCapacityFromWorld();
                s.bindCoreItems();
                s.refreshCoreCapacity();
            }
        }
    }

    /** 进入指定卫星的内部地图。返回 true 表示已开始尝试进入（异步加载），返回 false 表示被跳过或参数无效。 */
    public static boolean enterSatelliteMap(Satellite s) {
        if (s == null)
            return false;

        // 如果已经在目标卫星中，无需重复加载
        if (currentSatelliteId == s.id) {
            return false;
        }

        // 记录进入卫星前所在的区块，以便退出时返回
        if (state.isCampaign() && state.rules.sector != null) {
            lastSector = state.rules.sector;
        }

        // 状态一致性检查：如果当前在普通区块中，但 currentSatelliteId 仍 >=0，
        // 说明之前的状态恢复有误。必须清空，否则会把 sector 世界误保存到卫星存档。
        if (currentSatelliteId >= 0 && state.rules.sector != null) {
            Log.warn("Inconsistent satellite state detected: currentSatelliteId=@ but rules.sector=@, resetting",
                    currentSatelliteId, state.rules.sector);
            currentSatelliteId = -1;
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
            } catch (Throwable t) {
                // 保存失败时静默继续，进入卫星是更高优先级操作
            }
        }

        final int targetId = s.id;
        final Satellite target = s;

        enteringSatellite = true;
        ui.loadAnd(() -> {
            try {
                currentSatelliteId = targetId;

                // 切换为卫星地图专用建筑列表（仅显示已解锁建筑）
                installSatellitePlacementFragment();

                // 重置逻辑状态，准备进入新地图
                logic.reset();

                // 设置地图元数据（SaveIO.load 会恢复这些 tags；若使用 tiles 回退则保留此处设置）
                StringMap tags = new StringMap();
                tags.put("name", target.name);
                tags.put("author", "Crystal Aviation");
                tags.put("crystal-aviation-satellite", String.valueOf(targetId));
                tags.put("crystal-aviation-last-sector",
                        lastSector != null ? lastSector.planet.name + ":" + lastSector.id : "");
                state.map = new Map(tags);

                // 进入卫星前备份卫星 items。applyToWorld() 加载的 saveData 可能包含旧核心物品，
                // 某些情况下会把卫星 items 覆盖成存档旧值；这里保存权威值，加载后再强制恢复。
                ItemModule itemsBackup = target.items != null ? target.items.copy() : null;
                int itemsTotalBefore = itemsBackup != null ? itemsBackup.total() : -1;

                // 加载卫星世界数据：优先使用 saveData（.msav），否则回退到 transient tiles
                target.mapData.applyToWorld();

                // 根据实际建筑重新计算扩容仓/液体仓数量与容量，防止旧计数残留
                target.recalcStorageCapacityFromWorld();

                // 同步卫星物品容量与核心，确保核心容量与物品储量一致
                target.bindCoreItems();

                // 防御性恢复：若 applyToWorld / bindCoreItems 导致卫星 items 被覆盖，强制恢复备份
                if (itemsBackup != null && target.items != null && target.items.total() != itemsTotalBefore) {
                    Log.warn("[SatelliteManager] 进入卫星时发现卫星 items 被修改（@ -> @），强制恢复备份",
                            itemsTotalBefore, target.items.total());
                    target.items.set(itemsBackup);
                    target.bindCoreItems();
                }

                target.refreshCoreCapacity();

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

            } catch (Throwable t) {
                currentSatelliteId = -1;
                // 出错时安全返回之前的区块或星球界面
                Core.app.post(() -> {
                    if (lastSector != null) {
                        try {
                            control.playSector(lastSector);
                        } catch (Throwable t2) {
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
