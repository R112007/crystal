package crystal.entities.abilities;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.type.UnitType;

import static mindustry.Vars.*;

public class SpawnUnitShieldAbility extends Ability {
  public UnitType unit;
  /** 额定环绕数量 */
  public int amount = 3;
  /** 生产单个单位的间隔（tick） */
  public float spawnTime = 60f;
  /** 环绕半径（世界坐标） */
  public float orbitRadius = 40f;
  /** 环绕角速度（度/tick） */
  public float orbitSpeed = 0.5f;
  /** 从生成点飞入轨道的时间（tick） */
  public float arriveDuration = 30f;

  public Effect spawnEffect = Fx.spawn;

  // --- 以下字段均为 transient，不随存档保存；子单位本身作为普通 Unit 会被自动存档 ---
  protected transient Unit[] children;
  protected transient float[] arrive; // >0: 剩余飞入时间, -1: 已就位
  protected transient Vec2[] from; // 飞入动画的起始位置
  protected transient float timer;
  protected transient boolean scanned; // 用于读档后一次性扫描认领

  public SpawnUnitShieldAbility(UnitType unit, int amount, float spawnTime, float orbitRadius, float orbitSpeed) {
    this.unit = unit;
    this.amount = amount;
    this.spawnTime = spawnTime;
    this.orbitRadius = orbitRadius;
    this.orbitSpeed = orbitSpeed;
  }

  public SpawnUnitShieldAbility() {
  }

  @Override
  public void addStats(Table t) {
    super.addStats(t);
    t.add(abilityStat("amount", amount));
    t.row();
    t.add(abilityStat("buildtime", Strings.autoFixed(spawnTime / 60f, 2)));
    t.row();
    t.add((unit.hasEmoji() ? unit.emoji() : "") + "[stat]" + unit.localizedName);
  }

  @Override
  public void update(Unit parent) {
    // 初始化固定长度数组，槽位索引 = 环绕序号，永不变更
    if (children == null || children.length != amount) {
      children = new Unit[amount];
      arrive = new float[amount];
      from = new Vec2[amount];
      for (int i = 0; i < amount; i++) {
        arrive[i] = -1f;
        from[i] = new Vec2();
      }
    }

    // 1) 清理已死亡的子单位
    for (int i = 0; i < amount; i++) {
      if (children[i] != null && !children[i].isValid()) {
        children[i] = null;
        arrive[i] = -1f;
      }
    }

    // 2) 读档/进图后一次性扫描：通过 flag 重新认领属于自己的子单位
    if (!scanned) {
      scanned = true;
      double base = getBaseFlag(parent);
      Groups.unit.each(u -> {
        if (u.team != parent.team || u.type != this.unit)
          return;
        double f = u.flag;
        if (f >= base + 1 && f < base + amount + 1) {
          int slot = (int) (f - base - 1);
          if (slot >= 0 && slot < amount && children[slot] == null) {
            children[slot] = u;
            arrive[slot] = -1f; // 已就位的直接进轨道
          }
        }
      });
    }

    // 3) 驱动环绕 + 飞入动画
    float baseAngle = Time.time * orbitSpeed;
    int alive = 0;

    for (int i = 0; i < amount; i++) {
      Unit child = children[i];
      if (child == null || !child.isValid())
        continue;

      alive++;
      float angle = (baseAngle + i * 360f / amount) * Mathf.degRad;
      float tx = parent.x + Mathf.cos(angle) * orbitRadius;
      float ty = parent.y + Mathf.sin(angle) * orbitRadius;

      if (arrive[i] > 0f) {
        // 飞入轨道动画：从生成时的起点平滑插值到目标轨道位置
        arrive[i] -= Time.delta;
        float p = 1f - Mathf.clamp(arrive[i] / arriveDuration);
        p = Interp.smooth.apply(p);
        child.x = Mathf.lerp(from[i].x, tx, p);
        child.y = Mathf.lerp(from[i].y, ty, p);
      } else {
        arrive[i] = -1f;
        child.x = tx;
        child.y = ty;
      }

      // 子单位面向切线方向（运动方向）
      child.rotation = angle * Mathf.radDeg;
    }

    // 4) 补足空缺（仅主机/单机执行实际生成）
    if (alive < amount && Units.canCreate(parent.team, this.unit)) {
      timer += Time.delta * state.rules.unitBuildSpeed(parent.team);
      if (timer >= spawnTime && !net.client()) {
        spawnChild(parent);
        timer = 0f;
      }
    } else {
      timer = 0f;
    }
  }

  void spawnChild(Unit parent) {
    // 找第一个空槽
    int slot = -1;
    for (int i = 0; i < amount; i++) {
      if (children[i] == null || !children[i].isValid()) {
        slot = i;
        break;
      }
    }
    if (slot < 0)
      return;

    Unit u = this.unit.create(parent.team);
    u.set(parent.x, parent.y);
    u.rotation = parent.rotation;

    // 把槽位信息写进 flag，供读档后重新认领（SaveVersion 会读写 UnitComp.flag）
    double base = getBaseFlag(parent);
    u.flag = base + slot + 1;

    children[slot] = u;
    from[slot].set(parent.x, parent.y);
    arrive[slot] = arriveDuration;

    spawnEffect.at(parent.x, parent.y, 0f, parent);

    if (!Vars.net.client()) {
      u.add();
      Units.notifyUnitSpawn(u);
      Events.fire(new UnitCreateEvent(u, null, parent));
    }
  }

  /** 用父单位 id 生成一个独特的 flag 基值。取负减少与逻辑处理器 flag 的冲突概率。 */
  double getBaseFlag(Unit parent) {
    return -(parent.id * 1000000.0 + 1);
  }

  @Override
  public void draw(Unit parent) {
    int alive = 0;
    for (int i = 0; i < amount; i++) {
      if (children != null && children[i] != null && children[i].isValid())
        alive++;
    }

    if (alive < amount && Units.canCreate(parent.team, this.unit)) {
      Draw.draw(Draw.z(), () -> {
        Drawf.construct(parent.x, parent.y, this.unit.fullIcon, parent.rotation - 90, timer / spawnTime, 1f, timer);
      });
    }
  }

  @Override
  public String localized() {
    return Core.bundle.format("ability.orbitunitspawn", unit.localizedName);
  }
}
