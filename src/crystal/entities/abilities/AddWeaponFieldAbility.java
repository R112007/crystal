package crystal.entities.abilities;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import crystal.world.meta.CStatValues;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Predict;
import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.effect.ParticleEffect;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;

public class AddWeaponFieldAbility extends Ability {
  /** 作用范围（世界单位） */
  public float range;
  /** 武器持续时间（帧） */
  public float duration;
  /** 添加武器后的冷却时间（帧），冷却期间不会给新单位添加武器 */
  public float cooldown;
  /** 添加的武器 */
  public Weapon weapon;
  /** 施加在单位身上的特效，与武器存在时长相同 */
  public Effect unitEffect = new ParticleEffect() {
    {
      particles = 7;
      lifetime = 15;
      length = 10;
      sizeFrom = 2;
      sizeTo = 0;
      Color.valueOf("#6A97D9FF");
      colorFrom = Color.valueOf("#6A97D9FF");
      colorTo = Color.valueOf("#99E7FFFF");
    }
  };;
  /** 波纹扩散特效颜色 */
  public Color waveColor = Color.valueOf("#6A97D9FF");
  /** 计时圆颜色 */
  public Color timerColor = Color.valueOf("#99E7FFFF");
  /** 波纹线宽 */
  public float waveStroke = 2f;
  /** 计时圆线宽 */
  public float timerStroke = 2.5f;
  /** 波纹扩散持续时间（帧） */
  public float waveDuration = 40f;
  /** 单位特效触发间隔（帧） */
  public float unitEffectInterval = 20f;

  // 运行时数据
  private transient float timer;
  /** 冷却计时器（帧），大于0表示正在冷却 */
  private transient float cooldownTimer;
  /** 单位特效计时器 */
  private transient float unitEffectTimer;
  /** 本能力实例追踪的单位（用于特效），不代表实际过期时间 */
  private transient ObjectMap<Unit, WeaponTimer> activeUnits = new ObjectMap<>();
  /** 临时列表用于清理 */
  private transient Seq<Unit> tmpRemove = new Seq<>();
  /** 波纹扩散进度，-1表示未在扩散 */
  private transient float waveProgress = -1f;

  /** 全局共享：目标单位 -> (武器 -> 过期时间)，确保同一武器在同一目标上只存在一份 */
  private static final ObjectMap<Unit, ObjectMap<Weapon, Float>> globalExpireTimes = new ObjectMap<>();

  // 仿照 AddWeaponAbility 的瞄准逻辑
  private transient Teamc target;
  private transient float noTargetTime;
  private static final float rotateBackTimer = 60f * 5f;

  public AddWeaponFieldAbility() {
    super();
  }

  public AddWeaponFieldAbility(float range, float duration, float cooldown, Weapon weapon) {
    this.range = range;
    this.duration = duration;
    this.cooldown = cooldown;
    this.weapon = weapon;
  }

  @Override
  public void init(mindustry.type.UnitType type) {
    super.init(type);
    if (weapon != null) {
      weapon.load();
      if (weapon.region == null || !weapon.region.found()) {
        weapon.region = Core.atlas.find("clear");
      }
    }
  }

  @Override
  public void update(Unit unit) {
    if (weapon == null)
      return;

    // 更新冷却计时器（帧）
    if (cooldownTimer > 0) {
      cooldownTimer -= Time.delta;
      if (cooldownTimer < 0)
        cooldownTimer = 0;
    }

    // 更新波纹扩散进度
    if (waveProgress >= 0f) {
      waveProgress += Time.delta;
      if (waveProgress >= waveDuration) {
        waveProgress = -1f; // 扩散结束
      }
    }

    // 更新单位特效计时器
    unitEffectTimer += Time.delta;

    // 核心：冷却到了才检测并施加效果（仿照 StatusFieldAbility）
    if ((timer += Time.delta) >= cooldown) {
      timer = 0f;
      cooldownTimer = cooldown; // 重置冷却计时器
      // 触发波纹扩散
      waveProgress = 0f;
      applyEffect(unit);
    }

    // 每帧更新计时器，清理过期或死亡的单位
    updateTimers();

    // 为所有受影响的单位更新武器瞄准/射击（仿照 AddWeaponAbility）
    for (var entry : activeUnits) {
      Unit targetUnit = entry.key;
      if (targetUnit.isValid()) {
        updateWeaponForUnit(targetUnit);
      }
    }
  }

  /**
   * 仿照 StatusFieldAbility：冷却到了才施加效果
   * 扫描范围内单位，给没有武器的单位添加，给已有武器的单位刷新时间
   */
  private void applyEffect(Unit source) {
    Units.nearby(source.team, source.x, source.y, range, target -> {
      if (!target.isValid() || target == source)
        return;

      if (hasWeapon(target, weapon)) {
        // 已有武器：刷新全局过期时间
        refreshWeaponTime(target);
        // 确保纳入本能力追踪（用于特效和武器更新）
        if (!activeUnits.containsKey(target)) {
          activeUnits.put(target, new WeaponTimer(Time.time + duration, -1));
        } else {
          activeUnits.get(target).expireTime = Time.time + duration;
        }
      } else {
        // 新单位：添加武器
        addWeaponToUnit(target);
      }
    });
  }

  /** 检查单位是否已有指定武器 */
  private boolean hasWeapon(Unit unit, Weapon weapon) {
    for (WeaponMount mount : unit.mounts) {
      if (mount.weapon == weapon) {
        return true;
      }
    }
    return false;
  }

  /** 刷新目标单位上本武器的全局过期时间 */
  private void refreshWeaponTime(Unit unit) {
    ObjectMap<Weapon, Float> unitWeapons = globalExpireTimes.get(unit);
    if (unitWeapons == null) {
      unitWeapons = new ObjectMap<>();
      globalExpireTimes.put(unit, unitWeapons);
    }
    unitWeapons.put(weapon, Time.time + duration);
  }

  private void updateTimers() {
    tmpRemove.clear();
    float now = Time.time;

    for (var entry : activeUnits) {
      Unit target = entry.key;

      // 单位死亡立即移除
      if (!target.isValid()) {
        tmpRemove.add(target);
        continue;
      }

      // 以全局过期时间为准
      ObjectMap<Weapon, Float> unitWeapons = globalExpireTimes.get(target);
      if (unitWeapons == null || !unitWeapons.containsKey(weapon)) {
        tmpRemove.add(target);
        continue;
      }

      if (now >= unitWeapons.get(weapon)) {
        tmpRemove.add(target);
      }
    }

    // 执行移除
    for (Unit target : tmpRemove) {
      removeWeaponFromUnit(target);

      // 清理全局记录
      ObjectMap<Weapon, Float> unitWeapons = globalExpireTimes.get(target);
      if (unitWeapons != null) {
        unitWeapons.remove(weapon);
        if (unitWeapons.isEmpty()) {
          globalExpireTimes.remove(target);
        }
      }

      activeUnits.remove(target);
    }
  }

  /**
   * 添加武器到单位
   */
  private void addWeaponToUnit(Unit unit) {
    WeaponMount[] old = unit.mounts;
    WeaponMount[] mounts = new WeaponMount[old.length + 1];
    System.arraycopy(old, 0, mounts, 0, old.length);

    WeaponMount newMount = weapon.mountType.get(weapon);

    // 同步当前瞄准位置
    if (old.length > 0) {
      newMount.aimX = old[0].aimX;
      newMount.aimY = old[0].aimY;
      newMount.shoot = old[0].shoot;
      newMount.rotate = old[0].rotate;
    } else {
      newMount.aimX = unit.aimX;
      newMount.aimY = unit.aimY;
    }

    mounts[old.length] = newMount;
    unit.mounts = mounts;

    // 写入全局过期时间
    refreshWeaponTime(unit);

    // 记录到本能力实例（用于特效和武器更新）
    activeUnits.put(unit, new WeaponTimer(Time.time + duration, old.length));
  }

  /**
   * 从单位移除武器
   */
  private void removeWeaponFromUnit(Unit unit) {
    WeaponMount[] old = unit.mounts;
    if (old.length <= 0)
      return;

    int removeIndex = -1;
    for (int i = old.length - 1; i >= 0; i--) {
      if (old[i].weapon == weapon) {
        removeIndex = i;
        break;
      }
    }

    if (removeIndex < 0)
      return;

    WeaponMount[] mounts = new WeaponMount[old.length - 1];
    System.arraycopy(old, 0, mounts, 0, removeIndex);
    System.arraycopy(old, removeIndex + 1, mounts, removeIndex, old.length - removeIndex - 1);

    unit.mounts = mounts;
  }

  /**
   * 仿照 AddWeaponAbility.updateWeapons() 为单个单位更新武器瞄准和射击
   * 使用武器自身射程，不受 unit.range() / type.maxRange 限制
   * 
   * 开火逻辑：
   * - 非玩家控制时：武器自主索敌并开火
   * - 玩家控制但未输入开火指令时：武器自主索敌并开火
   * - 玩家控制且输入开火指令时：武器跟随玩家控制开火
   */
  private void updateWeaponForUnit(Unit unit) {
    float rotation = unit.rotation - 90;
    float weaponRange = weapon.range();

    // 找到我们添加的武器 mount
    WeaponMount mount = null;
    for (WeaponMount m : unit.mounts) {
      if (m.weapon == weapon) {
        mount = m;
        break;
      }
    }
    if (mount == null)
      return;

    // 计算武器挂载位置
    float mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y);
    float mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y);

    // 判断玩家是否输入了开火指令
    boolean playerFiring = unit.isPlayer() && unit.isShooting;

    if (playerFiring) {
      // 玩家控制且输入开火指令：武器跟随玩家控制
      mount.shoot = true;
      mount.rotate = true;
      mount.aimX = unit.aimX;
      mount.aimY = unit.aimY;
    } else {
      // 非玩家控制，或玩家控制但未输入开火指令：武器自主发射
      // 重新索敌
      target = findTarget(unit, unit.x, unit.y, weaponRange, unit.type.targetAir, unit.type.targetGround);

      if (invalid(unit, target)) {
        if (target instanceof Healthc h && !h.isValid()) {
          target = null;
        }
        target = null;
        noTargetTime += Time.delta;
      } else {
        noTargetTime = 0f;
      }

      // 让武器旋转指向目标
      mount.rotate = true;

      boolean shoot = false;

      if (target != null) {
        shoot = target.within(mountX, mountY, weaponRange + (target instanceof Sized s ? s.hitSize() / 2f : 0f));

        // 预测拦截
        Vec2 to = Predict.intercept(unit, target, weapon.bullet);
        mount.aimX = to.x;
        mount.aimY = to.y;
      }

      mount.shoot = shoot;

      // 无目标时慢慢转回默认角度
      if (target == null && !shoot && !Angles.within(mount.rotation, weapon.baseRotation, 0.01f)
          && noTargetTime >= rotateBackTimer) {
        mount.rotate = true;
        Tmp.v1.trns(unit.rotation + weapon.baseRotation, 5f);
        mount.aimX = mountX + Tmp.v1.x;
        mount.aimY = mountY + Tmp.v1.y;
      }

      if (shoot) {
        unit.aimX = mount.aimX;
        unit.aimY = mount.aimY;
        unit.isShooting = true;
      }
    }
  }

  /** 仿照 AddWeaponAbility.findTarget */
  public Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground) {
    return Units.closestTarget(unit.team, x, y, range,
        u -> u.checkTarget(air, ground),
        t -> ground && (unit.type.targetUnderBlocks || !t.block.underBullets));
  }

  /** 仿照 AddWeaponAbility.invalid */
  public boolean invalid(Unit unit, Teamc target) {
    return Units.invalidateTarget(target, unit.team, unit.x, unit.y);
  }

  @Override
  public void draw(Unit unit) {
    // 绘制计时圆：冷却期间从顶部(90°)开始绕圈
    if (cooldown > 0 && timer > 0) {
      float progress = timer / cooldown;
      Tmp.c1.set(timerColor).a = 0.5f;
      Draw.color(Tmp.c1);
      Lines.stroke(timerStroke);
      Lines.arc(unit.x, unit.y, range / 2 + 8f, progress, 90f);
      Draw.reset();
    }

    // 绘制波纹扩散特效（绕完圈后触发）
    if (waveProgress >= 0f) {
      drawWave(unit.x, unit.y, waveProgress / waveDuration, unit.team.color);
    }

    // 每20帧为单位触发一次特效
    if (unitEffect != Fx.none && unitEffectTimer >= unitEffectInterval) {
      unitEffectTimer = 0f;
      for (var entry : activeUnits) {
        Unit target = entry.key;
        if (target.isValid()) {
          unitEffect.at(target.x, target.y, 0, unit.team.color);
        }
      }
    }
  }

  /**
   * 绘制向外扩散的波纹
   * 
   * @param x     中心X
   * @param y     中心Y
   * @param fin   0~1 完成度
   * @param color 颜色
   */
  private void drawWave(float x, float y, float fin, Color color) {
    float fout = 1f - fin;
    float rad = fin * range;

    Draw.color(color, fout);
    Lines.stroke(waveStroke * (fout + 0.1f));
    Lines.circle(x, y, rad);

    Draw.reset();
  }

  @Override
  public void addStats(Table t) {
    super.addStats(t);
    t.add("[lightgray]范围: [stat]" + Strings.autoFixed(range / 8f, 1) + " [lightgray]格").row();
    t.add("[lightgray]持续时间: [stat]" + Strings.autoFixed(duration / 60f, 1) + " [lightgray]秒").row();
    t.add("[lightgray]冷却时间: [stat]" + Strings.autoFixed(cooldown / 60f, 1) + " [lightgray]秒").row();
    if (weapon != null) {
      CStatValues.displayWeapon(weapon, t);
    }
    t.row();
  }

  @Override
  public String localized() {
    return Core.bundle.get("ability.addweaponfieldability", "武器力场");
  }

  private static class WeaponTimer {
    public float expireTime;
    public int mountIndex;

    public WeaponTimer(float expireTime, int mountIndex) {
      this.expireTime = expireTime;
      this.mountIndex = mountIndex;
    }
  }
}
