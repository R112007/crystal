package crystal.world.blocks.effect;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Rect;
import arc.struct.Seq;
import crystal.content.CFx;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class CoverFloorMachine extends Block {
  Seq<Tile> everytiles = new Seq<>();
  public int range = 12;
  public float explosionShake = 1f;
  public float explosionShakeDuration = 6f;
  public Color baseColor = Pal.accent;
  public Effect smokeEffect = CFx.redExplosion;
  public Block surface = Blocks.hotrock, deep = Blocks.magmarock;

  public CoverFloorMachine(String name) {
    super(name);
    solid = true;
    update = true;
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
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    super.drawPlace(x, y, rotation, valid);
    x *= tilesize;
    y *= tilesize;
    x += offset;
    y += offset;
    Drawf.dashSquare(baseColor, x, y, range * tilesize);
  }

  @Override
  public boolean canPlaceOn(Tile tile, Team team, int rotation) {
    everytiles = tile.getLinkedTilesAs(this, tempTiles);
    return super.canPlaceOn(tile, team, rotation);
  }

  public class CoverFloorMachineBuild extends Building {
    Seq<Tile> undertiles = new Seq<>();
    float timer;
    public Rand rand = new Rand();

    @Override
    public void placed() {
      undertiles = getDashSquareCoveredTiles(x + block.offset, y + block.offset, realRange());
      super.placed();
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
    public void updateTile() {
      if ((timer += delta()) >= 120f) {
        if (undertiles.isEmpty())
          undertiles = getDashSquareCoveredTiles(x + block.offset, y + block.offset, realRange());
        smokeEffect.at(this);
        if (explosionShake > 0) {
          Effect.shake(explosionShake, explosionShakeDuration, this);
        }
        for (int i = 0; i < 10; i++) {
          cover();
        }
        kill();
      }
    }

    public void cover() {
      int index = rand.random(0, undertiles.size - 1);
      Tile tile = undertiles.get(index);
      if (tile.floor() == deep || tile.floor().liquidDrop != null)
        return;
      if (tile.floor() == surface) {
        tile.setFloor((Floor) deep);
        return;
      }
      if (rand.chance(0.7)) {
        tile.setFloor((Floor) surface);
      } else {
        tile.setFloor((Floor) deep);
      }
    }
  }
}
