package crystal.world.blocks.stroage;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.gen.Corec;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class CoreUnderUnloader extends Block {
  public float speed = 1f;
  public int selectionRows = 5, selectionColumns = 4;

  public CoreUnderUnloader(String name) {
    super(name);
    update = true;
    solid = true;
    hasItems = false; // 自身不存物品，核心不会吸它
    configurable = true;
    saveConfig = true;
    noUpdateDisabled = true;
    clearOnDoubleTap = true;

    config(Item.class, (CoreUnderUnloaderBuild tile, Item item) -> tile.sortItem = item);
    configClear((CoreUnderUnloaderBuild tile) -> tile.sortItem = null);
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(Stat.speed, 60f / speed, StatUnit.perSecond);
  }

  public class CoreUnderUnloaderBuild extends Building {
    public Item sortItem = null;
    public float unloadTimer = 0f;
    public int rotations = 0;

    /**
     * 获取覆盖此方块的可移动核心。
     * 范围和核心 nearbyBuilds() 一致：hitSize/2 + tilesize*2
     */
    public Corec getCoreUnit() {
      Corec[] result = { null };
      // 核心 nearbyBuilds 的范围是 hitSize/2 + tilesize*2
      // 这里用更大的搜索半径确保找到，然后精确检查
      Units.nearby(x - tilesize * 6, y - tilesize * 6, tilesize * 12, tilesize * 12, u -> {

        if (result[0] == null && u instanceof Corec core) {
          float range = core.hitSize() / 2f + tilesize * 2f;
          if (core.within(x, y, range)) {
            result[0] = core;
          }
        }
      });
      return result[0];
    }

    @Override
    public void updateTile() {
      Corec core = getCoreUnit();
      if (core == null)
        return;

      mindustry.world.modules.ItemModule coreItems = core.items();
      if (coreItems == null || coreItems.total() == 0)
        return;

      unloadTimer += edelta();
      if (unloadTimer < speed)
        return;

      int processCount = (int) (unloadTimer / speed);
      if (processCount <= 0)
        return;

      boolean any = false;

      for (int j = 0; j < processCount; j++) {
        Item item = sortItem;

        // 未指定时轮询核心内存在的物品
        if (item == null) {
          Item[] all = content.items().toArray(Item.class);
          for (int i = 0; i < all.length; i++) {
            int id = (rotations + i + 1) % all.length;
            Item possible = all[id];
            if (coreItems.get(possible) > 0) {
              item = possible;
              break;
            }
          }
          if (item != null)
            rotations = item.id;
        }

        if (item == null || coreItems.get(item) <= 0)
          continue;

        // 寻找可接收的邻近建筑
        Building target = null;
        for (Building other : proximity) {
          if (other.interactable(team) && other.acceptItem(this, item)) {
            int maxAccepted = other.getMaximumAccepted(item);
            if (other.items != null && other.items.get(item) < maxAccepted) {
              target = other;
              break;
            }
          }
        }

        if (target != null) {
          // 从核心直接扣除，注入目标
          coreItems.remove(item, 1);
          target.handleItem(this, item);
          any = true;
        } else {
          break; // 无目标，中断批量
        }
      }

      if (any) {
        unloadTimer -= processCount * speed;
      } else {
        unloadTimer = Math.min(unloadTimer, speed);
      }
    }

    @Override
    public void draw() {
      super.draw();
      if (sortItem != null) {
        Draw.color(sortItem.color);
        Draw.rect(region, x, y);
        Draw.color();
      }
    }

    @Override
    public void drawSelect() {
      super.drawSelect();
      Corec core = getCoreUnit();
      if (core != null) {
        // 绘制核心检测范围（和 nearbyBuilds 一致）
        float range = core.hitSize() / 2f + tilesize * 2f;
        Drawf.square(core.x(), core.y(), core.hitSize() / 2f, Color.acid);
        Drawf.dashCircle(core.x(), core.y(), range, Color.orange);
      }
    }

    @Override
    public void buildConfiguration(Table table) {
      ItemSelection.buildTable(CoreUnderUnloader.this, table, content.items(),
          () -> sortItem, this::configure, selectionRows, selectionColumns);
    }

    @Override
    public Item config() {
      return sortItem;
    }

    @Override
    public byte version() {
      return 1;
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.s(sortItem == null ? -1 : sortItem.id);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      int id = revision == 1 ? read.s() : read.b();
      sortItem = id == -1 ? null : content.item(id);
    }
  }
}
