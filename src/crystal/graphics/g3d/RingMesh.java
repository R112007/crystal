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
 * 行星环 - 统一入口类
 */
public class RingMesh implements GenericMesh {
    protected Mesh mesh;
    protected Planet planet;
    public float tiltAngle = 0f;
    public float rotationSpeed = 0f;
    public float outerRadius;
    public float radiusScale = 1f;

    private final Mat3D tmpMat = new Mat3D();

    public static class Band {
        float inner, outer;
        Color innerColor, outerColor;

        public Band(float inner, float outer, Color innerColor, Color outerColor) {
            this.inner = inner;
            this.outer = outer;
            this.innerColor = innerColor;
            this.outerColor = outerColor;
        }
    }

    public RingMesh(Planet planet, Band[] bands, int segments, int thicknessSeg, float thickness) {
        this.planet = planet;
        buildMesh(bands, segments, thicknessSeg, thickness * planet.radius);

        outerRadius = 0;
        for (Band b : bands) {
            outerRadius = Math.max(outerRadius, b.outer * planet.radius);
        }
        outerRadius *= radiusScale;
    }

    // ==================== 预设风格 ====================

    /** 亮蓝环 - 中间厚边缘薄，比例协调版 */
    public static RingMesh techBlue(Planet planet) {
        return techBlue(planet, 1f);
    }

    public static RingMesh techBlue(Planet planet, float scale) {
        Band[] bands = {
                // 1. 内晕 - 紧贴星球
                new Band(1.1f, 1.25f,
                        new Color(0.5f, 0.65f, 0.85f, 0.15f),
                        new Color(0.6f, 0.75f, 0.92f, 0.35f)),

                // 2. C 环 - 暗薄
                new Band(1.25f, 1.55f,
                        new Color(0.55f, 0.7f, 0.9f, 0.4f),
                        new Color(0.65f, 0.78f, 0.95f, 0.6f)),

                // 3. 卡西尼缝 - 明显暗缝
                new Band(1.55f, 1.7f,
                        new Color(0.45f, 0.6f, 0.8f, 0.2f),
                        new Color(0.35f, 0.5f, 0.7f, 0.1f)),

                // 4. B 环内侧 - 渐亮，变厚
                new Band(1.7f, 2.0f,
                        new Color(0.65f, 0.8f, 0.95f, 0.7f),
                        new Color(0.8f, 0.9f, 1f, 0.95f)),

                // 5. B 环核心 - 最亮最厚，主体
                new Band(2.0f, 2.25f,
                        new Color(0.8f, 0.9f, 1f, 0.95f),
                        new Color(0.85f, 0.93f, 1f, 1f)),

                // 6. B 环外侧 - 渐暗，变薄
                new Band(2.25f, 2.45f,
                        new Color(0.85f, 0.93f, 1f, 1f),
                        new Color(0.65f, 0.78f, 0.95f, 0.65f)),

                // 7. A 环 - 中等亮度
                new Band(2.48f, 2.8f,
                        new Color(0.55f, 0.7f, 0.9f, 0.5f),
                        new Color(0.7f, 0.82f, 0.95f, 0.75f)),

                // 8. 外晕 - 渐淡收尾
                new Band(2.8f, 3.0f,
                        new Color(0.6f, 0.75f, 0.9f, 0.55f),
                        new Color(0.45f, 0.6f, 0.8f, 0.05f)),
        };
        RingMesh ring = new RingMesh(planet, bands, 100, 4, 0.08f);
        ring.radiusScale = scale;
        return ring;
    }

    /** 土星金 - 经典土星风格 */
    public static RingMesh saturnGold(Planet planet) {
        return saturnGold(planet, 1f);
    }

    public static RingMesh saturnGold(Planet planet, float scale) {
        Band[] bands = {
                new Band(1.1f, 1.25f,
                        new Color(0.55f, 0.5f, 0.4f, 0.15f),
                        new Color(0.62f, 0.56f, 0.42f, 0.35f)),
                new Band(1.25f, 1.55f,
                        new Color(0.6f, 0.54f, 0.42f, 0.4f),
                        new Color(0.68f, 0.62f, 0.48f, 0.6f)),
                new Band(1.55f, 1.7f,
                        new Color(0.45f, 0.4f, 0.3f, 0.2f),
                        new Color(0.35f, 0.3f, 0.22f, 0.1f)),
                new Band(1.7f, 2.0f,
                        new Color(0.7f, 0.65f, 0.52f, 0.7f),
                        new Color(0.85f, 0.8f, 0.65f, 0.95f)),
                new Band(2.0f, 2.25f,
                        new Color(0.85f, 0.8f, 0.65f, 0.95f),
                        new Color(0.9f, 0.85f, 0.7f, 1f)),
                new Band(2.25f, 2.45f,
                        new Color(0.9f, 0.85f, 0.7f, 1f),
                        new Color(0.7f, 0.65f, 0.52f, 0.65f)),
                new Band(2.48f, 2.8f,
                        new Color(0.6f, 0.55f, 0.42f, 0.5f),
                        new Color(0.75f, 0.7f, 0.55f, 0.75f)),
                new Band(2.8f, 3.0f,
                        new Color(0.65f, 0.6f, 0.48f, 0.55f),
                        new Color(0.5f, 0.45f, 0.35f, 0.05f)),
        };
        RingMesh ring = new RingMesh(planet, bands, 120, 3, 0.07f);
        ring.radiusScale = scale;
        return ring;
    }

    /** 冰石蓝 - 清冷自然 */
    public static RingMesh iceRock(Planet planet) {
        return iceRock(planet, 1f);
    }

    public static RingMesh iceRock(Planet planet, float scale) {
        Band[] bands = {
                new Band(1.1f, 1.25f,
                        new Color(0.5f, 0.6f, 0.75f, 0.15f),
                        new Color(0.55f, 0.65f, 0.8f, 0.3f)),
                new Band(1.25f, 1.55f,
                        new Color(0.55f, 0.65f, 0.8f, 0.35f),
                        new Color(0.6f, 0.7f, 0.85f, 0.5f)),
                new Band(1.55f, 1.7f,
                        new Color(0.45f, 0.55f, 0.7f, 0.2f),
                        new Color(0.4f, 0.5f, 0.65f, 0.1f)),
                new Band(1.7f, 2.0f,
                        new Color(0.7f, 0.82f, 0.95f, 0.75f),
                        new Color(0.9f, 0.96f, 1f, 0.95f)),
                new Band(2.0f, 2.25f,
                        new Color(0.9f, 0.96f, 1f, 0.95f),
                        new Color(0.75f, 0.85f, 0.95f, 0.7f)),
                new Band(2.48f, 2.8f,
                        new Color(0.6f, 0.72f, 0.88f, 0.5f),
                        new Color(0.8f, 0.9f, 1f, 0.85f)),
                new Band(2.8f, 3.0f,
                        new Color(0.65f, 0.75f, 0.9f, 0.55f),
                        new Color(0.5f, 0.6f, 0.75f, 0.05f)),
        };
        RingMesh ring = new RingMesh(planet, bands, 120, 3, 0.06f);
        ring.radiusScale = scale;
        return ring;
    }

    /** 暗灰铁 - 暗黑风格 */
    public static RingMesh darkIron(Planet planet) {
        return darkIron(planet, 1f);
    }

    public static RingMesh darkIron(Planet planet, float scale) {
        Band[] bands = {
                new Band(1.15f, 1.4f,
                        new Color(0.25f, 0.25f, 0.3f, 0.15f),
                        new Color(0.35f, 0.35f, 0.4f, 0.3f)),
                new Band(1.45f, 1.75f,
                        new Color(0.3f, 0.3f, 0.35f, 0.25f),
                        new Color(0.45f, 0.45f, 0.5f, 0.55f)),
                new Band(1.75f, 1.9f,
                        new Color(0.25f, 0.25f, 0.3f, 0.15f),
                        new Color(0.2f, 0.2f, 0.25f, 0.05f)),
                new Band(1.9f, 2.25f,
                        new Color(0.4f, 0.4f, 0.45f, 0.5f),
                        new Color(0.55f, 0.55f, 0.6f, 0.85f)),
                new Band(2.25f, 2.5f,
                        new Color(0.55f, 0.55f, 0.6f, 0.8f),
                        new Color(0.4f, 0.4f, 0.45f, 0.4f)),
                new Band(2.55f, 2.9f,
                        new Color(0.3f, 0.3f, 0.35f, 0.3f),
                        new Color(0.5f, 0.5f, 0.55f, 0.65f)),
                new Band(2.9f, 3.1f,
                        new Color(0.4f, 0.4f, 0.45f, 0.4f),
                        new Color(0.25f, 0.25f, 0.3f, 0.05f)),
        };
        RingMesh ring = new RingMesh(planet, bands, 120, 3, 0.05f);
        ring.radiusScale = scale;
        return ring;
    }

    // ==================== 内部实现 ====================

    private void buildMesh(Band[] bands, int segments, int thicknessSeg, float thickness) {
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

        for (Band band : bands) {
            float innerR = band.inner * planet.radius * radiusScale;
            float outerR = band.outer * planet.radius * radiusScale;

            float[] layerInnerCols = new float[thicknessSeg + 1];
            float[] layerOuterCols = new float[thicknessSeg + 1];

            for (int t = 0; t <= thicknessSeg; t++) {
                float tNorm = (float) t / thicknessSeg;
                float edgeBrightness = 1f - Math.abs(tNorm - 0.5f) * 2f;
                edgeBrightness = Mathf.lerp(0.6f, 1f, edgeBrightness);

                Color ic = band.innerColor.cpy();
                ic.r *= edgeBrightness;
                ic.g *= edgeBrightness;
                ic.b *= edgeBrightness;
                layerInnerCols[t] = ic.toFloatBits();

                Color oc = band.outerColor.cpy();
                oc.r *= edgeBrightness;
                oc.g *= edgeBrightness;
                oc.b *= edgeBrightness;
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
