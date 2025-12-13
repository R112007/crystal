package crystal.world.blocks.defence;

import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.util.CTmp;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.*;

public class LaserShield extends Block {
  public float radius = 200f;
  public int sides = 24;
  public @Nullable Color shieldColor;
  protected static LaserShieldBuild paramBuild;
  protected static final Cons<Bullet> bulletConsumer = bullet -> {
    if (bullet.team != paramBuild.team && bullet.type.absorbable && bullet.within(paramBuild, paramBuild.radius())) {
      bullet.absorb();
      // paramEffect.at(bullet);
      // paramBuild.hit = 1f;
      // paramBuild.buildup += bullet.damage;
    }
  };

  public LaserShield(String name) {
    super(name);
    hasPower = true;
    update = solid = true;
    rebuildable = false;
    this.radius = 220f;
    this.shieldColor = Pal.surge;
    this.health = 1200;
    this.armor = 6f;
    this.hasPower = true;
    this.consumePower(3f);
    this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(Items.copper, 1));
  }

  @Override
  public void init() {
    super.init();

    updateClipRadius(radius);
  }

  @Override
  public void drawPlace(int x, int y, int rotation, boolean valid) {
    super.drawPlace(x, y, rotation, valid);

    Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, radius, player.team().color);
  }

  public class LaserShieldBuild extends Building {
    public boolean broken = false; // TODO
    public float hit = 0f;
    public float smoothRadius;

    @Override
    public void updateTile() {
      smoothRadius = Mathf.lerpDelta(smoothRadius, radius * efficiency, 0.05f);

      float rad = radius();

      if (rad > 1) {
        paramBuild = this;
        Groups.bullet.intersect(x - rad, y - rad, rad * 2f, rad * 2f, bulletConsumer);
      }
    }

    public float radius() {
      return smoothRadius;
    }

    @Override
    public void drawSelect() {
      super.drawSelect();

      Drawf.dashCircle(x, y, radius, team.color);
    }

    @Override
    public void draw() {
      super.draw();

      drawShield();
    }

    // always visible due to their shield nature
    @Override
    public boolean inFogTo(Team viewer) {
      return false;
    }

    public void drawShield() {
      if (!broken) {
        float radius = radius();

        Draw.z(Layer.shields);

        Draw.color(shieldColor == null ? team.color : shieldColor, Color.white, Mathf.clamp(hit));

        if (renderer.animateShields) {
          Fill.poly(x, y, sides, radius);
        } else {
          Lines.stroke(1.5f);
          Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
          Fill.poly(x, y, sides, radius);
          Draw.alpha(1f);
          Lines.poly(x, y, sides, radius);
          Draw.reset();
        }
      }

      Draw.reset();
    }

    @Override
    public byte version() {
      return 1;
    }

    @Override
    public void write(Writes write) {
      super.write(write);

      write.f(smoothRadius);
      write.bool(broken);
    }

    @Override
    public void read(Reads read, byte revision) {
      super.read(read);

      if (revision >= 1) {
        smoothRadius = read.f();
        broken = read.bool();
      }
    }
  }
}
