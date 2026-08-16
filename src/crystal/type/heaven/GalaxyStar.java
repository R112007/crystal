// GalaxyStar.java
package crystal.type.heaven;

import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;

public class GalaxyStar {
    public final Galaxy galaxy;
    /** 0=核球, 1=旋臂, 2=弥散背景, 3=尘埃带 */
    public final int starType;
    public final int armIndex;
    public final float distance;
    public final float angleOffset;
    public final float orbitPhase;
    public final float size;
    public final Color color = new Color();
    public final float angularSpeed;

    public GalaxyStar(Galaxy galaxy) {
        this.galaxy = galaxy;
        boolean vivid = galaxy.style == Galaxy.GalaxyStyle.vivid;

        float roll = Mathf.random();
        if (roll < (vivid ? 0.30f : 0.35f)) {
            // 核球：集中在中心，形成明亮椭球
            this.starType = 0;
            this.distance = Mathf.pow(Mathf.random(), 2.5f) * (vivid ? 0.25f : 0.20f);
            this.armIndex = -1;
            this.angleOffset = Mathf.random(360f);
        } else if (roll < (vivid ? 0.85f : 0.88f)) {
            // 旋臂：严格沿臂分布，幂律让密度向中心线集中
            this.starType = 1;
            this.distance = 0.05f + Mathf.pow(Mathf.random(), 0.8f) * 0.95f;
            this.armIndex = Mathf.random(galaxy.arms - 1);
            float sign = Mathf.random() < 0.5f ? 1f : -1f;
            // 关键修正：pow(?, 2.5) 让绝大多数恒星紧贴旋臂中心线
            this.angleOffset = sign * Mathf.pow(Mathf.random(), 2.5f) * galaxy.armSpread;
        } else if (roll < (vivid ? 0.95f : 0.97f)) {
            // 弥散：填充臂间，但不要太密
            this.starType = 2;
            this.distance = 0.1f + Mathf.random() * 0.9f;
            this.armIndex = Mathf.random(galaxy.arms - 1);
            this.angleOffset = Mathf.range(galaxy.armSpread * 3f);
        } else {
            // 尘埃/暗带
            this.starType = 3;
            this.distance = 0.2f + Mathf.random() * 0.75f;
            this.armIndex = Mathf.random(galaxy.arms - 1);
            float sign = Mathf.random() < 0.5f ? 1f : -1f;
            this.angleOffset = sign * (galaxy.armSpread * 0.4f + Mathf.random() * galaxy.armSpread * 0.8f);
        }

        this.orbitPhase = Mathf.random(360f);

        // 大小：核球最大最亮，旋臂次之，弥散最小
        float centerBias = 1f + (1f - distance) * 0.6f;
        if (starType == 0) {
            this.size = Mathf.random(2.5f, 5f) * centerBias * (vivid ? 1.2f : 1f);
        } else if (starType == 1) {
            this.size = Mathf.random(1.2f, 3f) * centerBias;
        } else if (starType == 3) {
            this.size = Mathf.random(1.5f, 3.5f);
        } else {
            this.size = Mathf.random(0.8f, 2f);
        }

        // 角速度：关键修正！旋臂恒星用相近速度，避免差动旋转把臂缠乱
        if (starType == 0) {
            this.angularSpeed = (0.45f + Mathf.random() * 0.15f) * galaxy.rotationSpeed;
        } else if (starType == 1) {
            // 几乎刚体旋转，旋臂形状长期保持
            this.angularSpeed = (0.55f + Mathf.random() * 0.08f) * galaxy.rotationSpeed;
        } else {
            this.angularSpeed = (0.4f + Mathf.random() * 0.2f) * galaxy.rotationSpeed;
        }

        initColor();
    }

    void initColor() {
        boolean vivid = galaxy.style == Galaxy.GalaxyStyle.vivid;
        float rnd = Mathf.range(0.08f);

        if (starType == 0) {
            if (vivid) {
                // 图1：炽烈金黄橙红核球
                float t = distance / 0.25f;
                color.r = Mathf.clamp(1.0f + rnd, 0.95f, 1f);
                color.g = Mathf.clamp(0.55f + t * 0.3f + rnd, 0.45f, 0.9f);
                color.b = Mathf.clamp(0.2f + t * 0.5f + rnd, 0.15f, 0.7f);
            } else {
                // 图2：柔和黄白核球
                float t = distance / 0.20f;
                color.r = Mathf.clamp(1.0f + rnd, 0.95f, 1f);
                color.g = Mathf.clamp(0.75f + t * 0.2f + rnd, 0.6f, 0.98f);
                color.b = Mathf.clamp(0.45f + t * 0.4f + rnd, 0.35f, 0.85f);
            }
        } else if (starType == 1) {
            if (vivid) {
                // 图1：强分段对比 — 内圈橙红，外圈蓝白
                float t = distance;
                if (t < 0.35f) {
                    color.r = Mathf.clamp(0.95f + rnd, 0.85f, 1f);
                    color.g = Mathf.clamp(0.40f + rnd, 0.3f, 0.55f);
                    color.b = Mathf.clamp(0.18f + rnd, 0.1f, 0.3f);
                } else if (t < 0.65f) {
                    color.r = Mathf.clamp(0.90f + rnd, 0.8f, 1f);
                    color.g = Mathf.clamp(0.75f + rnd, 0.65f, 0.9f);
                    color.b = Mathf.clamp(0.50f + rnd, 0.4f, 0.65f);
                } else {
                    color.r = Mathf.clamp(0.35f + rnd, 0.25f, 0.5f);
                    color.g = Mathf.clamp(0.55f + rnd, 0.45f, 0.7f);
                    color.b = Mathf.clamp(0.90f + rnd, 0.8f, 1f);
                }
            } else {
                // 图2：自然蓝绿过渡
                float t = distance;
                color.r = Mathf.clamp(0.85f - t * 0.2f + rnd, 0.65f, 0.95f);
                color.g = Mathf.clamp(0.90f - t * 0.05f + rnd, 0.78f, 0.98f);
                color.b = Mathf.clamp(0.95f + t * 0.05f + rnd, 0.88f, 1f);
            }
        } else if (starType == 2) {
            if (vivid) {
                float t = distance;
                color.r = Mathf.clamp(0.25f - t * 0.1f + rnd, 0.12f, 0.4f);
                color.g = Mathf.clamp(0.35f - t * 0.05f + rnd, 0.2f, 0.45f);
                color.b = Mathf.clamp(0.65f + rnd, 0.5f, 0.85f);
            } else {
                float t = distance;
                color.r = Mathf.clamp(0.65f - t * 0.15f + rnd, 0.45f, 0.8f);
                color.g = Mathf.clamp(0.72f - t * 0.08f + rnd, 0.55f, 0.82f);
                color.b = Mathf.clamp(0.85f + rnd, 0.7f, 0.98f);
            }
        } else {
            // starType == 3 尘埃
            if (vivid) {
                color.r = Mathf.clamp(0.50f + rnd, 0.35f, 0.65f);
                color.g = Mathf.clamp(0.22f + rnd, 0.12f, 0.35f);
                color.b = Mathf.clamp(0.15f + rnd, 0.08f, 0.28f);
            } else {
                if (Mathf.random() < 0.35f) {
                    // 粉色星云团
                    color.r = Mathf.clamp(0.85f + rnd, 0.7f, 1f);
                    color.g = Mathf.clamp(0.55f + rnd, 0.4f, 0.7f);
                    color.b = Mathf.clamp(0.65f + rnd, 0.5f, 0.8f);
                } else {
                    // 暗尘埃
                    color.r = Mathf.clamp(0.30f + rnd, 0.18f, 0.45f);
                    color.g = Mathf.clamp(0.25f + rnd, 0.15f, 0.4f);
                    color.b = Mathf.clamp(0.22f + rnd, 0.12f, 0.38f);
                }
            }
        }

        float baseA;
        if (starType == 0)
            baseA = vivid ? 0.95f : 0.9f;
        else if (starType == 1)
            baseA = vivid ? 0.85f : 0.75f;
        else if (starType == 3)
            baseA = vivid ? 0.45f : 0.35f;
        else
            baseA = 0.35f;

        color.a = Mathf.clamp(baseA + (1f - distance) * 0.1f + size * 0.03f, 0.2f, 1f);
    }

    public float getAngle(float time) {
        if (starType == 0) {
            return angleOffset + time * angularSpeed * 10f;
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
        float r = getRadius();

        if (starType == 0) {
            float theta = angleOffset + time * angularSpeed * 10f;
            float lat = Mathf.sin(orbitPhase * 1.618f + time * 0.05f)
                    * 35f * (1f - distance * 2f);
            lat = Mathf.clamp(lat, -35f, 35f);
            float cosLat = Mathf.cos(lat);
            out.set(
                    Angles.trnsx(theta, r * cosLat),
                    r * Mathf.sin(lat) * 0.30f,
                    Angles.trnsy(theta, r * cosLat));
        } else {
            float angle = getAngle(time);
            out.set(Angles.trnsx(angle, r), 0f, Angles.trnsy(angle, r));

            float thickFactor = starType == 1
                    ? (0.06f + distance * 0.14f)
                    : (starType == 3 ? (0.04f + distance * 0.1f) : (0.2f + distance * 0.25f));
            float thickness = galaxy.thickness * thickFactor;
            out.y += Mathf.sin(angle * 2f + orbitPhase + time * 0.15f) * thickness * r;
        }

        float noise = galaxy.radius * 0.004f * (1f + distance * 0.3f);
        out.x += Mathf.sin(orbitPhase * 2.3f + distance * 5f) * noise;
        out.z += Mathf.cos(orbitPhase * 1.7f + distance * 5f) * noise;

        return out;
    }
}
