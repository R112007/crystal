package crystal.entities.effect;

import arc.graphics.Color;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.entities.effect.ParticleEffect;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

public class DirectionalSpreadParticleEffect extends ParticleEffect {
  // 移动距离（可调整）
  public float moveDistance = 30f;
  // 粒子数量（默认15个，适配扩散效果）
  public int particleCount = 15;
  // 扩散角度（默认15°，可微调，建议5°-30°区间）
  public float spreadAngle = 15f;

  public DirectionalSpreadParticleEffect() {
    this.lifetime = 60f; // 粒子存活时间（60帧≈1秒）
    this.layer = Layer.effect;
    this.line = false; // 精灵粒子（圆形）
    this.randLength = false; // 移动距离统一（确保定向性）
    this.interp = Interp.linear;
    this.sizeInterp = Interp.linear;
    this.colorFrom = Pal.lightOrange.cpy().a(1f);
    this.colorTo = Pal.lightOrange.cpy().a(0f);
    this.sizeFrom = 3.5f;
    this.sizeTo = 0f;
    this.lightScl = 1.5f;
    this.lightOpacity = 0.5f;
    this.lightColor = Pal.lightOrange;
    this.particles = particleCount;
    this.cone = spreadAngle; // 扩散锥角（关键：替代原0°无扩散）
    this.length = moveDistance;
    this.baseLength = 0f;
  }

  public void trigger(Vec2 start, Vec2 target) {
    float coreAngle = start.angleTo(target);
    this.at(start.x, start.y, coreAngle);
  }

  public void trigger(float startX, float startY, float targetX, float targetY) {
    trigger(new Vec2(startX, startY), new Vec2(targetX, targetY));
  }

  public DirectionalSpreadParticleEffect setMoveDistance(float distance) {
    this.moveDistance = distance;
    this.length = distance;
    return this;
  }

  public DirectionalSpreadParticleEffect setParticleCount(int count) {
    this.particleCount = count;
    this.particles = count;
    return this;
  }

  public DirectionalSpreadParticleEffect setSpreadAngle(float angle) {
    this.spreadAngle = Mathf.clamp(angle, 0f, 45f); // 限制最大45°，避免过度扩散
    this.cone = this.spreadAngle;
    return this;
  }

  public DirectionalSpreadParticleEffect setColor(Color from, Color to) {
    this.colorFrom = from;
    this.colorTo = to;
    this.lightColor = from;
    return this;
  }
}
