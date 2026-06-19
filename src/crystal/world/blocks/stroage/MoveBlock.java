package crystal.world.blocks.stroage;

import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.LegsUnit;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.Env;

import static mindustry.Vars.*;

public class MoveBlock extends Wall {
  public UnitType type;

  public MoveBlock(String name) {
    super(name);
    solid = true;
    envEnabled = Env.any;
    String s = this.name + "move";
    type = new UnitType(name + "move") {
      {
        playerControllable = false;
        constructor = LegsUnit::create;
        controller = UnitTypes.alpha.controller;
        legCount = 4;
      }
    };
    MoveBlockSystem.names.add(s);
    MoveBlockSystem.map.put(s, this);
  }

  public class MoveBlockBuild extends Building {
    public void removeButToUnit() {
      dead = true;
      float x = this.x;
      float y = this.y;
      Team t = this.team;
      if (tile != emptyTile) {
        tile.remove();
      }
      remove();
      afterDestroyed();
      type.spawn(t, x, y);
    }
  }
}
