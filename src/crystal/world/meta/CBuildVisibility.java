package crystal.world.meta;

import crystal.aviation.SatelliteManager;
import mindustry.world.meta.BuildVisibility;

public class CBuildVisibility {
  public static final BuildVisibility satelliteOnly = new BuildVisibility(
      () -> SatelliteManager.currentSatelliteId > -1);

}
