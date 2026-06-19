package crystal.world.blocks.stroage;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import crystal.gen.Corec;
import crystal.ui.fragments.CoreInventoryFragment;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.game.EventType.*;
import mindustry.gen.Unit;

import static mindustry.Vars.*;

public class MoveCoreSystem {
  public static final ObjectMap<Team, Seq<Corec>> mobileCores = new ObjectMap<>();
  public static final CoreInventoryFragment coreInventory = new CoreInventoryFragment();
  private static final float tapRange = 6f;

  public static void init() {
    Events.on(ClientLoadEvent.class, e -> {
      coreInventory.build(Vars.ui.hudGroup);
    });
    Events.on(TapEvent.class, e -> {
      Unit unit = Units.closest(e.player.team(), e.tile.worldx(), e.tile.worldy(),
          tapRange * tilesize, u -> !u.dead && u instanceof Corec);
      if (unit instanceof Corec core) {
        DLog.info("点击核心单位: " + core.type().name);
        coreInventory.showFor(core);
      } else {
        if (coreInventory.isShown()) {
          coreInventory.hide();
        }
      }
    });
    Events.on(UnitDestroyEvent.class, e -> {
      if (e.unit instanceof Corec core) {
        CoreInjector.removeCore(core.team().data(), core);
        if (Vars.state.isCampaign() && core.team().data().cores.isEmpty()) {
          mindustry.type.Sector sector = Vars.state.getSector();
          Events.fire(new SectorLoseEvent(sector));
          sector.info.hasCore = false;
          sector.save = null;
          sector.saveInfo();
        }
      }
    });
    Events.on(WorldLoadEvent.class, e -> {
      mobileCores.clear();
    });
  }

  public static void showCoreInventory(Corec core) {
    coreInventory.showFor(core);
  }

  public static void hideCoreInventory() {
    coreInventory.hide();
  }

  public static Seq<Corec> getCores(Team team) {
    return mobileCores.get(team, Seq::new);
  }
}
