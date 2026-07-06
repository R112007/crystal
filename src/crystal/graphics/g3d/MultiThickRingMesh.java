package crystal.graphics.g3d;

import arc.graphics.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.graphics.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;

/**
 * 自然风格行星环 - 低饱和、柔和过渡、微厚度
 */
public class MultiThickRingMesh implements GenericMesh {
  protected Mesh mesh;
  protected Planet planet;
  public float tiltAngle = 0f;
  public float rotationSpeed = 0f;
  public float outerRadius;

  private final Mat3D tmpMat = new Mat3D();

  public static class RingBand {
    float inner, outer;
    Color innerColor, outerColor;

    public RingBand(float inner, float outer, Color innerColor, Color outerColor) {
      this.inner = inner;
      this.outer = outer;
      this.innerColor = innerColor;
      this.outerColor = outerColor;
    }
  }

  public MultiThickRingMesh(Planet planet, RingBand[] bands, int segments, int thicknessSeg, float thickness) {
    this.planet = planet;
    buildMesh(bands, segments, thicknessSeg, thickness * planet.radius);

    outerRadius = 0;
    for (RingBand b : bands) {
      outerRadius = Math.max(outerRadius, b.outer * planet.radius);
    }
  }

  // ===== 自然风格预设 =====

  /** 冰石蓝 - 清冷的冰岩环，低饱和 */
  public static MultiThickRingMesh iceRock(Planet planet) {
    RingBand[] bands = {
        // 内晕，极淡
        new RingBand(1.2f, 1.35f,
            new Color(0.5f, 0.55f, 0.65f, 0.05f),
            new Color(0.55f, 0.6f, 0.7f, 0.15f)),

        // D 环 - 稀疏暗淡
        new RingBand(1.38f, 1.5f,
            new Color(0.55f, 0.6f, 0.7f, 0.18f),
            new Color(0.6f, 0.65f, 0.75f, 0.25f)),

        // 过渡暗区
        new RingBand(1.5f, 1.58f,
            new Color(0.6f, 0.65f, 0.75f, 0.25f),
            new Color(0.5f, 0.55f, 0.65f, 0.12f)),

        // C 环 - 中等密度
        new RingBand(1.6f, 1.85f,
            new Color(0.55f, 0.6f, 0.7f, 0.2f),
            new Color(0.65f, 0.7f, 0.8f, 0.4f)),

        new RingBand(1.85f, 2.0f,
            new Color(0.65f, 0.7f, 0.8f, 0.4f),
            new Color(0.7f, 0.75f, 0.85f, 0.5f)),

        // B 环内侧 - 开始变亮
        new RingBand(2.0f, 2.2f,
            new Color(0.7f, 0.75f, 0.85f, 0.5f),
            new Color(0.8f, 0.85f, 0.92f, 0.7f)),

        // B 环核心 - 最亮最密
        new RingBand(2.2f, 2.45f,
            new Color(0.8f, 0.85f, 0.92f, 0.7f),
            new Color(0.85f, 0.9f, 0.95f, 0.75f)),

        // B 环外侧 - 逐渐变暗
        new RingBand(2.45f, 2.6f,
            new Color(0.85f, 0.9f, 0.95f, 0.75f),
            new Color(0.7f, 0.75f, 0.85f, 0.45f)),

        // 卡西尼缝 - 明显变暗
        new RingBand(2.6f, 2.72f,
            new Color(0.6f, 0.65f, 0.75f, 0.25f),
            new Color(0.5f, 0.55f, 0.65f, 0.1f)),

        // A 环 - 中等亮度
        new RingBand(2.75f, 3.0f,
            new Color(0.55f, 0.6f, 0.7f, 0.15f),
            new Color(0.7f, 0.75f, 0.85f, 0.55f)),

        new RingBand(3.0f, 3.2f,
            new Color(0.7f, 0.75f, 0.85f, 0.55f),
            new Color(0.65f, 0.7f, 0.8f, 0.4f)),

        // 恩克缝 + F 环
        new RingBand(3.22f, 3.28f,
            new Color(0.6f, 0.65f, 0.75f, 0.2f),
            new Color(0.75f, 0.8f, 0.9f, 0.35f)),
        new RingBand(3.28f, 3.35f,
            new Color(0.75f, 0.8f, 0.9f, 0.35f),
            new Color(0.55f, 0.6f, 0.7f, 0.12f)),

        // E 环外晕 - 极淡
        new RingBand(3.4f, 3.8f,
            new Color(0.55f, 0.6f, 0.7f, 0.08f),
            new Color(0.5f, 0.55f, 0.65f, 0f)),
    };
    return new MultiThickRingMesh(planet, bands, 120, 3, 0.06f);
  }

  /** 暖沙金 - 土星风格，米黄调 */
  public static MultiThickRingMesh sandGold(Planet planet) {
    RingBand[] bands = {
        new RingBand(1.2f, 1.4f,
            new Color(0.55f, 0.5f, 0.4f, 0.06f),
            new Color(0.6f, 0.55f, 0.45f, 0.18f)),
        new RingBand(1.45f, 1.7f,
            new Color(0.6f, 0.55f, 0.45f, 0.2f),
            new Color(0.7f, 0.65f, 0.5f, 0.35f)),
        new RingBand(1.75f, 2.0f,
            new Color(0.65f, 0.6f, 0.48f, 0.3f),
            new Color(0.8f, 0.75f, 0.6f, 0.55f)),
        new RingBand(2.0f, 2.3f,
            new Color(0.8f, 0.75f, 0.6f, 0.55f),
            new Color(0.9f, 0.85f, 0.7f, 0.7f)),
        new RingBand(2.3f, 2.55f,
            new Color(0.9f, 0.85f, 0.7f, 0.7f),
            new Color(0.85f, 0.8f, 0.65f, 0.6f)),
        new RingBand(2.6f, 2.75f,
            new Color(0.65f, 0.6f, 0.48f, 0.2f),
            new Color(0.55f, 0.5f, 0.4f, 0.08f)),
        new RingBand(2.8f, 3.1f,
            new Color(0.6f, 0.55f, 0.45f, 0.15f),
            new Color(0.75f, 0.7f, 0.55f, 0.5f)),
        new RingBand(3.15f, 3.5f,
            new Color(0.7f, 0.65f, 0.5f, 0.35f),
            new Color(0.6f, 0.55f, 0.45f, 0.1f)),
        new RingBand(3.55f, 4.0f,
            new Color(0.55f, 0.5f, 0.4f, 0.06f),
            new Color(0.5f, 0.45f, 0.35f, 0f)),
    };
    return new MultiThickRingMesh(planet, bands, 120, 3, 0.06f);
  }

  /** 暗灰铁 - 暗色金属/岩石风格 */
  public static MultiThickRingMesh darkIron(Planet planet) {
    RingBand[] bands = {
        new RingBand(1.25f, 1.5f,
            new Color(0.3f, 0.3f, 0.35f, 0.1f),
            new Color(0.4f, 0.4f, 0.45f, 0.25f)),
        new RingBand(1.55f, 1.9f,
            new Color(0.35f, 0.35f, 0.4f, 0.2f),
            new Color(0.5f, 0.5f, 0.55f, 0.45f)),
        new RingBand(1.95f, 2.3f,
            new Color(0.45f, 0.45f, 0.5f, 0.4f),
            new Color(0.55f, 0.55f, 0.6f, 0.6f)),
        new RingBand(2.35f, 2.6f,
            new Color(0.5f, 0.5f, 0.55f, 0.5f),
            new Color(0.4f, 0.4f, 0.45f, 0.25f)),
        new RingBand(2.65f, 3.0f,
            new Color(0.35f, 0.35f, 0.4f, 0.2f),
            new Color(0.5f, 0.5f, 0.55f, 0.5f)),
        new RingBand(3.05f, 3.5f,
            new Color(0.45f, 0.45f, 0.5f, 0.35f),
            new Color(0.3f, 0.3f, 0.35f, 0.05f)),
    };
    return new MultiThickRingMesh(planet, bands, 120, 3, 0.05f);
  }

  private void buildMesh(RingBand[] bands, int segments, int thicknessSeg, float thickness) {
    int floatPerVertex = 4;

    int totalVertices = bands.length * (thicknessSeg + 1) * segments * 2;
    int totalIndices = bands.length * (thicknessSeg * segments * 12 + segments * 12);

    mesh = new Mesh(true, totalVertices, totalIndices,
        VertexAttribute.position3,
        VertexAttribute.color);

    float[] vertices = new float[totalVertices * floatPerVertex];
    short[] indices = new short[totalIndices];

    int vOffset = 0;
    int iOffset = 0;
    short baseIndex = 0;
    float halfThick = thickness / 2f;

    for (RingBand band : bands) {
      float innerR = band.inner * planet.radius;
      float outerR = band.outer * planet.radius;

      float[] layerInnerCols = new float[thicknessSeg + 1];
      float[] layerOuterCols = new float[thicknessSeg + 1];

      for (int t = 0; t <= thicknessSeg; t++) {
        float tNorm = (float) t / thicknessSeg;
        // 厚度方向渐变：中间稍亮，边缘很淡（模拟薄环的边缘柔化）
        float edgeFade = 1f - Math.abs(tNorm - 0.5f) * 2f;
        edgeFade = Mathf.lerp(0.15f, 1f, edgeFade);

        Color ic = band.innerColor.cpy();
        ic.a *= edgeFade;
        layerInnerCols[t] = ic.toFloatBits();

        Color oc = band.outerColor.cpy();
        oc.a *= edgeFade;
        layerOuterCols[t] = oc.toFloatBits();
      }

      for (int t = 0; t <= thicknessSeg; t++) {
        float y = -halfThick + (float) t / thicknessSeg * thickness;
        float innerCol = layerInnerCols[t];
        float outerCol = layerOuterCols[t];

        for (int i = 0; i < segments; i++) {
          float angle = i * 2f * Mathf.pi / segments;
          float cos = Mathf.cos(angle);
          float sin = Mathf.sin(angle);

          int base = vOffset + i * 2 * floatPerVertex;

          vertices[base] = cos * innerR;
          vertices[base + 1] = y;
          vertices[base + 2] = sin * innerR;
          vertices[base + 3] = innerCol;

          int outerBase = base + floatPerVertex;
          vertices[outerBase] = cos * outerR;
          vertices[outerBase + 1] = y;
          vertices[outerBase + 2] = sin * outerR;
          vertices[outerBase + 3] = outerCol;
        }

        vOffset += segments * 2 * floatPerVertex;
      }

      for (int t = 0; t < thicknessSeg; t++) {
        short base0 = (short) (baseIndex + t * segments * 2);
        short base1 = (short) (baseIndex + (t + 1) * segments * 2);

        for (int i = 0; i < segments; i++) {
          int next = (i + 1) % segments;

          indices[iOffset++] = (short) (base0 + i * 2 + 1);
          indices[iOffset++] = (short) (base0 + next * 2 + 1);
          indices[iOffset++] = (short) (base1 + i * 2 + 1);
          indices[iOffset++] = (short) (base1 + i * 2 + 1);
          indices[iOffset++] = (short) (base0 + next * 2 + 1);
          indices[iOffset++] = (short) (base1 + next * 2 + 1);

          indices[iOffset++] = (short) (base1 + i * 2);
          indices[iOffset++] = (short) (base1 + next * 2);
          indices[iOffset++] = (short) (base0 + i * 2);
          indices[iOffset++] = (short) (base0 + i * 2);
          indices[iOffset++] = (short) (base1 + next * 2);
          indices[iOffset++] = (short) (base0 + next * 2);
        }
      }

      short topBase = (short) (baseIndex + thicknessSeg * segments * 2);
      short bottomBase = baseIndex;

      for (int i = 0; i < segments; i++) {
        int next = (i + 1) % segments;

        indices[iOffset++] = (short) (topBase + i * 2);
        indices[iOffset++] = (short) (topBase + next * 2);
        indices[iOffset++] = (short) (topBase + i * 2 + 1);
        indices[iOffset++] = (short) (topBase + i * 2 + 1);
        indices[iOffset++] = (short) (topBase + next * 2);
        indices[iOffset++] = (short) (topBase + next * 2 + 1);

        indices[iOffset++] = (short) (bottomBase + i * 2 + 1);
        indices[iOffset++] = (short) (bottomBase + next * 2 + 1);
        indices[iOffset++] = (short) (bottomBase + i * 2);
        indices[iOffset++] = (short) (bottomBase + i * 2);
        indices[iOffset++] = (short) (bottomBase + next * 2 + 1);
        indices[iOffset++] = (short) (bottomBase + next * 2);
      }

      baseIndex += (thicknessSeg + 1) * segments * 2;
    }

    mesh.setVertices(vertices);
    mesh.setIndices(indices);
  }

  @Override
  public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
    if (mesh == null || mesh.isDisposed())
      return;

    tmpMat.set(transform);
    if (rotationSpeed != 0f) {
      tmpMat.rotate(Vec3.Y, Time.globalTime * rotationSpeed);
    }
    if (tiltAngle != 0f) {
      tmpMat.rotate(Vec3.X, tiltAngle);
    }

    boolean wasBlend = Gl.isEnabled(Gl.blend);
    boolean wasCull = Gl.isEnabled(Gl.cullFace);

    Shader shader = Shaders.unlit;
    shader.bind();
    shader.setUniformMatrix4("u_proj", projection.val);
    shader.setUniformMatrix4("u_trans", tmpMat.val);
    shader.apply();

    Gl.enable(Gl.blend);
    Gl.disable(Gl.cullFace);
    Gl.depthMask(false);

    mesh.render(shader, Gl.triangles);

    Gl.depthMask(true);
    if (wasCull)
      Gl.enable(Gl.cullFace);
    if (!wasBlend)
      Gl.disable(Gl.blend);
  }

  @Override
  public void dispose() {
    if (mesh != null && !mesh.isDisposed()) {
      mesh.dispose();
    }
  }
}
