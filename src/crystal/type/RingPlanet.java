package crystal.type;

import arc.math.geom.Mat3D;
import arc.util.Nullable;
import arc.func.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;
import static mindustry.Vars.*;

public class RingPlanet extends Planet {
  public @Nullable GenericMesh ringMesh;
  public Prov<GenericMesh> ringMeshLoader = () -> null;
  public float ringMaxRadius = 0f;

  public RingPlanet(String name, Planet parent, float radius) {
    super(name, parent, radius);
  }

  public RingPlanet(String name, Planet parent, float radius, int sectorSize) {
    super(name, parent, radius, sectorSize);
  }

  @Override
  public void load() {
    super.load();
    if (!headless && ringMesh == null && ringMeshLoader != null) {
      ringMesh = ringMeshLoader.get();
    }
  }

  @Override
  public void init() {
    if (ringMaxRadius > 0f) {
      clipRadius = Math.max(clipRadius, ringMaxRadius + 0.5f);
    }
    super.init();
  }

  @Override
  public void reloadMesh() {
    if (ringMesh != null) {
      ringMesh.dispose();
      ringMesh = null;
    }
    super.reloadMesh();
    if (!headless && ringMeshLoader != null) {
      ringMesh = ringMeshLoader.get();
    }
  }

  @Override
  public void draw(PlanetParams params, Mat3D projection, Mat3D transform) {
    super.draw(params, projection, transform);

    if (ringMesh != null) {
      ringMesh.render(params, projection, transform);
    }
  }
}
