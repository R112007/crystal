package crystal.world.time;

import arc.struct.*;
import arc.util.io.*;

import java.io.*;

/**
 * 单帧世界快照。保存游戏逻辑层需要恢复的全部状态：
 * - 全局时间/随机/游戏状态
 * - 动态实体（单位、子弹、火焰、液体坑、天气、标签等）
 * - 所有建筑（含传送带物品位置、工厂进度、库存等）
 */
public class TimeRewindFrame {
    /** 捕获时的逻辑帧序号 */
    public long tick;
    /** 捕获时的全局时间 */
    public float time, delta;
    /** 游戏波次状态 */
    public float waveTime;
    public int wave;
    /** 敌军计数与游戏结束标记 */
    public int enemies;
    public boolean gameOver;
    /** 全局随机状态 */
    public long randSeed0, randSeed1;

    /** 动态实体序列化数据 */
    public byte[] entityData;
    /** 建筑序列化数据 */
    public byte[] buildingData;

    public TimeRewindFrame() {
    }

    public TimeRewindFrame(byte[] entityData, byte[] buildingData) {
        this.entityData = entityData;
        this.buildingData = buildingData;
    }

    public void write(Writes write) {
        write.l(tick);
        write.f(time);
        write.f(delta);
        write.f(waveTime);
        write.i(wave);
        write.i(enemies);
        write.b(gameOver ? 1 : 0);
        write.l(randSeed0);
        write.l(randSeed1);

        write.i(entityData.length);
        write.b(entityData);

        write.i(buildingData.length);
        write.b(buildingData);
    }

    public void read(Reads read) {
        tick = read.l();
        time = read.f();
        delta = read.f();
        waveTime = read.f();
        wave = read.i();
        enemies = read.i();
        gameOver = read.b() == 1;
        randSeed0 = read.l();
        randSeed1 = read.l();

        int elen = read.i();
        if (elen < 0 || elen > 64 * 1024 * 1024)
            throw new RuntimeException("entityData length out of bounds: " + elen);
        entityData = new byte[elen];
        try {
            read.input.readFully(entityData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int blen = read.i();
        if (blen < 0 || blen > 64 * 1024 * 1024)
            throw new RuntimeException("buildingData length out of bounds: " + blen);
        buildingData = new byte[blen];
        try {
            read.input.readFully(buildingData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 估算本帧内存占用，用于日志/调试。 */
    public int approxSize() {
        return 64 + (entityData == null ? 0 : entityData.length) + (buildingData == null ? 0 : buildingData.length);
    }
}
