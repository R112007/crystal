package crystal.graphics;

import arc.Core;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import mindustry.Vars;

/**
 * Schwarzschild 黑洞着色器封装。
 * 从 Mindustry 效果缓冲区 (renderer.effectBuffer) 采样做引力透镜、吸积盘。
 */
public class BlackHoleShader extends Shader {
    public Vec2 center = new Vec2(0.5f, 0.5f);
    public float time;

    public float holeRadius = 0.025f;
    public float exposure = 1.4f;
    public float diskTemp = 5500f;
    public float diskIncl = 1.5f;
    public float diskRoll = 0.35f;
    public float diskInner = 1.8f;
    public float diskOuter = 8f;
    public float diskOpac = 0.9f;
    public float diskDopp = 0.6f;
    public float diskBeam = 2.5f;
    public float diskGain = 2.2f;
    public float diskContr = 1.6f;
    public float diskWind = 7f;
    public float diskSpeed = 5f;
    public float diskStar = 1f;

    public BlackHoleShader() {
        super(Vars.tree.get("shaders/blackhole.vert"), Vars.tree.get("shaders/blackhole.frag"));
    }

    @Override
    public void apply() {
        setUniformf("u_resolution", Core.graphics.getWidth(), Core.graphics.getHeight());
        setUniformf("u_time", time);
        setUniformf("u_center", center);
        setUniformf("u_holeRadius", holeRadius);
        setUniformf("u_exposure", exposure);

        setUniformf("u_diskTemp", diskTemp);
        setUniformf("u_diskIncl", diskIncl);
        setUniformf("u_diskRoll", diskRoll);
        setUniformf("u_diskInner", diskInner);
        setUniformf("u_diskOuter", diskOuter);
        setUniformf("u_diskOpac", diskOpac);
        setUniformf("u_diskDopp", diskDopp);
        setUniformf("u_diskBeam", diskBeam);
        setUniformf("u_diskGain", diskGain);
        setUniformf("u_diskContr", diskContr);
        setUniformf("u_diskWind", diskWind);
        setUniformf("u_diskSpeed", diskSpeed);
        setUniformf("u_diskStar", diskStar);
    }
}
