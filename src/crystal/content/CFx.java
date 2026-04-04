package crystal.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Position;
import arc.util.Tmp;
import crystal.graphics.CPal;
import crystal.util.CTmp;
import mindustry.entities.Effect;
import mindustry.entities.effect.ExplosionEffect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.Block;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static arc.math.Angles.*;
import static mindustry.Vars.*;

public class CFx {
  public static final Rand rand = new Rand();
  public static Effect spawn1 = new Effect(120f, e -> {
    color(CPal.blue1);
    Drawf.flame(e.x, e.y, 5, 360, 50, 50, 5);
    // Lines.arc(e.x, e.y, 50f, 10f);
    // Fill.circle(e.x, e.y, 80f);
    color(CPal.dark_blue2);
    // Fill.square(e.x, e.y, 60f);
    // Lines.line(e.x + block.offset, e.y + block.offset, 100, 100);
    color(CPal.dark_sharedyellow);
    // Fill.arc(e.x, e.y, 50f, 10f, 90f, 6);
  });
  public static Effect straightLine = new Effect(5f, 300f, e -> {
    if (!(e.data instanceof Position p))
      return;

    // 基础参数计算：起点、目标坐标、直线距离
    float startX = e.x, startY = e.y;
    float targetX = p.getX(), targetY = p.getY();
    float totalDst = Mathf.dst(startX, startY, targetX, targetY);

    // 归一化方向向量（仅保留方向，长度为1）
    Tmp.v1.set(targetX - startX, targetY - startY).nor();
    float dirX = Tmp.v1.x, dirY = Tmp.v1.y;

    Lines.stroke(1.5f); // 线宽从2.5f→0（生命周期内逐渐变细）
    Draw.color(Color.white, e.color, e.fin()); // 颜色从白色→自定义颜色（渐变）

    // 核心：绘制动态延伸的直线（根据生命周期进度控制延伸长度）
    float currentDst = totalDst * Mathf.clamp((e.fin() + 0.1f), 0f, 1f); // 当前延伸长度（0→totalDst，随时间推进）
    float endX = startX + dirX * currentDst; // 当前线条终点X
    float endY = startY + dirY * currentDst; // 当前线条终点Y

    // 绘制直线（直接用Lines.line画纯直线，无任何扭曲）
    Lines.line(startX, startY, endX, endY);

    // 可选：在当前线条终点添加一个小光点，增强“推进感”
    if (e.fin() > 0.1f) { // 避免一开始就显示光点
      Draw.color(e.color, Color.white, e.fin());
      Fill.circle(endX, endY, 1.2f * e.fout()); // 光点大小随线宽同步变细
      Draw.reset(); // 重置颜色，避免影响后续绘制
    }
  })
      .followParent(false) // 不跟随父实体移动
      .rotWithParent(false), // 不跟随父实体旋转
      redExplosion = new Effect(30, 500f, b -> {
        float intensity = 6.8f;
        float baseLifetime = 25f + intensity * 11f;
        b.lifetime = 50f + intensity * 65f;

        color(Pal.lightFlame);
        alpha(0.7f);
        for (int i = 0; i < 4; i++) {
          rand.setSeed(b.id * 2 + i);
          float lenScl = rand.random(0.4f, 1f);
          int fi = i;
          b.scaled(b.lifetime * lenScl, e -> {
            randLenVectors(e.id + fi - 1, e.fin(Interp.pow10Out), (int) (2.9f * intensity), 22f * intensity,
                (x, y, in, out) -> {
                  float fout = e.fout(Interp.pow5Out) * rand.random(0.5f, 1f);
                  float rad = fout * ((2f + intensity) * 2.35f);

                  Fill.circle(e.x + x, e.y + y, rad);
                  Drawf.light(e.x + x, e.y + y, rad * 2.5f, Pal.reactorPurple, 0.5f);
                });
          });
        }

        b.scaled(baseLifetime, e -> {
          Draw.color();
          e.scaled(5 + intensity * 2f, i -> {
            stroke((3.1f + intensity / 5f) * i.fout());
            Lines.circle(e.x, e.y, (3f + i.fin() * 14f) * intensity);
            Drawf.light(e.x, e.y, i.fin() * 14f * 2f * intensity, Color.white, 0.9f * e.fout());
          });

          color(Pal.lighterOrange, Pal.lightFlame, e.fin());
          stroke((2f * e.fout()));

          Draw.z(Layer.effect + 0.001f);
          randLenVectors(e.id + 1, e.finpow() + 0.001f, (int) (8 * intensity), 28f * intensity, (x, y, in, out) -> {
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + out * 4 * (4f + intensity));
            Drawf.light(e.x + x, e.y + y, (out * 4 * (3f + intensity)) * 3.5f, Draw.getColor(), 0.8f);
          });
        });
      });
  public static Effect airmisslesmall = new ExplosionEffect() {
    {
      this.waveColor = CPal.light_blue1;
      this.sparkColor = CPal.blue1;
    }
  }.layer(Layer.bullet);
}
