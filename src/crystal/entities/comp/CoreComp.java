package crystal.entities.comp;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import crystal.CVars;
import crystal.Crystal;
import crystal.content.CUnitCommands;
import crystal.gen.Corec;
import crystal.type.CoreUnit;
import crystal.type.CoreUnitType;
import crystal.util.DLog;
import crystal.world.blocks.stroage.CoreInjector;
import crystal.world.blocks.stroage.MoveCoreSystem;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.MethodPriority;
import ent.anno.Annotations.Remove;
import ent.anno.Annotations.Replace;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.core.UI;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.game.EventType.*;
import mindustry.game.EventType.SaveWriteEvent;
import mindustry.gen.Building;
import mindustry.gen.Minerc;
import mindustry.gen.Player;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.gen.Unitc;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.blocks.ExplosionShield;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.StatUnit;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

@EntityComponent
public abstract class CoreComp implements Unitc, Corec, Posc {
  public ItemModule items = new ItemModule();
  public int storageCapacity;
  public boolean deployed = false;
  public transient CoreBuild proxy;
  public float suckRange, // 收取核心机物品范围
      auxiliaryRange;// 挖矿建造范围
  public int unitCapBonus;
  public boolean accept = false; // 存取模式开关：false取货 / true存货
  public transient Unit currentInteractor; // 记录当前点击交互的核心机，给面板取货用
  public transient Seq<Building> nearbyBuildCache;
  public transient float cacheX, cacheY;
  public transient float cacheTime;
  public static final float moveThreshold = 2f;
  public static final int maxCacheFrames = 10;
  public ItemModule savedItems = new ItemModule();
  @Import
  ItemStack stack;
  @Import
  Team team;
  @Import
  @Nullable
  Tile mineTile;
  @Import
  UnitType type;
  @Import
  float mineTimer;

  public float idleTimer = 0f;
  public boolean autoSwitched = false;
  public Corec corec = self();

  @Override
  public void setType(UnitType type) {
    if (type instanceof CoreUnit c) {
      this.storageCapacity = c.storageCapacity();
      this.suckRange = c.suckRange();
      this.unitCapBonus = c.unitCapBonus();
      this.auxiliaryRange = c.auxiliaryRange();
    } else
      throw new IllegalArgumentException("CoreUnit must use CoreUnitType");
  }

  public @Nullable Item getOreItem(Tile tile) {
    if (type.mineFloor) {
      if (tile.floor() != null && tile.floor().itemDrop != null) {
        return tile.floor().itemDrop;
      }
      if (tile.overlay() != null && tile.overlay().itemDrop != null) {
        return tile.overlay().itemDrop;
      }
    }
    if (type.mineWalls && tile.block() != null && tile.block().itemDrop != null) {
      return tile.block().itemDrop;
    }
    return null;
  }

  public ItemModule flowItems() {
    return items;
  }

  @Override
  public void add() {
    if (!MoveCoreSystem.getCores(team()).contains(this)) {
      CoreInjector.injectCore(team().data(), this);
      Events.fire(new CoreChangeEvent(this.proxy()));
      Fx.upgradeCore.at(this);
      Events.on(SaveWriteEvent.class, e -> {
        if (proxy != null && proxy.items != null && !dead()) {
          savedItems.set(proxy.items);
        }
      });
      DLog.info("[创建]" + "单位编号" + id() + " " + "核心数量" + Vars.player.team().data().cores.size);
    }
  }

  public float realRad() {
    return hitSize() * 0.9f;
  }

  public Seq<Building> nearbyBuilds() {
    float now = Time.time;
    if (nearbyBuildCache != null
        && Math.abs(x() - cacheX) < moveThreshold
        && Math.abs(y() - cacheY) < moveThreshold
        && now - cacheTime < maxCacheFrames) {
      return nearbyBuildCache;
    }
    cacheX = x();
    cacheY = y();
    cacheTime = now;
    float half = hitSize() / 2f + tilesize * 2;
    int minX = (int) ((x() - half) / tilesize);
    int maxX = (int) ((x() + half) / tilesize);
    int minY = (int) ((y() - half) / tilesize);
    int maxY = (int) ((y() + half) / tilesize);
    minX = Math.max(0, minX);
    maxX = Math.min(world.width() - 1, maxX);
    minY = Math.max(0, minY);
    maxY = Math.min(world.height() - 1, maxY);
    if (nearbyBuildCache == null) {
      nearbyBuildCache = new Seq<>();
    } else {
      nearbyBuildCache.clear();
    }
    ObjectSet<Building> set = new ObjectSet<>();
    for (int tx = minX; tx <= maxX; tx++) {
      for (int ty = minY; ty <= maxY; ty++) {
        Building build = world.build(tx, ty);
        if (build != null && set.add(build) && !(build.block instanceof CoreBlock)) {
          nearbyBuildCache.add(build);
        }
      }
    }
    return nearbyBuildCache;
  }

  @Override
  public void afterRead() {
    if (!dead() && !MoveCoreSystem.getCores(team()).contains(this)) {
      CoreInjector.injectCore(team().data(), this);
      Events.fire(new CoreChangeEvent(this.proxy()));
    }
    if (proxy != null && proxy.items != null && savedItems != null && savedItems.total() > 0) {
      proxy.items.set(savedItems);
      this.items = proxy.items;
    }
    DLog.info("[读取]" + "单位编号" + id() + " " + "核心数量" + Vars.player.team().data().cores.size);
  }

  @MethodPriority(-999)
  @Override
  public void update() {
    if (proxy != null) {
      Tile currentTile = Vars.world.tileWorld(x(), y());
      if (proxy.tile != currentTile) {
        proxy.tile = currentTile;
      }
    }
    if (self() instanceof Corec core && core.deployed()) {
      vel().setZero();
      deltaX(0);
      deltaY(0);
    }
    if (Crystal.timer % 60 == 0 && proxy != null && proxy.items != null) {
      savedItems.set(proxy.items);
    }
    if (proxy != null) {
      proxy.team = team();
      if (proxy.items != null) {
        proxy.items.updateFlow();
      }
    }
    // 同步虚拟核心状态
    if (proxy != null) {
      proxy.team = team();
    }

    if (stack.amount > 0 && stack.item != null && proxy != null) {
      int max = proxy.storageCapacity;
      int have = items.get(stack.item);
      if (have >= max) {
        Fx.coreBurn.at(this);
        stack.set(null, 0);
      } else {
        int canStore = Math.min(stack.amount, max - have);
        items.add(stack.item, canStore);
        stack.amount -= canStore;
        if (stack.amount <= 0) {
          stack.set(null, 0);
        }
      }
    }

    if (Crystal.timer % 60 == 0) {
      updateClosestCore();
    }
    if (Crystal.timer % 200 == 0) {
      for (var c : player.team().data().cores) {
        DLog.info("核心" + c.id + "单位容量" + ((CoreBlock) c.block).unitCapModifier);
      }
      DLog.info("当前团队单位上限：" + Units.getCap(team()));
      DLog.info("单种容量: " + proxy.storageCapacity + ", 铜: " + items.get(Items.copper) + ", 总物品: " + items.total());
      DLog.info("高度:" + corec.elevation());
    }
    if (corec.elevation() > 0 && onSolid() == false) {
      corec.elevation(0f);
    }
    if (deployed) {
      Seq<Building> builds = nearbyBuilds();
      for (Building b : builds) {
        if (b.block.hasItems && b.items != null && b.items.total() > 0 && proxy != null) {

          // ===== 传送带（含装甲传送带）：只吸末端且朝向核心的 =====
          if (b instanceof mindustry.world.blocks.distribution.Conveyor.ConveyorBuild cb) {
            if (cb.next instanceof mindustry.world.blocks.distribution.Conveyor.ConveyorBuild
                && cb.next.team == team) {
              continue;
            }
            if (cb.next != null)
              continue;
            float dx = x() - b.x;
            float dy = y() - b.y;
            float dot = dx * Geometry.d4x(b.rotation) + dy * Geometry.d4y(b.rotation);
            if (dot <= 0)
              continue;
          }

          // ===== 管道（含装甲管道）：只吸末端且朝向核心的 =====
          if (b instanceof mindustry.world.blocks.distribution.Duct.DuctBuild db) {
            if (db.next instanceof mindustry.world.blocks.distribution.Duct.DuctBuild
                && db.next.team == team) {
              continue;
            }
            if (db.next != null)
              continue;
            float dx = x() - b.x;
            float dy = y() - b.y;
            float dot = dx * Geometry.d4x(b.rotation) + dy * Geometry.d4y(b.rotation);
            if (dot <= 0)
              continue;
          }

          // ===== 物品桥 (ItemBridge)：只吸输出端 =====
          // 输出端 = 有有效输出连接（link 有效）
          // 输入端/中间桥 = 无有效输出 或 有 incoming，不吸
          if (b instanceof mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild ib) {
            mindustry.world.blocks.distribution.ItemBridge bridgeBlock = (mindustry.world.blocks.distribution.ItemBridge) b.block;
            ib.checkIncoming();
            // 检查是否有有效输出连接
            boolean hasValidOutput = false;

            Tile linkTile = world.tile(ib.link);
            hasValidOutput = linkTile != null
                && !bridgeBlock.linkValid(ib.tile, world.tile(ib.link)) && ib.incoming.size > 0;
            // 无有效输出 → 是输入端或孤立桥，不吸
            if (!hasValidOutput)
              continue;

            // 有有效输出 → 是输出端，可以吸
          }

          // ===== 管道桥 (DuctBridge)：只吸输出端 =====
          // 输出端 = 有有效输出连接（lastLink 有效）
          if (b instanceof mindustry.world.blocks.distribution.DuctBridge.DuctBridgeBuild dbb) {
            // 无有效输出 → 是输入端，不吸
            if (dbb.lastLink == null || !dbb.lastLink.isValid() || dbb.lastLink.team() != team) {
              continue;
            }
            // 有有效输出 → 是输出端，可以吸
          }

          // ===== 原有吸取逻辑（容器、工厂等非运输建筑不受影响） =====
          b.items.each((item, amount) -> {
            if (amount <= 0)
              return;
            int max = proxy.storageCapacity;
            int have = items.get(item);
            if (have >= max) {
              int burnAmt = Math.min(amount, 10);
              if (burnAmt > 0) {
                b.removeStack(item, burnAmt);
                Fx.coreBurn.at(b.x, b.y);
              }
              return;
            }
            int space = max - have;
            int transfer = Math.min(amount, Math.min(space, 5));
            if (transfer > 0) {
              int removed = b.removeStack(item, transfer);
              if (removed > 0) {
                int particleCount = Mathf.clamp(removed / 3, 1, 8);
                int perParticle = removed / particleCount;
                int lastParticle = removed - perParticle * (particleCount - 1);
                for (int j = 0; j < particleCount; j++) {
                  final int carry = (j == particleCount - 1) ? lastParticle : perParticle;
                  Time.run(j * 3.0F, () -> {
                    InputHandler.createItemTransfer(item, 1, b.x, b.y, this, () -> {
                      int s = proxy.storageCapacity - items.get(item);
                      int toStore = Math.min(carry, Math.max(s, 0));
                      if (toStore > 0) {
                        items.add(item, toStore);
                      }
                      int burn = carry - toStore;
                      if (burn > 0) {
                        Fx.coreBurn.at(this);
                      }
                    });
                  });
                }
              }
            }
          });
        }
      }
    }
    updateCatchItemFromPlayer();
    updateAutoCommand();
  }

  @Override
  public void remove() {
    if (items != null && proxy != null && proxy.items != null) {
      ItemModule tmp = new ItemModule();
      tmp.set(proxy.items);
      this.items = tmp;
    }
    CoreInjector.removeCore(team().data(), this);
    Fx.explosion.at(self());
  }

  @Replace
  public void destroy() {
    if (!isAdded() || !killable())
      return;
    float shake = type.deathShake < 0 ? 3.0F + hitSize() / 3.0F : type.deathShake;
    if (type.createScorch) {
      Effect.scorch(x(), y(), (int) (hitSize() / 5));
    }
    Effect.shake(shake, shake, this);
    type.deathSound.at(this, 1.0F, type.deathSoundVolume);
    Events.fire(new UnitDestroyEvent(self()));
    for (WeaponMount mount : mounts()) {
      if (mount.weapon.shootOnDeath && !(mount.weapon.bullet.killShooter && mount.totalShots > 0)) {
        if (mount.weapon.shootOnDeathEffect != null && !hasTarget()) {
          mount.allowShootEffects = false;
          mount.weapon.shootOnDeathEffect.at(x(), y(), rotation());
        }
        mount.reload = 0.0F;
        mount.shoot = true;
        mount.weapon.update(self(), mount);
      }
    }
    if (type.flying && !spawnedByCore() && type.createWreck && state.rules.unitCrashDamage(team) > 0) {
      var shields = indexer.getEnemy(team, BlockFlag.shield);
      float crashDamage = Mathf.pow(hitSize(), 0.75F) * type.crashDamageMultiplier * 2.5F
          * state.rules.unitCrashDamage(team);
      if (shields.isEmpty()
          || !shields.contains((b) -> b instanceof ExplosionShield s && s.absorbExplosion(x(), y(), crashDamage))) {
        Damage.damage(team, x(), y(), Mathf.pow(hitSize(), 0.94F) * 1.25F, crashDamage, true, false, true);
      }
    }
    if (!headless && type.createScorch) {
      for (int i = 0; i < type.wreckRegions.length; i++) {
        if (type.wreckRegions[i].found()) {
          float range = type.hitSize / 4.0F;
          Tmp.v1.rnd(range);
          Effect.decal(type.wreckRegions[i], x() + Tmp.v1.x, y() + Tmp.v1.y, rotation() - 90);
        }
      }
    }
    for (Ability a : abilities()) {
      a.death(self());
    }
    type.killed(self());
    remove();
  }

  public boolean playerUnitInRange() {
    if (player.unit() == null)
      return false;
    Unit unit = player.unit();
    return unit.dst2(this.x(), this.y()) <= suckRange * suckRange;
  }

  public void updateClosestCore() {
    CoreBuild coreBuild = state.teams.closestCore(Core.camera.position.x, Core.camera.position.y, team);
    state.teams.get(team).cores.remove(coreBuild);
    state.teams.get(team).cores.insert(0, coreBuild);
  }

  public void updateCatchItemFromPlayer() {
    if (accept == false)
      return;
    if (player.unit() != null && player.unit().stack.item != null && player.unit().stack.amount != 0
        && playerUnitInRange()) {
      Item item = player.unit().stack.item;
      int amount = player.unit().stack.amount;
      player.unit().stack.amount = 0;
      player.unit().stack.item = null;
      InputHandler.createItemTransfer(item, amount, player.x, player.y, this, () -> {
        // ===== 修复：玩家交付时也按单种上限，满了就焚烧 =====
        int max = proxy.storageCapacity;
        int have = items.get(item);
        if (have >= max) {
          Fx.coreBurn.at(this);
          return;
        }
        int toStore = Math.min(amount, max - have);
        items.add(item, toStore);
        if (toStore < amount) {
          Fx.coreBurn.at(this);
        }
      });
    }
  }

  public void spawnPlayer(Player player) {
    UnitType spawnType = ((CoreUnitType) type()).unit;
    Fx.spawn.at(this);
    player.set(this);
    if (!net.client()) {
      Unit unit = spawnType.create(team());
      unit.set(this);
      unit.rotation(90f);
      unit.impulse(0f, 3f);
      unit.spawnedByCore(true);
      unit.controller(player);
      unit.add();
    }
  }

  public boolean acceptItem(Building source, Item item) {
    return proxy != null;
  }

  public void handleItem(Building source, Item item) {
    if (proxy == null)
      return;
    // ===== 修复：传送带等建筑输入时，满了就焚烧 =====
    if (items.get(item) >= proxy.storageCapacity) {
      Fx.coreBurn.at(this);
      return;
    }
    items.add(item, 1);
  }

  @Replace
  public boolean interact(Player player) {
    Unit interactor = player.unit();
    currentInteractor = interactor;
    if (interactor.stack().amount > 0) {
      if (acceptItem(null, interactor.stack().item)) {
        handleItem(null, interactor.stack().item);
        interactor.stack().amount--;
        return true;
      }
      return false;
    }
    return true;
  }

  @Override
  public void display(Table table) {
    if (team() != Vars.player.team() || proxy() == null)
      return;
    ItemModule items = items();
    String perSecond = " " + StatUnit.perSecond.localized();
    table.row();
    table.table(Styles.grayPanel, panel -> {
      panel.left().top();
      panel.add(Core.bundle.get("stat.items"))
          .left().padBottom(4f).row();
      panel.table(grid -> {
        grid.left();
        int col = 0;
        for (Item item : Vars.content.items()) {
          int amount = items.get(item);
          boolean hasFlow = items.hasFlowItem(item);
          if (amount <= 0 && !hasFlow)
            continue;
          grid.table(cell -> {
            cell.left();
            cell.image(item.uiIcon).size(24f).padRight(4f);
            cell.label(() -> UI.formatAmount(items.get(item))).left();
            if (hasFlow) {
              cell.row();
              float rate = items.getFlowRate(item);
              cell.label(() -> (rate >= 0 ? "+" : "") + Strings.fixed(rate, 1) + perSecond)
                  .color(rate >= 0 ? Color.forest : Color.scarlet)
                  .fontScale(0.75f).left();
            }
          }).pad(4f).fillY().left();
          col++;
          if (col >= 4) {
            col = 0;
            grid.row();
          }
        }
      }).left().growX().row();
      // 容量进度条
      panel.add(new Bar(
          () -> Core.bundle.format("bar.capacity", UI.formatAmount(proxy().storageCapacity)),
          () -> Pal.items,
          () -> items.total() / (float) proxy().storageCapacity)).growX().height(18f).padTop(6f);
    }).growX().pad(5f).row();
  }

  public void takeItem(Unitc machine, Item item, int amount) {
    int have = items().get(item);
    if (have < amount)
      amount = have;
    if (amount <= 0)
      return;
    items().remove(item, amount);
    ItemStack stack = machine.stack();
    if (stack.amount <= 0) {
      stack.set(item, amount);
    } else if (stack.item == item) {
      stack.amount += amount;
    }
  }

  public void updateAutoCommand() {
    if (vel().len() < 0.1f) {
      idleTimer += Math.min(Time.delta, 10f); // 最多一帧加 10，防止跳帧
      if (idleTimer >= 300f && !autoSwitched) {
        if (controller() instanceof CommandAI ai) {
          if (ai.command != CUnitCommands.coreAuxiliaryCommand) {
            ai.command = CUnitCommands.coreAuxiliaryCommand;
            autoSwitched = true;
          }
        }
      }
    } else {
      idleTimer = 0f;
      if (autoSwitched && controller() instanceof CommandAI ai) {
        ai.command = UnitCommand.moveCommand;
        autoSwitched = false;
      }
    }
  }

  @Replace
  public boolean mining() {
    if (mineTile == null || this.<Unit>self().activelyBuilding())
      return false;
    float targetAngle = angleTo(mineTile.worldx(), mineTile.worldy());
    return Math.abs(Angles.angleDist(rotation(), targetAngle)) <= 4f;
  }

  @Override
  public void draw() {
    if (CVars.debug) {
      float half = hitSize() / 2f + tilesize * 2; // 查询范围半宽
      Draw.color(Color.scarlet); // 红色 = 查询范围
      Draw.alpha(0.3f);
      Fill.rect(x(), y(), half * 2, half * 2);
      Draw.color(Color.scarlet);
      Draw.alpha(1f);
      Lines.rect(x() - half, y() - half, half * 2, half * 2);
      // ====== 调试绘制：单位自身碰撞箱 ======
      float hitHalf = hitSize() / 2f;
      Draw.color(Color.lime); // 绿色 = 自身碰撞箱
      Draw.alpha(0.5f);
      Lines.rect(x() - hitHalf, y() - hitHalf, hitHalf * 2, hitHalf * 2);
      // ====== 调试绘制：高亮检测到的建筑 ======
      Draw.color(Color.gold); // 金色 = 检测到的建筑
      Draw.alpha(0.5f);
      for (Building build : nearbyBuilds()) {
        float bSize = build.block.size * tilesize;
        Fill.rect(build.x, build.y, bSize, bSize);
      }
      Draw.reset();
    }
  }
}
