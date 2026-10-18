package crystal.dbg;

import arc.Core;
import crystal.aviation.Satellite;
import crystal.aviation.SatelliteManager;
import crystal.aviation.SatelliteSectorInfoManager;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Planets;
import mindustry.core.GameState.State;
import mindustry.game.SectorInfo;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.Sector;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

/**
 * Headless regression test for satellite background item production/injection.
 *
 * Run with a fresh data directory: the first check intentionally starts with no
 * SatelliteSectorInfo records, which is the state that previously stalled all
 * background injection.
 */
public class SatelliteItemSimulationTest {
    public static void run(String[] args) {
        boolean ok = true;
        try {
            ok = runChecks();
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        System.out.println("[SAT-SIM] " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }

    private static boolean runChecks() {
        Sector target = Planets.serpulo.sectors.first();
        Item item = Items.copper;

        Vars.state.teams = new Teams();
        Vars.state.set(State.playing);
        Vars.state.gameOver = false;
        Vars.state.rules.sector = target;
        Vars.state.rules.defaultTeam = Team.sharded;
        Vars.state.rules.waveTeam = Team.crux;

        CoreBlock.CoreBuild core = (CoreBlock.CoreBuild) Blocks.coreShard.newBuilding();
        core.block = Blocks.coreShard;
        core.team = Team.sharded;
        core.items = new ItemModule();
        core.storageCapacity = 1000;
        Team.sharded.data().cores.add(core);

        Satellite satellite = new Satellite(Planets.serpulo, "simulation-probe");
        satellite.bindToSector(target.id);
        satellite.injectMode = true;
        satellite.items.add(item, 100);
        satellite.setInjectorConfig(1, item, 7);
        SatelliteManager.satellites.put(satellite.id, satellite);
        SatelliteManager.currentSatelliteId = -1;

        boolean ok = true;
        // No sector-info record exists yet. Injection must still run.
        SatelliteSectorInfoManager.runBackgroundSimulation(30f);
        ok &= check("one interval injects without prior launch statistics", 93, satellite.items.get(item), 7,
                core.items.get(item));
        ok &= check("the shared injection timer is reset after a full interval", 0f, satellite.injectTimer);

        // Fractional elapsed time must carry between calls.
        satellite.simulateInject(15f);
        ok &= check("half an interval does not inject", 93, satellite.items.get(item), 7, core.items.get(item));
        ok &= check("half an interval is retained", 15f, satellite.injectTimer);
        satellite.simulateInject(15f);
        ok &= check("the retained half completes the next interval", 86, satellite.items.get(item), 14,
                core.items.get(item));
        ok &= check("the injection timer wraps after completion", 0f, satellite.injectTimer);

        core.items.set(item, 997);
        SatelliteSectorInfoManager.runBackgroundSimulation(30f);
        ok &= check("destination capacity clips the transfer", 83, satellite.items.get(item), 1000,
                core.items.get(item));

        satellite.items.set(item, 2);
        SatelliteSectorInfoManager.runBackgroundSimulation(30f);
        ok &= check("capacity-rejected items remain in the satellite", 2, satellite.items.get(item), 1000,
                core.items.get(item));

        satellite.injectMode = false;
        ok &= checkSaveRoundTrip(satellite.id, item);
        ok &= checkUnloadedSectorExport();
        return ok;
    }

    private static boolean checkSaveRoundTrip(int satelliteId, Item item) {
        Satellite before = SatelliteManager.get(satelliteId);
        before.injectTimer = 12.5f;
        byte[] saved = SatelliteManager.saveBytes();
        SatelliteManager.loadBytes(saved);
        Satellite after = SatelliteManager.get(satelliteId);

        boolean ok = after != null;
        ok &= check("satellite inventory survives save/load", 2, after == null ? -1 : after.items.get(item));
        ok &= check("inject mode survives save/load", false, after != null && after.injectMode);
        ok &= check("inject timer survives save/load", 12.5f, after == null ? 0f : after.injectTimer);
        var config = after == null ? null : after.injectorConfigs.get(1);
        ok &= check("injector configuration survives save/load", 7, config == null ? -1 : config.amount);
        return ok;
    }

    private static boolean checkUnloadedSectorExport() {
        Item item = Items.copper;
        Sector source = Planets.serpulo.sectors.get(1);
        if (source == Vars.state.rules.sector) {
            source = Planets.serpulo.sectors.get(2);
        }

        source.save = createSaveSlotStub();
        source.info = new SectorInfo();
        source.info.hasCore = true;
        source.info.items = new ItemSeq();
        source.info.storageCapacity = 1000;
        source.info.attack = false;
        source.info.waves = false;

        Satellite satellite = new Satellite(Planets.serpulo, "export-probe");
        satellite.items.clear();
        SatelliteManager.satellites.put(satellite.id, satellite);

        SatelliteSectorInfoManager.recordItemLaunch(source, satellite, item, 10);
        SatelliteSectorInfoManager.refreshAllStats();
        var stats = SatelliteSectorInfoManager.getItemExportsToSatellite(satellite);
        for (var itemStats : stats.values()) {
            var stat = itemStats.get(item);
            if (stat != null) {
                // Use a stable rate rather than depending on persisted window samples.
                stat.mean = 10f;
            }
        }
        SatelliteSectorInfoManager.runBackgroundSimulation(30f);

        return check("an unloaded sector exports at its recorded rate", 300, satellite.items.get(item));
    }

    private static mindustry.game.Saves.SaveSlot createSaveSlotStub() {
        try {
            Class<?> slotType = Class.forName("mindustry.game.Saves$SaveSlot");
            var ctor = slotType.getDeclaredConstructor(Class.forName("mindustry.game.Saves"),
                    arc.files.Fi.class);
            ctor.setAccessible(true);
            return (mindustry.game.Saves.SaveSlot) ctor.newInstance(null,
                    Core.files.local("satellite-sim-probe"));
        } catch (Throwable t) {
            throw new RuntimeException("cannot create save-slot probe", t);
        }
    }

    private static boolean check(String label, int expectedSatellite, int actualSatellite, int expectedCore,
            int actualCore) {
        boolean ok = expectedSatellite == actualSatellite && expectedCore == actualCore;
        System.out.println("[SAT-SIM] " + label + ": satellite=" + actualSatellite + "/" + expectedSatellite
                + ", core=" + actualCore + "/" + expectedCore + (ok ? " OK" : " FAIL"));
        return ok;
    }

    private static boolean check(String label, float expected, float actual) {
        boolean ok = Math.abs(expected - actual) < 0.001f;
        System.out.println("[SAT-SIM] " + label + ": " + actual + "/" + expected + (ok ? " OK" : " FAIL"));
        return ok;
    }

    private static boolean check(String label, int expected, int actual) {
        boolean ok = expected == actual;
        System.out.println("[SAT-SIM] " + label + ": " + actual + "/" + expected + (ok ? " OK" : " FAIL"));
        return ok;
    }

    private static boolean check(String label, boolean expected, boolean actual) {
        boolean ok = expected == actual;
        System.out.println("[SAT-SIM] " + label + ": " + actual + "/" + expected + (ok ? " OK" : " FAIL"));
        return ok;
    }
}
