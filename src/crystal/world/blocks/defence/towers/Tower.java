package crystal.world.blocks.defence.towers;

import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.content.CFx;
import crystal.util.DLog;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Statusc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.logic.Ranged;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class Tower extends Block {
  public float reload = 140f;
  public float range = 300f;
  public float damage = 30f;
  public float width = 3f;
  public int maxTarget = 5;
  public boolean targetAir = true;
  public boolean targetGround = true;
  public StatusEffect status = StatusEffects.none;
  public float statusDuration = 60f * 6f;
  public Color beamColor = Pal.accent;
  public Effect hitEffect = Fx.hitLaserBlast, damageEffect = CFx.straightLine;

  public Tower(String name) {
    super(name);
    update = true;
    solid = true;
    group = BlockGroup.turrets;
  }

  @Override
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    super.drawPlace(x, y, rotation, valid);
    Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, player.team().color);
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(Stat.damage, damage, StatUnit.none);
    stats.add(Stat.range, range / tilesize, StatUnit.blocks);
    stats.add(Stat.reload, 60f / reload * maxTarget, StatUnit.perSecond);
    stats.add(Stat.targetsAir, targetAir);
    stats.add(Stat.targetsGround, targetGround);
  }

  public class TowerBuild extends Building implements Ranged, TowerBlock {
    public float timer;
    protected final Seq<Healthc> all = new Seq<>();
    protected boolean isAttacking = false; // 是否处于攻击轮次中
    protected float attackIntervalTimer = 0f; // 攻击间隔计时器（累计到 0.1 秒触发下一次）
    protected int attackedCount = 0; // 当前轮次已攻击次数

    /** 同步的陆军目标（地面单位+敌方建筑） */
    public Seq<Healthc> syncedGroundTargets = new Seq<>();
    /** 同步的空军目标（飞行单位） */
    public Seq<Healthc> syncedAirTargets = new Seq<>();
    /** 是否被扩展塔链接，为true时使用同步目标，否则使用自身范围扫描 */
    public boolean isLinked = false;

    @Override
    public float range() {
      return range;
    }

    @Override
    public void drawSelect() {
      Drawf.dashCircle(x, y, range(), team.color);
    }

    public float cdelta() {
      return this.delta();
    }

    @Override
    public boolean shouldConsume() {
      return timer < reload || hasEnemy();
    }

    @Override
    public void placed() {
      super.placed();
      Events.fire(new TowerBuildEvent(this.x, this.y));
    }

    public boolean hasEnemy() {
      if (isLinked) {
        // 被链接时，判断同步目标中是否有存活的敌方单位
        if (targetGround && !syncedGroundTargets.isEmpty()) {
          for (Healthc h : syncedGroundTargets) {
            if (!h.dead())
              return true;
          }
        }
        if (targetAir && !syncedAirTargets.isEmpty()) {
          for (Healthc h : syncedAirTargets) {
            if (!h.dead())
              return true;
          }
        }
        return false;
      } else {
        // 未被链接时，使用原有自身范围扫描逻辑
        Seq<Healthc> tmp = new Seq<>();
        if (targetGround) {
          Units.nearby(null, x, y, range, other -> {
            if (other.isGrounded() && !other.isFlying() && !other.dead() && other.team() != this.team) {
              tmp.add(other);
            }
          });
          indexer.eachBlock(null, x, y, this.range(), build -> build.team != this.team && !build.dead, build -> {
            tmp.add(build);
          });
        }
        if (targetAir) {
          Units.nearby(null, x, y, range, other -> {
            if (other.isFlying() && !other.dead() && other.team() != this.team) {
              tmp.add(other);
            }
          });
        }
        return !tmp.isEmpty();
      }
    }

    public void updateAll() {
      all.clear();
      if (isLinked) {
        // 被链接时，使用扩展塔同步的目标，按自身攻击类型筛选
        if (targetGround) {
          all.addAll(syncedGroundTargets);
        }
        if (targetAir) {
          all.addAll(syncedAirTargets);
        }
        // 过滤已死亡目标，避免空指针
        all.removeAll(Healthc::dead);
      } else {
        // 未被链接时，使用原有自身范围扫描逻辑
        if (targetGround) {
          Units.nearby(null, x, y, range, other -> {
            if (other.isGrounded() && !other.isFlying() && !other.dead() && other.team() != this.team) {
              all.add(other);
            }
          });
          indexer.eachBlock(null, x, y, this.range(), build -> build.team != this.team && !build.dead, build -> {
            all.add(build);
          });
        }
        if (targetAir) {
          Units.nearby(null, x, y, range, other -> {
            if (other.isFlying() && !other.dead() && other.team() != this.team) {
              all.add(other);
            }
          });
        }
      }
    }

    @Override
    public void updateTile() {
      DLog.info("timer " + timer);
      if (!isAttacking) {
        // 阶段 1：冷却阶段（累计到 reload 时间后，开始攻击轮次）
        if ((timer += cdelta()) >= reload) {
          // 收集目标（和原逻辑一致，一轮攻击中目标列表固定）
          updateAll();
          // 有目标则开始攻击轮次
          if (!all.isEmpty()) {
            all.sort(h -> h.dst2(x, y)); // 按距离排序（优先攻击近的）
            isAttacking = true;
            attackedCount = 0;
            attackIntervalTimer = 0f; // 重置攻击间隔计时器
          } else {
            // 无目标则重置冷却，等待下一轮
            // timer = 0f;
            return;
          }
        }
      } else {
        // 阶段 2：攻击阶段（每 0.1 秒攻击一次，直到达到 maxTarget 次）
        attackIntervalTimer += cdelta();
        // 0.1 秒间隔到，且还有攻击次数剩余
        if (attackIntervalTimer >= 8f && attackedCount < maxTarget) {
          // 获取当前要攻击的目标（循环取目标列表，避免目标不足时数组越界）
          if (all.isEmpty())
            updateAll();
          Healthc target = all.get(attackedCount % all.size);
          if (!target.dead()) { // 目标存活才攻击
            attack(target);
          }
          // 更新状态：攻击次数 +1，重置间隔计时器
          attackedCount++;
          attackIntervalTimer = 0f;

          // 攻击次数达到上限，结束攻击轮次，重置冷却计时器
          if (attackedCount >= maxTarget) {
            consume();
            isAttacking = false;
            timer = 0f; // 开始下一轮 reload 冷却
          }
        }
      }
    }

    public void attack(Healthc healthc) {
      if (healthc instanceof Building b) {
        b.damage(this.team(), damage);
      } else {
        healthc.damage(damage);
      }
      if (healthc instanceof Statusc s) {
        s.apply(status, statusDuration);
      }
      damageEffect.at(x, y, 0f, beamColor, healthc);
    }

    public void consume() {
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.f(timer);
      write.f(attackIntervalTimer);
      write.i(attackedCount);
      write.bool(isAttacking);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      timer = read.f();
      attackIntervalTimer = read.f();
      attackedCount = read.i();
      isAttacking = read.bool();
    }
  }

  public static class TowerBuildEvent {
    public float x, y;

    public TowerBuildEvent(float x, float y) {
      this.x = x;
      this.y = y;
    }
  }
}
