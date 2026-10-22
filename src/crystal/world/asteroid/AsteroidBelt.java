package crystal.world.asteroid;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import mindustry.type.Planet;
import mindustry.type.Sector;

/**
 * 环绕恒星的一段小行星带。
 *
 * <p>小行星带本身固定在恒星参考系里，目标行星沿轨道运行；当行星的轨道角进入
 * {@link #startAngle} ~ {@link #startAngle} + {@link #sweepAngle} 这一段时，
 * 就认为行星“闯入了小行星带”。</p>
 *
 * <p>带到行星中心的射线方向定义撞击面：行星上法线朝向该方向的区块才是正对带面的
 * 区块。默认带子位于行星轨道外侧，因此受击面是行星的外侧半球。</p>
 */
public class AsteroidBelt {
  public final String name;
  /** 小行星带环绕的恒星。 */
  public final Planet star;
  /** 会穿过这条带的行星。 */
  public final Planet target;

  // ---------------- 轨道参数 ----------------

  /** 密集带起始轨道角（度），与 planet.getOrbitAngle() 同一参考系。 */
  public float startAngle = 18f;
  /** 密集带角宽度（度）。 */
  public float sweepAngle = 42f;
  /** 小行星带中心线到恒星中心的距离（世界单位）。 */
  public float orbitRadius = 67f;
  /** 小行星带径向半厚（世界单位）。 */
  public float radialHalfWidth = 9f;
  /** 小行星带在垂直方向上的半厚（世界单位），只影响星图里的视觉。 */
  public float thickness = 2.2f;

  /**
   * 行星公转一周的时间（秒）。原版 lx 的轨道周期接近 5 天，
   * 这里缩短到 5 分钟，保证正常游玩时能遇到这个机制。
   */
  public float orbitPeriodSeconds = 300f;
  /** 是否用 {@link #orbitPeriodSeconds} 覆盖行星原本的 orbitTime。 */
  public boolean overrideOrbitPeriod = true;

  // ---------------- 遭遇参数 ----------------

  /** 进入前多少秒开始预警。 */
  public float warningSeconds = 6f;
  /** 连续流星流中相邻小批次的随机间隔范围（秒）。 */
  public float minImpactInterval = 0.08f;
  public float maxImpactInterval = 0.18f;
  /** 每波流星数量的随机范围。 */
  public int minImpactsPerWave = 1;
  public int maxImpactsPerWave = 2;
  /** 刚进入小行星带时额外砸下的流星数量。 */
  public int entryBurst = 10;
  /** 单颗流星从出现到落地的时长（tick）。 */
  public float fallTicks = 120f;

  // ---------------- 伤害参数 ----------------

  /** 单颗流星爆炸半径的随机范围（世界单位；tilesize = 8）。 */
  public float minImpactRadius = 18f;
  public float maxImpactRadius = 38f;
  /** 单颗流星中心伤害的随机范围，带距离衰减。 */
  public float minImpactDamage = 45f;
  public float maxImpactDamage = 95f;
  /** 每波对所有建筑/单位追加伤害的随机范围。 */
  public float minGlobalDamagePerWave = 1f;
  public float maxGlobalDamagePerWave = 2.5f;
  /** 全图追加伤害的固定节拍（秒），不跟随流星频率一起变密。 */
  public float globalDamageInterval = 1f;
  /** 撞击概率随“靠近赤道”衰减的指数；越大，赤道附近越少。 */
  public float impactFalloff = 1.5f;

  /** 随机一颗流星的爆炸半径。 */
  public float rollImpactRadius() {
    return randomRange(minImpactRadius, maxImpactRadius, 0.1f);
  }

  /** 随机一颗流星的伤害。 */
  public float rollImpactDamage() {
    return randomRange(minImpactDamage, maxImpactDamage, 1f);
  }

  /** 随机下一波流星的距离（秒）。 */
  public float rollImpactInterval() {
    return randomRange(minImpactInterval, maxImpactInterval, 0.03f);
  }

  /** 随机下一波的流星数量。 */
  public int rollImpactsPerWave() {
    int lo = Math.min(minImpactsPerWave, maxImpactsPerWave);
    int hi = Math.max(minImpactsPerWave, maxImpactsPerWave);
    return lo + Mathf.random(Math.max(0, hi - lo));
  }

  /** 随机下一波的全图追加伤害。 */
  public float rollGlobalDamage() {
    return randomRange(minGlobalDamagePerWave, maxGlobalDamagePerWave, 0f);
  }

  private final Vec3 starPos = new Vec3();
  private final Vec3 planetPos = new Vec3();
  private final Vec3 normal = new Vec3();

  public AsteroidBelt(String name, Planet star, Planet target) {
    this.name = name;
    this.star = star;
    this.target = target;
  }

  /** 把参数写回目标行星。 */
  public void apply() {
    if (overrideOrbitPeriod && orbitPeriodSeconds > 0f) {
      target.orbitTime = orbitPeriodSeconds;
    }
  }

  /** 行星轨道半径是否落在小行星带的径向范围内。 */
  public boolean radialOverlap() {
    return Math.abs(target.orbitRadius - orbitRadius) <= radialHalfWidth + target.radius;
  }

  /** 指定轨道角是否在小行星带内。 */
  public boolean containsAngle(float angle) {
    if (!radialOverlap() || sweepAngle <= 0f || star == null) {
      return false;
    }
    if (sweepAngle >= 360f) {
      return true;
    }
    return wrap360(angle - startAngle) <= sweepAngle;
  }

  /** 距离下一次进入小行星带还有多少秒；已经在带内时返回 0。 */
  public float secondsUntilEntry(float angle) {
    if (containsAngle(angle)) {
      return 0f;
    }
    if (target.orbitTime <= 0f || sweepAngle <= 0f) {
      return Float.POSITIVE_INFINITY;
    }
    float relative = wrap360(angle - startAngle);
    float remaining = 360f - relative;
    float degreesPerSecond = 360f / target.orbitTime;
    return remaining / degreesPerSecond;
  }

  /**
   * 行星中心指向小行星带最近中心线点的方向（世界坐标，已归一化）。
   * 这是“正对带面”的方向。
   */
  public Vec3 faceDirection(float angle, Vec3 out) {
    return faceDirection(angle, target.getOrbitAngle(), out);
  }

  /**
   * 可分别指定“带上的角度”和“行星所在角度”，方便测试与提前预览。
   * 正常运行时两个参数相同。
   */
  public Vec3 faceDirection(float beltAngle, float planetAngle, Vec3 out) {
    float nearest = nearestAngle(beltAngle);

    star.getWorldPosition(starPos);
    planetPos.set(starPos)
        .add(Angles.trnsx(planetAngle, target.orbitRadius), 0f, Angles.trnsy(planetAngle, target.orbitRadius));

    out.set(starPos)
        .add(Angles.trnsx(nearest, orbitRadius), 0f, Angles.trnsy(nearest, orbitRadius))
        .sub(planetPos);

    if (out.len2() > 0.0001f) {
      out.nor();
    } else {
      // 行星正好落在带中心线上时，用径向朝外方向兜底。
      out.set(Angles.trnsx(nearest, 1f), 0f, Angles.trnsy(nearest, 1f));
    }
    return out;
  }

  /**
   * 该区块的撞击强度：朝向带子的半球内为 0~1，正对带面最大，赤道附近趋近 0。
   * 半球外为 0。
   */
  public float impactIntensity(Sector sector, Vec3 faceDir) {
    if (sector == null || sector.planet != target || sector.tile == null) {
      return 0f;
    }
    sectorNormal(sector, normal);
    float dot = normal.dot(faceDir);
    if (dot <= 0f) {
      return 0f;
    }
    return Mathf.pow(dot, Math.max(0.1f, impactFalloff));
  }

  /** 是否位于朝向小行星带的半球内。 */
  public boolean affectsSector(Sector sector, Vec3 faceDir) {
    return impactIntensity(sector, faceDir) > 0f;
  }

  /** 区块局部法线转世界方向；与 Planet.lookAt / Sector.getLight 使用同一符号。 */
  public Vec3 sectorNormal(Sector sector, Vec3 out) {
    return out.set(sector.tile.v).rotate(Vec3.Y, -sector.planet.getRotation()).nor();
  }

  /** 带弧线上离指定轨道角最近的点。 */
  public float nearestAngle(float angle) {
    if (sweepAngle >= 360f) {
      return angle;
    }
    float relative = wrap360(angle - startAngle);
    if (relative <= sweepAngle) {
      return angle;
    }

    float start = wrap360(startAngle);
    float end = wrap360(startAngle + sweepAngle);
    float startDist = Math.abs(angleDelta(angle, start));
    float endDist = Math.abs(angleDelta(angle, end));
    return startDist <= endDist ? start : end;
  }

  private static float wrap360(float degrees) {
    float result = degrees % 360f;
    return result < 0f ? result + 360f : result;
  }

  private static float angleDelta(float a, float b) {
    return wrap360(a - b + 180f) - 180f;
  }

  private static float randomRange(float a, float b, float floor) {
    float lo = Math.max(floor, Math.min(a, b));
    float hi = Math.max(lo, Math.max(a, b));
    return Mathf.random(lo, hi);
  }
}
