package crystal.world.blocks.effect;

import arc.Events;
import arc.struct.Seq;
import crystal.Crystal;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild;

public class ScrambleUnitLanding extends Block {
  public Influence influence = Influence.destory;

  public ScrambleUnitLanding(String name) {
    super(name);
    update = true;
    solid = true;
  }

  public class ScrambleUnitLandingBuild extends Building {
    Seq<UnitFactoryBuild> unitFactorys = new Seq<>();

    @Override
    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      Building building = super.init(tile, team, shouldAdd, rotation);
      Events.on(UnitCreateEvent.class, e -> {
        if (e.spawner != null && e.spawnerUnit == null && e.unit.team != Vars.player.team()) {
          if (influence == Influence.destory) {
            e.unit.kill();
          } else if (influence == Influence.teamChange) {
            e.unit.team(Vars.state.rules.waveTeam);
          }
        }
      });
      return building;
    }

    @Override
    public void updateTile() {
      if (Crystal.timer % 60 == 0) {
        for (var b : Vars.state.teams.get(Vars.player.team()).buildings
            .select(b -> b.block instanceof UnitFactory)) {
          unitFactorys.add((UnitFactoryBuild) b);
        }
        for (UnitFactoryBuild u : unitFactorys) {
          u.applySlowdown(0f, 70f);
        }
      }
    }
  }

}
