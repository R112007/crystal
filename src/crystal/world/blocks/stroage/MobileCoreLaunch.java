package crystal.world.blocks.stroage;

import arc.Core;
import arc.audio.Music;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Scl;
import arc.util.Tmp;
import arc.util.Time;
import crystal.gen.Corec;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Musics;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.LaunchAnimator;

import static mindustry.Vars.*;

public class MobileCoreLaunch implements LaunchAnimator {
  private static final float[] thrusterSizes =
      { 0f, 0f, 0f, 0f, 0.3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f };
  private static final float[] cloudAlphas = { 0f, 0.5f, 1f, 0.1f, 0f, 0f };
  private static final float cloudScaling = 1700f, cfinScl = -2f, cfinOffset = 0.3f,
      calphaFinOffset = 0.25f, cloudAlpha = 0.81f;
  public Unit unit;
  public float endX, endY;
  public float time;
  public float duration;
  public boolean launching;
  protected float cloudSeed;

  public MobileCoreLaunch(Unit unit, float endX, float endY, float duration) {
    this.unit = unit;
    this.endX = endX;
    this.endY = endY;
    this.duration = duration;
  }

  @Override
  public void drawLaunch() {
    float fin = renderer.getLandTimeIn();
    float fout = 1f - fin;
    float cameraScl = renderer.getDisplayScale();
    float scale = Scl.scl(4f) / cameraScl;
    float pfin = Interp.pow3Out.apply(fin);
    float pf = Interp.pow2In.apply(fout);
    TextureRegion icon = unit.type.fullIcon;

    // Impact streaks. Vanilla fades these in from 100 to 800 world units while
    // the camera is still zoomed far out, which is the main sense of falling.
    Draw.color(Pal.lightTrail);
    Angles.randLenVectors(1, pfin, 100, 800f * scale * pfin, (ax, ay, ffin, ffout) -> {
      Lines.stroke(scale * ffin * pf * 3f);
      Lines.lineAngle(unit.x + ax, unit.y + ay, Mathf.angle(ax, ay),
          (ffin * 20f + 1f) * scale);
    });
    Draw.color();

    drawLanding(icon, scale);

    Draw.color();
    Draw.mixcol(Color.white, Interp.pow5In.apply(fout));

    if (state.rules.cloudColor.a > 0.0001f) {
      Texture clouds = Core.assets.get("sprites/clouds.png", Texture.class);
      float sscl = Math.max(1f + Mathf.clamp(fin + cfinOffset) * cfinScl, 0f) * cameraScl;

      Tmp.tr1.set(clouds);
      Tmp.tr1.set(
          (Core.camera.position.x - Core.camera.width / 2f * sscl) / cloudScaling,
          (Core.camera.position.y - Core.camera.height / 2f * sscl) / cloudScaling,
          (Core.camera.position.x + Core.camera.width / 2f * sscl) / cloudScaling,
          (Core.camera.position.y + Core.camera.height / 2f * sscl) / cloudScaling);
      Tmp.tr1.scroll(10f * cloudSeed, 10f * cloudSeed);

      Draw.alpha(Mathf.sample(cloudAlphas, fin + calphaFinOffset) * cloudAlpha);
      Draw.mixcol(state.rules.cloudColor, state.rules.cloudColor.a);
      Draw.rect(Tmp.tr1, Core.camera.position.x, Core.camera.position.y,
          Core.camera.width, Core.camera.height);
      Draw.reset();
    }
  }

  protected void drawLanding(TextureRegion icon, float scale) {
    float fin = renderer.getLandTimeIn();
    float fout = 1f - fin;
    float shadowSize = icon.width * icon.scl() * scale * 3.6f * Interp.pow2Out.apply(fout);
    float rotation = Interp.pow2In.apply(fout) * 135f;
    float thrusterOpen = 0.25f;
    float thrusterFrame = fin >= thrusterOpen ? 1f : fin / thrusterOpen;
    float thrusterSize = Mathf.sample(thrusterSizes, fin);

    Draw.z(Layer.space + 0.1f);
    Draw.color(Pal.lightTrail);
    Draw.rect("circle-shadow", unit.x, unit.y, shadowSize, shadowSize);
    Draw.scl(scale);

    float strength = scale * thrusterSize * (0.95f + Mathf.absin(2f, 0.1f));
    float bodyRadius = unit.hitSize / 2f * scale;
    for (int i = 0; i < 4; i++) {
      float angle = i * 90f + rotation;
      float distance = bodyRadius + strength * 2f;
      float tx = unit.x + Angles.trnsx(angle, distance);
      float ty = unit.y + Angles.trnsy(angle, distance);

      Draw.color(unit.team.color);
      Fill.circle(tx, ty, 6f * strength);
      Draw.color();
      Fill.circle(tx, ty, 3.5f * strength);
      Draw.color(unit.team.color, 0.8f * thrusterFrame);
      Drawf.tri(tx, ty, 5f * strength, 24f * strength, angle);
      Draw.color(Pal.lightTrail, 0.7f * thrusterFrame);
      Drawf.tri(tx, ty, 2.5f * strength, 15f * strength, angle);
    }

    Draw.alpha(Interp.pow4In.apply(thrusterFrame));
    Draw.mixcol(Color.white, Interp.pow5In.apply(fout));
    Drawf.spinSprite(icon, unit.x, unit.y, rotation);
    Draw.alpha(1f);
    Draw.scl();
    Draw.reset();
  }

  @Override
  public void drawLaunchGlobalZ() {
    // 全局Z层特效，不需要
  }

  @Override
  public void beginLaunch(boolean launching) {
    this.launching = launching;
    time = 0f;
    cloudSeed = Mathf.random(1f);
    if (!launching && !headless) {
      Sounds.coreLand.at(Core.camera.position, 1f, 1f);

      if (!Core.settings.getBool("skipcoreanimation")) {
        Image fade = new Image();
        fade.color.a = 1f;
        fade.touchable = Touchable.disabled;
        fade.setFillParent(true);
        fade.actions(Actions.fadeOut(35f / 60f), Actions.remove());
        fade.update(() -> {
          fade.toFront();
          if (ui != null && ui.loadfrag != null) ui.loadfrag.toFront();
          if (state.isMenu()) fade.remove();
        });
        Core.scene.add(fade);
      }
    }
    if (unit instanceof Corec core) {
      core.landing(!launching);
    }
  }

  @Override
  public void endLaunch() {
    if (unit instanceof Corec core) {
      core.landing(false);
    }
    unit.set(endX, endY);
    unit.elevation(0f);
    Fx.landShock.at(unit);
    Fx.launch.at(unit);
    Effect.shake(6f, 6f, unit);
    Core.camera.position.set(unit);
    unit.update();
  }

  @Override
  public void updateLaunch() {
    time += Time.delta;
    // Core position remains fixed; drawLaunch mirrors the vanilla core scale/spin landing.
    unit.set(endX, endY);
    unit.elevation(0f);
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
    Core.camera.position.set(unit);
    return Interp.pow3.apply(Scl.scl(0.02f), Scl.scl(4f), renderer.getLandTimeIn());
  }
}
