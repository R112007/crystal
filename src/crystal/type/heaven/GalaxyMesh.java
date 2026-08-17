package crystal.type.heaven;

import arc.graphics.*;
import arc.graphics.g3d.VertexBatch3D;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.graphics.g3d.PlanetMesh;
import mindustry.graphics.g3d.PlanetParams;

public class GalaxyMesh extends PlanetMesh {
    private final Galaxy galaxy;
    private VertexBatch3D batch;
    private Shader pointShader;

    public GalaxyMesh(Galaxy galaxy) {
        super(galaxy, null, null);
        this.galaxy = galaxy;
    }

    private void ensureInit() {
        if (batch != null) return;
        this.pointShader = createPointShader();
        this.batch = new VertexBatch3D(Math.min(galaxy.starCount, 12000), false, true, 0, pointShader);
    }

    private static Shader createPointShader() {
        String vert = "attribute vec3 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "uniform mat4 u_proj;\n" +
                "varying vec4 v_col;\n" +
                "varying float v_size;\n" +
                "void main() {\n" +
                "   gl_Position = u_proj * vec4(a_position, 1.0);\n" +
                "   // 点大小 3~13 像素，大星星更大\n" +
                "   gl_PointSize = 3.0 + a_color.a * 10.0;\n" +
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
                "   // 大星星中心更锐利、整体更亮\n" +
                "   glow = pow(glow, 1.0 + v_size * 0.8);\n" +
                "   // 额外提亮，避免 additive 下太暗\n" +
                "   glow *= 1.2;\n" +
                "   gl_FragColor = vec4(v_col.rgb, glow);\n" +
                "}\n";

        return new Shader(vert, frag);
    }

    @Override
    public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
        ensureInit();
        batch.proj(projection);
        float time = Time.globalTime;

        int count = Math.min(galaxy.stars.size, batch.getMaxVertices());

        for (int i = 0; i < count; i++) {
            GalaxyStar star = galaxy.stars.get(i);
            Vec3 pos = star.getPosition(Tmp.v31, time);
            Mat3D.prj(pos, transform);

            // 亮度：中心更亮，大星星更亮，整体提亮 30%
            float centerGlow = 1f + (1f - star.distance) * 0.8f;
            float brightness = (0.9f + star.size * 0.1f) * centerGlow * 1.3f;
            Tmp.c1.set(star.color).mul(brightness, brightness, brightness, 1f);

            // alpha 通道编码点大小 [0,1]
            float sizeNorm = Mathf.clamp((star.size - 1.0f) / 5.0f, 0f, 1f);

            batch.color(Tmp.c1.r, Tmp.c1.g, Tmp.c1.b, sizeNorm);
            batch.vertex(pos.x, pos.y, pos.z);
        }

        Gl.enable(Gl.blend);
        Gl.blendFunc(Gl.srcAlpha, Gl.one);
        batch.flush(Gl.points);
        Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);
    }

    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
    }
}
