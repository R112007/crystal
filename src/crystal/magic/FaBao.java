package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Events;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectMap;
import mindustry.game.EventType.Trigger;

public abstract class FaBao {
  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static ObjectMap<Integer, FaBao> map = new ObjectMap<>();
  public String name;
  public String description;
  public TextureRegionDrawable icon;
  public boolean instantWork;
  public boolean effectedByXiuWei;
  public boolean update;
  public int id;

  public FaBao(String name) {
    this.name = name;
    this.id = maxId.getAndIncrement();
    map.put(id, this);
  }

  public void init() {
    Events.run(Trigger.draw, () -> {
      draw();
    });
  }

  public void draw() {
  }

  public abstract void drawBeforeRelease();

}
