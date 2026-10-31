package crystal.type;

import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;

/**
 * Marks a sector as using the mobile-core-only gameplay mode.
 *
 * <p>The gameplay restrictions are intentionally opt-in. A regular
 * {@link SectorPreset} may launch with either core type but keeps unrestricted
 * unit control. Presets of this type force a mobile core launch and keep the
 * player bound to that core.</p>
 */
public class MobileCoreSectorPreset extends SectorPreset {
  public MobileCoreSectorPreset(String name, Planet planet, int sector) {
    super(name, planet, sector);
  }

  public static boolean isMarked(Sector sector) {
    return sector != null && sector.preset instanceof MobileCoreSectorPreset;
  }
}
