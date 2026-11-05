package crystal.ui;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.util.Log;
import crystal.magic.Skill;
import crystal.ui.style.CircleButton;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Player;
import mindustry.ui.Styles;

/**
 * Floating radial skill menu. The center button stays fixed while skill
 * buttons animate out from underneath it and form a clickable ring.
 */
public final class SkillHUD {
  private static final float MAIN_SIZE_DESKTOP = 64f;
  private static final float MAIN_SIZE_MOBILE = 128f;
  private static final float SKILL_SIZE_DESKTOP = 48f;
  private static final float SKILL_SIZE_MOBILE = 96f;
  private static final float RING_GAP = 14f;
  private static final float MIN_RING_RADIUS_DESKTOP = 76f;
  private static final float MIN_RING_RADIUS_MOBILE = 160f;
  private static final float MAIN_ICON_INSET = 16f;
  private static final float SKILL_ICON_INSET = 12f;
  private static final Color MAIN_BACKGROUND = Color.valueOf("171d24");
  private static final Color SKILL_BACKGROUND = Color.valueOf("252d36");
  private static final Color ACTIVE_COLOR = Color.valueOf("ffd36a");

  private static SkillWheel wheel;
  private static boolean initialized;

  private SkillHUD() {
  }

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;
    Events.on(ClientLoadEvent.class, e -> {
      if (wheel != null) {
        return;
      }
      wheel = new SkillWheel();
      wheel.setFillParent(true);
      Vars.ui.hudGroup.addChild(wheel);
    });
  }

  private static class SkillWheel extends WidgetGroup {
    private final Seq<SkillButton> skillButtons = new Seq<>();
    private final CircleButton mainButton;
    private String signature = "";
    private boolean open;
    private float progress;
    private boolean layoutLogged;

    SkillWheel() {
      touchable = Touchable.childrenOnly;
      mainButton = createCircleButton(new TextureRegionDrawable(Core.atlas.find("crystal-skills")), mainSize(),
          MAIN_BACKGROUND, ACTIVE_COLOR);
      scaleImageForMobile(mainButton, mainSize(), MAIN_ICON_INSET);
      mainButton.name = "skill-menu";
      mainButton.clicked(this::toggle);
    }

    private void toggle() {
      if (skillButtons.isEmpty() || !canShow()) {
        return;
      }
      open = !open;
    }

    @Override
    public void act(float delta) {
      super.act(delta);
      refreshSkills();

      boolean show = canShow() && !skillButtons.isEmpty();
      visible = show;
      if (!show) {
        open = false;
      } else {
        // HUD fragments are created at different times; keep this overlay on
        // top so its circular buttons win hit-testing over vanilla controls.
        toFront();
      }

      progress = Mathf.lerpDelta(progress, open ? 1f : 0f, 0.22f);
      if (Math.abs(progress - (open ? 1f : 0f)) < 0.002f) {
        progress = open ? 1f : 0f;
      }

      float width = getWidth(), height = getHeight();
      if (width <= 0f || height <= 0f) {
        return;
      }
      if (show && !layoutLogged) {
        layoutLogged = true;
        Log.info("[SkillHUD] visible=@ size=@x@ skills=@",
            show, width, height, skillButtons.size);
      }

      float radius = ringRadius();
      float mainSize = mainSize();
      float skillSize = skillSize();
      float edgeInset = radius + skillSize / 2f + 18f;
      float centerX = width - edgeInset;
      float minY = radius + skillSize / 2f + 24f;
      float maxY = height - radius - skillSize / 2f - 24f;
      float centerY = Mathf.clamp(
          height * (Vars.mobile ? 0.38f : 0.34f),
          minY,
          Math.max(minY, maxY));
      float eased = Interp.pow2Out.apply(progress);

      mainButton.setSize(mainSize, mainSize);
      mainButton.setPosition(centerX - mainSize / 2f, centerY - mainSize / 2f);
      mainButton.setColor(open ? ACTIVE_COLOR : Color.white);
      mainButton.touchable = show ? Touchable.enabled : Touchable.disabled;

      int count = skillButtons.size;
      for (int i = 0; i < count; i++) {
        SkillButton button = skillButtons.get(i);
        float targetAngle = 90f + i * 360f / count;
        float targetX = centerX + Mathf.cosDeg(targetAngle) * radius
            - skillSize / 2f;
        float targetY = centerY + Mathf.sinDeg(targetAngle) * radius
            - skillSize / 2f;
        float startX = centerX - skillSize / 2f;
        float startY = centerY - skillSize / 2f;

        button.setSize(skillSize, skillSize);
        button.setPosition(
            Mathf.lerp(startX, targetX, eased),
            Mathf.lerp(startY, targetY, eased));
        button.setScale(Mathf.lerp(0.65f, 1f, eased));
        button.setOrigin(skillSize / 2f, skillSize / 2f);
        button.color.a = Mathf.clamp(progress * 2f);
        button.visible = progress > 0.01f;
        button.touchable = open && progress > 0.92f
            ? Touchable.enabled
            : Touchable.disabled;
      }
    }

    private boolean canShow() {
      return Vars.state != null
          && Vars.state.isPlaying()
          && Vars.state.rules != null
          && !Vars.state.rules.editor;
    }

    private float ringRadius() {
      float circumferenceRadius = (skillSize() + RING_GAP) * skillButtons.size / Mathf.PI2;
      return Math.max(Vars.mobile ? MIN_RING_RADIUS_MOBILE : MIN_RING_RADIUS_DESKTOP,
          circumferenceRadius);
    }

    private static float mainSize() {
      return Vars.mobile ? MAIN_SIZE_MOBILE : MAIN_SIZE_DESKTOP;
    }

    private static float skillSize() {
      return Vars.mobile ? SKILL_SIZE_MOBILE : SKILL_SIZE_DESKTOP;
    }

    private void refreshSkills() {
      String nextSignature = buildSignature();
      if (nextSignature.equals(signature)) {
        return;
      }
      signature = nextSignature;

      for (SkillButton button : skillButtons) {
        button.remove();
      }
      skillButtons.clear();
      mainButton.remove();

      for (Skill skill : Skill.list) {
        if (!skill.visible()) {
          continue;
        }
        SkillButton button = new SkillButton(skill);
        skillButtons.add(button);
        addChild(button);
      }
      // Add the center button last so icons visibly slide out from underneath it.
      addChild(mainButton);
    }

    private String buildSignature() {
      StringBuilder result = new StringBuilder();
      for (Skill skill : Skill.list) {
        if (skill.visible()) {
          result.append(skill.id).append(',');
        }
      }
      return result.toString();
    }
  }

  private static class SkillButton extends CircleButton {
    final Skill skill;

    SkillButton(Skill skill) {
      super(
          new TextureRegionDrawable(skill.uiIcon()),
          createStyle(new TextureRegionDrawable(skill.uiIcon())));
      this.skill = skill;
      backgroundColor = SKILL_BACKGROUND;
      strokeColor = skill.iconColor.cpy().mul(0.8f);
      strokeWidth = 2f;
      drawBackground = false;
      drawStroke = false;
      setColor(skill.usingFallbackIcon() ? skill.iconColor : Color.white);
      scaleImageForMobile(this, SKILL_SIZE_MOBILE, SKILL_ICON_INSET);
      name = "skill-" + skill.name;
      touchable = Touchable.disabled;
      clicked(this::cast);
    }

    private void cast() {
      Player player = Vars.player;
      if (player == null || Vars.net.client()) {
        return;
      }
      float x = player.x(), y = player.y();
      float angle = player.unit() == null ? 90f : player.unit().rotation;
      skill.cast(player, x, y, angle);
    }

    @Override
    public void draw() {
      boolean active = skill.isActive();
      backgroundColor = active ? Color.valueOf("3f3521") : SKILL_BACKGROUND;
      super.draw();

      float cx = x + width / 2f;
      float cy = y + height / 2f;
      float radius = Math.min(width, height) / 2f;
      float cooldown = skill.cooldownProgress();

      if (cooldown > 0f) {
        Draw.color(Color.black, 0.62f);
        Fill.arc(cx, cy, radius, cooldown * 360f, 90f);
      } else if (!active && !skill.ready()) {
        Draw.color(Color.black, 0.38f);
        Fill.circle(cx, cy, radius);
      }

      if (active) {
        Draw.color(ACTIVE_COLOR);
        Lines.stroke(3f);
        Lines.circle(cx, cy, radius - 2f);
      }
      Draw.reset();
    }
  }

  private static CircleButton createCircleButton(
      arc.scene.style.Drawable icon, float size, Color background, Color iconColor) {
    CircleButton button = new CircleButton(icon, createStyle(icon));
    button.backgroundColor = background;
    button.strokeColor = Color.valueOf("778596");
    button.strokeWidth = 2f;
    button.drawBackground = false;
    button.drawStroke = false;
    button.setColor(iconColor);
    button.setSize(size, size);
    return button;
  }

  /**
   * ImageButton keeps its child image at the texture's native size unless the
   * image cell is explicitly resized. Mobile needs both a larger hit target and
   * a correspondingly larger icon inside it.
   */
  private static void scaleImageForMobile(ImageButton button, float buttonSize, float inset) {
    if (Vars.mobile) {
      button.getImageCell().size(buttonSize - inset);
    }
  }

  private static ImageButtonStyle createStyle(arc.scene.style.Drawable icon) {
    ImageButtonStyle style = new ImageButtonStyle(Styles.clearNonei);
    style.imageUp = icon;
    return style;
  }
}
