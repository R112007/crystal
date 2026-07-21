package crystal.world.blocks.magic;

import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Timer;
import crystal.game.CEventType;
import crystal.game.CEventType.MapChangeEvent;
import crystal.world.meta.CStat;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.WorldLabel;
import mindustry.maps.Map;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import static mindustry.Vars.*;

public class NatureMagicGenerater extends Block {
  public float amount, reload;
  public int maxBlocks;
  public static Seq<NatureMagicGeneraterBuild> entities = new Seq<>();

  public NatureMagicGenerater(String name) {
    super(name);
    update = true;
    solid = true;
    buildVisibility = BuildVisibility.campaignOnly;
    canOverdrive = false;
    Events.on(MapChangeEvent.class, e -> {
      entities.clear();
      entities = getbuilds(e.map);
    });
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(CStat.magicPowerRegenTime, reload / 60f, StatUnit.perSecond);
    stats.add(CStat.magicPowerRegen, amount);
    stats.add(CStat.maxBlock, this.maxBlocks);
  }

  public static Seq<NatureMagicGeneraterBuild> getbuilds(Map map) {
    int mapx = map.width;
    int mapy = map.height;
    Seq<NatureMagicGeneraterBuild> builds = new Seq<>();
    for (int x = 1; x <= mapx; x++) {
      for (int y = 1; y <= mapy; y++) {
        if (world.build(x, y) instanceof NatureMagicGeneraterBuild candle) {
          builds.add(candle);
        }
      }
    }
    return builds;
  }

  @Override
  public boolean canPlaceOn(Tile tile, Team team, int rotation) {
    return entities.size < maxBlocks;
  }

  public class NatureMagicGeneraterBuild extends Building {
    public float timer = 0f;

    @Override
    public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
      if (entities.size < maxBlocks)
        entities.add((NatureMagicGeneraterBuild) super.init(tile, team, shouldAdd, rotation));
      else {
        Timer.schedule(() -> {
          this.killed();
        }, 0.2f);
      }
      return super.init(tile, team, shouldAdd, rotation);
    }

    @Override
    public void remove() {
      super.remove();
      entities.remove(this);
    }

    @Override
    public void updateTile() {
      timer += Time.delta;
      if (timer >= reload && Vars.state.isCampaign()) {
        Events.fire(new CEventType.MagicPowerChange(amount));
        Vars.ui.showLabel("+" + amount, id, 2f, x, y);
        timer = 0f;
      }
    }
  }
}
