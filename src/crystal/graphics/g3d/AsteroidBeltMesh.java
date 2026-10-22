package crystal.graphics.g3d;

import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g3d.VertexBatch3D;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.util.Tmp;
import crystal.world.asteroid.AsteroidBelt;
import mindustry.graphics.g3d.PlanetMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;

/**
 * 星图里的小行星带视觉：以遭遇弧段为中心画一条端部渐隐、略长的弧形碎石带。
 * 只覆盖轨道的一段，不会铺满整圈；暗端用于暗示带子继续延伸。
 * 只负责画，不参与逻辑。
 */
public class AsteroidBeltMesh extends PlanetMesh {
  private final AsteroidBelt belt;
  private final Seq<AsteroidPoint> points = new Seq<>();

  private VertexBatch3D batch;
  private Shader shader;

  public AsteroidBeltMesh(AsteroidBelt belt) {
    super(belt.star, null, null);
    this.belt = belt;
    buildPoints();
  }

  private void buildPoints() {
    int count = 6000;
    points.ensureCapacity(count);

    for (int i = 0; i < count; i++) {
      long seed = (belt.name.hashCode() & 0xffffffffL) + i * 0x9E3779B97F4A7C15L;
      // 视觉带比遭遇弧段略长，但最多只占轨道的一段；两端逐渐变窄变暗。
      float visualSpan = Math.max(1f, Math.min(160f, Math.max(belt.sweepAngle, belt.sweepAngle * 1.8f)));
      float center = belt.startAngle + belt.sweepAngle * 0.5f;
      // 三角分布让中心更密、两端更稀。
      float t = (Mathf.randomSeed(seed, -1f, 1f) + Mathf.randomSeed(seed + 6, -1f, 1f)) * 0.5f;
      float angle = center + t * visualSpan * 0.5f;
      float edge = 1f - Math.abs(t);
      boolean core = Math.abs(t) <= belt.sweepAngle / visualSpan;

      float radialScale = (0.35f + 0.65f * edge) * (core ? 1f : 0.7f);
      float radius = belt.orbitRadius
          + Mathf.randomSeed(seed + 1, -belt.radialHalfWidth, belt.radialHalfWidth) * radialScale;
      float y = Mathf.randomSeed(seed + 2, -belt.thickness, belt.thickness)
          * (0.45f + 0.55f * edge) * (core ? 1f : 0.7f);

      float size = Mathf.randomSeed(seed + 3, 0.25f, 1f)
          * (0.4f + 0.6f * edge) * (core ? 1f : 0.65f);
      float shade = Mathf.randomSeed(seed + 4, 0.55f, 1f);
      Color color = core
          ? Tmp.c1.set(Color.valueOf("b69a7a")).lerp(Color.valueOf("f0dab8"), shade)
          : Tmp.c1.set(Color.valueOf("786858")).lerp(Color.valueOf("b9a58a"), shade);

      // 少量冰蓝色/暗色碎石，避免整条带颜色太单一。
      float tone = Mathf.randomSeed(seed + 5, 0f, 1f);
      if (tone < 0.14f) {
        color.lerp(Color.valueOf("b9d7e8"), 0.55f);
      } else if (tone > 0.9f) {
        color.mul(0.55f, 0.52f, 0.5f, 1f);
      }

      points.add(new AsteroidPoint(
          Mathf.cosDeg(angle) * radius,
          y,
          Mathf.sinDeg(angle) * radius,
          color.cpy(),
          size));
    }
  }

  private void ensureInit() {
    if (batch != null) {
      return;
    }
    shader = createPointShader();
    batch = new VertexBatch3D(Math.min(points.size + 8, 8192), false, true, 0, shader);
  }

  /**
   * 抵消 cloudMesh transform 里的恒星自转。
   * Mat3D.rotate 与 Vec3.rotate 的 Y 轴旋转方向相反，所以这里用正角度。
   */
  public static Vec3 cancelStarRotation(Vec3 point, Planet star) {
    return point.rotate(Vec3.Y, star.getRotation());
  }

  private static Shader createPointShader() {
    String vert = "attribute vec3 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "uniform mat4 u_proj;\n" +
        "varying vec4 v_col;\n" +
        "varying float v_size;\n" +
        "void main() {\n" +
        "   gl_Position = u_proj * vec4(a_position, 1.0);\n" +
        "   gl_PointSize = 5.0 + a_color.a * 18.0;\n" +
        "   v_col = vec4(a_color.rgb, 1.0);\n" +
        "   v_size = a_color.a;\n" +
        "}\n";

    String frag = "varying vec4 v_col;\n" +
        "varying float v_size;\n" +
        "void main() {\n" +
        "   vec2 coord = gl_PointCoord - vec2(0.5);\n" +
        "   float dist = length(coord);\n" +
        "   if(dist > 0.5) discard;\n" +
        "   float glow = 1.0 - smoothstep(0.0, 0.5, dist);\n" +
        "   glow = pow(glow, 0.55 + v_size * 0.25);\n" +
        "   gl_FragColor = vec4(v_col.rgb, glow);\n" +
        "}\n";

    return new Shader(vert, frag);
  }

  @Override
  public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
    ensureInit();
    batch.proj(projection);

    int count = Math.min(points.size, batch.getMaxVertices());
    // cloudMesh 的 transform 会带上恒星自转；小行星带的逻辑角度固定在恒星参考系里。
    // 注意 Mat3D.rotate 与 Vec3.rotate 的 Y 轴方向相反，这里要用正角度抵消。
    for (int i = 0; i < count; i++) {
      AsteroidPoint point = points.get(i);
      Vec3 pos = cancelStarRotation(Tmp.v31.set(point.x, point.y, point.z), belt.star);
      Mat3D.prj(pos, transform);

      batch.color(point.color.r, point.color.g, point.color.b, point.size);
      batch.vertex(pos.x, pos.y, pos.z);
    }

    Gl.enable(Gl.blend);
    Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);
    batch.flush(Gl.points);
    Gl.blendFunc(Gl.srcAlpha, Gl.one);
  }

  @Override
  public void dispose() {
    if (batch != null) {
      batch.dispose();
      batch = null;
    }
    if (shader != null) {
      shader.dispose();
      shader = null;
    }
  }

  private static class AsteroidPoint {
    final float x, y, z;
    final Color color;
    final float size;

    AsteroidPoint(float x, float y, float z, Color color, float size) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.color = color;
      this.size = size;
    }
  }
}
