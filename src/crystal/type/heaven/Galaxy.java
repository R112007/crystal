// Galaxy.java
package crystal.type.heaven;

import arc.math.*;
import arc.struct.*;
import mindustry.graphics.g3d.*;
import mindustry.type.Planet;
import static mindustry.Vars.*;

public class Galaxy extends Planet {
    /** 银河风格枚举 */
    public enum GalaxyStyle {
        vivid, // 图1：鲜艳艺术风，橙红中心+蓝色外围，强对比
        natural // 图2：写实自然风，黄白核球+蓝绿旋臂
    }

    public GalaxyStyle style = GalaxyStyle.natural;

    public int arms = 4;
    public float armSpread = 10f;
    public float spiralTightness = 2.2f;
    public float thickness = 0.032f;
    public float rotationSpeed = 0.8f;
    public int starCount = 4000;

    public Seq<GalaxyStar> stars = new Seq<>();

    public Galaxy(String name, Planet parent, float radius) {
        this(name, parent, radius, GalaxyStyle.natural);
    }

    public Galaxy(String name, Planet parent, float radius, GalaxyStyle style) {
        super(name, parent, radius);
        this.style = style;
        applyStyle();

        this.accessible = false;
        this.hasAtmosphere = false;
        this.drawOrbit = false;
        this.bloom = true;
        this.clipRadius = radius * 1.5f;
        this.meshLoader = () -> new GalaxyMesh(this);

        initStars();
    }

    /** 根据风格调整全局参数 */
    void applyStyle() {
        if (style == GalaxyStyle.vivid) {
            arms = 4;
            armSpread = 12f;
            spiralTightness = 2.0f;
            thickness = 0.038f;
            rotationSpeed = 0.75f;
            starCount = 4500;
        } else {
            arms = 4;
            armSpread = 9f;
            spiralTightness = 2.4f;
            thickness = 0.028f;
            rotationSpeed = 0.85f;
            starCount = 4000;
        }
    }

    /** 运行时切换风格（会重建 mesh） */
    public void setStyle(GalaxyStyle newStyle) {
        if (this.style == newStyle)
            return;
        this.style = newStyle;
        applyStyle();
        initStars();
        // 重建 mesh，让 Shader 也更新
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (!headless) {
            mesh = meshLoader.get();
        }
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
