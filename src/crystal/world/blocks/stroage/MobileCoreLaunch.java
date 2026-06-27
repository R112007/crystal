package crystal.world.blocks.stroage;

import arc.Core;
import arc.audio.Music;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Musics;
import mindustry.gen.Unit;
import mindustry.world.blocks.LaunchAnimator;

public class MobileCoreLaunch implements LaunchAnimator {
  public Unit unit;
  public float endX, endY;
  public float time;
  public float duration;
  public boolean launching;

  public MobileCoreLaunch(Unit unit, float endX, float endY, float duration) {
    this.unit = unit;
    this.endX = endX;
    this.endY = endY;
    this.duration = duration;
  }

  @Override
  public void drawLaunch() {
    // 单位自己会通过 Drawc.draw() 绘制，这里不用额外画
  }

  @Override
  public void drawLaunchGlobalZ() {
    // 全局Z层特效，不需要
  }

  @Override
  public void beginLaunch(boolean launching) {
    this.launching = launching;
    time = 0f;
  }

  @Override
  public void endLaunch() {
    unit.set(endX, endY);
    unit.elevation(0f);
    Fx.landShock.at(unit);
    Effect.shake(6f, 6f, unit);
    Core.camera.position.set(unit);
    unit.update();
  }

  @Override
  public void updateLaunch() {
    time += Time.delta;
    float progress = Mathf.clamp(time / duration, 0f, 1f);
    // 缓动下落
    float eased = 1f - (1f - progress) * (1f - progress);

    // 只改 elevation，y 坐标保持地面位置不变，腿就不会乱摆
    unit.elevation(1f - eased + 0.01f); // +0.01 避免完全贴地时腿乱晃
  }

  @Override
  public float launchDuration() {
    return duration;
  }

  @Override
  public Music landMusic() {
    return Musics.land;
  }

  @Override
  public float zoomLaunch() {
    return 2f; // 降落时镜头拉近
  }
}
