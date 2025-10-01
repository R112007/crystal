package crystal.content;

import arc.graphics.Color;
import arc.util.Log;
import crystal.map.planet.LxPlanetGenerator;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexSkyMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.PlanetGrid;
import mindustry.graphics.g3d.SunMesh;
import mindustry.maps.planet.ErekirPlanetGenerator;
import mindustry.maps.planet.SerpuloPlanetGenerator;
import mindustry.maps.planet.TantrosPlanetGenerator;
import mindustry.type.Planet;
import mindustry.type.Sector;

public class CPlanets {
  public static Planet csun;
  public static Planet lx;

  public static void load() {
    int sectorSize = 3;
    Log.info("加载sun");
    csun = new Planet("scsun", null, 9.5f) {
      {
        Log.info("加载size");
        grid = PlanetGrid.create(sectorSize);
        sectors.ensureCapacity(grid.tiles.length);
        for (int i = 0; i < grid.tiles.length; i++) {
          sectors.add(new Sector(this, grid.tiles[i]));
        }
        sectorApproxRadius = sectors.first().tile.v.dst(sectors.first().tile.corners[0].v);
        Log.info("加载完size");
        this.bloom = true;
        this.accessible = false;
        sectorSeed = 2048;
        Log.info("加载完seed sun");

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
    Log.info("加载lx");
    CPlanets.lx = new Planet("lx", csun, 1.2f, 3) {
      {
        loadPlanetData = true;
        orbitRadius = 45f;
        generator = new LxPlanetGenerator();
        meshLoader = () -> new HexMesh(this, 6);
        cloudMeshLoader = () -> new MultiMesh(
            new HexSkyMesh(this, 11, 0.15f, 0.13f, 5, new Color().set(Color.valueOf("#CB5DA8")).mul(0.9f).a(0.75f), 2,
                0.45f, 0.9f,
                0.38f),
            new HexSkyMesh(this, 1, 0.6f, 0.16f, 5, Color.white.cpy().lerp(Color.valueOf("#EAC3EF"), 0.55f).a(0.75f), 2,
                0.45f, 1f,
                0.41f));

        launchCapacityMultiplier = 0.7f;
        sectorSeed = 114514;
        Log.info("加载完seed lx");
        allowWaves = true;
        allowLegacyLaunchPads = true;
        allowWaveSimulation = true;
        allowSectorInvasion = true;
        allowLaunchSchematics = true;
        enemyCoreSpawnReplace = true;
        allowLaunchLoadout = true;
        // doesn't play well with configs
        prebuildBase = false;
        ruleSetter = r -> {
          r.waveTeam = Team.crux;
          r.placeRangeCheck = false;
          r.showSpawns = false;
          r.coreDestroyClear = true;
        };
        showRtsAIRule = true;
        iconColor = Color.valueOf("#D2F0FFFF");
        atmosphereColor = Color.valueOf("#75F7F7FF");
        atmosphereRadIn = 0.08f;
        atmosphereRadOut = 1.2f;
        startSector = 0;
        alwaysUnlocked = true;
        bloom = true;
        updateLighting = false;
        allowSelfSectorLaunch = true;
        landCloudColor = Color.valueOf("#CB5DA8").cpy().a(0.5f);
      }
    };
  }
}
