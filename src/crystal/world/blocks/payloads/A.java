package crystal.world.blocks.payloads;

import arc.util.Log;
import crystal.game.UnitInfo;
import crystal.game.WaitTime;
import mindustry.content.Fx;
import mindustry.content.SectorPresets;
import mindustry.gen.Building;
import mindustry.type.UnitType;
import mindustry.world.Block;

public class A extends Block {
  public UnitType u;

  public A(String name) {
    super(name);
    update = true;
    solid = true;
    configurable = true;
  }

  public class ABuild extends Building {
    @Override
    public void updateTile() {
      if (WaitTime.waittime(240f)) {
        Fx.shieldWave.at(x, y);
        Log.info("设备" + name + "发送单位" + u.name);
        UnitInfo.get(SectorPresets.groundZero.sector).handUnitsPossessed(u, 1);
        Log.info("发送" + u.name + "完毕");
      }
    }
  }
}
