package crystal.world.blocks.effect;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.WorldLabel;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class ReplaceFloor extends Block {
  public Seq<FloorData> floors = new Seq<>();
  public float range = 5;
  public Color baseColor = Pal.accent;
  Seq<Tile> everytiles;
  public boolean restoreAfterDestroyed = false;

  public ReplaceFloor(String name) {
    super(name);
    solid = true;
    update = true;
    configurable = true;
    config(FloorData.class, (ReplaceFloorBuild build, FloorData floor) -> {
      if (build.config != floor) {
        build.config = floor;
      }
    });
  }

  public void addData(Block block, ItemStack[] items) {
    floors.add(new FloorData((Floor) block, items));
  }

  @Override
  public void setStats() {
    super.setStats();
    stats.add(Stat.range, realRange(), StatUnit.blocks);
  }

  public float realRange() {
    return range * Vars.tilesize;
  }

  @Override
  public boolean canPlaceOn(Tile tile, Team team, int rotation) {
    everytiles = tile.getLinkedTilesAs(this, tempTiles);
    return super.canPlaceOn(tile, team, rotation);
  }

  @Override
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    super.drawPlace(x, y, rotation, valid);
    x *= tilesize;
    y *= tilesize;
    x += offset;
    y += offset;
    Drawf.dashSquare(baseColor, x, y, range * tilesize);
  }

  public static class FloorData {
    public Floor floor;
    public ItemStack[] items;

    public FloorData(Floor floor, ItemStack[] items) {
      this.floor = floor;
      this.items = items;
    }
  }

  public static class ChangeFloorEvent {
    public FloorData floorData;

    public ChangeFloorEvent(FloorData floorData) {
      this.floorData = floorData;
    }
  }

  public class ReplaceFloorBuild extends Building {
    public @Nullable FloorData config;
    Seq<Tile> undertiles = new Seq<>();
    Seq<TileData> origin = new Seq<>();

    @Override
    public @Nullable FloorData config() {
      return config;
    }

    @Override
    public void placed() {
      undertiles = getDashSquareCoveredTiles(x + block.offset, y + block.offset, realRange());
      for (var t : undertiles) {
        origin.add(new TileData(t.pos(), t.floor().name));
      }
      Events.on(ChangeFloorEvent.class, e -> {
        checkUnderTiles();
        checkCoreItems(e.floorData);
      });
      super.placed();

    }

    public void checkUnderTiles() {
      if (undertiles.isEmpty())
        undertiles = getDashSquareCoveredTiles(x + block.offset, y + block.offset, realRange());
    }

    public int needReplaceCount(FloorData data) {
      checkUnderTiles();
      int count = 0;
      for (var t : undertiles) {
        if (t.floor() != data.floor)
          count++;
      }
      return count;
    }

    public void checkCoreItems(FloorData data) {
      int need = needReplaceCount(data);
      if (need == 0)
        return;

      CoreBuild core = core();
      if (core == null) {
        Vars.ui.showLabel("无核心", id, 5f, x, y,
            WorldLabel.flagBackground | WorldLabel.flagAutoscale);
        return;
      }

      boolean allow = true;
      for (ItemStack stack : data.items) {
        if (core.items.get(stack.item) < need * stack.amount) {
          allow = false;
          break;
        }
      }

      if (allow) {
        // 扣除资源并执行替换
        for (ItemStack stack : data.items) {
          core.items.remove(stack.item, need * stack.amount);
        }
        replaceFloors(data);
      } else {
        Vars.ui.showLabel("资源不足", id, 5f, x, y,
            WorldLabel.flagBackground | WorldLabel.flagAutoscale);
      }
    }

    public void replaceFloors(FloorData data) {
      checkUnderTiles();
      for (var f : undertiles) {
        f.setFloor(data.floor);
      }
    }

    public Seq<Tile> getDashSquareCoveredTiles(float x, float y, float size) {
      Seq<Tile> result = new Seq<>();
      if (size <= 0 || Vars.world == null)
        return result;

      // 1. 计算 dashSquare 的世界坐标矩形（与 Drawf.dashSquare 逻辑一致）
      Rect dashRect = new Rect(x - size / 2f, y - size / 2f, size, size);

      // 2. 转换为 Tile 索引范围（避免超出世界边界）
      int tileXStart = Mathf.floor(dashRect.x / Vars.tilesize);
      int tileXEnd = Mathf.ceil((dashRect.x + dashRect.width) / Vars.tilesize);
      int tileYStart = Mathf.floor(dashRect.y / Vars.tilesize);
      int tileYEnd = Mathf.ceil((dashRect.y + dashRect.height) / Vars.tilesize);

      // 边界裁剪（防止索引越界）
      tileXStart = Math.max(0, tileXStart);
      tileXEnd = Math.min(Vars.world.width() - 1, tileXEnd);
      tileYStart = Math.max(0, tileYStart);
      tileYEnd = Math.min(Vars.world.height() - 1, tileYEnd);

      // 3. 遍历范围内 Tile，筛选与 dashRect 重叠的 Tile
      for (int tx = tileXStart; tx <= tileXEnd; tx++) {
        for (int ty = tileYStart; ty <= tileYEnd; ty++) {
          Tile tile = Vars.world.tile(tx, ty);
          if (tile == null)
            continue;

          // Tile 的世界坐标矩形（左下角为 (tx*tilesize, ty*tilesize)）
          Rect tileRect = new Rect(
              tx * Vars.tilesize,
              ty * Vars.tilesize,
              Vars.tilesize,
              Vars.tilesize);

          // 两个矩形重叠则加入结果（包含部分重叠）
          if (dashRect.overlaps(tileRect)) {
            result.add(tile);
          }
        }
      }

      return result;
    }

    @Override
    public void drawSelect() {
      super.drawSelect();

      Drawf.dashSquare(baseColor, x, y, range * tilesize);
    }

    @Override
    public void remove() {
      if (restoreAfterDestroyed)
        restoreFloors();
      super.remove();
    }

    @Override
    public void buildConfiguration(Table table) {
      checkUnderTiles();

      // --- Restore 占一行 ---
      table.button(b -> {
        b.left();
        b.image(Icon.upOpen).size(32f).padRight(6f);
        b.add("恢复").left();
      }, Styles.flatt, () -> {
        restoreFloors();
      }).growX().left().height(45f).pad(2f);
      table.row();

      // --- 每个 FloorData 占一行 ---
      for (var f : floors) {
        table.button(b -> {
          b.left();
          b.image(f.floor.uiIcon).size(32f).padRight(8f);

          for (var stack : f.items) {
            b.image(stack.item.uiIcon).size(24f).padRight(2f);

            // 实时计算：总需求 = 需替换地板数 × 单块消耗
            b.label(() -> {
              int need = needReplaceCount(f);
              int required = need * stack.amount;
              int coreAmt = core() != null ? core().items.get(stack.item) : 0;
              int display = Math.min(coreAmt, required); // 核心数量最大显示为总需求
              return required + "/" + display;
            }).padRight(8f);
          }
        }, Styles.flatt, () -> {
          checkCoreItems(f); // 直接调用，不再发事件
        }).growX().left().height(45f).pad(2f);
        table.row();
      }
    }

    public void restoreFloors() {
      for (var t : origin) {
        t.setFloor();
      }
    }

    @Override
    public void write(Writes write) {
      super.write(write);
      write.i(origin.size);
      for (var t : origin) {
        write.i(t.pos);
        write.str(t.floor);
      }
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read, revision);
      int size = read.i();
      for (int i = 0; i < size; i++) {
        int pos = read.i();
        String str = read.str();
        origin.add(new TileData(pos, str));
      }
    }
  }

  public static class TileData {
    public int pos;
    public String floor;

    public TileData(int pos, String floor) {
      this.pos = pos;
      this.floor = floor;
    }

    public void setFloor() {
      try {
        world.tile(pos).setFloor((Floor) content.getByName(ContentType.block, floor));
      } catch (Exception e) {
        Vars.ui.showException(e);
      }
    }
  }
}
