package crystal.graphics.g3d;

import arc.graphics.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.noise.*;
import mindustry.graphics.Shaders;
import mindustry.graphics.g3d.*;

public class RingMesh2 implements GenericMesh {
  private Mesh mesh;
  private final Shader shader;
  private final Band[] bands;
  private final int segments;
  private final float tilt;
  private final int seed;
  private static final Mat3D tmpMat = new Mat3D();

  public static class Band {
    public float inner, outer;
    public Color color;
    public float alpha;

    public Band(float inner, float outer, Color color, float alpha) {
      this.inner = inner;
      this.outer = outer;
      this.color = color;
      this.alpha = alpha;
    }
  }

  public RingMesh2(Band[] bands, int segments, float tilt, int seed) {
    this.bands = bands;
    this.segments = segments;
    this.tilt = tilt;
    this.seed = seed;
    this.shader = Shaders.unlit;
    buildMesh();
  }

  void buildMesh() {
    int totalVerts = 0;
    for (Band b : bands)
      totalVerts += segments * 6;

    mesh = new Mesh(true, totalVerts, 0,
        VertexAttribute.position3,
        VertexAttribute.color);

    mesh.getVerticesBuffer().limit(mesh.getVerticesBuffer().capacity());
    mesh.getVerticesBuffer().position(0);

    float[] v = new float[totalVerts * 4];
    int idx = 0;
    Color tmp = new Color();

    for (Band band : bands) {
      for (int i = 0; i < segments; i++) {
        float a1 = (i / (float) segments) * Mathf.PI2;
        float a2 = ((i + 1) / (float) segments) * Mathf.PI2;

        float c1 = Mathf.cos(a1), s1 = Mathf.sin(a1);
        float c2 = Mathf.cos(a2), s2 = Mathf.sin(a2);

        Vec3 v0 = new Vec3(c1 * band.inner, 0, s1 * band.inner);
        Vec3 v1 = new Vec3(c1 * band.outer, 0, s1 * band.outer);
        Vec3 v2 = new Vec3(c2 * band.outer, 0, s2 * band.outer);
        Vec3 v3 = new Vec3(c2 * band.inner, 0, s2 * band.inner);

        float n = Simplex.noise2d(seed, 3, 0.5f, 2f, i / 8f, 0f);
        float density = Mathf.clamp(0.7f + n * 0.3f, 0.3f, 1f);
        float r = 1f + Simplex.noise2d(seed + 1, 2, 0.5f, 3f, i / 5f, 0f) * 0.08f;

        tmp.set(band.color).mul(r, r, r, 1f).a(band.alpha * density);
        float col = tmp.toFloatBits();

        idx = put(v, idx, v0, col);
        idx = put(v, idx, v1, col);
        idx = put(v, idx, v2, col);
        idx = put(v, idx, v0, col);
        idx = put(v, idx, v2, col);
        idx = put(v, idx, v3, col);
      }
    }

    mesh.getVerticesBuffer().put(v, 0, idx);
    mesh.getVerticesBuffer().limit(idx);
  }

  int put(float[] v, int i, Vec3 p, float col) {
    v[i++] = p.x;
    v[i++] = p.y;
    v[i++] = p.z;
    v[i++] = col;
    return i;
  }

  @Override
  public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
    if (mesh == null || mesh.isDisposed())
      return;

    // 环的倾斜固定相对于空间，叠加在星球变换上
    tmpMat.set(transform).rotate(Vec3.X, tilt);

    shader.bind();
    shader.setUniformMatrix4("u_proj", projection.val);
    shader.setUniformMatrix4("u_trans", tmpMat.val);
    shader.apply();

    Gl.disable(Gl.cullFace);
    Gl.enable(Gl.blend);
    Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);
    Gl.depthMask(false);

    mesh.render(shader, Gl.triangles);

    // 恢复关键状态，让 PlanetRenderer 后续渲染不出问题
    Gl.depthMask(true);
    Gl.disable(Gl.blend);
    Gl.enable(Gl.cullFace);
    Gl.cullFace(Gl.back);
  }

  @Override
  public void dispose() {
    if (mesh != null)
      mesh.dispose();
  }
}
