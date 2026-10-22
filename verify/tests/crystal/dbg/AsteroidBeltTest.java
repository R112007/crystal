package crystal.dbg;

import arc.math.Mathf;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import crystal.content.CAsteroidBelts;
import crystal.content.CPlanets;
import crystal.graphics.g3d.AsteroidBeltMesh;
import crystal.world.asteroid.AsteroidBelt;
import crystal.world.asteroid.AsteroidBeltSystem;
import mindustry.type.Sector;

/** Headless logic test for asteroid-belt entry and facing-sector selection. */
public class AsteroidBeltTest {
  private static boolean ok = true;

  public static void run(String[] args) {
    try {
      AsteroidBelt belt = CAsteroidBelts.lxBelt;
      check("belt loaded", true, belt != null);
      if (belt == null) {
        finish();
        return;
      }

      check("target is lx", true, belt.target == CPlanets.lx);
      check("star is lx parent", true, belt.star == CPlanets.lx.parent);
      check("orbit period applied", 300f, belt.target.orbitTime);
      check("radial overlap", true, belt.radialOverlap());
      check("belt center crosses the planet orbit", true,
          Math.abs(belt.orbitRadius - belt.target.orbitRadius) <= 1f);
      check("belt hugs the planet orbit for close-up visibility", true,
          Math.abs(belt.orbitRadius - belt.target.orbitRadius) <= 5f);
      check("planet close zoom keeps the belt in frame", true, CPlanets.lx.minZoom >= 0.2f);

      float mid = belt.startAngle + belt.sweepAngle * 0.5f;
      check("mid-arc is inside", true, belt.containsAngle(mid));
      check("after-arc is outside", false, belt.containsAngle(belt.startAngle + belt.sweepAngle + 20f));
      check("entry countdown is positive", true,
          belt.secondsUntilEntry(belt.startAngle + belt.sweepAngle + 180f) > 0f);

      Vec3 face = new Vec3();
      belt.faceDirection(mid, mid, face);
      Vec3 radialOut = new Vec3(Mathf.cos(mid * Mathf.degRad), 0f, Mathf.sin(mid * Mathf.degRad));
      check("face direction points at belt", true, face.dot(radialOut) > 0.99f);

      Sector sampleSector = belt.target.sectors.first();
      Vec3 expectedNormal = belt.target.lookAt(sampleSector, new Vec3());
      Vec3 actualNormal = belt.sectorNormal(sampleSector, new Vec3());
      check("sector normal matches planet lookAt", true, actualNormal.dot(expectedNormal) > 0.999f);

      Mat3D starTransform = new Mat3D().rotate(Vec3.Y, belt.star.getRotation());
      Vec3 aligned = Mat3D.prj(
          AsteroidBeltMesh.cancelStarRotation(new Vec3(1f, 0f, 0f), belt.star),
          starTransform);
      check("belt visual rotation is aligned with the orbit", true,
          Math.abs(aligned.x - 1f) < 0.01f && Math.abs(aligned.z) < 0.01f);

      int hit = 0;
      int safe = 0;
      float maxIntensity = 0f;
      float minPositiveIntensity = 1f;
      for (Sector sector : belt.target.sectors) {
        float intensity = belt.impactIntensity(sector, face);
        if (intensity > 0f) {
          hit++;
          maxIntensity = Math.max(maxIntensity, intensity);
          minPositiveIntensity = Math.min(minPositiveIntensity, intensity);
        } else {
          safe++;
        }
      }
      check("some sectors are on the impact hemisphere", true, hit > 0);
      check("some sectors are safe", true, safe > 0);
      check("impact face covers a hemisphere, not the whole planet", true, hit < belt.target.sectors.size);
      check("impact intensity peaks near the pole", true, maxIntensity > 0.8f);
      check("impact intensity fades toward the equator", true, minPositiveIntensity < 0.2f);
      check("impact intensity has a real gradient", true, maxIntensity > minPositiveIntensity * 3f);

      AsteroidBelt wrapped = new AsteroidBelt("wrap-test", belt.star, belt.target);
      wrapped.startAngle = 350f;
      wrapped.sweepAngle = 30f;
      wrapped.orbitRadius = belt.orbitRadius;
      wrapped.radialHalfWidth = belt.radialHalfWidth;
      check("wrapped radial overlap", true, wrapped.radialOverlap());
      check("wrapped sweep", 30f, wrapped.sweepAngle);
      check("wrapped arc contains 5 degrees", true, wrapped.containsAngle(5f));
      check("wrapped arc contains the endpoint", true, wrapped.containsAngle(20f));
      check("wrapped arc excludes 25 degrees", false, wrapped.containsAngle(25f));

      check("radius range is ordered", true, belt.minImpactRadius <= belt.maxImpactRadius);
      check("damage range is ordered", true, belt.minImpactDamage <= belt.maxImpactDamage);
      check("interval range is ordered", true, belt.minImpactInterval <= belt.maxImpactInterval);
      check("wave-count range is ordered", true, belt.minImpactsPerWave <= belt.maxImpactsPerWave);
      check("global-damage range is ordered", true, belt.minGlobalDamagePerWave <= belt.maxGlobalDamagePerWave);
      check("meteor stream stays continuous", true, belt.maxImpactInterval <= 0.25f);
      check("global damage uses a separate cadence", true,
          belt.globalDamageInterval > belt.maxImpactInterval * 2f);

      boolean radiusInRange = true;
      boolean damageInRange = true;
      boolean intervalInRange = true;
      boolean countInRange = true;
      boolean globalDamageInRange = true;
      boolean radiusVaried = false;
      float firstRadius = belt.rollImpactRadius();
      for (int i = 0; i < 500; i++) {
        float radius = belt.rollImpactRadius();
        float damage = belt.rollImpactDamage();
        float interval = belt.rollImpactInterval();
        int count = belt.rollImpactsPerWave();
        float globalDamage = belt.rollGlobalDamage();

        radiusInRange &= radius >= belt.minImpactRadius - 0.001f && radius <= belt.maxImpactRadius + 0.001f;
        damageInRange &= damage >= belt.minImpactDamage - 0.001f && damage <= belt.maxImpactDamage + 0.001f;
        intervalInRange &= interval >= belt.minImpactInterval - 0.001f && interval <= belt.maxImpactInterval + 0.001f;
        countInRange &= count >= belt.minImpactsPerWave && count <= belt.maxImpactsPerWave;
        globalDamageInRange &= globalDamage >= belt.minGlobalDamagePerWave - 0.001f
            && globalDamage <= belt.maxGlobalDamagePerWave + 0.001f;
        radiusVaried |= Math.abs(radius - firstRadius) > 0.0001f;
      }
      check("random impact radius stays in range", true, radiusInRange);
      check("random impact damage stays in range", true, damageInRange);
      check("random impact interval stays in range", true, intervalInRange);
      check("random wave count stays in range", true, countInRange);
      check("random global damage stays in range", true, globalDamageInRange);
      check("impact radius is actually randomized", true, radiusVaried);

      AsteroidBeltSystem.setBelt(belt);
      AsteroidBeltSystem.debugSetForced(false);
      check("debug forced starts off", false, AsteroidBeltSystem.isDebugForced());
      AsteroidBeltSystem.debugSetForced(true);
      check("debug forced toggles on", true, AsteroidBeltSystem.isDebugForced());
      AsteroidBeltSystem.reset();
      check("reset clears debug forced", false, AsteroidBeltSystem.isDebugForced());
    } catch (Throwable t) {
      t.printStackTrace();
      ok = false;
    }
    finish();
  }

  private static void finish() {
    System.out.println("[ASTEROID] " + (ok ? "PASS" : "FAIL"));
    System.exit(ok ? 0 : 1);
  }

  private static void check(String label, boolean expected, boolean actual) {
    boolean pass = expected == actual;
    ok &= pass;
    System.out.println("[ASTEROID] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
  }

  private static void check(String label, float expected, float actual) {
    boolean pass = Math.abs(expected - actual) < 0.001f;
    ok &= pass;
    System.out.println("[ASTEROID] " + label + ": " + actual + "/" + expected + (pass ? " OK" : " FAIL"));
  }
}
