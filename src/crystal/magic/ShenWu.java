package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectMap;

public abstract class ShenWu {

  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static ObjectMap<Integer, ShenWu> map = new ObjectMap<>();
  public String name;
  public String description;
  public TextureRegionDrawable icon;
  public int id;
  public float magicAmount;

  public ShenWu(String name) {
    this.name = name;
    this.id = maxId.getAndIncrement();
    map.put(id, this);
  }
}
