package crystal.world.blocks.payloads;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Eachable;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.game.UnitInfo;
import crystal.type.UnitStack;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.blocks.payloads.UnitPayload;

import static mindustry.Vars.*;

public class UnitReceivePad extends PayloadBlock {
  static ObjectMap<UnitType, Seq<UnitReceivePadBuild>> waiting = new ObjectMap<>();
  static long lastUpdateId = -1;
  static {
    Events.on(ResetEvent.class, e -> {
      waiting.clear();
      lastUpdateId = -1;
    });
  }
  public float reload = 600f;
  public float payloadsCapacity = 100f;

  public UnitReceivePad(String name) {
    super(name);
    this.update = true;
    this.solid = true;
    this.hasItems = false;
    this.configurable = true;
    this.clipSize = 120;
    this.payloadSpeed = 1.2f;
    this.outputsPayload = true;
    this.acceptsPayload = false;
    this.rotate = true;
    this.commandable = true;
    configClear((UnitReceivePadBuild build) -> {
      build.config = null;
      build.payload = null;
      build.scl = 0f;
    });
    config(UnitType.class, (UnitReceivePadBuild build, UnitType unit) -> {
      if (canProduce(unit) && build.config != unit) {
        build.config = unit;
        build.payload = null;
      }
    });
  }

  @Override
  public void getPlanConfigs(Seq<UnlockableContent> options) {
    options.add(content.units().select(this::canProduce));
  }

  @Override
  public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
    Draw.rect(region, plan.drawx(), plan.drawy());
    Draw.rect(outRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
    Draw.rect(topRegion, plan.drawx(), plan.drawy());
  }

  @Override
  public void setBars() {
    super.setBars();
    addBar("payloadsCapacity",
        (UnitReceivePadBuild build) -> new Bar(() -> Core.bundle.format("bar.payloadsCapacity", build.payloadsTotal),
            () -> Pal.items, () -> (float) (build.payloadsTotal / this.payloadsCapacity)));
  }

  public boolean canProduce(UnitType t) {
    return !t.isHidden() && !t.isBanned() && t.supportsEnv(Vars.state.rules.env);
  }

  public class UnitReceivePadBuild extends PayloadBlockBuild<Payload> {
    public @Nullable UnitType config;
    public @Nullable UnitType arriving;
    public float payloadsTotal;
    public float timer = 0f;
    public float scl;
    public int priority = Mathf.rand.nextInt();
    public int times1 = 0, times2 = 0;
    public boolean firstProduce = true;
    public @Nullable Vec2 commandPos;
    public @Nullable Payload lastPayload;

    @Override
    public Vec2 getCommandPosition() {
      return commandPos;
    }

    @Override
    public void onCommand(Vec2 target) {
      commandPos = target;
    }

    @Override
    public UnitType config() {
      return config;
    }

    public boolean isFake() {
      return team != state.rules.defaultTeam || !state.isCampaign();
    }

    @Override
    public boolean acceptPayload(Building source, Payload payload) {
      return false;
    }

    public int getAmount() {
      if (arriving == null) {
        Log.info("null");
        return 0;
      }
      float tmp = payloadsCapacity;
      int amount = 0;
      while (tmp > arriving.hitSize) {
        tmp -= arriving.hitSize;
        amount++;
      }
      return amount;
    }

    public void produceUnit(UnitType unitType) {
      if (!unitType.supportsEnv(state.rules.env)) {
        return;
      }
      payload = new UnitPayload(unitType.create(team));
      Unit p = ((UnitPayload) payload).unit;
      if (commandPos != null && p.isCommandable()) {
        p.command().commandPosition(commandPos);
      }
      Events.fire(new UnitCreateEvent(p, this));
      Log.info("生成单位" + unitType);
    }

    @Override
    public void updateTile() {
      timer += delta();
      if (timer < reload)
        return;
      arriving = config;
      @Nullable
      Payload currentPayload = this.payload;
      if (arriving == null && UnitInfo.get(state.rules.sector) == null) {
        return;
      }
      if (UnitInfo.get(state.rules.sector).getPossessedUnitStack(arriving) == null)
        return;
      if (UnitInfo.get(state.rules.sector).getPossessedUnitStack(arriving).amount == 0) {
        return;
      } else {
        if (getAmount() == 0)
          return;
        if (getUnitInfo().getPossessedUnitStack(arriving) == null)
          return;
        // if (getAmount() <= getUnitInfo().getPossessedUnitStack(arriving).amount) {
        times1 = getAmount();
        // Log.info("选择getAmount=" + times1);
        // }
        /*
         * else {
         * times1 = getUnitInfo().getPossessedUnitStack(arriving).amount;
         * Log.info("选择amount=" + times1);
         * }
         */
      }
      if (getUnitInfo().getPossessedUnitStack(arriving).amount <= 0) {
        timer = 0;
        times1 = 0;
        times2 = 0;
        firstProduce = true;
        return;
      }
      if (times2 <= times1) {
        if (payload == null) {
          produceUnit(arriving);
          getUnitInfo().getPossessedUnitStack(arriving).amount--;
          times2++;
          Log.info("times2" + times2);
        }
      }
      moveOutPayload();
      if (lastPayload != null && currentPayload == null) {
      }
      if (times2 >= times1) {
        timer = 0;
        times1 = 0;
        times2 = 0;
        firstProduce = true;
      }
      lastPayload = currentPayload;
    }

    public UnitInfo getUnitInfo() {
      return UnitInfo.get(state.rules.sector);
    }

    public boolean accessible() {
      return state.rules.editor || state.rules.allowEditWorldProcessors || state.isCampaign()
          || state.rules.infiniteResources
          || (team != state.rules.defaultTeam && !state.rules.pvp && team != Team.derelict);
    }

    @Override
    public void buildConfiguration(Table table) {
      ItemSelection.buildTable(UnitReceivePad.this, table,
          Vars.content.units().select(UnitReceivePad.this::canProduce).as(),
          () -> (UnlockableContent) config(), this::configure, selectionRows, selectionColumns);
    }

    @Override
    public void display(Table table) {
      super.display(table);
      if (config != null) {
        if (!config.supportsEnv(state.rules.env)) {
          table.row();
          table.label(() -> {
            return Core.bundle.get("unsupportenv") + config.name;
          });
        }
      }
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.s(config == null ? -1 : config.id);
      write.f(timer);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      config = Vars.content.unit(read.s());
      if (revision >= 1) {
        timer = read.f();
      }
    }
  }
}
