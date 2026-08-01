package crystal.aviation;

import arc.files.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.type.*;
import mindustry.world.*;

import crystal.aviation.world.*;
import crystal.type.SatelliteMissile;
import crystal.world.modules.SatelliteMissleModule;

/**
 * 代表一颗在轨人造卫星。
 * 包含：显示名称、轨道参数、地图数据、对接关系、移动状态。
 */
public class Satellite {
    private static int idCounter = 0;

    /** 唯一标识 */
    public int id;
    /** 玩家输入的卫星名称 */
    public String name;
    /** 所属星球 */
    public Planet planet;
    /** 当前轨道角度（弧度） */
    public float orbitAngle;
    /** 轨道半径（相对于星球半径的倍数） */
    public float orbitRadius;
    /** 轨道倾角（用于3D视觉效果） */
    public float orbitTilt;
    /** 轨道角速度 */
    public float orbitSpeed;

    /** 卫星地图数据 */
    public SatelliteMapData mapData;
    /** 已对接的卫星ID */
    public IntSeq dockedSatellites = new IntSeq();
    /** 对接后作为主体的卫星ID（-1表示自己就是主体） */
    public int dockMaster = -1;

    /** 移动目标：目标Sector ID（星球区块索引），-1表示无 */
    public int targetSectorId = -1;
    /** 移动进度 0~1 */
    public float moveProgress = 0f;
    /** 移动总耗时（秒） */
    public float moveDuration = 60f;
    /** 移动起始角度 */
    public float moveFromAngle;
    /** 移动目标角度 */
    public float moveToAngle;
    /** 是否正在移动 */
    public boolean moving = false;
    /** 是否已绑定到某个区块（绑定后静止在区块上方） */
    public boolean boundToSector = false;

    /** 3D渲染辅助字段 */
    public transient float renderX, renderY, renderZ;
    public transient float visualScale = 1f;
    /** 太阳能板旋转角度 */
    public transient float spinAngle = 0f;

    /** 卫星资源仓库（按物品 ID） */
    public IntIntMap storage = new IntIntMap();
    /** 太阳能发电量（单位：能量/秒） */
    public float solarPower = 0f;
    /** 已扫描的 Sector ID */
    public IntSeq scannedSectors = new IntSeq();
    /** 卫星等级（1 + 升级次数 + 对接数量） */
    public int tier = 1;
    /** 通过升级建筑获得的等级加成 */
    public int upgradeTier = 0;
    /** 卫星导弹仓库 */
    public SatelliteMissleModule missileModule = new SatelliteMissleModule();

    public Satellite() {
        this.id = ++idCounter;
    }

    public Satellite(Planet planet, String name) {
        this(planet, name, null, -1f, -1f);
    }

    public Satellite(Planet planet, String name, @Nullable Fi mapFile) {
        this(planet, name, mapFile, -1f, -1f);
    }

    /**
     * 创建卫星。
     * @param orbitRadius 轨道半径（相对于星球半径的倍数），<=0 时使用随机默认值
     * @param orbitAngleDeg 初始轨道角度（度），<0 时使用随机默认值
     */
    public Satellite(Planet planet, String name, @Nullable Fi mapFile, float orbitRadius, float orbitAngleDeg) {
        this();
        this.planet = planet;
        this.name = name;
        if (orbitAngleDeg >= 0f) {
            this.orbitAngle = orbitAngleDeg * Mathf.degRad;
        } else {
            this.orbitAngle = Mathf.random(360f) * Mathf.degRad;
        }
        if (orbitRadius > 0f) {
            this.orbitRadius = orbitRadius;
        } else {
            this.orbitRadius = 2.2f + Mathf.random(0.8f);
        }
        this.orbitTilt = Mathf.random(-15f, 15f) * Mathf.degRad;
        this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        this.mapData = new SatelliteMapData(this);
        if (mapFile != null && mapFile.exists()) {
            try {
                mapData.loadFromMapFile(mapFile);
            } catch (Exception e) {
                mapData.generateDefault();
            }
        } else {
            mapData.generateDefault();
        }

        // 新卫星不再默认携带导弹，避免一创建卫星地图就获得导弹
    }

    /** 更新轨道位置与移动（绑定区块仅作为数据记录，不再强制卫星跟随区块自转） */
    public void update() {
        if (moving) {
            moveProgress += Time.delta / (moveDuration * 60f);
            if (moveProgress >= 1f) {
                moveProgress = 1f;
                orbitAngle = moveToAngle;
                moving = false;
                // 若已绑定到区块，保留 targetSectorId 作为绑定记录，不要清零
                if (!boundToSector) {
                    targetSectorId = -1;
                }
            } else {
                orbitAngle = Mathf.lerp(moveFromAngle, moveToAngle, moveProgress);
            }
        } else {
            // 始终按自身轨道参数运动，不再因绑定区块而静止/跟随
            orbitAngle += orbitSpeed * Time.delta;
        }

        // 缓慢自转太阳能板
        spinAngle += orbitSpeed * Time.delta * 4f;

        // 计算世界坐标（用于3D渲染）
        float r = planet.radius * orbitRadius;
        fallbackRenderPosition(r);

        // 根据升级次数与对接数量更新等级和视觉缩放
        tier = 1 + upgradeTier + dockedSatellites.size;
        visualScale = 1f + 0.25f * upgradeTier + 0.3f * dockedSatellites.size;
    }

    private void fallbackRenderPosition(float r) {
        renderX = planet.position.x + Mathf.cos(orbitAngle) * r;
        renderY = planet.position.y + Mathf.sin(orbitAngle) * r * Mathf.cos(orbitTilt);
        renderZ = planet.position.z + Mathf.sin(orbitAngle) * r * Mathf.sin(orbitTilt);
    }

    /** 根据 ID 查找所属星球上的区块 */
    private @Nullable Sector findSector(int id) {
        if (planet == null || planet.sectors == null)
            return null;
        for (Sector sec : planet.sectors) {
            if (sec.id == id)
                return sec;
        }
        return null;
    }

    /** 开始移动到目标角度 */
    public void startMove(float targetAngle, float duration) {
        this.moveFromAngle = orbitAngle;
        this.moveToAngle = targetAngle;
        this.moveDuration = duration;
        this.moveProgress = 0f;
        this.moving = true;
        // 移动期间恢复轨道速度（动画用），到达后若已绑定则停止
        if (boundToSector && orbitSpeed == 0f) {
            this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        }
    }

    /** 绑定到指定区块（移动结束后静止在该区块上方） */
    public void bindToSector(int sectorId) {
        this.targetSectorId = sectorId;
        this.boundToSector = true;
    }

    /** 解除区块绑定，恢复自由环绕 */
    public void unbindSector() {
        this.boundToSector = false;
        if (this.orbitSpeed == 0f) {
            this.orbitSpeed = (Mathf.random(0.3f, 0.7f) * (Mathf.randomBoolean() ? 1 : -1)) * 0.002f;
        }
    }

    /** 对接另一颗卫星 */
    public void dockWith(Satellite other) {
        if (other == null || other.id == this.id)
            return;
        if (dockedSatellites.contains(other.id))
            return;

        dockedSatellites.add(other.id);
        other.dockedSatellites.add(this.id);

        // 主体接管：id较小的作为主体
        Satellite master = this.id < other.id ? this : other;
        Satellite slave = this.id < other.id ? other : this;
        slave.dockMaster = master.id;

        // 合并地图
        master.mapData.mergeFrom(slave.mapData);
        // 合并导弹仓库
        if (slave.missileModule != null) {
            for (SatelliteMissile missile : SatelliteMissile.map.values()) {
                int amount = slave.missileModule.get(missile);
                if (amount > 0) {
                    master.missileModule.add(missile, amount);
                }
            }
        }
        // visualScale 在 update() 中按 upgradeTier + dock 数量统一重算
    }

    /** 通过升级建筑提升卫星等级并改变外观 */
    public void upgrade(){
        upgradeTier++;
    }

    public boolean isDockMaster() {
        return dockMaster == -1;
    }

    public void rename(String newName) {
        if (newName != null && !newName.trim().isEmpty()) {
            this.name = newName.trim();
        }
    }

    /** 解除与所有其他卫星的对接关系 */
    public void undockAll() {
        dockedSatellites.clear();
        dockMaster = -1;
    }

    public void write(Writes write) {
        write.i(id);
        write.str(name);
        write.str(planet.name);
        write.f(orbitAngle);
        write.f(orbitRadius);
        write.f(orbitTilt);
        write.f(orbitSpeed);
        mapData.write(write);
        write.i(dockMaster);
        write.i(dockedSatellites.size);
        for (int i = 0; i < dockedSatellites.size; i++)
            write.i(dockedSatellites.get(i));
        write.i(targetSectorId);
        write.f(moveProgress);
        write.f(moveDuration);
        write.f(moveFromAngle);
        write.f(moveToAngle);
        write.bool(moving);
        write.bool(boundToSector);

        // 扩展数据
        write.f(solarPower);
        write.i(tier);
        write.i(upgradeTier);
        write.i(storage.size);
        for (arc.struct.IntIntMap.Entry entry : storage) {
            write.i(entry.key);
            write.i(entry.value);
        }
        write.i(scannedSectors.size);
        for (int i = 0; i < scannedSectors.size; i++)
            write.i(scannedSectors.get(i));

        // 卫星导弹仓库（revision >= 10）
        if (missileModule != null) {
            missileModule.write(write);
        } else {
            write.i(0);
            write.i(0);
        }
    }

    public void read(Reads read, byte revision) {
        id = read.i();
        name = read.str();
        String planetName = read.str();
        planet = mindustry.Vars.content.planet(planetName);
        if (planet == null)
            planet = mindustry.content.Planets.serpulo;
        orbitAngle = read.f();
        orbitRadius = read.f();
        orbitTilt = read.f();
        orbitSpeed = read.f();
        mapData = new SatelliteMapData(this);
        mapData.read(read, revision);
        dockMaster = read.i();
        int dockCount = read.i();
        dockedSatellites.clear();
        for (int i = 0; i < dockCount; i++)
            dockedSatellites.add(read.i());
        targetSectorId = read.i();
        moveProgress = read.f();
        moveDuration = read.f();
        moveFromAngle = read.f();
        moveToAngle = read.f();
        moving = read.bool();
        boundToSector = revision >= 3 && read.bool();

        // 扩展数据（revision >= 2）
        if (revision >= 2) {
            solarPower = read.f();
            tier = read.i();
            // revision >= 11 时单独保存 upgradeTier；旧存档通过 tier 反推
            if (revision >= 11) {
                upgradeTier = read.i();
            } else {
                upgradeTier = Math.max(0, tier - 1 - dockedSatellites.size);
            }
            storage.clear();
            int storageSize = read.i();
            for (int i = 0; i < storageSize; i++) {
                int itemId = read.i();
                int amount = read.i();
                storage.put(itemId, amount);
            }
            scannedSectors.clear();
            int scanSize = read.i();
            for (int i = 0; i < scanSize; i++)
                scannedSectors.add(read.i());

            // 卫星导弹仓库（revision >= 10）
            if (missileModule == null)
                missileModule = new SatelliteMissleModule();
            if (revision >= 10) {
                missileModule.read(read);
            } else {
                missileModule.clear();
            }
        }

        // 恢复ID计数器，避免重复
        idCounter = Math.max(idCounter, id);
    }

    @Override
    public String toString() {
        return "Satellite#" + id + "(" + name + ")";
    }
}
