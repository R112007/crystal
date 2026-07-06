package crystal.type;

import arc.graphics.Color;
import crystal.graphics.g3d.MultiRingMesh;
import crystal.graphics.g3d.RingMesh;
import crystal.graphics.g3d.MultiThickRingMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.type.Planet;
import static mindustry.Vars.*;

public class SaturnPlanet extends Planet {

  public SaturnPlanet(String name, Planet parent, float radius, int sectorSize) {
    super(name, parent, radius, sectorSize);

    // 土星风格参数
    hasAtmosphere = true;
    atmosphereColor = new Color(0.9f, 0.8f, 0.5f, 0.3f);
    bloom = true;
  }

  @Override
  public void load() {
    super.load();

    if (!headless) {
      // 4 种风格选一个：
      // RingMesh.iceRock(this) 冰石蓝（清冷自然，推荐）
      // RingMesh.saturnGold(this) 土星金（经典土星风）
      // RingMesh.techBlue(this) 科技蓝（发光科技感）
      // RingMesh.darkIron(this) 暗灰铁（暗黑岩石）

      RingMesh ring = RingMesh.techBlue(this);
      ring.tiltAngle = 38f;
      ring.rotationSpeed = 0.03f;

      if (cloudMesh != null) {
        cloudMesh = new MultiMesh(cloudMesh, ring);
      } else {
        cloudMesh = ring;
      }

      clipRadius = Math.max(clipRadius, ring.outerRadius + 0.5f);
    }
  }

}
