package crystal.content;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Position;
import arc.util.Tmp;
import arc.util.noise.Simplex;
import crystal.entities.effect.MultiEffect;
import crystal.graphics.CPal;
import crystal.util.CTmp;
import mindustry.entities.Effect;
import mindustry.entities.effect.ExplosionEffect;
import mindustry.entities.effect.ParticleEffect;
import mindustry.entities.effect.WaveEffect;
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
  public static Effect airmisslemiddle = new MultiEffect(airmisslesmall, new ParticleEffect() {
    {
      this.particles = 5;
      this.lifetime = 20;
      this.length = 20;
      this.baseLength = -20;
      this.sizeFrom = 5;
      this.sizeTo = 0;
      this.colorFrom = CPal.light_blue1;
      this.colorTo = Color.white;
    }
  });
  public static Effect airpiercedown = new MultiEffect(airmisslemiddle, new WaveEffect() {
    {
      this.sizeFrom = 0;
      this.sizeTo = 20;
      this.lifetime = 30;
      this.sides = 1;
      this.colorFrom = CPal.light_blue1;
      this.colorTo = CPal.blue1;
      this.strokeFrom = 2.5f;
      this.strokeTo = 0;

    }
  });
  public static final Effect energySphere = new Effect(140f, 360f, e -> {
    float radius = 64f;
    float fin = e.fin();
    float fout = e.fout();
    float alpha = fout * e.color.a;
    Color c = e.color;

    // ===== 0. 加法混合：所有发光层叠加 =====
    Draw.blend(Blending.additive);

    // ===== 1. 外层大气辉光（Fill.light 径向渐变） =====
    Draw.color(c, alpha * 0.35f);
    Fill.light(e.x, e.y, 28, radius * 3.0f * fout, c, Color.clear);

    // ===== 2. 中心径向光晕 + 极亮核 =====
    Draw.color(c, alpha);
    Fill.light(e.x, e.y, 32, radius * 0.65f * fout, Color.white, c);

    float corePulse = 1f + Mathf.absin(fin * 10f, 10f, 0.3f);
    Draw.color(Color.white, alpha);
    Fill.circle(e.x, e.y, radius * 0.18f * corePulse * (1f + fin * 0.25f));
    Draw.color(Color.white, alpha * 0.95f);
    Fill.circle(e.x, e.y, radius * 0.09f * corePulse);

    // ===== 3. 多层旋转光环 =====
    // 内环：白色，较粗，快速旋转
    Lines.stroke(3.0f * fout);
    Draw.color(Color.white, alpha * 0.95f);
    Lines.circle(e.x, e.y, radius * (0.30f + 0.22f * fin));

    // 中环：主题色
    Lines.stroke(2.0f * fout);
    Draw.color(c, alpha * 0.85f);
    Lines.circle(e.x, e.y, radius * (0.48f + 0.30f * fin));

    // 外环：淡蓝，较细
    Lines.stroke(1.4f * fout);
    Draw.color(Color.sky, alpha * 0.65f);
    Lines.circle(e.x, e.y, radius * (0.68f + 0.24f * fin));

    // ===== 4. 环绕能量带（厚发光环） =====
    Draw.color(c, alpha * 0.45f);
    Fill.lightInner(e.x, e.y, 40,
        radius * (0.40f + 0.15f * fin),
        radius * (0.55f + 0.20f * fin),
        fin * 90f + e.rotation,
        Color.white, Color.clear);

    // ===== 5. 3D 球面粒子层 =====
    // 用球坐标投影到 2D，并按 z 深度缩放大小与透明度，营造立体球感
    int sphereParticles = 90;
    for (int i = 0; i < sphereParticles; i++) {
      long s = e.id * 0x9E3779B97F4A7C15L + i * 2654435761L;

      // 球坐标
      float theta = Mathf.randomSeed(s, 0f, Mathf.PI2);
      float z = Mathf.randomSeed(s + 1, -1f, 1f);
      float rXY = Mathf.sqrt(1f - z * z);

      float px = rXY * Mathf.cos(theta);
      float py = rXY * Mathf.sin(theta);
      float pz = z;

      // 缓慢旋转
      float rot = fin * 45f + e.rotation;
      float rx = px * Mathf.cosDeg(rot) - pz * Mathf.sinDeg(rot);
      float rz = px * Mathf.sinDeg(rot) + pz * Mathf.cosDeg(rot);

      float size = Mathf.lerp(0.5f, 2.0f, (rz + 1f) / 2f) * fout;
      float pAlpha = alpha * Mathf.lerp(0.3f, 1f, (rz + 1f) / 2f);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - (rz + 1f) / 2f).a(pAlpha));
      Fill.circle(
          e.x + rx * radius * 0.75f * fout,
          e.y + py * radius * 0.75f * fout,
          size);
    }

    // ===== 6. 圆盘随机粒子（补充密度） =====
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.4f).a(alpha));
    Angles.randLenVectors(e.id, 45, radius * 0.95f * fout, (x, y) -> {
      float dist = Mathf.dst(x, y);
      float depth = 1f - Mathf.clamp(dist / (radius * 0.95f));
      float pSize = Mathf.lerp(0.5f, 1.8f, depth) * fout;
      float pAlpha = alpha * Mathf.lerp(0.35f, 0.9f, depth);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - depth).a(pAlpha));
      Fill.circle(e.x + x, e.y + y, pSize);
    });

    // ===== 7. 放射状能量触须（带 Simplex 噪声抖动） =====
    int tendrils = 14;
    for (int i = 0; i < tendrils; i++) {
      long seed = e.id * 41L + i * 17L;
      float baseAngle = i * 360f / tendrils + e.rotation + fin * 75f;
      float len = radius * (1.0f + 1.1f * fin);

      // 颜色：根部偏白，梢部偏主题色
      Draw.color(Tmp.c1.set(Color.white).lerp(c, fin).a(alpha * (1f - fin * 0.25f)));
      Lines.stroke(Mathf.lerp(1.4f, 0.5f, fin) * fout);

      Lines.beginLine();
      Lines.linePoint(e.x, e.y);

      int segs = 18;
      for (int j = 1; j <= segs; j++) {
        float t = j / (float) segs;
        float nx = seed * 0.017f + j * 0.31f;
        float ny = fin * 2.8f + j * 0.19f;
        float jitter = Simplex.noise2d((int) seed, 2, 0.5f, 1.5f, nx, ny) * 32f * t * t;
        float angle = baseAngle + jitter;
        float r = len * t;

        Lines.linePoint(
            e.x + Angles.trnsx(angle, r),
            e.y + Angles.trnsy(angle, r));
      }
      Lines.endLine(false);
    }

    // ===== 8. 环绕螺旋电弧（贴合球面） =====
    int streams = 6;
    for (int i = 0; i < streams; i++) {
      long seed = e.id * 73L + i * 29L;
      float baseAngle = i * 360f / streams + e.rotation * 0.5f - fin * 120f;
      float orbitRadius = radius * (0.55f + 0.25f * fin);

      Draw.color(c, alpha * 0.7f);
      Lines.stroke(1.2f * fout);
      Lines.beginLine();

      int segs = 24;
      for (int j = 0; j <= segs; j++) {
        float t = j / (float) segs;
        float angle = baseAngle + t * 180f;
        float nx = seed * 0.013f + j * 0.21f;
        float ny = fin * 3f + t * 1.5f;
        float wobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.2f, nx, ny) * 18f;
        float r = orbitRadius + wobble * (1f - Math.abs(t - 0.5f) * 2f);

        Lines.linePoint(
            e.x + Angles.trnsx(angle + wobble, r),
            e.y + Angles.trnsy(angle + wobble, r));
      }
      Lines.endLine(false);
    }

    // ===== 9. 中心放射短射线 =====
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.6f).a(alpha * 0.8f));
    Angles.randLenVectors(e.id + 999, 20, radius * 0.6f * fout, e.rotation, 360f, (x, y) -> {
      Lines.line(e.x, e.y, e.x + x, e.y + y);
    });

    // ===== 10. 环境点光源 =====
    Drawf.light(e.x, e.y, radius * 3.6f, c, alpha * 0.8f);

    // ===== 11. 恢复 =====
    Draw.blend();
    Draw.reset();
  }).layer(Layer.effect + 0.1f);
  public static final Effect energyRound = new Effect(140f, 360f, e -> {
    float radius = 64f;
    float fin = e.fin();
    float fout = e.fout();
    float alpha = fout * e.color.a;
    Color c = e.color;

    // ===== 0. 加法混合：所有发光层叠加 =====
    Draw.blend(Blending.additive);

    // ===== 1. 外层大气辉光（Fill.light 径向渐变） =====
    Draw.color(c, alpha * 0.35f);
    Fill.light(e.x, e.y, 28, radius * 3.0f * fout, c, Color.clear);

    // ===== 2. 中心径向光晕 + 极亮核 =====
    Draw.color(c, alpha);
    Fill.light(e.x, e.y, 32, radius * 0.65f * fout, Color.white, c);

    float corePulse = 1f + Mathf.absin(fin * 10f, 10f, 0.3f);
    Draw.color(Color.white, alpha);
    Fill.circle(e.x, e.y, radius * 0.18f * corePulse * (1f + fin * 0.25f));
    Draw.color(Color.white, alpha * 0.95f);
    Fill.circle(e.x, e.y, radius * 0.09f * corePulse);

    // ===== 3. 多层旋转光环 =====
    // 内环：白色，存在时间较短，随时间快速变细消失
    e.scaled(90f, sub -> {
      float fade = sub.fout();
      Lines.stroke(3.0f * fade * fade);
      Draw.color(Color.white, fade * sub.color.a * 0.95f);
      Lines.circle(e.x, e.y, radius * (0.30f + 0.22f * sub.fin()));
    });

    // 中环：主题色，存在时间比整体短 5 帧
    if (e.time <= e.lifetime - 5f) {
      Lines.stroke(2.0f * fout);
      Draw.color(c, alpha * 0.85f);
      Lines.circle(e.x, e.y, radius * (0.48f + 0.30f * fin));
    }

    // 外环：淡蓝，较细
    Lines.stroke(1.4f * fout);
    Draw.color(Color.sky, alpha * 0.65f);
    Lines.circle(e.x, e.y, radius * (0.68f + 0.24f * fin));

    // ===== 4. 环绕能量带（厚发光环） =====
    Draw.color(c, alpha * 0.45f);
    Fill.lightInner(e.x, e.y, 40,
        radius * (0.40f + 0.15f * fin),
        radius * (0.55f + 0.20f * fin),
        fin * 90f + e.rotation,
        Color.white, Color.clear);

    // ===== 5. 3D 球面粒子层 =====
    // 用球坐标投影到 2D，并按 z 深度缩放大小与透明度，营造立体球感
    int sphereParticles = 90;
    for (int i = 0; i < sphereParticles; i++) {
      long s = e.id * 0x9E3779B97F4A7C15L + i * 2654435761L;

      // 球坐标
      float theta = Mathf.randomSeed(s, 0f, Mathf.PI2);
      float z = Mathf.randomSeed(s + 1, -1f, 1f);
      float rXY = Mathf.sqrt(1f - z * z);

      float px = rXY * Mathf.cos(theta);
      float py = rXY * Mathf.sin(theta);
      float pz = z;

      // 缓慢旋转
      float rot = fin * 45f + e.rotation;
      float rx = px * Mathf.cosDeg(rot) - pz * Mathf.sinDeg(rot);
      float rz = px * Mathf.sinDeg(rot) + pz * Mathf.cosDeg(rot);

      float size = Mathf.lerp(0.5f, 2.0f, (rz + 1f) / 2f) * fout;
      float pAlpha = alpha * Mathf.lerp(0.3f, 1f, (rz + 1f) / 2f);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - (rz + 1f) / 2f).a(pAlpha));
      Fill.circle(
          e.x + rx * radius * 0.75f * fout,
          e.y + py * radius * 0.75f * fout,
          size);
    }

    // ===== 6. 圆盘随机粒子（补充密度） =====
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.4f).a(alpha));
    Angles.randLenVectors(e.id, 45, radius * 0.95f * fout, (x, y) -> {
      float dist = Mathf.dst(x, y);
      float depth = 1f - Mathf.clamp(dist / (radius * 0.95f));
      float pSize = Mathf.lerp(0.5f, 1.8f, depth) * fout;
      float pAlpha = alpha * Mathf.lerp(0.35f, 0.9f, depth);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - depth).a(pAlpha));
      Fill.circle(e.x + x, e.y + y, pSize);
    });

    // ===== 7. 环绕螺旋电弧（贴合球面） =====
    int streams = 6;
    for (int i = 0; i < streams; i++) {
      long seed = e.id * 73L + i * 29L;
      float baseAngle = i * 360f / streams + e.rotation * 0.5f - fin * 120f;
      float orbitRadius = radius * (0.55f + 0.25f * fin);

      Draw.color(c, alpha * 0.7f);
      Lines.stroke(1.2f * fout);
      Lines.beginLine();

      int segs = 24;
      for (int j = 0; j <= segs; j++) {
        float t = j / (float) segs;
        float angle = baseAngle + t * 180f;
        float nx = seed * 0.013f + j * 0.21f;
        float ny = fin * 3f + t * 1.5f;
        float wobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.2f, nx, ny) * 18f;
        float r = orbitRadius + wobble * (1f - Math.abs(t - 0.5f) * 2f);

        Lines.linePoint(
            e.x + Angles.trnsx(angle + wobble, r),
            e.y + Angles.trnsy(angle + wobble, r));
      }
      Lines.endLine(false);
    }

    // ===== 8. 中心放射短射线 =====
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.6f).a(alpha * 0.8f));
    Angles.randLenVectors(e.id + 999, 20, radius * 0.6f * fout, e.rotation, 360f, (x, y) -> {
      Lines.line(e.x, e.y, e.x + x, e.y + y);
    });

    // ===== 9. 环境点光源 =====
    Drawf.light(e.x, e.y, radius * 3.6f, c, alpha * 0.8f);

    // ===== 10. 恢复 =====
    Draw.blend();
    Draw.reset();
  }).layer(Layer.effect + 0.1f);
  public static final Effect energySphere2 = new Effect(140f, 360f, e -> {
    float radius = 70f;
    float fin = e.fin();
    float fout = e.fout();
    float alpha = fout * e.color.a;
    Color c = e.color;

    // ===== 0. 加法混合 =====
    Draw.blend(Blending.additive);

    // ===== 1. 外层大气辉光 =====
    Draw.color(c, alpha * 0.40f);
    Fill.light(e.x, e.y, 28, radius * 3.2f * fout, c, Color.clear);

    // ===== 2. 中心径向光晕 + 极亮核 =====
    Draw.color(c, alpha);
    Fill.light(e.x, e.y, 32, radius * 0.75f * fout, Color.white, c);

    float corePulse = 1f + Mathf.absin(fin * 12f, 12f, 0.2f);
    Draw.color(Color.white, alpha);
    Fill.circle(e.x, e.y, radius * 0.18f * corePulse * (1f + fin * 0.15f));
    Draw.color(Color.white, alpha * 0.95f);
    Fill.circle(e.x, e.y, radius * 0.08f * corePulse);

    // ===== 2.5 球壳辉光（让球体边界更连续、更像参考图） =====
    Draw.color(c, alpha * 0.55f);
    Fill.light(e.x, e.y, 32, radius * 0.92f * fout, Color.white, c);

    // ===== 3. 内部辐射状能量丝（从中心连到球面） =====
    // 参考图中从核心向外辐射、略有弯曲的细密能量线
    int filaments = 80;
    for (int i = 0; i < filaments; i++) {
      long seed = e.id * 53L + i * 19L;
      float baseAngle = Mathf.randomSeed(seed, 0f, 360f) + fin * 25f;
      float len = radius * Mathf.randomSeed(seed + 1, 0.70f, 1.0f) * 0.92f * fout;

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.5f).a(alpha * 0.9f));
      Lines.stroke(Mathf.lerp(1.3f, 0.35f, fin) * fout);
      Lines.beginLine();
      Lines.linePoint(e.x, e.y);

      int segs = 14;
      for (int j = 1; j <= segs; j++) {
        float t = j / (float) segs;
        float nx = seed * 0.013f + j * 0.31f;
        float ny = fin * 4f + j * 0.18f;
        // 用双重噪声制造更自然的弯曲
        float curve1 = Simplex.noise2d((int) seed, 2, 0.5f, 1.6f, nx, ny) * 40f * t * (1f - t);
        float curve2 = Simplex.noise2d((int) seed + 1, 2, 0.5f, 2.4f, nx * 1.5f, ny * 1.5f) * 18f * t;
        float angle = baseAngle + curve1 + curve2;
        float r = len * t;

        Lines.linePoint(
            e.x + Angles.trnsx(angle, r),
            e.y + Angles.trnsy(angle, r));
      }
      Lines.endLine(false);
    }

    // ===== 3.5 球面缠绕弧线（贴合球面表面，增加有机感） =====
    int surfaceArcs = 12;
    for (int i = 0; i < surfaceArcs; i++) {
      long seed = e.id * 61L + i * 23L;
      float baseAngle = Mathf.randomSeed(seed, 0f, 360f);
      float tilt = Mathf.randomSeed(seed + 1, -70f, 70f);
      float orbitR = radius * Mathf.randomSeed(seed + 2, 0.65f, 0.90f) * fout;

      Draw.color(Tmp.c1.set(c).lerp(Color.white, 0.4f).a(alpha * 0.7f));
      Lines.stroke(0.9f * fout);
      Lines.beginLine();

      int segs = 18;
      for (int j = 0; j <= segs; j++) {
        float t = j / (float) segs;
        float arcAngle = baseAngle + t * 220f + fin * 60f;
        float nx = seed * 0.019f + j * 0.25f;
        float ny = fin * 3f + j * 0.16f;
        float wobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.4f, nx, ny) * 25f;

        // 将球面弧线投影到 2D，加入倾斜角
        float ax = Angles.trnsx(arcAngle + wobble, orbitR);
        float ay = Angles.trnsy(arcAngle + wobble, orbitR) * Mathf.cosDeg(tilt);
        float az = Angles.trnsy(arcAngle + wobble, orbitR) * Mathf.sinDeg(tilt);

        // 简单 2D 旋转
        float rot = fin * 35f + e.rotation;
        float rx = ax * Mathf.cosDeg(rot) - az * Mathf.sinDeg(rot);
        float ry = ay;

        Lines.linePoint(e.x + rx, e.y + ry);
      }
      Lines.endLine(false);
    }

    // ===== 4. 3D 球面粒子层（密集球壳） =====
    int sphereParticles = 160;
    for (int i = 0; i < sphereParticles; i++) {
      long s = e.id * 0x9E3779B97F4A7C15L + i * 2654435761L;

      // 球坐标
      float theta = Mathf.randomSeed(s, 0f, Mathf.PI2);
      float z = Mathf.randomSeed(s + 1, -1f, 1f);
      float rXY = Mathf.sqrt(1f - z * z);

      float px = rXY * Mathf.cos(theta);
      float py = rXY * Mathf.sin(theta);
      float pz = z;

      // 缓慢旋转
      float rot = fin * 40f + e.rotation;
      float rx = px * Mathf.cosDeg(rot) - pz * Mathf.sinDeg(rot);
      float rz = px * Mathf.sinDeg(rot) + pz * Mathf.cosDeg(rot);

      float depth = (rz + 1f) / 2f;
      // 外层粒子更亮更大，内层更淡更小
      float size = Mathf.lerp(0.5f, 2.0f, depth) * fout;
      float pAlpha = alpha * Mathf.lerp(0.45f, 1f, depth);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - depth).a(pAlpha));
      Fill.circle(
          e.x + rx * radius * 0.85f * fout,
          e.y + py * radius * 0.85f * fout,
          size);
    }

    // ===== 5. 两条细光环（点缀球体） =====
    // 内环：白色，细，快速变细消失
    e.scaled(95f, sub -> {
      float fade = sub.fout();
      Lines.stroke(1.6f * fade * fade);
      Draw.color(Color.white, fade * sub.color.a * 0.9f);
      Lines.circle(e.x, e.y, radius * (0.42f + 0.18f * sub.fin()));
    });

    // 外环：主题色，更细
    Lines.stroke(1.1f * fout);
    Draw.color(c, alpha * 0.85f);
    Lines.circle(e.x, e.y, radius * (0.72f + 0.22f * fin));

    // ===== 6. 光环上的粒子点 =====
    int ringParticles = 36;
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.3f).a(alpha));
    for (int i = 0; i < ringParticles; i++) {
      float angle = i * 360f / ringParticles + fin * 60f + e.rotation;
      float r = radius * (0.72f + 0.22f * fin);
      Fill.circle(
          e.x + Angles.trnsx(angle, r),
          e.y + Angles.trnsy(angle, r),
          1.0f * fout);
    }

    // ===== 7. 有机外伸触须 =====
    int tendrils = 8;
    for (int i = 0; i < tendrils; i++) {
      long seed = e.id * 41L + i * 17L;
      float baseAngle = i * 360f / tendrils + e.rotation + fin * 40f;
      float len = radius * (1.1f + 0.9f * fin);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.6f).a(alpha * 0.85f));
      Lines.stroke(Mathf.lerp(1.2f, 0.35f, fin) * fout);
      Lines.beginLine();
      Lines.linePoint(e.x, e.y);

      // 正弦波 + 噪声制造飘逸曲线
      int segs = 24;
      for (int j = 1; j <= segs; j++) {
        float t = j / (float) segs;
        float wave = Mathf.sin(t * Mathf.PI2 * 1.2f + fin * 3f + seed) * 28f * t;
        float nx = seed * 0.021f + j * 0.23f;
        float ny = fin * 2.5f + j * 0.12f;
        float noiseWobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.2f, nx, ny) * 16f * t;
        float angle = baseAngle + wave + noiseWobble;
        float r = len * t;

        Lines.linePoint(
            e.x + Angles.trnsx(angle, r),
            e.y + Angles.trnsy(angle, r));
      }
      Lines.endLine(false);
    }

    // ===== 8. 环境点光源 =====
    Drawf.light(e.x, e.y, radius * 3.4f, c, alpha * 0.75f);

    // ===== 9. 恢复 =====
    Draw.blend();
    Draw.reset();
  }).layer(Layer.effect + 0.1f);
  public static final Effect energySphere3 = new Effect(160f, 420f, e -> {
    float radius = 78f;
    float fin = e.fin();
    float fout = e.fout();
    float alpha = fout * e.color.a;
    Color c = e.color;

    // ===== 0. 加法混合 =====
    Draw.blend(Blending.additive);

    // ===== 1. 外层大气辉光（半径削减，alpha降低，避免全屏泛光） =====
    // 原 radius * 3.2f = 249.6f 会覆盖半个屏幕 → 改为 1.3f 仅保留球体边缘气晕
    Draw.color(c, alpha * 0.08f);
    Fill.light(e.x, e.y, 28, radius * 1.3f * fout, c, Color.clear);

    // 放射状外发光束（alpha和长度减半，避免光带溢出）
    int beams = 8;
    for (int i = 0; i < beams; i++) {
      long seed = e.id * 97L + i * 31L;
      float angle = i * 360f / beams + e.rotation + fin * 20f;
      float len = radius * (1.0f + 0.7f * fin); // 原 1.6+1.4 → 缩减

      Draw.color(Tmp.c1.set(c).lerp(Color.white, 0.5f).a(alpha * 0.15f)); // 0.35→0.15
      for (int j = 0; j < 3; j++) {
        float subAngle = angle + Mathf.randomSeedRange(seed + j, 12f);
        float subLen = len * (0.7f + j * 0.15f);
        float x2 = e.x + Angles.trnsx(subAngle, subLen);
        float y2 = e.y + Angles.trnsy(subAngle, subLen);
        Fill.light(
            Mathf.lerp(e.x, x2, 0.5f), Mathf.lerp(e.y, y2, 0.5f), 12, subLen * 0.5f,
            Tmp.c1.set(c).lerp(Color.white, 0.6f).a(alpha * 0.04f), // 0.08→0.04
            Color.clear);
      }
    }

    // ===== 2. 中心星形光芒（降低alpha，避免中心过曝） =====
    int starRays = 12;
    Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.3f).a(alpha * 0.25f)); // 0.45→0.25
    for (int i = 0; i < starRays; i++) {
      float angle = i * 360f / starRays + e.rotation + fin * 15f;
      float len = radius * (0.10f + 0.18f * fin);
      float w = radius * 0.02f * (1f + fin * 0.5f);
      float x2 = e.x + Angles.trnsx(angle, len);
      float y2 = e.y + Angles.trnsy(angle, len);

      Fill.tri(
          e.x, e.y,
          e.x + Angles.trnsx(angle - 90f, w), e.y + Angles.trnsy(angle - 90f, w),
          x2, y2);
      Fill.tri(
          e.x, e.y,
          e.x + Angles.trnsx(angle + 90f, w), e.y + Angles.trnsy(angle + 90f, w),
          x2, y2);
    }

    // ===== 3. 极亮核 + 中心光晕（核心：不再使用纯白，而是lerp到原色抑制过曝） =====
    Draw.color(c, alpha);
    Fill.light(e.x, e.y, 32, radius * 0.85f * fout, Color.white, c);

    float corePulse = 1f + Mathf.absin(fin * 14f, 14f, 0.18f);
    // 中心核：用原色lerp白色 0.7，避免纯白的核在加法混合下烧屏
    Draw.color(Tmp.c1.set(c).lerp(Color.white, 0.7f).a(alpha));
    Fill.circle(e.x, e.y, radius * 0.16f * corePulse * (1f + fin * 0.12f));
    Draw.color(Tmp.c1.set(c).lerp(Color.white, 0.5f).a(alpha * 0.95f));
    Fill.circle(e.x, e.y, radius * 0.07f * corePulse);

    // ===== 4. 球壳辉光（alpha降低） =====
    Draw.color(c, alpha * 0.7f); // 增加衰减系数
    Fill.light(e.x, e.y, 36, radius * 1.1f * fout, Color.white, c);

    // ===== 5. 大量细能量丝（alpha从0.60降至0.30） =====
    int filaments = 120;
    for (int i = 0; i < filaments; i++) {
      long seed = e.id * 53L + i * 19L;
      float baseAngle = Mathf.randomSeed(seed, 0f, 360f) + fin * 20f;
      float len = radius * Mathf.randomSeed(seed + 1, 0.65f, 1.0f) * 0.98f * fout;

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.55f).a(alpha * 0.30f)); // 0.60→0.30
      Lines.stroke(Mathf.lerp(0.6f, 0.15f, fin) * fout);
      Lines.beginLine();
      Lines.linePoint(e.x, e.y);

      int segs = 16;
      for (int j = 1; j <= segs; j++) {
        float t = j / (float) segs;
        float nx = seed * 0.013f + j * 0.31f;
        float ny = fin * 4f + j * 0.18f;
        float curve1 = Simplex.noise2d((int) seed, 2, 0.5f, 1.6f, nx, ny) * 38f * t * (1f - t);
        float curve2 = Simplex.noise2d((int) seed + 1, 2, 0.5f, 2.4f, nx * 1.5f, ny * 1.5f) * 16f * t;
        float angle = baseAngle + curve1 + curve2;
        float r = len * t;

        Lines.linePoint(
            e.x + Angles.trnsx(angle, r),
            e.y + Angles.trnsy(angle, r));
      }
      Lines.endLine(false);
    }

    // ===== 6. 球面缠绕弧线（alpha降低） =====
    int surfaceArcs = 16;
    for (int i = 0; i < surfaceArcs; i++) {
      long seed = e.id * 61L + i * 23L;
      float baseAngle = Mathf.randomSeed(seed, 0f, 360f);
      float tilt = Mathf.randomSeed(seed + 1, -80f, 80f);
      float orbitR = radius * Mathf.randomSeed(seed + 2, 0.60f, 0.98f) * fout;

      Draw.color(Tmp.c1.set(c).lerp(Color.white, 0.5f).a(alpha * 0.45f)); // 0.90→0.45
      Lines.stroke(0.75f * fout);
      Lines.beginLine();

      int segs = 20;
      for (int j = 0; j <= segs; j++) {
        float t = j / (float) segs;
        float arcAngle = baseAngle + t * 260f + fin * 50f;
        float nx = seed * 0.019f + j * 0.25f;
        float ny = fin * 3f + j * 0.16f;
        float wobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.4f, nx, ny) * 28f;

        float ax = Angles.trnsx(arcAngle + wobble, orbitR);
        float ay = Angles.trnsy(arcAngle + wobble, orbitR) * Mathf.cosDeg(tilt);
        float az = Angles.trnsy(arcAngle + wobble, orbitR) * Mathf.sinDeg(tilt);

        float rot = fin * 30f + e.rotation;
        float rx = ax * Mathf.cosDeg(rot) - az * Mathf.sinDeg(rot);
        float ry = ay;

        Lines.linePoint(e.x + rx, e.y + ry);
      }
      Lines.endLine(false);
    }

    // ===== 7. 3D 球面粒子层（降低alpha，避免密集白点叠加过曝） =====
    int sphereParticles = 240;
    for (int i = 0; i < sphereParticles; i++) {
      long s = e.id * 0x9E3779B97F4A7C15L + i * 2654435761L;

      float theta = Mathf.randomSeed(s, 0f, Mathf.PI2);
      float z = Mathf.randomSeed(s + 1, -1f, 1f);
      float rXY = Mathf.sqrt(1f - z * z);

      float px = rXY * Mathf.cos(theta);
      float py = rXY * Mathf.sin(theta);
      float pz = z;

      float rot = fin * 35f + e.rotation;
      float rx = px * Mathf.cosDeg(rot) - pz * Mathf.sinDeg(rot);
      float rz = px * Mathf.sinDeg(rot) + pz * Mathf.cosDeg(rot);

      float depth = (rz + 1f) / 2f;
      float size = Mathf.lerp(0.7f, 2.2f, depth) * fout;
      float pAlpha = alpha * Mathf.lerp(0.35f, 0.7f, depth); // 基础透明度降低

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 1f - depth).a(pAlpha));
      Fill.circle(
          e.x + rx * radius * 0.95f * fout,
          e.y + py * radius * 0.95f * fout,
          size);
    }

    // ===== 8. 多层虚线光环（降低alpha） =====
    int rings = 4;
    float[] ringRadii = { 0.32f, 0.48f, 0.68f, 0.88f };
    float[] ringAlphas = { 0.50f, 0.45f, 0.40f, 0.35f }; // 整体减半
    float[] ringThickness = { 2.2f, 1.6f, 1.2f, 0.9f };

    for (int ri = 0; ri < rings; ri++) {
      float rr = ringRadii[ri] + 0.12f * fin;
      float ringR = radius * rr * fout;

      Lines.stroke(ringThickness[ri] * fout);
      Draw.color(ri == 0 ? Color.white : c, alpha * ringAlphas[ri]);
      Lines.circle(e.x, e.y, ringR);

      int dots = 24 + ri * 12;
      Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.35f).a(alpha * ringAlphas[ri]));
      for (int i = 0; i < dots; i++) {
        float angle = i * 360f / dots + fin * (40f + ri * 15f) + e.rotation;
        Fill.circle(
            e.x + Angles.trnsx(angle, ringR),
            e.y + Angles.trnsy(angle, ringR),
            (0.8f + 0.4f * (rings - ri)) * fout);
      }
    }

    // ===== 9. 有机外伸触须（alpha和亮度降低） =====
    int tendrils = 10;
    for (int i = 0; i < tendrils; i++) {
      long seed = e.id * 41L + i * 17L;
      float baseAngle = i * 360f / tendrils + e.rotation + fin * 35f;
      float len = radius * (1.0f + 1.1f * fin);

      Draw.color(Tmp.c1.set(Color.white).lerp(c, 0.6f).a(alpha * 0.45f)); // 0.90→0.45
      Lines.stroke(Mathf.lerp(1.0f, 0.3f, fin) * fout);
      Lines.beginLine();
      Lines.linePoint(e.x, e.y);

      int segs = 26;
      for (int j = 1; j <= segs; j++) {
        float t = j / (float) segs;
        float wave = Mathf.sin(t * Mathf.PI2 * 1.3f + fin * 3f + seed) * 26f * t;
        float nx = seed * 0.021f + j * 0.23f;
        float ny = fin * 2.5f + j * 0.12f;
        float noiseWobble = Simplex.noise2d((int) seed, 2, 0.5f, 1.2f, nx, ny) * 14f * t;
        float angle = baseAngle + wave + noiseWobble;
        float r = len * t;

        Lines.linePoint(
            e.x + Angles.trnsx(angle, r),
            e.y + Angles.trnsy(angle, r));
      }
      Lines.endLine(false);
    }

    // ===== 10. 环境点光源（关键修复：半径和透明度大幅削减） =====
    // 原 radius * 2.0f = 156f，几乎照亮小半个地图
    // 改为 radius * 0.6f ≈ 47f，仅照亮球体周围一小圈；opacity也降低
    Drawf.light(e.x, e.y, radius * 0.6f, c, alpha * 0.12f);

    // ===== 11. 恢复 =====
    Draw.blend();
    Draw.reset();
  }).layer(Layer.effect + 0.1f);

}
