package crystal.type;

import java.util.concurrent.atomic.AtomicInteger;

import arc.struct.ObjectMap;
import crystal.gen.Missile;
import crystal.gen.Missilec;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Drawc;
import mindustry.gen.Teamc;
import mindustry.gen.Timedc;

public class SatelliteMissile implements Comparable<SatelliteMissile> {
  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static ObjectMap<Integer, SatelliteMissile> map = new ObjectMap<>();
  public String name;
  public int id;
  public float splashDamageRadius, splashDamage, lifetime;
  public Effect despawnEffect = Fx.hitBulletSmall;
  public Effect shootEffect = Fx.shootSmall;

  public SatelliteMissile(String name) {
    this.name = name;
    id = maxId.getAndIncrement();
    map.put(id, this);
  }

  public void hit(Missile missile) {
  }

  public void update(Missile missile) {

  }

  @Override
  public int compareTo(SatelliteMissile other) {
    return Integer.compare(id, other.id);
  }

  @EntityDef(value = { Missilec.class }, serialize = false, pooled = true)
  @EntityComponent
  public static abstract class MissileComp implements Drawc, Teamc, Timedc, Missilec {
    @Import
    public float lifetime;
    public SatelliteMissile type;

    @Override
    public void update() {
      type.update(self());
    }

    @Override
    public void remove() {
      type.hit(self());
    }

    @Override
    public void draw() {
    }
  }
}
