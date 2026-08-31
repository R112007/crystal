package crystal.world.blocks.unit;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.ai.type.OrbitDroneAI;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class DeployUnitShieldBlock extends Block {
  public UnitType droneType;
  public int droneCount = 4;
  public float spawnTime = 120f;
  public float orbitRadius = 48f;
  public float orbitSpeed = 0.6f;
  public float arriveDuration = 30f;
  public float threatRange = 180f;
  public float angleLerpSpeed = 8f;
  public int maxListUnits = 8;

  public Effect spawnEffect = Fx.spawn;
  public Sound createSound = Sounds.unitCreate;
  public float createSoundVolume = 1f;

  public DeployUnitShieldBlock(String name) {
    super(name);
    update = true;
    solid = true;
    configurable = true;
    saveConfig = true;
    hasPower = true;

    config(Integer.class, (DeployUnitShieldBlockBuild build, Integer value) -> {
      if (value == 0 || value == 1) {
        build.mode = value;
      } else if (value == -1) {
        build.targetUnitId = -1;
      } else {
        build.targetUnitId = value;
      }
    });
  }

  @Override
  public void init() {
    super.init();
    consumePower(1.5f);
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(Stat.range, orbitRadius / 8f, StatUnit.blocks);
    stats.add(Stat.reload, spawnTime / 60f, StatUnit.seconds);
  }

  @Override
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    super.drawPlace(x, y, rotation, valid);
    Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, orbitRadius, Pal.placing);
  }

  public class DeployUnitShieldBlockBuild extends Building {
    transient Unit[] children;
    transient float timer;
    transient float warmup;

    int mode = 0;
    int targetUnitId = -1;

    transient Position lastOrbitTarget;
    transient int[] pendingChildrenIds;

    void initArrays() {
      children = new Unit[droneCount];
    }

    @Override
    public void updateTile() {
      if (children == null || children.length != droneCount)
        initArrays();

      if (pendingChildrenIds != null) {
        boolean allFound = true;
        for (int i = 0; i < droneCount; i++) {
          if (pendingChildrenIds[i] != -1 && (children[i] == null || !children[i].isValid())) {
            Unit u = Groups.unit.getByID(pendingChildrenIds[i]);
            if (u != null && u.isValid() && u.type == droneType && u.team == team) {
              children[i] = u;
              if (!(u.controller() instanceof OrbitDroneAI)) {
                OrbitDroneAI ai = new OrbitDroneAI();
                ai.protectedTarget = this;
                u.controller(ai);
              }
              u.flag = -(id * 10000L + i);
            } else {
              allFound = false;
            }
          }
        }
        if (allFound)
          pendingChildrenIds = null;
      }

      boolean hasEmpty = false;
      for (int i = 0; i < droneCount; i++) {
        if (children[i] != null && !children[i].isValid()) {
          if (children[i].controller() instanceof OrbitDroneAI ai) {
            ai.clearIntercept();
          }
          children[i] = null;
        }
        if (children[i] == null)
          hasEmpty = true;
      }
      if (hasEmpty)
        scanChildren();

      Unit protectedUnit = null;
      Position orbitTarget = this;
      if (mode == 1) {
        protectedUnit = resolveTargetUnit();
        if (protectedUnit != null && protectedUnit.isValid()) {
          orbitTarget = protectedUnit;
        }
      }

      syncToAI(orbitTarget, protectedUnit);
      spawnMissing();
    }

    Unit resolveTargetUnit() {
      if (targetUnitId > 0) {
        Unit u = Groups.unit.getByID(targetUnitId);
        if (u != null && u.isValid() && u.team == team)
          return u;
      }
      return Units.closest(team, x, y, threatRange * 2f,
          u -> u.isValid() && u.type != droneType);
    }

    void syncToAI(Position orbitTarget, Unit protectedUnit) {
      float baseRot = Time.time * orbitSpeed;
      boolean targetChanged = lastOrbitTarget != orbitTarget && lastOrbitTarget != null;
      lastOrbitTarget = orbitTarget;

      for (int i = 0; i < droneCount; i++) {
        Unit child = children[i];
        if (child == null || !child.isValid())
          continue;
        if (!(child.controller() instanceof OrbitDroneAI ai))
          continue;

        ai.protectedTarget = (protectedUnit != null) ? protectedUnit : this;

        float destDeg = baseRot + i * 360f / droneCount;
        float rad = destDeg * Mathf.degRad;
        float tx = orbitTarget.getX() + Mathf.cos(rad) * orbitRadius;
        float ty = orbitTarget.getY() + Mathf.sin(rad) * orbitRadius;

        if (targetChanged && !ai.isArriving()) {
          ai.startArrive(child.x, child.y, tx, ty, destDeg, arriveDuration);
        } else {
          ai.setOrbit(tx, ty, destDeg);
        }
      }
    }

    void spawnMissing() {
      int alive = 0;
      boolean hasEmpty = false;
      for (Unit c : children) {
        if (c != null && c.isValid())
          alive++;
        else
          hasEmpty = true;
      }

      float powerStatus = power == null ? 0f : power.status;
      boolean canSpawn = alive < droneCount && Units.canCreate(team, droneType)
          && isValid() && powerStatus > 0.001f && hasEmpty;

      if (canSpawn) {
        timer += Time.delta * state.rules.unitBuildSpeed(team) * powerStatus;
        warmup = Mathf.lerpDelta(warmup, 1f, 0.1f);
        if (timer >= spawnTime && !net.client()) {
          for (int i = 0; i < droneCount; i++) {
            if (children[i] == null || !children[i].isValid()) {
              spawnDrone(i);
              break;
            }
          }
          timer = 0f;
        }
      } else {
        warmup = Mathf.lerpDelta(warmup, 0f, 0.1f);
      }
    }

    void spawnDrone(int slot) {
      Unit u = droneType.create(team);
      u.set(x, y);

      float destDeg = Time.time * orbitSpeed + slot * 360f / droneCount;
      float rad = destDeg * Mathf.degRad;
      float tx = x + Mathf.cos(rad) * orbitRadius;
      float ty = y + Mathf.sin(rad) * orbitRadius;

      u.rotation = 90f;
      u.flag = -(id * 10000L + slot);

      OrbitDroneAI ai = new OrbitDroneAI();
      ai.protectedTarget = this;
      // 先设轨道目标，再启动到达动画（从建筑位置飞向轨道，而不是瞬移）
      ai.setOrbit(tx, ty, destDeg);
      ai.startArrive(x, y, tx, ty, destDeg, arriveDuration * 1.5f);
      u.controller(ai);

      children[slot] = u;

      if (spawnEffect != null)
        spawnEffect.at(x, y, 0f, this);
      createSound.at(this, 1f + Mathf.range(0.06f), createSoundVolume);
      Fx.unitAssemble.at(x, y, 0f, droneType);
      Events.fire(new UnitCreateEvent(u, this));

      if (!net.client()) {
        u.add();
        Units.notifyUnitSpawn(u);
      }
    }

    void scanChildren() {
      Groups.unit.each(u -> {
        if (u.team != team || u.type != droneType)
          return;
        double f = u.flag;
        if (f >= 0)
          return;
        long val = (long) (-f);
        long bid = val / 10000L;
        if (bid != id)
          return;
        int slot = (int) (val % 10000L);
        if (slot >= 0 && slot < droneCount && children[slot] == null) {
          children[slot] = u;
          if (!(u.controller() instanceof OrbitDroneAI)) {
            OrbitDroneAI ai = new OrbitDroneAI();
            ai.protectedTarget = this;
            u.controller(ai);
          }
        }
      });
    }

    @Override
    public void onRemoved() {
      super.onRemoved();
      for (int i = 0; i < droneCount; i++) {
        if (children[i] != null && children[i].isValid()
            && children[i].controller() instanceof OrbitDroneAI ai) {
          ai.clearIntercept();
          children[i].remove();
        }
      }
    }

    @Override
    public void buildConfiguration(Table table) {
      TextButton modeBtn = new TextButton("", Styles.logict);
      modeBtn.update(() -> modeBtn.setText(mode == 0 ? "模式: 环绕建筑" : "模式: 环绕单位"));
      modeBtn.clicked(() -> configure(mode == 0 ? 1 : 0));
      table.add(modeBtn).size(180, 45).row();

      Table extra = new Table();
      extra.visible(() -> mode == 1);

      extra.label(() -> {
        if (targetUnitId > 0) {
          Unit u = Groups.unit.getByID(targetUnitId);
          if (u != null && u.isValid()) {
            return "目标: [accent]" + u.type.localizedName + "[]";
          }
          return "目标: [scarlet]已丢失[]";
        }
        return "目标: [lightgray]自动选择[]";
      }).padTop(4).row();

      extra.table(Styles.black6, list -> {
        list.label(() -> "[gray]附近友军（点击选择）").pad(4).row();

        Seq<Unit> nearby = new Seq<>();
        Units.nearby(team, x, y, threatRange * 1.5f, u -> {
          if (u.isValid() && u.type != droneType)
            nearby.add(u);
        });
        nearby.sort(u -> u.dst(this));
        if (nearby.size > maxListUnits)
          nearby.truncate(maxListUnits);

        if (nearby.isEmpty()) {
          list.add("[darkgray]范围内无友军").pad(4);
        } else {
          for (Unit u : nearby) {
            list.button(b -> {
              b.left();
              b.image(u.type.uiIcon).size(32).padRight(6);
              b.add(u.type.localizedName + "\n[gray]"
                  + Strings.autoFixed(u.dst(this) / 8f, 1) + "格").left();
            }, Styles.flatt, () -> {
              configure(u.id);
              ui.showInfoToast("已指定: " + u.type.localizedName, 2f);
            }).size(200, 44).pad(2).row();
          }
        }
      }).pad(4).row();

      extra.button("[lightgray]恢复自动选择", Styles.flatt, () -> {
        configure(-1);
      }).size(140, 36).padTop(4);

      table.add(extra).padTop(4).row();
    }

    @Override
    public Object config() {
      return mode;
    }

    @Override
    public void draw() {
      super.draw();
      if (warmup > 0.001f) {
        Draw.draw(Layer.blockOver, () -> {
          Drawf.construct(this, droneType, 0f, timer / spawnTime, warmup, Time.time);
        });
      }
    }

    @Override
    public void drawSelect() {
      super.drawSelect();
      Drawf.dashCircle(x, y, orbitRadius, Pal.accent);
      Drawf.dashCircle(x, y, threatRange, Pal.lightishOrange);
    }

    @Override
    public byte version() {
      return 2;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.i(mode);
      write.i(targetUnitId);
      for (int i = 0; i < droneCount; i++) {
        Unit c = children[i];
        write.i(c != null && c.isValid() ? c.id : -1);
      }
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      mode = read.i();
      targetUnitId = read.i();
      if (revision >= 2) {
        pendingChildrenIds = new int[droneCount];
        for (int i = 0; i < droneCount; i++) {
          pendingChildrenIds[i] = read.i();
        }
      }
    }
  }
}
