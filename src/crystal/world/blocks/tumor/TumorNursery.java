package crystal.world.blocks.tumor;

import arc.graphics.g2d.Draw;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.world.tumor.TissueState;
import crystal.world.tumor.TumorNetwork;
import crystal.world.tumor.TumorRender;
import crystal.world.tumor.TumorTissue;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

/** Level-gated unit nursery. It draws minerals from the core and births adapted crux units. */
public class TumorNursery extends Block {
  public TumorNursery(String name) {
    super(name);
    size = 2;
    health = 1050;
    armor = 5f;
    solid = true;
    update = true;
    destructible = true;
    buildVisibility = BuildVisibility.sandboxOnly;
    alwaysUnlocked = true;
  }

  @Override
  public boolean canPlaceOn(mindustry.world.Tile tile, mindustry.game.Team team, int rotation) {
    return TumorNetwork.allowsManualPlacement()
        && super.canPlaceOn(tile, team, rotation);
  }

  public class TumorNurseryBuild extends Building implements TumorTissue {
    public final TissueState tissue = new TissueState();
    public float spawnTimer;
    public float pulse;

    @Override
    public TissueState tumor() {
      return tissue;
    }

    @Override
    public void updateTile() {
      pulse += Time.delta;
      if (TumorNetwork.updateTissue(this, tissue) || net.client()) return;

      var core = TumorNetwork.rootCore(this, tissue);
      if (core == null) return;
      spawnTimer += Time.delta * (1f + (core.level - 1) * 0.2f);
      if (spawnTimer >= 60f * 20f) {
        spawnTimer = 0f;
        trySpawn(core);
      }
    }

    private void trySpawn(TumorCore.TumorCoreBuild core) {
      if (core.items == null || core.items.empty()) return;
      if (!TumorNetwork.hasMemoryBudget(32L * 1024 * 1024)) return;
      double unitMarker = -(core.id + 1.0);
      // Negative flags survive save/load even when building IDs are reassigned.
      int active = Groups.unit.count(unit -> unit.team == team && unit.flag < 0);
      if (active >= 6 + core.level * 3) return;
      UnitType type = switch (Math.min(core.level, 4)) {
        case 1 -> UnitTypes.flare;
        case 2 -> UnitTypes.dagger;
        case 3 -> UnitTypes.atrax;
        default -> UnitTypes.spiroct;
      };
      if (!Units.canCreate(team, type)) return;

      int cost = 18 + core.level * 12;
      if (core.items.total() < cost) return;
      Item payment = core.items.first();
      core.items.remove(payment, cost);

      Unit unit = type.create(team);
      unit.set(x, y);
      unit.rotation = 90f;
      unit.flag = unitMarker;
      unit.add();
      Units.notifyUnitSpawn(unit);
      Fx.spawn.at(x, y);
      Fx.unitAssemble.at(x, y, 0f, type);
    }

    @Override
    public void draw() {
      float scale = tissue.visualScale();
      float breath = 1f + 0.025f * (float) Math.sin(pulse * 0.045f);
      Draw.alpha(tissue.visualAlpha());
      TumorRender.draw(region, x, y, 0f, scale * breath);
      Draw.reset();
    }

    @Override
    public byte version() {
      return 3;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      tissue.write(write);
      write.f(spawnTimer);
      write.f(pulse);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      tissue.read(read, revision);
      spawnTimer = read.f();
      pulse = read.f();
    }
  }
}
