package crystal.graphics.g3d;

import arc.graphics.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.graphics.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;

public class MultiRingMesh implements GenericMesh {
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

  public MultiRingMesh(Planet planet, RingBand[] bands, int segments) {
    this.planet = planet;
    buildMesh(bands, segments);

    outerRadius = 0;
    for (RingBand b : bands) {
      outerRadius = Math.max(outerRadius, b.outer * planet.radius);
    }
  }

  /**
   * 宽版科技蓝 - 更宽更亮更有存在感
   */
  public static MultiRingMesh techBlueWide(Planet planet) {
    RingBand[] bands = {
        // 内晕
        new RingBand(1.15f, 1.3f,
            new Color(0.3f, 0.5f, 0.9f, 0.1f),
            new Color(0.4f, 0.7f, 1f, 0.4f)),

        // 主环 1 - 亮蓝
        new RingBand(1.3f, 1.6f,
            new Color(0.4f, 0.7f, 1f, 0.4f),
            new Color(0.6f, 0.85f, 1f, 0.85f)),

        // 暗缝
        new RingBand(1.6f, 1.7f,
            new Color(0.6f, 0.85f, 1f, 0.85f),
            new Color(0.2f, 0.4f, 0.7f, 0.2f)),

        // 主环 2 - 最亮
        new RingBand(1.7f, 2.1f,
            new Color(0.3f, 0.55f, 0.9f, 0.3f),
            new Color(0.7f, 0.9f, 1f, 0.95f)),

        new RingBand(2.1f, 2.4f,
            new Color(0.7f, 0.9f, 1f, 0.95f),
            new Color(0.5f, 0.75f, 1f, 0.7f)),

        // 暗缝
        new RingBand(2.4f, 2.5f,
            new Color(0.5f, 0.75f, 1f, 0.7f),
            new Color(0.2f, 0.35f, 0.6f, 0.15f)),

        // 外环
        new RingBand(2.5f, 2.8f,
            new Color(0.3f, 0.5f, 0.85f, 0.2f),
            new Color(0.5f, 0.7f, 1f, 0.6f)),

        // 外晕
        new RingBand(2.8f, 3.2f,
            new Color(0.5f, 0.7f, 1f, 0.4f),
            new Color(0.3f, 0.5f, 0.9f, 0f)),
    };

    return new MultiRingMesh(planet, bands, 90);
  }

  /**
   * 星空紫蓝 - 神秘风格
   */
  public static MultiRingMesh cosmicPurple(Planet planet) {
    RingBand[] bands = {
        new RingBand(1.2f, 1.4f,
            new Color(0.4f, 0.2f, 0.8f, 0.1f),
            new Color(0.6f, 0.3f, 0.9f, 0.5f)),
        new RingBand(1.4f, 1.8f,
            new Color(0.6f, 0.3f, 0.9f, 0.5f),
            new Color(0.8f, 0.5f, 1f, 0.9f)),
        new RingBand(1.8f, 2.0f,
            new Color(0.8f, 0.5f, 1f, 0.9f),
            new Color(0.4f, 0.2f, 0.7f, 0.2f)),
        new RingBand(2.0f, 2.5f,
            new Color(0.5f, 0.25f, 0.8f, 0.3f),
            new Color(0.7f, 0.4f, 1f, 0.75f)),
        new RingBand(2.5f, 3.0f,
            new Color(0.7f, 0.4f, 1f, 0.6f),
            new Color(0.4f, 0.2f, 0.7f, 0f)),
    };
    return new MultiRingMesh(planet, bands, 90);
  }

  private void buildMesh(RingBand[] bands, int segments) {
    int floatPerVertex = 4;
    int totalVertices = bands.length * segments * 2;
    int totalIndices = bands.length * segments * 6;

    mesh = new Mesh(true, totalVertices, totalIndices,
        VertexAttribute.position3,
        VertexAttribute.color);

    float[] vertices = new float[totalVertices * floatPerVertex];
    short[] indices = new short[totalIndices];

    int vertexOffset = 0;
    int indexOffset = 0;
    short baseIndex = 0;

    for (RingBand band : bands) {
      float innerR = band.inner * planet.radius;
      float outerR = band.outer * planet.radius;
      float innerCol = band.innerColor.toFloatBits();
      float outerCol = band.outerColor.toFloatBits();

      for (int i = 0; i < segments; i++) {
        float angle = i * 2f * Mathf.pi / segments;
        float cos = Mathf.cos(angle);
        float sin = Mathf.sin(angle);

        int base = vertexOffset + i * 2 * floatPerVertex;

        vertices[base] = cos * innerR;
        vertices[base + 1] = 0f;
        vertices[base + 2] = sin * innerR;
        vertices[base + 3] = innerCol;

        int outerBase = base + floatPerVertex;
        vertices[outerBase] = cos * outerR;
        vertices[outerBase + 1] = 0f;
        vertices[outerBase + 2] = sin * outerR;
        vertices[outerBase + 3] = outerCol;

        short i0 = (short) (baseIndex + i * 2);
        short i1 = (short) (baseIndex + i * 2 + 1);
        short i2 = (short) (baseIndex + (i * 2 + 2) % (segments * 2));
        short i3 = (short) (baseIndex + (i * 2 + 3) % (segments * 2));

        indices[indexOffset + i * 6] = i0;
        indices[indexOffset + i * 6 + 1] = i2;
        indices[indexOffset + i * 6 + 2] = i1;
        indices[indexOffset + i * 6 + 3] = i1;
        indices[indexOffset + i * 6 + 4] = i2;
        indices[indexOffset + i * 6 + 5] = i3;
      }

      vertexOffset += segments * 2 * floatPerVertex;
      indexOffset += segments * 6;
      baseIndex += segments * 2;
    }

    mesh.setVertices(vertices);
    mesh.setIndices(indices);
  }

  @Override
  public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
    if (mesh.isDisposed())
      return;

    tmpMat.set(transform);
    if (rotationSpeed != 0f) {
      tmpMat.rotate(Vec3.Y, Time.globalTime * rotationSpeed);
    }
    if (tiltAngle != 0f) {
      tmpMat.rotate(Vec3.X, tiltAngle);
    }

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
    Gl.enable(Gl.cullFace);
    Gl.disable(Gl.blend);
  }

  @Override
  public void dispose() {
    if (mesh != null && !mesh.isDisposed()) {
      mesh.dispose();
    }
  }
}
