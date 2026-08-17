package crystal.type.heaven;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import mindustry.graphics.g3d.*;
import mindustry.type.Planet;
import static mindustry.Vars.*;

/**
 * 银河系星球类型。支持两种视觉风格切换。
 */
public class Galaxy extends Planet {

    public enum GalaxyStyle {
        /** 艺术风格：高饱和、4条清晰旋臂、蓬松发光感（对应图片1） */
        artistic {
            @Override
            public void configure(Galaxy g) {
                g.arms = 4;
                g.armSpread = 22f; // 旋臂更窄，更清晰
                g.spiralTightness = 2.2f; // 缠绕更紧
                g.thickness = 0.08f;
                g.rotationSpeed = 0.6f;
                g.starCount = 8000; // 更多星星
                g.bulgeRatio = 0.15f;
                g.armConcentration = 0.55f; // 更向中心线集中
            }

            @Override
            public Color getColor(float dist, float armT, float rnd) {
                Color[] palette = {
                        Color.valueOf("ffdd44"), // 0.00 金黄
                        Color.valueOf("ff8800"), // 0.15 亮橙
                        Color.valueOf("ff3300"), // 0.30 橙红
                        Color.valueOf("cc1144"), // 0.45 深红
                        Color.valueOf("ff33aa"), // 0.60 亮粉
                        Color.valueOf("9933ff"), // 0.75 紫
                        Color.valueOf("2211aa"), // 1.00 深蓝
                };
                return lerpPalette(palette, dist, armT, rnd, 0.1f);
            }
        },

        /** 写实风格：自然色调、3条清晰旋臂、粉星云点缀（对应图片2） */
        realistic {
            @Override
            public void configure(Galaxy g) {
                g.arms = 3;
                g.armSpread = 18f;
                g.spiralTightness = 1.6f;
                g.thickness = 0.045f;
                g.rotationSpeed = 0.9f;
                g.starCount = 6000;
                g.bulgeRatio = 0.12f;
                g.armConcentration = 0.5f;
            }

            @Override
            public Color getColor(float dist, float armT, float rnd) {
                Color[] palette = {
                        Color.valueOf("fff8aa"), // 0.00 黄白
                        Color.valueOf("e8d060"), // 0.18 淡黄
                        Color.valueOf("55cccc"), // 0.35 青绿
                        Color.valueOf("33aa99"), // 0.50 海绿
                        Color.valueOf("5588bb"), // 0.70 蓝
                        Color.valueOf("334466"), // 0.85 暗蓝灰
                        Color.valueOf("111155"), // 1.00 深蓝
                };
                Color c = lerpPalette(palette, dist, armT, rnd, 0.06f);

                // 粉星云点缀（5%概率，外半星系）
                if (dist > 0.35f && Mathf.random() < 0.05f) {
                    c.lerp(Color.valueOf("ff66aa"), 0.55f + rnd * 0.25f);
                }
                // 尘埃带：旋臂边缘更暗
                if (armT > 0.55f && dist > 0.2f) {
                    c.mul(0.65f, 0.7f, 0.75f, 1f);
                }
                return c;
            }
        };

        public abstract void configure(Galaxy g);

        public abstract Color getColor(float dist, float armT, float rnd);

        protected static Color lerpPalette(Color[] palette, float t, float armT, float rnd, float rndStrength) {
            int n = palette.length;
            float scaled = Mathf.clamp(t * (n - 1), 0, n - 1);
            int idx = (int) scaled;
            int next = Math.min(idx + 1, n - 1);
            float frac = scaled - idx;

            Color base = palette[idx].cpy().lerp(palette[next], frac);
            base.r += rnd * rndStrength;
            base.g += rnd * rndStrength;
            base.b += rnd * rndStrength;
            base.clamp();

            // 旋臂中心更亮
            float boost = 1f + (1f - armT) * 0.5f;
            base.mul(boost, boost, boost, 1f);
            return base;
        }
    }

    public GalaxyStyle style = GalaxyStyle.artistic;

    public int arms = 4;
    public float armSpread = 22f;
    public float spiralTightness = 2.0f;
    public float thickness = 0.06f;
    public float rotationSpeed = 1f;
    public int starCount = 3000;
    public float bulgeRatio = 0.15f;
    public float armConcentration = 0.5f;

    public Seq<GalaxyStar> stars = new Seq<>();

    public Galaxy(String name, Planet parent, float radius) {
        super(name, parent, radius);
        this.accessible = false;
        this.hasAtmosphere = false;
        this.drawOrbit = false;
        this.bloom = true;
        this.clipRadius = radius * 1.5f;
        this.meshLoader = () -> new GalaxyMesh(this);
        style.configure(this);
        initStars();
    }

    public void setStyle(GalaxyStyle newStyle) {
        this.style = newStyle;
        newStyle.configure(this);
        initStars();
    }

    void initStars() {
        stars.clear();
        stars.ensureCapacity(starCount);
        for (int i = 0; i < starCount; i++) {
            stars.add(new GalaxyStar(this));
        }
    }

    @Override
    public void load() {
        super.load();
        if (!headless && mesh == null) {
            mesh = meshLoader.get();
        }
    }
}
