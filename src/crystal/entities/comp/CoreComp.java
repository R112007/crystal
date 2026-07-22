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
import arc.math.geom.QuadTree;
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
import crystal.entities.mindustryX.MindustryXAdapter;
import crystal.entities.mindustryX.MindustryXUnitc;
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
import mindustry.ai.Pathfinder;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.core.UI;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.StatusEntry;
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

import java.lang.reflect.Field;

@EntityComponent
public abstract class CoreComp implements Unitc, Corec, Posc, MindustryXUnitc {
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
  private transient MindustryXAdapter mindustryXAdapter = new MindustryXAdapter(self());
  public static final float moveThreshold = 2f;
  public static final int maxCacheFrames = 10;
  public ItemModule savedItems = new ItemModule();
  private static final TileChangeEvent tileChange = new TileChangeEvent();
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
  float mineTimer, shield, health, shieldAlpha, hitTime, maxHealth;
  @Import
  boolean dead;
  @Import
  Seq<StatusEntry> statuses;
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

  @Override
  public void heal() {
    dead = false;
    health = maxHealth;
    mindustryXAdapter.fireHealthChanged(self());
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

  @Override
  public Seq<StatusEntry> statuses() {
    return statuses;
  }

  @Override
  public void healthChanged() {
    mindustryXAdapter.fireHealthChanged(self());
  }

  @Override
  public float healthBalance() {
    return mindustryXAdapter.getHealthBalance();
  }

  @Override
  public void clampHealth() {
    health = Math.min(health, maxHealth);
    if (Float.isNaN(health))
      health = 0.0F;

    mindustryXAdapter.fireHealthChanged(self());
  }

  public ItemModule flowItems() {
    return items;
  }

  private void refreshEnemyCoreFields(Team coreTeam) {
    if (Vars.pathfinder == null)
      return;

    try {
      Field cacheField = Pathfinder.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      Object cacheObj = cacheField.get(Vars.pathfinder);
      if (!(cacheObj instanceof Pathfinder.Flowfield[][][] cache))
        return;

      Field dirtyField = Pathfinder.Flowfield.class.getDeclaredField("dirty");
      dirtyField.setAccessible(true);
      Field targetsField = Pathfinder.Flowfield.class.getDeclaredField("targets");
      targetsField.setAccessible(true);

      for (Team enemy : Team.all) {
        if (enemy == coreTeam || enemy == Team.derelict)
          continue;

        Pathfinder.Flowfield[][] enemyCache = cache[enemy.id];
        if (enemyCache == null)
          continue;

        for (int cost = 0; cost < Pathfinder.costTypes.size && cost < enemyCache.length; cost++) {
          Pathfinder.Flowfield field = enemyCache[cost][Pathfinder.fieldCore];
          if (field == null)
            continue;

          Object targets = targetsField.get(field);
          synchronized (targets) {
            field.updateTargetPositions();
          }
          dirtyField.setBoolean(field, true);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void ensureProxyIndexed(Team coreTeam) {
    if (proxy == null || dead())
      return;
    boolean changed = false;

    if (Vars.indexer != null) {
      Seq<Building> flagged = Vars.indexer.getFlagged(coreTeam, BlockFlag.core);
      if (flagged != null && !flagged.contains(proxy, true)) {
        flagged.add(proxy);
        changed = true;
      }
    }

    mindustry.game.Teams.TeamData data = coreTeam.data();
    if (!data.buildings.contains(proxy, true)) {
      data.buildings.add(proxy);
      proxy.indexerBuildIndex = (short) (data.buildings.size - 1);
      changed = true;
    }
    if (data.buildingTree == null) {
      data.buildingTree = new QuadTree<>(
          new arc.math.geom.Rect(0, 0, Vars.world.unitWidth(), Vars.world.unitHeight()));
    }
    data.buildingTree.insert(proxy);

    Seq<Building> targetTypes = data.buildingTypes.get(proxy.block, () -> new Seq<>(false));
    if (!targetTypes.contains(proxy, true)) {
      targetTypes.add(proxy);
      proxy.indexerBuildTypeIndex = (short) (targetTypes.size - 1);
      changed = true;
    }

  }

  @Override
  public void add() {
    if (!MoveCoreSystem.getCores(team()).contains(this)) {
      CoreInjector.injectCore(team().data(), this);
      Events.fire(new CoreChangeEvent(this.proxy()));
      Fx.upgradeCore.at(this);
      // 核心创建/读档后立即刷新敌人寻路目标，否则敌人已创建的 fieldCore 流场可能是空目标
      refreshEnemyCoreFields(team());
      Events.on(SaveWriteEvent.class, (e) -> {
        if (proxy != null && proxy.items != null && !dead()) {
          savedItems.set(proxy.items);
        }
      });
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
      refreshEnemyCoreFields(team());
    }
    if (proxy != null && proxy.items != null && savedItems != null && savedItems.total() > 0) {
      proxy.items.set(savedItems);
      this.items = proxy.items;
    }
  }

  @MethodPriority(-999)
  @Override
  public void update() {
    if (proxy != null) {
      Tile currentTile = Vars.world.tileWorld(x(), y());
      Tile oldTile = proxy.tile;
      boolean moved = oldTile != currentTile;
      if (moved) {
        proxy.tile = currentTile;
        refreshEnemyCoreFields(team());
        if (oldTile != null) {
          tileChange.set(oldTile);
          Events.fire(tileChange);
        }
        if (currentTile != null) {
          tileChange.set(currentTile);
          Events.fire(tileChange);
        }
      }
      // 同步坐标，防止直接访问 Building.x/y 字段的系统拿到旧位置
      proxy.x = x();
      proxy.y = y();
      // 定期补注册，防止 WorldLoadEvent 等清空索引
      if (Crystal.timer % 90f < Time.delta) {
        refreshEnemyCoreFields(team);
        ensureProxyIndexed(team());
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
    if (corec.elevation() > 0 && onSolid() == false) {
      corec.elevation(0.0F);
    }
    if (deployed) {
      Seq<Building> builds = nearbyBuilds();
      for (Building b : builds) {
        if (b.block.hasItems && b.items != null && b.items.total() > 0 && proxy != null) {
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
          if (b instanceof mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild ib) {
            mindustry.world.blocks.distribution.ItemBridge bridgeBlock = (mindustry.world.blocks.distribution.ItemBridge) b.block;
            ib.checkIncoming();
            boolean hasValidOutput = false;
            Tile linkTile = world.tile(ib.link);
            hasValidOutput = linkTile != null && !bridgeBlock.linkValid(ib.tile, world.tile(ib.link))
                && ib.incoming.size > 0;
            if (!hasValidOutput)
              continue;
          }
          if (b instanceof mindustry.world.blocks.distribution.DuctBridge.DuctBridgeBuild dbb) {
            if (dbb.lastLink == null || !dbb.lastLink.isValid() || dbb.lastLink.team() != team) {
              continue;
            }
          }
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
    mindustryXAdapter.update(self());
  }

  @Override
  public void remove() {
    if (items != null && proxy != null && proxy.items != null) {
      ItemModule tmp = new ItemModule();
      tmp.set(proxy.items);
      this.items = tmp;
    }
    CoreInjector.removeCore(team().data(), this);
    // 移动核心死亡后立即刷新敌人寻路目标，避免继续前往旧位置
    refreshEnemyCoreFields(team());
    // 死亡特效不要太猛烈
    Fx.smokePuff.at(this);
  }

  @Replace
  public void rawDamage(float amount) {
    boolean hadShields = shield > 1.0E-4F;
    if (Float.isNaN(health))
      health = 0.0F;
    if (hadShields) {
      shieldAlpha = 1.0F;
    }
    float shieldDamage = Math.min(Math.max(shield, 0), amount);
    shield -= shieldDamage;
    hitTime = 1.0F;
    amount -= shieldDamage;
    if (amount > 0 && type.killable) {
      if (player != null && team == player.team() && control != null) {
        Vars.control.lastDamagedCore = this.proxy;
        Events.fire(Trigger.teamCoreDamage);
      }
      health -= amount;
      if (health <= 0 && !dead) {
        kill();
      }
      if (hadShields && shield <= 1.0E-4F) {
        Fx.unitShieldBreak.at(x(), y(), 0, type.shieldColor(self()), this);
      }
    }
    mindustryXAdapter.fireHealthChanged(self());
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
