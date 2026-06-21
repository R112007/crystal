package crystal.net;

import crystal.gen.Corec;
import mindustry.gen.Unitc;
import mindustry.net.Net;

import static mindustry.Vars.*;

public class CCall {
  public static void load() {
    Net.registerPacket(DeployTogglePacket::new);
  }

  public static void setUnitDeployed(Unitc unit, boolean deployed) {
    if (unit == null || !(unit instanceof Corec))
      return;
    if (net.server() || !net.active()) {
      ((Corec) unit).deployed(deployed);
      return;
    }
    if (net.client()) {
      DeployTogglePacket packet = new DeployTogglePacket();
      packet.unitid = unit.id();
      packet.deployed = deployed;
      net.send(packet, true);
    }
  }
}
