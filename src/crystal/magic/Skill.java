package crystal.magic;

import java.util.concurrent.atomic.AtomicInteger;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import crystal.gen.SkillEntityc;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Drawc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Timedc;

public abstract class Skill {

  private static final AtomicInteger maxId = new AtomicInteger(0);
  public static final ObjectMap<Integer, Skill> map = new ObjectMap<>();
  public static final ObjectMap<String, Skill> all = new ObjectMap<>();
  public static final Seq<Skill> list = new Seq<>();

  public int id;
  public String name, localizedName, description;
  public TextureRegion icon;
  // 技能是否是持续效果
  public boolean continous;
  static {
    Events.on(ClientLoadEvent.class, e -> {
      for (Skill s : list) {
        s.loadIcon();
      }
    });
  }

  public Skill(String name) {
    this.name = name;
    this.id = maxId.getAndIncrement();
    map.put(id, this);
    if (all.containsKey(name))
      throw new IllegalArgumentException("Two skill cannot have the same name! (issue: '" + name + "')");
    all.put(name, this);
    list.add(this);
    this.localizedName = Core.bundle.get("skill." + name + ".name", name);
    this.description = Core.bundle.getOrNull("skill." + name + ".description");
  }

  public void loadIcon() {
    TextureRegion found = Core.atlas.find("shenwu-" + name);
    this.icon = found.found() ? found : Icon.add.getRegion();
  }

  public abstract void influence();

  @EntityDef({ SkillEntityc.class })
  @EntityComponent
  static abstract class SkillEntityComp implements Drawc, Timedc, Teamc {
    @Override
    public void update() {
    }

    @Override
    public void draw() {

    }
  }
}
