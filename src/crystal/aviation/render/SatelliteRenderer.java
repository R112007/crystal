package crystal.aviation.render;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g3d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;

import crystal.aviation.*;

import static mindustry.Vars.*;

/**
 * 负责在星球/宇宙界面渲染轨道上的3D卫星。
 * 包含更精细的几何体：圆柱主体、可旋转太阳能板、天线、推进器、轨道环。
 */
public class SatelliteRenderer{
    private static final Color bodyLight = Color.valueOf("e8e8e8");
    private static final Color bodyMid = Color.valueOf("b0b0b0");
    private static final Color bodyDark = Color.valueOf("707070");
    private static final Color panelBase = Color.valueOf("1a237e");
    private static final Color panelGrid = Color.valueOf("3949ab");
    private static final Color gold = Color.valueOf("ffb300");
    private static final Color glow = Color.valueOf("00e5ff");
    private static final Color thrust = Color.valueOf("ff5722");
    private static final Color dock = Color.valueOf("ff9100");

    private static final Vec3 tmp = new Vec3();
    private static final Vec3 tmp2 = new Vec3();
    private static final Vec3 a = new Vec3();
    private static final Vec3 b = new Vec3();
    private static final Vec3 c = new Vec3();
    private static final Vec3 d = new Vec3();
    private static final Vec3 e = new Vec3();
    private static final Vec3 f = new Vec3();
    private static final Vec3 g = new Vec3();
    private static final Vec3 h = new Vec3();
    private static final Vec3 p1 = new Vec3();
    private static final Vec3 p2 = new Vec3();
    private static final Vec3 p3 = new Vec3();
    private static final Vec3 p4 = new Vec3();

    public static void init(){
        Events.run(Trigger.universeDrawEnd, SatelliteRenderer::render);
    }

    public static void render(){
        if(renderer == null || renderer.planets == null) return;

        SatelliteManager.update();

        VertexBatch3D batch = renderer.planets.batch;
        Camera3D cam = renderer.planets.cam;
        batch.proj(cam.combined);

        for(Satellite s : SatelliteManager.satellites.values()){
            if(!s.isDockMaster()) continue;
            // 若卫星位于星球背面（从相机看被星球本体遮挡），则跳过模型渲染
            if(isOccludedByPlanet(s, cam)) continue;
            drawSatellite(batch, s);
            drawDockedSatellites(batch, s);
            drawOrbitRing(batch, s);
        }

        batch.flush(Gl.triangles);
    }

    /** 判断卫星是否被其所属星球本体遮挡。 */
    static boolean isOccludedByPlanet(Satellite s, Camera3D cam){
        if(s.planet == null) return false;
        Vec3 camPos = cam.position;
        Vec3 planetPos = s.planet.position;
        Vec3 satPos = tmp.set(s.renderX, s.renderY, s.renderZ);

        Vec3 camToSat = new Vec3(satPos).sub(camPos);
        Vec3 camToPlanet = new Vec3(planetPos).sub(camPos);
        float satDist2 = camToSat.len2();
        float planetDist2 = camToPlanet.len2();

        // 相机在星球内部时不做遮挡
        if(planetDist2 <= s.planet.radius * s.planet.radius) return false;

        // 将星球中心投影到相机->卫星的连线上
        float t = camToPlanet.dot(camToSat) / satDist2;
        if(t <= 0f || t >= 1f) return false;

        Vec3 closest = new Vec3(camToSat).scl(t).add(camPos);
        return closest.dst2(planetPos) < s.planet.radius * s.planet.radius;
    }

    static void drawSatellite(VertexBatch3D batch, Satellite s){
        Vec3 pos = tmp.set(s.renderX, s.renderY, s.renderZ);
        float scale = 0.08f * s.visualScale * s.planet.radius;
        float spin = s.spinAngle;
        int model = Mathf.clamp(s.tier, 1, 3);

        switch(model){
            case 1 -> drawModel1(batch, pos, scale, spin, s);
            case 2 -> drawModel2(batch, pos, scale, spin, s);
            default -> drawModel3(batch, pos, scale, spin, s);
        }
    }

    /** 基础卫星模型：简单核心 + 四片太阳能板 */
    static void drawModel1(VertexBatch3D batch, Vec3 pos, float scale, float spin, Satellite s){
        // 主体：中央圆柱形核心舱（近似八棱柱）
        drawOctPrism(batch, pos, scale * 0.55f, scale * 1.6f, bodyMid, bodyLight, bodyDark);

        // 中部连接环
        tmp2.set(pos).add(0, scale * 0.35f, 0);
        drawOctPrism(batch, tmp2, scale * 0.62f, scale * 0.25f, gold, gold, gold);
        tmp2.set(pos).add(0, -scale * 0.35f, 0);
        drawOctPrism(batch, tmp2, scale * 0.62f, scale * 0.25f, gold, gold, gold);

        // 可旋转太阳能板阵列：绕 Y 轴旋转，呈 X 形分布
        float wingSpan = scale * 2.6f;
        float wingW = scale * 1.0f;
        float wingH = scale * 0.04f;
        float cos = Mathf.cos(spin);
        float sin = Mathf.sin(spin);

        for(int i = 0; i < 4; i++){
            float baseAngle = i * Mathf.PI / 2f;
            float bx = Mathf.cos(baseAngle) * scale * 0.85f;
            float bz = Mathf.sin(baseAngle) * scale * 0.85f;
            float dx = cos * wingSpan;
            float dz = sin * wingSpan;
            Vec3 base = tmp2.set(pos).add(bx, 0, bz);
            Vec3 end = tmp.set(base).add(dx, 0, dz);
            drawPanel(batch, base, end, wingW, wingH, (i % 2 == 0) ? panelBase : panelGrid);
        }

        // 顶部通讯天线
        tmp2.set(pos).add(0, scale * 0.95f, 0);
        drawDish(batch, tmp2, scale * 0.55f, glow);

        // 底部推进器发光
        tmp2.set(pos).add(0, -scale * 0.85f, 0);
        drawBox(batch, tmp2, scale * 0.25f, scale * 0.35f, scale * 0.25f, bodyDark);
        tmp2.add(0, -scale * 0.22f, 0);
        drawGlow(batch, tmp2, scale * 0.18f, thrust);

        // 对接桁架
        if(s.dockedSatellites.size > 0){
            tmp2.set(pos).add(0, 0, scale * 0.9f);
            drawBox(batch, tmp2, scale * 0.35f, scale * 0.35f, scale * 0.6f, dock);
        }
    }

    /** 进阶卫星模型：更长的核心、八片太阳能板、顶部双天线 */
    static void drawModel2(VertexBatch3D batch, Vec3 pos, float scale, float spin, Satellite s){
        // 主体更长
        drawOctPrism(batch, pos, scale * 0.65f, scale * 2.2f, bodyMid, bodyLight, bodyDark);

        // 上下两圈金色环
        for(float sign : new float[]{1f, -1f}){
            tmp2.set(pos).add(0, sign * scale * 0.75f, 0);
            drawOctPrism(batch, tmp2, scale * 0.72f, scale * 0.28f, gold, gold, gold);
        }

        // 八片太阳能板（两圈各四片，反向旋转）
        for(int ring = 0; ring < 2; ring++){
            float wingSpan = scale * (3.2f + ring * 0.6f);
            float wingW = scale * 0.9f;
            float wingH = scale * 0.04f;
            float dir = ring == 0 ? 1f : -1f;
            float cos = Mathf.cos(spin * dir);
            float sin = Mathf.sin(spin * dir);
            float yOff = (ring == 0 ? 1f : -1f) * scale * 0.5f;

            for(int i = 0; i < 4; i++){
                float baseAngle = i * Mathf.PI / 2f;
                float bx = Mathf.cos(baseAngle) * scale * 0.85f;
                float bz = Mathf.sin(baseAngle) * scale * 0.85f;
                float dx = cos * wingSpan;
                float dz = sin * wingSpan;
                Vec3 base = tmp2.set(pos).add(bx, yOff, bz);
                Vec3 end = tmp.set(base).add(dx, yOff, dz);
                Color col = (i % 2 == 0) ? panelBase : panelGrid;
                drawPanel(batch, base, end, wingW, wingH, col.cpy().mul(1f - ring * 0.15f));
            }
        }

        // 顶部双天线
        tmp2.set(pos).add(-scale * 0.35f, scale * 1.25f, 0);
        drawDish(batch, tmp2, scale * 0.4f, glow);
        tmp2.set(pos).add(scale * 0.35f, scale * 1.25f, 0);
        drawDish(batch, tmp2, scale * 0.4f, glow);

        // 底部推进器
        tmp2.set(pos).add(0, -scale * 1.2f, 0);
        drawBox(batch, tmp2, scale * 0.3f, scale * 0.4f, scale * 0.3f, bodyDark);
        tmp2.add(0, -scale * 0.25f, 0);
        drawGlow(batch, tmp2, scale * 0.22f, thrust);

        if(s.dockedSatellites.size > 0){
            tmp2.set(pos).add(0, 0, scale * 1.1f);
            drawBox(batch, tmp2, scale * 0.45f, scale * 0.45f, scale * 0.75f, dock);
        }
    }

    /** 高级卫星模型：球形核心、大型环状天线、多组太阳能翼 */
    static void drawModel3(VertexBatch3D batch, Vec3 pos, float scale, float spin, Satellite s){
        // 球形核心：用两个垂直交叉的八棱柱近似
        drawOctPrism(batch, pos, scale * 0.75f, scale * 1.6f, bodyMid, bodyLight, bodyDark);
        tmp2.set(pos);
        drawOctPrism(batch, tmp2, scale * 0.75f, scale * 1.6f, bodyLight, bodyMid, bodyDark);

        // 环状天线（水平大环）
        int seg = 32;
        float ringR = scale * 1.6f;
        float tube = scale * 0.06f;
        Color ringColor = glow.cpy().a(0.9f);
        for(int i = 0; i < seg; i++){
            float a0 = i * Mathf.PI2 / seg;
            float a1 = (i + 1) * Mathf.PI2 / seg;
            p1.set(pos.x + Mathf.cos(a0) * ringR, pos.y + scale * 0.3f, pos.z + Mathf.sin(a0) * ringR);
            p2.set(pos.x + Mathf.cos(a1) * ringR, pos.y + scale * 0.3f, pos.z + Mathf.sin(a1) * ringR);
            p3.set(pos.x + Mathf.cos(a1) * (ringR + tube), pos.y + scale * 0.3f, pos.z + Mathf.sin(a1) * (ringR + tube));
            p4.set(pos.x + Mathf.cos(a0) * (ringR + tube), pos.y + scale * 0.3f, pos.z + Mathf.sin(a0) * (ringR + tube));
            batch.quad(p1, p2, p3, p4, ringColor);
        }

        // 四组大型太阳能翼，沿 Z 轴与 X 轴展开
        float wingSpan = scale * 3.6f;
        float wingW = scale * 1.1f;
        float wingH = scale * 0.05f;
        float cos = Mathf.cos(spin);
        float sin = Mathf.sin(spin);

        for(int i = 0; i < 4; i++){
            float baseAngle = i * Mathf.PI / 2f;
            float bx = Mathf.cos(baseAngle) * scale * 1.0f;
            float bz = Mathf.sin(baseAngle) * scale * 1.0f;
            float dx = cos * wingSpan;
            float dz = sin * wingSpan;
            Vec3 base = tmp2.set(pos).add(bx, 0, bz);
            Vec3 end = tmp.set(base).add(dx, 0, dz);
            drawPanel(batch, base, end, wingW, wingH, (i % 2 == 0) ? panelBase : panelGrid);
        }

        // 顶部大型通讯天线阵列
        for(int i = 0; i < 3; i++){
            float ang = i * Mathf.PI2 / 3f + spin * 0.5f;
            tmp2.set(pos).add(Mathf.cos(ang) * scale * 0.5f, scale * 1.1f, Mathf.sin(ang) * scale * 0.5f);
            drawDish(batch, tmp2, scale * 0.35f, glow);
        }

        // 底部推进器阵列
        for(int i = 0; i < 4; i++){
            float ang = i * Mathf.PI2 / 4f;
            tmp2.set(pos).add(Mathf.cos(ang) * scale * 0.45f, -scale * 0.95f, Mathf.sin(ang) * scale * 0.45f);
            drawGlow(batch, tmp2, scale * 0.12f, thrust);
        }

        if(s.dockedSatellites.size > 0){
            tmp2.set(pos).add(0, 0, scale * 1.3f);
            drawBox(batch, tmp2, scale * 0.55f, scale * 0.55f, scale * 0.9f, dock);
        }
    }

    static void drawDockedSatellites(VertexBatch3D batch, Satellite master){
        Vec3 base = a.set(master.renderX, master.renderY, master.renderZ);
        float scale = 0.08f * master.visualScale * master.planet.radius;
        int n = master.dockedSatellites.size;
        int idx = 0;
        for(int dockId : master.dockedSatellites.items){
            Satellite slave = SatelliteManager.get(dockId);
            if(slave == null) continue;

            float angle = idx * Mathf.PI * 2f / Math.max(n, 1);
            float rad = scale * 2.8f;
            float ox = Mathf.cos(angle) * rad;
            float oy = Mathf.sin(angle * 1.3f) * scale * 0.35f;
            float oz = Mathf.sin(angle) * rad;
            Vec3 spos = b.set(base).add(ox, oy, oz);

            // 连接臂
            drawConnector(batch, base, spos, scale * 0.08f, bodyDark);

            // 从属卫星模型（较小）
            drawOctPrism(batch, spos, scale * 0.35f, scale * 0.9f, bodyMid, bodyLight, bodyDark);

            // 小太阳能板
            tmp.set(spos).add(-scale * 0.9f, 0, 0);
            tmp2.set(spos).add(scale * 0.9f, 0, 0);
            drawBox(batch, tmp, scale * 0.9f, scale * 0.04f, scale * 0.45f, panelBase);
            drawBox(batch, tmp2, scale * 0.9f, scale * 0.04f, scale * 0.45f, panelGrid);

            // 小天线
            tmp.set(spos).add(0, scale * 0.55f, 0);
            drawDish(batch, tmp, scale * 0.25f, glow);
            idx++;
        }
    }

    static void drawOrbitRing(VertexBatch3D batch, Satellite s){
        if(s.planet == null) return;
        float r = s.planet.radius * s.orbitRadius;
        int segments = 64;
        float tilt = s.orbitTilt;
        Color ring = glow.cpy().a(0.25f);
        for(int i = 0; i < segments; i++){
            float a1 = (i / (float)segments) * Mathf.PI2;
            float a2 = ((i + 1) / (float)segments) * Mathf.PI2;
            p1.set(s.planet.position).add(Mathf.cos(a1) * r, Mathf.sin(a1) * r * Mathf.cos(tilt), Mathf.sin(a1) * r * Mathf.sin(tilt));
            p2.set(s.planet.position).add(Mathf.cos(a2) * r, Mathf.sin(a2) * r * Mathf.cos(tilt), Mathf.sin(a2) * r * Mathf.sin(tilt));
            // 使用极细四边形模拟线
            Vec3 dir = tmp.set(p2).sub(p1).nor().crs(tmp2.set(0, 1, 0)).nor().scl(r * 0.003f);
            p3.set(p2).add(dir);
            p4.set(p1).add(dir);
            batch.quad(p1, p2, p3, p4, ring);
        }
    }

    /** 绘制连接两个点的圆柱臂 */
    static void drawConnector(VertexBatch3D batch, Vec3 from, Vec3 to, float thickness, Color color){
        Vec3 dir = new Vec3(to).sub(from);
        float len = dir.len();
        if(len < 0.001f) return;
        dir.nor();

        // 构造垂直于 dir 的两个正交基
        Vec3 up = new Vec3(0, 1, 0);
        if(Math.abs(dir.dot(up)) > 0.95f) up.set(1, 0, 0);
        Vec3 side1 = new Vec3(dir).crs(up).nor().scl(thickness);
        Vec3 side2 = new Vec3(dir).crs(side1).nor().scl(thickness);

        Vec3 q1 = new Vec3(from).add(side1);
        Vec3 q2 = new Vec3(from).add(side2);
        Vec3 q3 = new Vec3(from).sub(side1);
        Vec3 q4 = new Vec3(from).sub(side2);
        Vec3 d05 = new Vec3(dir).scl(len * 0.05f);
        q1.add(d05);
        q2.add(d05);
        q3.add(d05);
        q4.add(d05);

        Vec3 r1 = new Vec3(to).add(side1).sub(d05);
        Vec3 r2 = new Vec3(to).add(side2).sub(d05);
        Vec3 r3 = new Vec3(to).sub(side1).sub(d05);
        Vec3 r4 = new Vec3(to).sub(side2).sub(d05);

        // 四个侧面
        batch.quad(q1, r1, r2, q2, color);
        batch.quad(q2, r2, r3, q3, color);
        batch.quad(q3, r3, r4, q4, color);
        batch.quad(q4, r4, r1, q1, color);
    }

    /** 绘制近似八棱柱，沿 Y 轴 */
    static void drawOctPrism(VertexBatch3D batch, Vec3 center, float radius, float height, Color side, Color top, Color bottom){
        int n = 8;
        float halfH = height / 2f;
        Vec3[] topVerts = new Vec3[n];
        Vec3[] bottomVerts = new Vec3[n];
        for(int i = 0; i < n; i++){
            float angle = i * Mathf.PI2 / n;
            float x = Mathf.cos(angle) * radius;
            float z = Mathf.sin(angle) * radius;
            topVerts[i] = new Vec3(center.x + x, center.y + halfH, center.z + z);
            bottomVerts[i] = new Vec3(center.x + x, center.y - halfH, center.z + z);
        }

        // 侧面
        for(int i = 0; i < n; i++){
            int j = (i + 1) % n;
            // 根据朝向给侧面不同亮度，制造光影感
            Color sideColor = side.cpy().mul(0.85f + 0.15f * Mathf.cos(i * Mathf.PI2 / n));
            batch.quad(bottomVerts[i], bottomVerts[j], topVerts[j], topVerts[i], sideColor);
        }

        // 顶面
        for(int i = 1; i < n - 1; i++){
            batch.tri(topVerts[0], topVerts[i], topVerts[i + 1], top);
        }
        // 底面
        for(int i = 1; i < n - 1; i++){
            batch.tri(bottomVerts[0], bottomVerts[i + 1], bottomVerts[i], bottom);
        }
    }

    /** 绘制旋转太阳能板：base -> end 为板中心线，w/h 为板宽/厚 */
    static void drawPanel(VertexBatch3D batch, Vec3 base, Vec3 end, float width, float height, Color color){
        Vec3 dir = new Vec3(end).sub(base);
        float len = dir.len();
        if(len < 0.001f) return;
        dir.nor();
        Vec3 up = new Vec3(0, 1, 0);
        if(Math.abs(dir.dot(up)) > 0.99f) up.set(1, 0, 0);
        Vec3 side = new Vec3(dir).crs(up).nor().scl(width / 2f);
        Vec3 thick = new Vec3(dir).crs(side).nor().scl(height / 2f);

        Vec3 q1 = new Vec3(base).add(side).add(thick);
        Vec3 q2 = new Vec3(base).sub(side).add(thick);
        Vec3 q3 = new Vec3(end).sub(side).add(thick);
        Vec3 q4 = new Vec3(end).add(side).add(thick);
        Vec3 q5 = new Vec3(base).add(side).sub(thick);
        Vec3 q6 = new Vec3(base).sub(side).sub(thick);
        Vec3 q7 = new Vec3(end).sub(side).sub(thick);
        Vec3 q8 = new Vec3(end).add(side).sub(thick);

        batch.quad(q1, q4, q3, q2, color); // 顶面
        batch.quad(q5, q6, q7, q8, color.cpy().mul(0.7f)); // 底面
        batch.quad(q1, q2, q6, q5, color.cpy().mul(0.85f));
        batch.quad(q2, q3, q7, q6, color.cpy().mul(0.9f));
        batch.quad(q3, q4, q8, q7, color.cpy().mul(0.85f));
        batch.quad(q4, q1, q5, q8, color.cpy().mul(0.9f));
    }

    /** 绘制碟形天线 */
    static void drawDish(VertexBatch3D batch, Vec3 center, float radius, Color color){
        // 支柱
        tmp.set(center).sub(0, radius * 0.6f, 0);
        drawBox(batch, tmp, radius * 0.08f, radius * 0.6f, radius * 0.08f, bodyDark);

        // 碟面：几个同心梯形
        int seg = 8;
        for(int ring = 0; ring < 3; ring++){
            float r0 = radius * (ring * 0.33f);
            float r1 = radius * ((ring + 1) * 0.33f);
            float y0 = -ring * radius * 0.08f;
            float y1 = -(ring + 1) * radius * 0.08f;
            for(int i = 0; i < seg; i++){
                float a0 = i * Mathf.PI2 / seg;
                float a1 = (i + 1) * Mathf.PI2 / seg;
                p1.set(center.x + Mathf.cos(a0) * r0, center.y + y0, center.z + Mathf.sin(a0) * r0);
                p2.set(center.x + Mathf.cos(a1) * r0, center.y + y0, center.z + Mathf.sin(a1) * r0);
                p3.set(center.x + Mathf.cos(a1) * r1, center.y + y1, center.z + Mathf.sin(a1) * r1);
                p4.set(center.x + Mathf.cos(a0) * r1, center.y + y1, center.z + Mathf.sin(a0) * r1);
                batch.quad(p1, p2, p3, p4, color.cpy().mul(1f - ring * 0.1f));
            }
        }
    }

    /** 绘制发光球体（推进器） */
    static void drawGlow(VertexBatch3D batch, Vec3 center, float radius, Color color){
        int seg = 12;
        for(int i = 0; i < seg; i++){
            float a0 = i * Mathf.PI2 / seg;
            float a1 = (i + 1) * Mathf.PI2 / seg;
            p1.set(center);
            p2.set(center.x + Mathf.cos(a0) * radius, center.y, center.z + Mathf.sin(a0) * radius);
            p3.set(center.x + Mathf.cos(a1) * radius, center.y, center.z + Mathf.sin(a1) * radius);
            batch.tri(p1, p2, p3, color);
            p2.add(0, -radius * 0.4f, 0);
            p3.add(0, -radius * 0.4f, 0);
            batch.tri(p1, p3, p2, color.cpy().a(color.a * 0.5f));
        }
    }

    /** 在指定世界坐标绘制一个轴对齐盒子 */
    static void drawBox(VertexBatch3D batch, Vec3 center, float width, float height, float depth, Color color){
        float x = center.x, y = center.y, z = center.z;
        float hw = width / 2f, hh = height / 2f, hd = depth / 2f;

        a.set(x - hw, y - hh, z - hd);
        b.set(x + hw, y - hh, z - hd);
        c.set(x + hw, y + hh, z - hd);
        d.set(x - hw, y + hh, z - hd);
        e.set(x - hw, y - hh, z + hd);
        f.set(x + hw, y - hh, z + hd);
        g.set(x + hw, y + hh, z + hd);
        h.set(x - hw, y + hh, z + hd);

        batch.quad(a, b, c, d, color);
        batch.quad(e, h, g, f, color);
        batch.quad(d, h, e, a, color);
        batch.quad(b, f, g, c, color);
        batch.quad(d, c, g, h, color);
        batch.quad(a, e, f, b, color);
    }
}
