package crystal.world.blocks.payloads;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.game.UnitInfo;
import crystal.io.CTypeIO;
import crystal.type.UnitStack;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.core.World;
import mindustry.entities.EntityGroup;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.LaunchPayload;
import mindustry.gen.Unitc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.type.Sector;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.*;

public class UnitLaunchPayload extends LaunchPayload {
  protected transient boolean added;

  public transient int id = EntityGroup.nextId();

  public transient Interval in = new Interval();

  protected transient int index__all = -1;

  protected transient int index__draw = -1;

  public float lifetime;

  public UnitStack unitStack;

  public TextureRegion region;

  public Team team = Team.derelict;

  public float time;

  public float x;

  public float y;

  protected UnitLaunchPayload() {
  }

  @Nullable
  public Building buildOn() {
    return world.buildWorld(x, y);
  }

  @Nullable
  public Tile tileOn() {
    return world.tileWorld(x, y);
  }

  @Nullable
  public CoreBlock.CoreBuild closestCore() {
    return state.teams.closestCore(x, y, team);
  }

  @Nullable
  public CoreBlock.CoreBuild closestEnemyCore() {
    return state.teams.closestEnemyCore(x, y, team);
  }

  @Nullable
  public CoreBlock.CoreBuild core() {
    return team.core();
  }

  @Override
  public Seq<ItemStack> stacks() {
    return stacks;
  }

  @Override
  public Interval in() {
    return in;
  }

  @Override
  public float lifetime() {
    return lifetime;
  }

  @Override
  public float time() {
    return time;
  }

  @Override
  public float x() {
    return x;
  }

  @Override
  public float y() {
    return y;
  }

  @Override
  public int classId() {
    return 53;
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public String toString() {
    return "UnitLaunchPayload#" + id;
  }

  @Override
  public Team team() {
    return team;
  }

  @Override
  public void id(int id) {
    this.id = id;
  }

  @Override
  public void in(Interval in) {
    this.in = in;
  }

  @Override
  public void lifetime(float lifetime) {
    this.lifetime = lifetime;
  }

  @Override
  public void setIndex__all(int index) {
    index__all = index;
  }

  @Override
  public void setIndex__draw(int index) {
    index__draw = index;
  }

  @Override
  public void team(Team team) {
    this.team = team;
  }

  @Override
  public void time(float time) {
    this.time = time;
  }

  @Override
  public void x(float x) {
    this.x = x;
  }

  @Override
  public void y(float y) {
    this.y = y;
  }

  @Override
  public void read(Reads read) {
    short REV = read.s();
    if (REV == 0) {
      this.lifetime = read.f();
      this.unitStack = new UnitStack(Vars.content.unit(read.str()), read.i());
      this.team = mindustry.io.TypeIO.readTeam(read);
      this.time = read.f();
      this.x = read.f();
      this.y = read.f();
    } else {
      throw new IllegalArgumentException("Unknown revision '" + REV + "' for entity type 'UnitLaunchPayload'");
    }
    afterRead();
  }

  @Override
  public void write(Writes write) {
    write.s(0);
    write.f(this.lifetime);
    write.str(unitStack.unit.name);
    write.i(unitStack.amount);
    mindustry.io.TypeIO.writeTeam(write, this.team);
    write.f(this.time);
    write.f(this.x);
    write.f(this.y);
  }

  public <T extends Entityc> T self() {
    return (T) this;
  }

  public <T> T as() {
    return (T) this;
  }

  public boolean cheating() {
    return team.rules().cheat;
  }

  public boolean inFogTo(Team viewer) {
    return this.team != viewer && !fogControl.isVisible(viewer, x, y);
  }

  public boolean isAdded() {
    return added;
  }

  public boolean isLocal() {
    return ((Object) this) == player || ((Object) this) instanceof Unitc u && u.controller() == player;
  }

  public boolean isRemote() {
    return ((Object) this) instanceof Unitc u && u.isPlayer() && !isLocal();
  }

  public boolean onSolid() {
    Tile tile = tileOn();
    return tile == null || tile.solid();
  }

  public boolean serialize() {
    return true;
  }

  public float clipSize() {
    return Float.MAX_VALUE;
  }

  public float cx() {
    return x + fin(Interp.pow2In) * (12.0F + Mathf.randomSeedRange(id() + 3, 4.0F));
  }

  public float cy() {
    return y + fin(Interp.pow5In) * (100.0F + Mathf.randomSeedRange(id() + 2, 30.0F));
  }

  public float fin() {
    return time / lifetime;
  }

  public float getX() {
    return x;
  }

  public float getY() {
    return y;
  }

  public int tileX() {
    return World.toTile(x);
  }

  public int tileY() {
    return World.toTile(y);
  }

  public Block blockOn() {
    Tile tile = tileOn();
    return tile == null ? Blocks.air : tile.block();
  }

  public Floor floorOn() {
    Tile tile = tileOn();
    return tile == null || tile.block() != Blocks.air ? (Floor) Blocks.air : tile.floor();
  }

  public static UnitLaunchPayload create() {
    return new UnitLaunchPayload();
  }

  public void add() {
    if (added == true)
      return;
    index__all = Groups.all.addIndex(this);
    index__draw = Groups.draw.addIndex(this);
    added = true;
  }

  public void afterRead() {
  }

  public void afterReadAll() {
  }

  public void beforeWrite() {
  }

  public void draw() {
    launchpayload: {
      float alpha = fout(Interp.pow5Out);
      float scale = (1.0F - alpha) * 1.3F + 1.0F;
      float cx = cx();
      float cy = cy();
      float rotation = fin() * (130.0F + Mathf.randomSeedRange(id(), 50.0F));
      Draw.z(Layer.effect + 0.001F);
      Draw.color(Pal.engine);
      float rad = 0.2F + fslope();
      Fill.light(cx, cy, 10, 25.0F * (rad + scale - 1.0F), Tmp.c2.set(Pal.engine).a(alpha),
          Tmp.c1.set(Pal.engine).a(0.0F));
      Draw.alpha(alpha);
      for (int i = 0; i < 4; i++) {
        Drawf.tri(cx, cy, 6.0F, 40.0F * (rad + scale - 1.0F), i * 90.0F + rotation);
      }
      Draw.color();
      Draw.z(Layer.weather - 1);
      if (region != unitStack.unit.fullIcon)
        region = unitStack.unit.fullIcon;
      scale *= region.scl();
      float rw = region.width * scale;
      float rh = region.height * scale;
      Draw.alpha(alpha);
      Draw.rect(region, cx, cy, rw, rh, rotation);
      Tmp.v1.trns(225.0F, fin(Interp.pow3In) * 250.0F);
      Draw.z(Layer.flyingUnit + 1);
      Draw.color(0, 0, 0, 0.22F * alpha);
      Draw.rect(region, cx + Tmp.v1.x, cy + Tmp.v1.y, rw, rh, rotation);
      Draw.reset();
    }
  }

  public void remove() {
    if (added == false)
      return;
    Groups.all.removeIndex(this, index__all);
    ;
    index__all = -1;
    Groups.draw.removeIndex(this, index__draw);
    ;
    index__draw = -1;
    entity: {
      added = false;
    }
    launchpayload: {
      if (!state.isCampaign() || net.client())
        break launchpayload;
      Sector destsec = state.rules.sector.info.destination;
      if (team() == state.rules.defaultTeam && destsec != null && destsec != state.rules.sector) {
        UnitInfo.get(destsec).handUnitsPossessed(unitStack);
      }
    }
  }

  public void set(Position pos) {
    set(pos.getX(), pos.getY());
  }

  public void set(float x, float y) {
    this.x = x;
    this.y = y;
  }

  public void trns(Position pos) {
    trns(pos.getX(), pos.getY());
  }

  public void trns(float x, float y) {
    set(this.x + x, this.y + y);
  }

  public void update() {
    launchpayload: {
      float r = 3.0F;
      if (in.get(4.0F - fin() * 2.0F)) {
        Fx.rocketSmoke.at(cx() + Mathf.range(r), cy() + Mathf.range(r), fin());
      }
    }
    timed: {
      time = Math.min(time + Time.delta, lifetime);
      if (time >= lifetime) {
        remove();
      }
    }
  }
}
