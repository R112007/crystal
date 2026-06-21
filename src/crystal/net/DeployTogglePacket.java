package crystal.net;

import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.gen.Corec;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.net.NetConnection;
import mindustry.net.Packet;

public class DeployTogglePacket extends Packet {
  public int unitid;
  public boolean deployed;

  @Override
  public void write(Writes buffer) {
    buffer.i(unitid);
    buffer.bool(deployed);
  }

  @Override
  public void read(Reads buffer) {
    unitid = buffer.i();
    deployed = buffer.bool();
  }

  @Override
  public void handleServer(NetConnection con) {
    Unit unit = Groups.unit.getByID(unitid);
    if (unit == null || con.player == null || unit.team() != con.player.team())
      return;

    if (unit instanceof Corec core) {
      core.deployed(deployed);
    }
  }

  @Override
  public int getPriority() {
    return priorityNormal;
  }
}
