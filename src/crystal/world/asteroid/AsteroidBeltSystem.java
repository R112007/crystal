package crystal.world.asteroid;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.util.Time;
import crystal.content.CAsteroidBelts;
import mindustry.content.Blocks;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import static mindustry.Vars.*;

/**
 * 星球闯入小行星带的运行时逻辑。
 *
 * <p>只有当前正在游玩的区块、并且该区块位于“正对带面”的半球上时，才会下流星。
 * 流星落地时通过 {@link Damage#damage} 的 team = null 形式伤害所有队伍，
 * 另外每波对全图建筑/单位追加一次小额伤害，保证所有队伍都会被持续波及。</p>
 */
public class AsteroidBeltSystem {
  private static final Vec3 face = new Vec3();
  private static final Seq<Building> buildingSnapshot = new Seq<>();
  private static final Seq<Unit> unitSnapshot = new Seq<>();

  private static AsteroidBelt belt;
  private static boolean initialized;
  private static boolean inside;
  private static boolean warned;
  private static boolean sectorHit;
  private static boolean debugForced;
  private static boolean orbitInside;
  private static boolean orbitWarned;
  private static float impactTimer;
  private static float globalDamageTimer;
  private static Sector lastSector;

  private static float impactX, impactY;

  private AsteroidBeltSystem() {
  }

  public static void init() {
    if (CAsteroidBelts.lxBelt != null) {
      setBelt(CAsteroidBelts.lxBelt);
    }
    if (!initialized) {
      Events.run(Trigger.update, AsteroidBeltSystem::update);
      initialized = true;
    }
  }

  public static AsteroidBelt belt() {
    return belt;
  }

  public static void setBelt(AsteroidBelt newBelt) {
    belt = newBelt;
    reset();
    if (belt != null) {
      belt.apply();
    }
  }

  /** 只用于测试/调试：立即重置运行时状态。 */
  public static void reset() {
    resetSectorState();
    resetOrbitState();
    lastSector = null;
  }

  private static void resetSectorState() {
    inside = false;
    warned = false;
    sectorHit = false;
    debugForced = false;
    impactTimer = 0f;
    globalDamageTimer = 0f;
    lastSector = null;
  }

  private static void resetOrbitState() {
    orbitInside = false;
    orbitWarned = false;
  }

  private static void update() {
    AsteroidBelt current = belt;
    if (current == null || !state.isGame() || net.client()) {
      reset();
      return;
    }
    // 星球界面 / 暂停时也要跟踪轨道位置，否则看不到“进入小行星带”的提示。
    updateOrbitStatus(current);
    if (state.isPaused()) {
      return;
    }

    Sector sector = state.rules.sector;
    if (sector == null || sector.planet == null || sector.planet != current.target) {
      resetSectorState();
      return;
    }
    if (sector != lastSector) {
      lastSector = sector;
      sectorHit = false;
      impactTimer = 0f;
      globalDamageTimer = 0f;
    }

    Planet planet = sector.planet;
    float angle = planet.getOrbitAngle();

    handleDebugKeys();

    // 调试模式：跳过轨道/朝向判定，持续在当前区块下流星。
    if (debugForced) {
      current.faceDirection(angle, face);
      sectorHit = true;
      updateImpactClock(sector, current, 1f);
      return;
    }

    boolean nowInside = current.containsAngle(angle);

    if (!nowInside && !inside) {
      float eta = current.secondsUntilEntry(angle);
      if (!warned && eta <= current.warningSeconds && eta < Float.POSITIVE_INFINITY) {
        warned = true;
        announce("[orange]小行星带接近：约 " + Mathf.ceil(eta) + " 秒后进入密集带");
      }
    }

    if (nowInside && !inside) {
      inside = true;
      warned = false;
      impactTimer = 0f;
      globalDamageTimer = 0f;
      onEnter(sector, current, angle);
    } else if (!nowInside && inside) {
      inside = false;
      impactTimer = 0f;
      globalDamageTimer = 0f;
      announce("[green]行星已离开小行星带");
    }

    if (!nowInside) {
      return;
    }

    current.faceDirection(angle, face);
    float intensity = current.impactIntensity(sector, face);
    boolean hit = intensity > 0f;
    if (hit && !sectorHit) {
      sectorHit = true;
      announce("[scarlet]本区块已进入小行星带撞击半球");
    } else if (!hit && sectorHit) {
      sectorHit = false;
      announce("[green]本区块已离开小行星带撞击半球");
    }
    if (!hit) {
      return;
    }

    updateImpactClock(sector, current, intensity);
  }

  private static void updateImpactClock(Sector sector, AsteroidBelt current, float intensity) {
    impactTimer -= Time.delta / 60f;
    if (impactTimer > 0f) {
      return;
    }
    impactTimer = Math.max(0.03f, current.rollImpactInterval());
    // 半球内都会被撞，但越靠近赤道（intensity 越低）命中批次越少。
    if (Mathf.chance(Mathf.clamp(intensity, 0f, 1f))) {
      spawnWave(sector, current);
    }
  }

  /**
   * 独立于当前区块跟踪目标行星的轨道状态。
   * 这样在星球界面、暂停中或玩家待在其他星球时，也能收到进入/离开小行星带的提示。
   */
  private static void updateOrbitStatus(AsteroidBelt current) {
    if (current == null || current.target == null) {
      return;
    }

    float angle = current.target.getOrbitAngle();
    boolean nowInside = current.containsAngle(angle);

    if (!nowInside && !orbitInside) {
      float eta = current.secondsUntilEntry(angle);
      if (!orbitWarned && eta <= current.warningSeconds && eta < Float.POSITIVE_INFINITY) {
        orbitWarned = true;
        if (shouldAnnounceGlobal(current)) {
          announcePlanet("[orange]" + current.target.localizedName + " 即将进入小行星带（约 "
              + Mathf.ceil(eta) + " 秒）");
        }
      }
    }

    if (nowInside && !orbitInside) {
      orbitInside = true;
      orbitWarned = false;
      if (shouldAnnounceGlobal(current)) {
        announcePlanet("[scarlet]" + current.target.localizedName + " 已进入小行星带");
      }
    } else if (!nowInside && orbitInside) {
      orbitInside = false;
      orbitWarned = false;
      if (shouldAnnounceGlobal(current)) {
        announcePlanet("[green]" + current.target.localizedName + " 已离开小行星带");
      }
    }
  }

  private static boolean shouldAnnounceGlobal(AsteroidBelt current) {
    return state.isPaused()
        || state.rules.sector == null
        || state.rules.sector.planet != current.target;
  }

  private static void announcePlanet(String message) {
    if (!headless && ui != null) {
      ui.announce(message, 3f);
    }
  }

  /** 立即在当前区块触发一波流星，用于测试。 */
  public static boolean debugTriggerWave() {
    AsteroidBelt current = belt;
    if (current == null || !state.isGame() || state.rules.sector == null
        || state.rules.sector.planet != current.target) {
      announce("[orange]小行星测试：当前区块不在目标星球上");
      return false;
    }
    spawnWave(state.rules.sector, current);
    announce("[accent]小行星测试：已手动触发一波流星");
    return true;
  }

  /** 开启/关闭强制遭遇；开启后不再检查行星轨道位置和区块朝向。 */
  public static void debugSetForced(boolean forced) {
    if (forced && belt != null && state.isGame()
        && (state.rules.sector == null || state.rules.sector.planet != belt.target)) {
      debugForced = false;
      announce("[orange]小行星测试：请先进入 lx 的区块再开启强制遭遇");
      return;
    }
    debugForced = forced;
    inside = forced;
    warned = false;
    sectorHit = forced;
    impactTimer = 0f;
    announce(forced
        ? "[accent]小行星测试：强制遭遇已开启（Ctrl+Shift+G 关闭）"
        : "[accent]小行星测试：强制遭遇已关闭");
  }

  public static boolean isDebugForced() {
    return debugForced;
  }

  private static void handleDebugKeys() {
    if (headless || Core.scene == null || Core.scene.hasDialog() || Core.scene.hasKeyboard()) {
      return;
    }
    if (Core.input.ctrl() && Core.input.shift()) {
      if (Core.input.keyTap(KeyCode.b)) {
        debugTriggerWave();
      }
      if (Core.input.keyTap(KeyCode.g)) {
        debugSetForced(!debugForced);
      }
    }
  }

  private static void onEnter(Sector sector, AsteroidBelt current, float angle) {
    current.faceDirection(angle, face);
    float intensity = current.impactIntensity(sector, face);
    if (intensity > 0f) {
      sectorHit = true;
      announce(intensity > 0.55f
          ? "[scarlet]星球闯入小行星带！本区块正对带面，撞击密集"
          : "[scarlet]星球闯入小行星带！本区块位于撞击半球，靠近赤道，撞击较少");
      int burst = Math.max(1, Math.round(current.entryBurst * intensity));
      for (int i = 0; i < burst; i++) {
        int index = i;
        Time.run(index * 4f, () -> spawnMeteor(sector, current));
      }
    } else {
      sectorHit = false;
      announce("[orange]星球闯入小行星带，本区块不在撞击面");
    }
  }

  private static void spawnWave(Sector sector, AsteroidBelt current) {
    // 流星可以很密，但全图追加伤害保持独立节拍，避免总伤害随频率暴涨。
    globalDamageTimer -= Time.delta / 60f;
    if (globalDamageTimer <= 0f) {
      globalDamageTimer = Math.max(0.25f, current.globalDamageInterval);
      float globalDamage = current.rollGlobalDamage();
      if (globalDamage > 0f) {
        damageAll(globalDamage);
      }
    }
    int count = current.rollImpactsPerWave();
    for (int i = 0; i < count; i++) {
      int index = i;
      Time.run(index * 3f, () -> spawnMeteor(sector, current));
    }
  }

  private static void spawnMeteor(Sector sector, AsteroidBelt current) {
    if (state.rules.sector != sector) {
      return;
    }

    pickImpactPoint();
    final float x = impactX;
    final float y = impactY;
    // 全周随机入射方向，避免所有陨石都从同一侧飞进来。
    final float fromAngle = Mathf.random(360f);
    final float radius = current.rollImpactRadius();
    final float damage = current.rollImpactDamage();
    final float sizeScale = Mathf.clamp(radius / 28f, 0.6f, 1.6f);
    float startDistance = 600f;
    if (!headless && Core.camera != null) {
      float diagonal = Mathf.sqrt(Core.camera.width * Core.camera.width + Core.camera.height * Core.camera.height);
      // 起点只略微超出画面对角线，保证从屏幕外进入，又不会飞得太远。
      startDistance = Math.max(60f, diagonal * 1.05f + 10f);
    }

    if (!headless) {
      AsteroidFx.falling.at(x, y, fromAngle, Color.white,
          new AsteroidFx.FallData(sizeScale, startDistance));
    }

    Time.run(current.fallTicks, () -> impact(sector, x, y, radius, damage));
  }

  private static void impact(Sector sector, float x, float y, float radius, float damage) {
    if (!state.isGame() || state.rules.sector != sector) {
      return;
    }

    if (!headless) {
      AsteroidFx.impactFlash.at(x, y, radius, Color.white);
      AsteroidFx.impactRing.at(x, y, radius, Color.white);
      if (Mathf.chance(0.35f)) {
        Effect.scorch(x, y, 2);
      }
      Sounds.explosion.at(x, y, 0.6f);
      Effect.shake(2.5f, 8f, x, y);
    }

    // team = null：不区分敌我，所有队伍的建筑和单位都会在爆炸范围内受伤。
    Damage.damage(null, x, y, radius, damage, false, true, true);
  }

  /** 每波对全图所有队伍追加一次小额伤害。 */
  private static void damageAll(float amount) {
    buildingSnapshot.clear();
    Groups.build.each(build -> {
      if (build != null && build.isValid() && build.health > 0f) {
        buildingSnapshot.add(build);
      }
    });
    for (Building build : buildingSnapshot) {
      if (build.isValid() && build.health > 0f) {
        build.damage(null, amount);
      }
    }

    unitSnapshot.clear();
    Groups.unit.each(unit -> {
      if (unit != null && unit.isValid() && unit.health > 0f) {
        unitSnapshot.add(unit);
      }
    });
    for (Unit unit : unitSnapshot) {
      if (unit.isValid() && unit.health > 0f) {
        unit.damage(amount);
      }
    }
  }

  /**
   * 全图随机落点，优先落在镜头附近，但不以建筑/单位为目标。
   * 会跳过环境墙、建筑占位、地图边界外和地图阴影区域。
   */
  private static void pickImpactPoint() {
    for (int i = 0; i < 32; i++) {
      if (!headless && Core.camera != null && Mathf.chance(0.65f)) {
        float rangeX = Math.min(Core.camera.width, world.unitWidth()) * 0.48f;
        float rangeY = Math.min(Core.camera.height, world.unitHeight()) * 0.48f;
        impactX = Core.camera.position.x + Mathf.range(rangeX);
        impactY = Core.camera.position.y + Mathf.range(rangeY);
      } else {
        impactX = Mathf.random(12f, Math.max(12f, world.unitWidth() - 12f));
        impactY = Mathf.random(12f, Math.max(12f, world.unitHeight() - 12f));
      }
      clampToWorld();
      if (isValidImpactPoint(impactX, impactY)) {
        return;
      }
    }

    // 镜头周围全是墙/阴影时，从地图中扫一块合法空地。
    int total = world.tiles.width * world.tiles.height;
    int start = total <= 0 ? 0 : Mathf.random(total - 1);
    for (int i = 0; i < total; i++) {
      Tile tile = world.tiles.geti((start + i) % total);
      if (isValidImpactTile(tile)) {
        impactX = tile.worldx();
        impactY = tile.worldy();
        return;
      }
    }

    impactX = world.unitWidth() / 2f;
    impactY = world.unitHeight() / 2f;
  }

  private static boolean isValidImpactPoint(float x, float y) {
    return isValidImpactTile(world.tileWorld(x, y));
  }

  private static boolean isValidImpactTile(Tile tile) {
    if (tile == null || !world.isInMapArea(tile.x, tile.y)) {
      return false;
    }
    // 环境墙、矿石、建筑等都算不可落点；只落在地面空地上。
    if (tile.block() != null && tile.block() != Blocks.air) {
      return false;
    }
    if (tile.solid()) {
      return false;
    }
    Floor floor = tile.floor();
    if (floor == null || floor == Blocks.air) {
      return false;
    }
    if (floor.name.startsWith("dark-panel") || floor.name.equals("dark-metal")) {
      return false;
    }
    // 阴影/边境区域不落流星。
    return world.getDarkness(tile.x, tile.y) < 2f;
  }

  private static void clampToWorld() {
    impactX = Mathf.clamp(impactX, 8f, Math.max(8f, world.unitWidth() - 8f));
    impactY = Mathf.clamp(impactY, 8f, Math.max(8f, world.unitHeight() - 8f));
  }

  private static void announce(String message) {
    if (!headless && ui != null) {
      ui.showInfoFade(message);
    }
  }
}
