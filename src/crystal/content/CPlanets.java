package crystal.content;

import arc.graphics.Color;
import crystal.content.blocks.CStroage;
import crystal.graphics.CPal;
import crystal.graphics.g3d.RingMesh2;
import crystal.map.planet.GreenPlanetGenerator;
import crystal.map.planet.LxPlanetGenerator;
import crystal.type.RingPlanet;
import crystal.type.SaturnPlanet;
import mindustry.game.Team;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexSkyMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.graphics.g3d.PlanetGrid;
import mindustry.graphics.g3d.SunMesh;
import mindustry.maps.planet.TantrosPlanetGenerator;
import mindustry.type.Planet;
import mindustry.type.Sector;

public class CPlanets {
  public static Planet csun;
  public static Planet lx;
  public static Planet saturn;

  public static void load() {
    int sectorSize = 3;
    csun = new Planet("scsun", null, 9.5f) {
      {
        grid = PlanetGrid.create(sectorSize);
        sectors.ensureCapacity(grid.tiles.length);
        for (int i = 0; i < grid.tiles.length; i++) {
          sectors.add(new Sector(this, grid.tiles[i]));
        }
        sectorApproxRadius = sectors.first().tile.v.dst(sectors.first().tile.corners[0].v);
        this.bloom = true;
        this.accessible = false;
        sectorSeed = 2048;

        this.meshLoader = () -> new SunMesh(
            this, 4,
            5, 0.3, 1.7, 1.2, 1,
            1.1f,
            Color.valueOf("#CB5DA8"),
            Color.valueOf("#DAB9E8"),
            Color.valueOf("#EAC3EF"),
            Color.valueOf("#DB5FB5"),
            Color.valueOf("#634E87"),
            Color.valueOf("#D489C4"));
      }
    };

    CPlanets.lx = new SaturnPlanet("lx", csun, 1.3f, 3) {
      {
        minZoom = 0.1f; // 最远视角
        maxZoom = 2.5f; // 最近视角
        loadPlanetData = true;
        generator = new TantrosPlanetGenerator();
        // generator = new GreenPlanetGenerator();
        // generator = new LxPlanetGenerator();
        meshLoader = () -> new HexMesh(this, 6);
        cloudMeshLoader = () -> new MultiMesh(
            new HexSkyMesh(this, 11, 0.15f, 0.13f, 5, new Color().set(Color.valueOf("#ffffff")).mul(0.9f).a(0.75f), 2,
                0.45f, 0.9f,
                0.38f),
            new HexSkyMesh(this, 11, 0.15f, 0.13f, 5, new Color().set(CPal.light_blue1).mul(0.9f).a(0.75f), 2,
                0.45f, 0.9f,
                0.38f),
            new HexSkyMesh(this, 1, 0.6f, 0.16f, 5, Color.white.cpy().lerp(CPal.blue1, 0.55f).a(0.75f), 2,
                0.45f, 1f,
                0.41f));
        cloudMeshLoader = () -> new MultiMesh();
        defaultCore = CStroage.core1;
        launchCapacityMultiplier = 0.7f;
        sectorSeed = 8888;
        allowWaves = true;
        orbitRadius = 60;
        allowLegacyLaunchPads = true;
        allowSectorInvasion = true;
        allowLaunchSchematics = true;
        enemyCoreSpawnReplace = true;
        allowLaunchLoadout = true;
        prebuildBase = false;
        ruleSetter = r -> {
          r.waveTeam = Team.crux;
          r.placeRangeCheck = true;
          r.showSpawns = true;
        };
        showRtsAIRule = true;
        iconColor = Color.valueOf("#D2F0FFFF");
        atmosphereColor = Color.valueOf("#75F7F7FF");
        atmosphereRadIn = 0.08f;
        atmosphereRadOut = 0.2f;
        startSector = 0;
        alwaysUnlocked = true;
        bloom = true;
        campaignRuleDefaults.showSpawns = true;
        updateLighting = true;
        allowSelfSectorLaunch = true;
        landCloudColor = Color.valueOf("#CB5DA8").cpy().a(0.5f);
      }
    };
    // 在你的模组 content 加载处
    saturn = new RingPlanet("saturn", csun, 1.6f, 3) {
      {
        // ---- 星球本体：气体巨星，金黄-棕褐条纹 ----
        meshLoader = () -> new NoiseMesh(this, 8, 5, 1.1f, 5, 0.55f, 2.2f, 0.12f,
            Color.valueOf("e9c46a"), Color.valueOf("d4a373"), 4, 0.5f, 1.8f, 0.25f);

        // ---- 土星环：多层绚丽环带 ----
        ringMeshLoader = () -> new RingMesh2(new RingMesh2.Band[] {
            // D环 — 内层极淡
            new RingMesh2.Band(1.25f, 1.35f, Color.valueOf("a68a64"), 0.25f),
            // C环 — 暗淡灰
            new RingMesh2.Band(1.37f, 1.52f, Color.valueOf("c9ada7"), 0.45f),
            // B环 — 最亮最宽，主金黄
            new RingMesh2.Band(1.54f, 1.92f, Color.valueOf("e9c46a"), 0.95f),
            // 卡西尼缝 — 深色间隙
            new RingMesh2.Band(1.94f, 1.98f, Color.valueOf("6f4e37"), 0.08f),
            // A环 — 外层明亮
            new RingMesh2.Band(2.00f, 2.22f, Color.valueOf("f4a261"), 0.80f),
            // F环 — 最外层细窄
            new RingMesh2.Band(2.28f, 2.32f, Color.valueOf("d4a373"), 0.55f),
        }, 512, // 512 段，足够平滑
            26.7f, // 环倾斜角（度），与土星一致
            42); // 噪声种子

        // ---- 大气与轨道 ----
        ringMaxRadius = 2.32f;
        hasAtmosphere = true;
        alwaysUnlocked = true;
        atmosphereColor = Color.valueOf("e9c46a").a(0.25f);
        atmosphereRadIn = 0.05f;
        atmosphereRadOut = 0.35f;
        bloom = true;
        minZoom = 0.1f; // 最远视角
        maxZoom = 2.5f; // 最近视角
        generator = new TantrosPlanetGenerator();

        rotateTime = 10.7f * 60f; // ~10.7 小时自转
        orbitRadius = 95f;
        orbitTime = 29.5f * 60f * 60f; // ~29.5 年公转
        orbitSpacing = 18f;
        lightColor = Color.valueOf("fff8e7");
        iconColor = Color.valueOf("e9c46a");
      }
    };

  }
}
