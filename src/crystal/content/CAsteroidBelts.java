package crystal.content;

import crystal.graphics.g3d.AsteroidBeltMesh;
import crystal.world.asteroid.AsteroidBelt;
import mindustry.type.Planet;

/** 小行星带内容定义。想改机制手感，优先改这里的参数。 */
public class CAsteroidBelts {
  public static AsteroidBelt lxBelt;

  public static void load() {
    if (CPlanets.lx == null || CPlanets.lx.parent == null) {
      return;
    }

    Planet star = CPlanets.lx.parent;
    AsteroidBelt belt = new AsteroidBelt("lx-asteroid-belt", star, CPlanets.lx);

    // 环绕恒星的密集带。lx 的轨道半径是 60，带中心线也在 60，
    // 行星会真的穿过带子；受击面是朝外的半球。
    belt.startAngle = 18f;
    belt.sweepAngle = 42f;
    belt.orbitRadius = CPlanets.lx.orbitRadius;
    belt.radialHalfWidth = 9f;
    belt.thickness = 2.2f;

    // 5 分钟公转一周，约每 5 分钟遇到一次密集带，持续约 35 秒。
    belt.orbitPeriodSeconds = 300f;
    belt.overrideOrbitPeriod = true;
    belt.warningSeconds = 6f;

    // 连续流星流：小批次间隔和数量都在区间内随机。
    belt.minImpactInterval = 0.08f;
    belt.maxImpactInterval = 0.18f;
    belt.minImpactsPerWave = 1;
    belt.maxImpactsPerWave = 2;
    belt.entryBurst = 10;
    belt.fallTicks = 120f;

    // 每颗流星的爆炸半径和伤害独立随机；全图追加伤害也在区间内随机，
    // 保证所有队伍的建筑和单位在整场流星雨里都会持续掉血。
    belt.minImpactRadius = 18f;
    belt.maxImpactRadius = 38f;
    belt.minImpactDamage = 45f;
    belt.maxImpactDamage = 95f;
    belt.minGlobalDamagePerWave = 1f;
    belt.maxGlobalDamagePerWave = 2.5f;
    belt.globalDamageInterval = 1f;
    belt.impactFalloff = 1.5f;

    belt.apply();
    lxBelt = belt;

    // 在星图上把这条带画出来，这样行星公转进入小行星带是看得见的。
    star.cloudMeshLoader = () -> new AsteroidBeltMesh(belt);
    star.clipRadius = Math.max(star.clipRadius, belt.orbitRadius + belt.radialHalfWidth + 2f);
  }
}
