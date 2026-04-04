package crystal.game;

import crystal.entities.units.UnitEnum.JingJie;
import crystal.type.GongFa;
import crystal.type.UnitStack;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.maps.Map;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

public class CEventType {
  public static class CreateNewUnitInfo {
  }

  public static class LaunchUnitEvent {
    public final UnitStack stack;

    public LaunchUnitEvent(UnitStack stack) {
      this.stack = stack;
    }
  }

  public static class SummonUnitEvent {
    public UnitType type;
    public float x, y, delay;
    public Effect effect;
    public Team team;

    public SummonUnitEvent(UnitType type, float x, float y, Effect effect, Team team, float delay) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.effect = effect;
      this.team = team;
      this.delay = delay;
    }
  }

  public static class SectorChangeEvent {
    public Sector sector;

    public SectorChangeEvent(Sector sector) {
      this.sector = sector;
    }
  }

  public static class MapChangeEvent {
    public Map map;

    public MapChangeEvent(Map map) {
      this.map = map;
    }
  }

  public static class GenerateBuild {
    public Block block;
    public Tile tile;
    public Team team;
    public int rotation;

    public GenerateBuild(Block block, Tile tile, Team team, int rotation) {
      this.block = block;
      this.tile = tile;
      this.team = team;
      this.rotation = rotation;
    }
  }

  public static class MagicPowerChange {
    public float amount;

    public MagicPowerChange(float amount) {
      this.amount = amount;
    }
  }

  public static class XiuWeiChange {
    public JingJie jingJie;

    public XiuWeiChange(JingJie jingJie) {
      this.jingJie = jingJie;
    }
  }

  public static class JingJieChange {
    public float amount;

    public JingJieChange(Float amount) {
      this.amount = amount;
    }
  }

  public static class GongFaBuQuanEvent {
    public JingJie jingJie;
    public GongFa gongFa;

    public GongFaBuQuanEvent(JingJie jingJie, GongFa gongFa) {
      this.jingJie = jingJie;
      this.gongFa = gongFa;
    }
  }

  public static class ResearchGongFaEvent {
    public GongFa gongFa;

    public ResearchGongFaEvent(GongFa gongFa) {
      this.gongFa = gongFa;
    }
  }
}
