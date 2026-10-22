package crystal.world.asteroid;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

/** 小行星坠落与落地阶段的视觉效果。 */
public class AsteroidFx {
  public static final Color rockDark = Color.valueOf("41362f");
  public static final Color rockMid = Color.valueOf("6f5a49");
  public static final Color rockLight = Color.valueOf("a18468");
  /**
   * 从高空斜着砸向落点的陨石。e.rotation 是从落点指向天空起点的方向。
   * 与 {@link AsteroidBelt#fallTicks} 保持一致（120 tick）。
   */
  public static final Effect falling = new Effect(120f, 2000f, e -> {
    float fin = e.fin();
    float fout = e.fout();
    FallData data = e.data instanceof FallData d ? d : null;
    float scale = data != null ? Mathf.clamp(data.sizeScale, 0.3f, 2f) : 1f;
    float startDistance = data != null ? data.startDistance : 700f;
    float angle = e.rotation;
    // 起点在屏幕外；保留非零初速，整体飞行更慢。
    float progress = 0.35f * fin + 0.65f * fin * fin;
    float distance = startDistance * (1f - progress);

    float x = e.x + Angles.trnsx(angle, distance);
    float y = e.y + Angles.trnsy(angle, distance);
    float size = Mathf.lerp(1.1f, 10.5f, fin * fin) * scale;
    float trailLength = (50f + 110f * fin) * scale;

    Draw.blend(Blending.additive);

    // 长火尾：从陨石头部一直拖向天空方向。
    for (int i = 0; i <= 10; i++) {
      float t = i / 10f;
      float jitter = Mathf.randomSeed(e.id * 31L + i, -1f, 1f) * size * 0.5f * t;
      float tx = x + Angles.trnsx(angle, trailLength * t) + Angles.trnsx(angle + 90f, jitter);
      float ty = y + Angles.trnsy(angle, trailLength * t) + Angles.trnsy(angle + 90f, jitter);
      Draw.color(Pal.lightFlame, fout * 0.55f * (1f - t) * (1f - t));
      Fill.circle(tx, ty, size * (0.55f * (1f - t) + 0.12f));
    }

    Draw.color(Pal.lightOrange, 0.35f * fout);
    Fill.circle(x, y, size * 2.2f);
    Draw.color(Color.white, 0.55f * fout);
    Fill.circle(x, y, size * 0.65f);

    Draw.blend();

    // 尾迹后段的烟雾。
    Draw.color(Color.valueOf("4b4a52"), 0.18f * fout * fin);
    for (int i = 1; i <= 7; i++) {
      float t = i / 7f;
      float sx = x + Angles.trnsx(angle, trailLength * (0.55f + 0.45f * t));
      float sy = y + Angles.trnsy(angle, trailLength * (0.55f + 0.45f * t));
      Fill.circle(sx, sy, size * (0.65f - t * 0.4f));
    }

    // 不规则岩体：三个错开的小圆拼出轮廓。
    float seed = e.id * 17.37f;
    float ox = Mathf.randomSeed((long) seed, -0.35f, 0.35f) * size;
    float oy = Mathf.randomSeed((long) seed + 1, -0.35f, 0.35f) * size;

    Draw.color(rockDark);
    Fill.circle(x + ox, y + oy, size);
    Draw.color(rockMid);
    Fill.circle(x - size * 0.28f + ox, y + size * 0.24f + oy, size * 0.62f);
    Draw.color(rockLight);
    Fill.circle(x + size * 0.2f + ox, y - size * 0.12f + oy, size * 0.42f);
    Draw.color(rockDark);
    Fill.circle(x + size * 0.34f + ox, y + size * 0.22f + oy, size * 0.26f);

    // 朝向下落方向的炽热前缘。
    Draw.color(Pal.lightFlame, 0.75f * fout);
    Fill.circle(x - Angles.trnsx(angle, size * 0.45f), y - Angles.trnsy(angle, size * 0.45f), size * 0.35f);

    Lines.stroke(1.4f + 1.6f * fin);
    Draw.color(Pal.lightOrange, 0.6f * fout);
    Lines.circle(x + ox, y + oy, size * 1.04f);

    Drawf.light(x, y, size * 8f, Pal.lightOrange, 0.8f * fout);
    Draw.reset();
  }).layer(Layer.effect + 4f);

  /** 短促的落地闪光，约 0.27 秒。 */
  public static final Effect impactFlash = new Effect(16f, 260f, e -> {
    float radius = Math.max(e.rotation, 8f);
    float fin = e.fin();
    float fout = e.fout();

    Draw.blend(Blending.additive);
    Draw.color(Pal.lightFlame, 0.65f * fout);
    Fill.light(e.x, e.y, 24, radius * (0.45f + 0.8f * fin), Pal.lightOrange, Color.clear);
    Draw.color(Color.white, 0.7f * fout);
    Fill.circle(e.x, e.y, radius * (0.08f + 0.22f * fin));
    Draw.blend();

    Drawf.light(e.x, e.y, radius * 1.8f, Pal.lightOrange, 0.45f * fout);
    Draw.reset();
  }).layer(Layer.effect + 2f);

  /** 短促的落地尘环，约 0.33 秒。 */
  public static final Effect impactRing = new Effect(20f, 300f, e -> {
    float radius = Math.max(e.rotation, 8f);
    float fin = e.fin(Interp.pow3Out);
    float fout = e.fout(Interp.pow2Out);

    Lines.stroke(2.2f * fout + 0.27f);
    Draw.color(Pal.lightOrange, 0.55f * fout);
    Lines.ellipse(e.x, e.y, radius * (0.25f + 1f * fin), 1f, 0.45f, 0f);

    Draw.color(Color.valueOf("b8a894"), 0.35f * fout);
    Angles.randLenVectors(e.id, 10, radius * (0.3f + 0.8f * fin), (x, y) -> {
      Fill.circle(e.x + x, e.y + y * 0.45f, 0.6f + 1.8f * e.fout());
    });
    Draw.reset();
  }).layer(Layer.effect + 1.5f);

  /** 传给坠落特效的尺寸倍率；由实际爆炸半径换算。 */
  public static class FallData {
    public final float sizeScale;
    public final float startDistance;

    public FallData(float sizeScale, float startDistance) {
      this.sizeScale = sizeScale;
      this.startDistance = startDistance;
    }
  }

  private AsteroidFx() {
  }
}
