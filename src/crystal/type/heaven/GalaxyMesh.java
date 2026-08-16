// GalaxyMesh.java
package crystal.type.heaven;

import arc.graphics.*;
import arc.graphics.g3d.VertexBatch3D;
import arc.graphics.gl.*;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.util.*;
import mindustry.graphics.g3d.PlanetMesh;
import mindustry.graphics.g3d.PlanetParams;

public class GalaxyMesh extends PlanetMesh {
    private final Galaxy galaxy;
    private VertexBatch3D batch;
    private Shader pointShader;
    private Galaxy.GalaxyStyle cachedStyle;

    public GalaxyMesh(Galaxy galaxy) {
        super(galaxy, null, null);
        this.galaxy = galaxy;
    }

    /** 若 style 变化或首次调用，重建 Shader 与 Batch */
    private void ensureInit() {
        if (batch != null && cachedStyle == galaxy.style)
            return;
        if (batch != null) {
            batch.dispose();
        }
        this.cachedStyle = galaxy.style;
        this.pointShader = createPointShader();
        this.batch = new VertexBatch3D(Math.min(galaxy.starCount, 8000), true, true, 0, pointShader);
    }

    private Shader createPointShader() {
        boolean vivid = galaxy.style == Galaxy.GalaxyStyle.vivid;

        String vert = "attribute vec3 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "attribute vec3 a_normal;\n" +
                "uniform mat4 u_proj;\n" +
                "varying vec4 v_col;\n" +
                "varying float v_size;\n" +
                "void main() {\n" +
                "   gl_Position = u_proj * vec4(a_position, 1.0);\n" +
                "   gl_PointSize = " + (vivid ? "2.4" : "2.0") + " + a_normal.x * " + (vivid ? "6.0" : "5.0") + ";\n" +
                "   v_col = a_color;\n" +
                "   v_size = a_normal.x;\n" +
                "}\n";

        String frag = "varying vec4 v_col;\n" +
                "varying float v_size;\n" +
                "void main() {\n" +
                "   vec2 coord = gl_PointCoord - vec2(0.5);\n" +
                "   float dist = length(coord);\n" +
                "   if(dist > 0.5) discard;\n" +
                // 大恒星 glow 更集中锐利，小恒星更柔和
                "   float glow = 1.0 - smoothstep(0.0, 0.5, dist);\n" +
                "   glow = pow(glow, " + (vivid ? "0.8" : "1.0") + " + v_size * " + (vivid ? "0.5" : "0.4") + ");\n" +
                // 核心白点，增强眩光
                "   float core = 1.0 - smoothstep(0.0, 0.08 + v_size * 0.03, dist);\n" +
                "   vec3 rgb = v_col.rgb + vec3(core * " + (vivid ? "0.8" : "0.6") + ");\n" +
                "   gl_FragColor = vec4(rgb, v_col.a * glow);\n" +
                "}\n";

        return new Shader(vert, frag);
    }

    @Override
    public void render(PlanetParams params, Mat3D projection, Mat3D transform) {
        ensureInit();
        batch.proj(projection);
        float time = Time.globalTime;
        int count = Math.min(galaxy.stars.size, batch.getMaxVertices());
        boolean vivid = galaxy.style == Galaxy.GalaxyStyle.vivid;

        for (int i = 0; i < count; i++) {
            GalaxyStar star = galaxy.stars.get(i);
            Vec3 pos = star.getPosition(Tmp.v31, time);
            Mat3D.prj(pos, transform);

            float boost;
            if (star.starType == 0)
                boost = vivid ? 1.3f : 1.2f;
            else if (star.starType == 1)
                boost = vivid ? 1.15f : 1.05f;
            else if (star.starType == 3)
                boost = vivid ? 0.6f : 0.5f;
            else
                boost = 0.8f;

            Tmp.c1.set(star.color).mul(boost, boost, boost, 1f);
            batch.color(Tmp.c1);
            batch.normal(Mathf.clamp(star.size / 6f, 0f, 1f), 0, 0);
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
            pointShader = null;
        }
    }
}
