package crystal.world.blocks.payloads;

import arc.Core;
import arc.Graphics.Cursor;
import arc.Graphics.Cursor.SystemCursor;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Timer;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.CVars;
import crystal.game.UnitInfo;
import crystal.io.CTypeIO;
import crystal.type.UnitStack;
import crystal.util.DLog;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
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

public class UnitLaunchPad extends PayloadBlock {
  public float payloadsCapacity = 100f;
  public float reload = 600f;
  public Sound launchSound = Sounds.none;
  public Color lightColor = Color.valueOf("#eab678");
  public Color bottomColor = Pal.darkerMetal;
  public TextureRegion lightRegion;
  public TextureRegion podRegion;

  public UnitLaunchPad(String name) {
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
    public ObjectMap<UnitType, UnitStack> units = new ObjectMap<>();
    public float timer = 0f, launchTimer = 0f, launchReload = 0f;
    public boolean full = false, directly = false, isLaunch = false;
    public int index = 0;
    public UnitType[] launchTypes = null;

    public void addUnitToMap(Unit unit) {
      if (units.containsKey(unit.type)) {
        units.get(unit.type).amount++;
      } else {
        units.put(unit.type, new UnitStack(unit.type, 1));
      }

    }

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
        if (((UnitPayload) payload).unit.type() == this.unit) {
          if (payloadsTotal + ((UnitPayload) payload).unit.hitSize <= payloadsCapacity) {
            payloadsTotal += ((UnitPayload) payload).unit.hitSize;
            addUnitToMap(((UnitPayload) payload).unit);
            full = false;
            return true;
          } else {
            full = true;
            return false;
          }
        } else {
          full = false;
          return false;
        }
      } else {
        full = false;
        return false;
      }
    }

    public void launchEntity(UnitStack stack) {
      launchSound.at(x, y, 1f + Mathf.range(0.1f));
      UnitLaunchPayload entity = UnitLaunchPayload.create();
      entity.unitStack = stack.copy();
      entity.set(this);
      entity.lifetime(120f);
      entity.team(team);
      entity.add();
      Fx.launchPod.at(this);
      Effect.shake(3f, 3f, this);
    }

    @Override
    public void updateTile() {
      super.updateTile();
      if (!isLaunch) {
        timer += edelta();
        if ((timer >= reload && full) || (directly && timer >= reload)) {
          isLaunch = true;
        }
      } else {
        if (launchTypes == null) {
          Seq<UnitType> temp = units.keys().toSeq();
          temp.sort();
          int size = temp.size;
          launchTypes = new UnitType[size];
          for (int i = 0; i < size; i++) {
            launchTypes[i] = temp.get(i);
          }
          DLog.info("创建launchtypes");
          for (var u : launchTypes) {
            DLog.info(u);
          }
        }
        if (launchTypes.length == 0)
          return;
        launchTimer += edelta();
        if (launchTimer >= launchReload) {
          launchEntity(units.get(launchTypes[index]));
          launchTimer = 0f;
          launchReload = 90f;
          index++;
        }
        if (index >= units.size)
          clear();
      }
      if (moveInPayload(false) && efficiency > 0) {
        payload = null;
      }
    }

    public void clear() {
      units.clear();
      timer = 0f;
      payloadsTotal = 0f;
      full = false;
      directly = false;
      isLaunch = false;
      launchTypes = null;
      index = 0;
      launchTimer = 0f;
      launchReload = 0f;
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
      ItemSelection.buildTable(UnitLaunchPad.this, table,
          Vars.content.units().select(UnitLaunchPad.this::canProduce).as(),
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
      table.button(Icon.upload, Styles.cleari, () -> {
        directly = true;
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
      write.f(payloadsTotal);
      write.bool(full);
      write.i(units.size);
      if (units.size > 0) {
        for (UnitStack stack : units.values()) {
          CTypeIO.writeUnitStack(write, stack);
        }
      }
      write.bool(isLaunch);
      if (isLaunch) {
        write.i(index);
        write.f(launchTimer);
        write.f(launchReload);
        write.i(launchTypes.length);
        DLog.info("写入launchtypes" + launchTypes);
        for (var u : launchTypes) {
          write.i(u.id);
        }
      }
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      unit = Vars.content.unit(read.s());
      if (revision >= 1) {
        timer = read.f();
        payloadsTotal = read.f();
        full = read.bool();
        int size = read.i();
        if (size > 0) {
          for (int i = 0; i < size; i++) {
            UnitStack stack = CTypeIO.readUnitStack(read);
            units.put(stack.unit, stack);
            DLog.info("发射台读取单位种类：" + stack.unit.name + " 数量：" + stack.amount);
          }
        }
        isLaunch = read.bool();
        if (isLaunch) {
          index = read.i();
          launchTimer = read.f();
          launchReload = read.f();
          int length = read.i();
          launchTypes = new UnitType[length];
          for (int i = 0; i < length; i++) {
            launchTypes[i] = Vars.content.unit(read.i());
          }
          DLog.info("读取launchtypes" + launchTypes);
        }
      }
    }
  }
}
