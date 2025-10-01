package crystal.world.blocks.payloads;

import arc.Core;
import arc.Graphics.Cursor;
import arc.Graphics.Cursor.SystemCursor;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.CVars;
import crystal.game.UnitInfo;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class UnitLanuchPad extends PayloadBlock {
  public float payloadsCapacity = 100f;
  public float reload = 600f;
  public Sound launchSound = Sounds.none;
  public Color lightColor = Color.valueOf("#eab678");
  public Color bottomColor = Pal.darkerMetal;
  public TextureRegion lightRegion;
  public TextureRegion podRegion;

  public UnitLanuchPad(String name) {
    super(name);
    this.update = true;
    this.solid = true;
    this.hasItems = false;
    this.configurable = true;
    this.clipSize = 120;
    this.payloadSpeed = 1.2f;
    this.outputsPayload = false;
    this.acceptsPayload = true;
    config(UnitType.class, (UnitLanuchPadBuild build, UnitType unit) -> {
      if (canProduce(unit) && build.unit != unit) {
        build.unit = unit;
        build.payload = null;
      }
    });
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(Stat.launchTime, reload / 60f, StatUnit.seconds);
    stats.add(Stat.payloadCapacity, payloadsCapacity, StatUnit.blocksSquared);
  }

  @Override
  public void load() {
    super.load();
    this.lightRegion = Core.atlas.find(name + "-light");
  }

  @Override
  public void setBars() {
    super.setBars();
    addBar("progress", (UnitLanuchPadBuild build) -> new Bar(() -> Core.bundle.get("bar.launchcooldown"),
        () -> Pal.ammo, () -> Mathf.clamp(build.timer / reload)));
    addBar("payloadsCapacity",
        (UnitLanuchPadBuild build) -> new Bar(() -> Core.bundle.format("bar.payloadsCapacity", build.payloadsTotal),
            () -> Pal.items, () -> (float) (build.payloadsTotal / this.payloadsCapacity)));
  }

  public boolean canProduce(UnitType t) {
    return !t.isHidden() && !t.isBanned() && t.supportsEnv(Vars.state.rules.env);
  }

  @Override
  public TextureRegion[] icons() {
    return new TextureRegion[] { region, topRegion };
  }

  public class UnitLanuchPadBuild extends PayloadBlockBuild<Payload> {
    public UnitType unit;
    public float payloadsTotal;
    public Seq<UnitType> unitTypes = new Seq<>();
    public float timer = 0f;
    public boolean tmp = false;

    @Override
    public Cursor getCursor() {
      return !state.isCampaign() || net.client() ? SystemCursor.arrow : super.getCursor();
    }

    @Override
    public double sense(LAccess sensor) {
      if (sensor == LAccess.progress)
        return Mathf.clamp(timer / reload);
      return super.sense(sensor);
    }

    @Override
    public boolean acceptPayload(Building source, Payload payload) {
      if (payload instanceof UnitPayload) {
        if (((UnitPayload) payload).unit.type() == this.unit
            && payloadsTotal + ((UnitPayload) payload).unit.hitSize <= payloadsCapacity) {
          payloadsTotal += ((UnitPayload) payload).unit.hitSize;
          unitTypes.add(((UnitPayload) payload).unit.type());
          tmp = false;
          return true;
        } else {
          tmp = true;
          return false;
        }
      } else {
        return false;
      }
    }

    @Override
    public void updateTile() {
      super.updateTile();
      timer += edelta();
      if (timer >= reload && tmp) {
        for (var unit : unitTypes) {
          UnitInfo.get(state.rules.sector.info.destination).handUnitsPossessed(unit, 1);
        }
        clear();
        tmp = false;
      }
      if (moveInPayload(false) && efficiency > 0) {
        payload = null;
      }
    }

    public void clear() {
      unitTypes.clear();
      timer = 0f;
      payloadsTotal = 0f;
    }

    @Override
    public UnitType config() {
      return unit;
    }

    @Override
    public void display(Table table) {
      super.display(table);
      if (!state.isCampaign() || net.client() || team != player.team())
        return;
      table.row();
      table.label(() -> {
        Sector dest = state.rules.sector == null ? null : state.rules.sector.info.destination;
        return Core.bundle.format("launch.destination",
            dest == null || !dest.hasBase() ? Core.bundle.get("sectors.nonelaunch") : "[accent]" + dest.name());
      }).pad(4).wrap().width(200f).left();
    }

    @Override
    public void buildConfiguration(Table table) {
      ItemSelection.buildTable(UnitLanuchPad.this, table,
          Vars.content.units().select(UnitLanuchPad.this::canProduce).as(),
          () -> (UnlockableContent) config(), this::configure, selectionRows, selectionColumns);
      if (!state.isCampaign() || net.client()) {
        deselect();
        return;
      }
      table.button(Icon.upOpen, Styles.cleari, () -> {
        CVars.cui.cplanet.showSelect(state.rules.sector, other -> {
          if (state.isCampaign() && other.planet == state.rules.sector.planet) {
            var prev = state.rules.sector.info.destination;
            state.rules.sector.info.destination = other;
            if (prev != null) {
              prev.info.refreshImportRates(state.getPlanet());
            }
          }
        });
        deselect();
      }).size(40f);
    }

    @Override
    public boolean shouldShowConfigure(Player player) {
      return state.isCampaign();
    }

    @Override
    public byte version() {
      return 1;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.s(unit == null ? -1 : unit.id);
      write.f(timer);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      unit = Vars.content.unit(read.s());
      if (revision >= 1) {
        timer = read.f();
      }
    }
  }
}
