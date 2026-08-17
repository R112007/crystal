package crystal.type.heaven;

import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;

public class GalaxyStar {
    public final Galaxy galaxy;
    public final int armIndex;
    public final float distance;
    public final float angleOffset;
    public final float orbitPhase;
    public final float size;
    public final Color color = new Color();
    public final boolean inBulge;
    public final float angularSpeed;

    public GalaxyStar(Galaxy galaxy) {
        this.galaxy = galaxy;

        // 距离分布：artistic 中心更密，realistic 更接近真实指数盘
        float distRnd = Mathf.random();
        if (galaxy.style == Galaxy.GalaxyStyle.artistic) {
            this.distance = Mathf.pow(distRnd, 0.5f);
        } else {
            this.distance = Mathf.pow(distRnd, 0.7f);
        }

        this.inBulge = this.distance < galaxy.bulgeRatio;

        if (inBulge) {
            this.armIndex = 0;
            this.angleOffset = Mathf.random(360f);
            this.orbitPhase = Mathf.random(360f);
        } else {
            this.armIndex = Mathf.random(galaxy.arms - 1);

            // 旋臂偏移：用 pow(abs(t), 0.35) 让星星极度集中在旋臂中心线
            float t = Mathf.random() * 2f - 1f;
            float concentrated = Mathf.sign(t) * Mathf.pow(Math.abs(t), 0.35f);
            this.angleOffset = concentrated * galaxy.armSpread * galaxy.armConcentration;

            this.orbitPhase = Mathf.random(360f);
        }

        // 大小：中心更大更亮
        float centerBias = 1f - distance * 0.4f;
        if (galaxy.style == Galaxy.GalaxyStyle.artistic) {
            this.size = Mathf.random(2.0f, 5.0f) * centerBias;
        } else {
            this.size = Mathf.random(1.5f, 4.0f) * centerBias;
        }

        // 差动旋转
        this.angularSpeed = Mathf.random(0.25f, 0.9f)
                * Mathf.pow(1f - distance * 0.75f, 1.5f)
                * galaxy.rotationSpeed;

        initColor();
    }

    void initColor() {
        float armT = Mathf.clamp(Math.abs(angleOffset) / (galaxy.armSpread * galaxy.armConcentration + 0.001f), 0f, 1f);
        float rnd = Mathf.range(0.08f);

        Color base = galaxy.style.getColor(distance, armT, rnd);

        // 亮度：中心更亮，大星星更亮，整体提亮
        float a = Mathf.clamp(0.6f + (1f - distance) * 0.4f + size * 0.06f, 0.45f, 1f);
        color.set(base.r, base.g, base.b, a);
    }

    public float getAngle(float time) {
        if (inBulge) {
            return angleOffset + time * angularSpeed * 6f;
        }
        float baseAngle = armIndex * (360f / galaxy.arms);
        float spiral = distance * galaxy.spiralTightness * 360f;
        float rotation = orbitPhase + time * angularSpeed * 10f;
        return baseAngle + spiral + angleOffset + rotation;
    }

    public float getRadius() {
        return distance * galaxy.radius;
    }

    public Vec3 getPosition(Vec3 out, float time) {
        float angle = getAngle(time);
        float r = getRadius();

        out.set(Angles.trnsx(angle, r), 0f, Angles.trnsy(angle, r));

        float thick = galaxy.thickness * (0.5f + distance * 0.5f);
        if (inBulge) {
            float bulgeY = galaxy.radius * galaxy.thickness * (1.2f + (1f - distance / galaxy.bulgeRatio));
            out.y += Mathf.range(bulgeY);
        } else {
            out.y += Mathf.sin(angle * 2.5f + time * 0.2f + orbitPhase) * thick * r;
        }

        float noise = galaxy.radius * (galaxy.style == Galaxy.GalaxyStyle.artistic ? 0.018f : 0.01f);
        out.x += Mathf.sin(orbitPhase * 2.3f + distance * 7f) * noise;
        out.z += Mathf.cos(orbitPhase * 1.7f + distance * 7f) * noise;

        return out;
    }
}
