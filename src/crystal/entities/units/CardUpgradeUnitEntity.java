package crystal.entities.units;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.content.CUnits;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Dedicated first-phase boss entity.
 *
 * All phase state and rendering live on the unit entity itself. There is no
 * upgrade Ability involved.
 */
public class CardUpgradeUnitEntity extends UnitEntity {
  private static final int CARD_COUNT = 12;
  private static final float LAUNCH_START = 15f;
  private static final float LAUNCH_STAGGER = 10f;
  private static final float FLIGHT_TIME = 78f;
  private static final float ORBIT_TURN_TIME = 120f;
  private static final float ORBIT_HOLD_TIME = 120f;
  private static final float ASSEMBLY_STAGGER = 15f;
  private static final float ASSEMBLY_TIME = 120f;
  private static final float PART_OUTLINE_LEAD = 60f;
  private static final float PART_SWAP_FADE_TIME = 50f;
  private static final float PART_OUTLINE_FADE = 60f;
  private static final float FINAL_GLOW_TIME = 60f;
  private static final float EFFECT_FADE_TIME = 120f;
  private static final float ORBIT_ENERGY_FADE_TIME = 45f;
  private static final float ORBIT_ENERGY_FADE_IN = 40f;
  private static final float CARD_LIGHT_FADE_IN = 28f;
  private static final float CARD_GLOW_FADE_IN = 40f;
  private static final float ENERGY_LINK_FADE_IN = 24f;
  private static final float WHOLE_GLOW_FADE_IN = 50f;
  private static final float INTRO_TIME = 72f;
  private static final float FULL_OUTLINE_GROW_TIME = 76f;
  private static final float FULL_OUTLINE_HOLD_TIME = 26f;
  private static final float FULL_OUTLINE_FADE_TIME = 70f;
  private static final float ENGINE_FADE_TIME = 34f;
  private static final float ENGINE_REVEAL_PART_OPACITY = 0.7f;
  private static final float CARD_VISUAL_SCALE = 0.36f;
  private static final float PART_SWAP_START = 1f;
  private static final float UPGRADE_HEALTH_THRESHOLD = 0.3f;
  // The atlas image has transparent padding; the sweep starts at the visible silhouette bounds.
  private static final int FULL_OUTLINE_CROP_LEFT = 55;
  private static final int FULL_OUTLINE_CROP_TOP = 42;
  private static final int FULL_OUTLINE_CROP_WIDTH = 690;
  private static final int FULL_OUTLINE_CROP_HEIGHT = 818;

  private static final float ALL_ENTER_ORBIT = LAUNCH_START + (CARD_COUNT - 1) * LAUNCH_STAGGER + FLIGHT_TIME;
  private static final float ORBIT_END = ALL_ENTER_ORBIT + ORBIT_HOLD_TIME;
  private static final float ALL_PARTS_READY =
      ORBIT_END + (CARD_COUNT - 1) * ASSEMBLY_STAGGER + ASSEMBLY_TIME
          + PART_OUTLINE_LEAD + PART_SWAP_FADE_TIME;
  private static final float ENGINE_START_TIME =
      ORBIT_END + (CARD_COUNT - 3) * ASSEMBLY_STAGGER + ASSEMBLY_TIME
          + PART_OUTLINE_LEAD + PART_SWAP_FADE_TIME * ENGINE_REVEAL_PART_OPACITY;
  private static final float TOTAL_TIME = ALL_PARTS_READY + FINAL_GLOW_TIME + EFFECT_FADE_TIME;

  private static final String[] CARD_NAMES = {
      "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"
  };
  private static final float[] PART_VISUAL_X = {
      218.57f, 266.61f, 399.78f, 533.19f,
      580.42f, 168.61f, 400.20f, 631.10f,
      307.05f, 492.75f, 177.88f, 621.53f
  };
  private static final float[] PART_VISUAL_Y = {
      172.73f, 357.42f, 259.65f, 357.42f,
      172.45f, 518.03f, 500.32f, 518.09f,
      668.26f, 668.57f, 763.94f, 764.09f
  };
  private static final float[] PART_CROP_LEFT = {
      163f, 158f, 313f, 414f, 525f, 55f, 276f, 526f, 180f, 401f, 59f, 440f
  };
  private static final float[] PART_CROP_TOP = {
      42f, 242f, 147f, 241f, 42f, 392f, 391f, 393f, 528f, 528f, 639f, 639f
  };
  private static final Color CARD_LIGHT = Color.valueOf("ffd36b");
  private static TextureRegion[] cards;
  private static TextureRegion[] parts;
  private static TextureRegion[] outlines;
  private static final TextureRegion[] outlineTiles = {
      new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion()
  };
  private static final TextureRegion[] fullOutlineTiles = {
      new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion()
  };
  private static TextureRegion fullOutline;
  private static TextureRegion partSource;
  private static int partSourceWidth;
  private static int partSourceHeight;
  private static final int[] partX = new int[CARD_COUNT];
  private static final int[] partY = new int[CARD_COUNT];
  private static final int[] partWidths = new int[CARD_COUNT];
  private static final int[] partHeights = new int[CARD_COUNT];

  private static final Vec2 startPos = new Vec2();
  private static final Vec2 targetPos = new Vec2();
  private static final Vec2 controlPos = new Vec2();
  private static final Vec2 assemblyControl = new Vec2();
  private static final Vec2 cardPos = new Vec2();
  private static final Vec2 trailPos = new Vec2();
  private static final Vec2 trailPrev = new Vec2();

  private boolean upgrading;
  private boolean transformed;
  private float upgradeTimer;
  private float upgradeRotation;

  public static CardUpgradeUnitEntity create() {
    return new CardUpgradeUnitEntity();
  }

  @Override
  public int classId() {
    return 54;
  }

  @Override
  public void update() {
    if (dead) {
      return;
    }

    if (!upgrading && !transformed && health < maxHealth * UPGRADE_HEALTH_THRESHOLD) {
      upgrading = true;
      upgradeTimer = 0f;
      upgradeRotation = rotation;
      vel.setZero();
    }

    if (upgrading) {
      vel.setZero();
      rotation = upgradeRotation;
      controlWeapons(false, false);
      upgradeTimer += Time.delta;

      if (upgradeTimer >= TOTAL_TIME) {
        completeUpgrade();
        return;
      }
    }

    super.update();

    if (upgrading) {
      vel.setZero();
      rotation = upgradeRotation;
      controlWeapons(false, false);
    }
  }

  @Override
  public void draw() {
    if (upgrading) {
      drawUpgrade();
    } else {
      super.draw();
    }
  }

  public boolean isUpgrading() {
    return upgrading && !transformed;
  }

  private void completeUpgrade() {
    transformed = true;
    upgrading = false;

    if (CUnits.kingLutian == null) {
      return;
    }

    boolean wasPlayer = isPlayer();
    Player player = wasPlayer ? (Player) controller() : null;

    Unit upgraded = CUnits.kingLutian.create(team);
    upgraded.set(x, y);
    upgraded.rotation = rotation;
    upgraded.vel.set(vel);
    upgraded.elevation = elevation;
    upgraded.health = upgraded.maxHealth;
    upgraded.add();

    if (wasPlayer && player != null) {
      player.unit(upgraded);
    }

    mindustry.content.Fx.upgradeCore.at(x, y);
    Sounds.unitCreate.at(x, y, 1f, 1f);
    remove();
  }

  private void drawUpgrade() {
    loadRegions();
    Draw.z(Layer.effect + 0.2f);

    drawIntroAura();
    drawFadingEngines();

    float assembled = Mathf.clamp(
        (upgradeTimer - ORBIT_END) / Math.max(1f, ALL_PARTS_READY - ORBIT_END));
    float bodyAlpha = 1f - Interp.pow2In.apply(assembled);

    if (bodyAlpha > 0.001f && type.fullIcon.found()) {
      Draw.alpha(bodyAlpha);
      Draw.rect(type.fullIcon, x, y, rotation - 90f);
      Draw.reset();
    }

    for (int i = 0; i < CARD_COUNT; i++) {
      if (cards[i] != null && cards[i].found()) {
        drawCard(i);
      }
    }

    for (int i = 0; i < CARD_COUNT; i++) {
      float handoff = partHandoff(i);
      float outline = partOutline(i);
      float outlineSweep = partOutlineSweep(i);
      if (parts[i] != null && parts[i].found() && (handoff > 0f || outline > 0f)) {
        partTarget(i, targetPos);

        if (outline > 0f) {
          drawPartOutline(i, outline, outlineSweep);
        }

        if (handoff <= 0f) {
          continue;
        }

        Draw.blend(Blending.additive);
        Draw.color(CARD_LIGHT, handoff * 0.55f);
        drawPart(i);
        Draw.blend();

        Draw.color(Color.white, handoff);
        drawPart(i);
        Draw.reset();
      }
    }

    drawFadingWeapons();
    Draw.z(Layer.effect + 0.35f);
    drawOrbitEnergy();
    if (upgradeTimer >= ORBIT_END) {
      drawWholeGlow();
    }
    if (upgradeTimer >= ALL_PARTS_READY) {
      drawFinalOutline();
    }

    Draw.reset();
  }

  private void drawCard(int index) {
    float launchTime = LAUNCH_START + index * LAUNCH_STAGGER;
    if (upgradeTimer < launchTime) {
      return;
    }

    TextureRegion card = cards[index];
    float flight = Mathf.clamp((upgradeTimer - launchTime) / FLIGHT_TIME);
    float assemblyStart = ORBIT_END + index * ASSEMBLY_STAGGER;
    float assembly = Mathf.clamp((upgradeTimer - assemblyStart) / ASSEMBLY_TIME);
    float cardRotation = rotation - 90f;
    float faceScale = 1f;
    float assemblyScale = 1f;
    float alpha = 1f;
    float morphProgress = 0f;
    float lightAppear = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - launchTime) / CARD_LIGHT_FADE_IN));
    float orbitGlow = upgradeTimer < ORBIT_END ? 0f
        : Interp.smooth.apply(Mathf.clamp(
            (upgradeTimer - ORBIT_END) / CARD_GLOW_FADE_IN));

    if (upgradeTimer < launchTime + FLIGHT_TIME) {
      float flipProgress = flipEase(flight);
      faceScale = 0.07f + 0.93f * flipProgress;
      cardRotation += 90f * (1f - flipProgress);
      flightPosition(flight, cardPos);
    } else if (upgradeTimer < assemblyStart) {
      orbitPosition(index, upgradeTimer, cardPos);
    } else {
      assemblyPosition(index, assemblyStart, assembly, cardPos);
      assemblyScale = 1f + 0.06f * Interp.pow2Out.apply(assembly);
      morphProgress = Mathf.clamp(
          (upgradeTimer - assemblyStart - ASSEMBLY_TIME) / PART_OUTLINE_LEAD);
      assemblyScale *= 1f - morphProgress * 0.55f;
      alpha = 1f - partHandoff(index);
    }

    if (alpha <= 0.001f) {
      return;
    }

    float effectAlpha = alpha * lightAppear;
    drawMovementTrail(index, effectAlpha);
    drawEnergyLink(index, effectAlpha);

    float glow = upgradeTimer >= ALL_ENTER_ORBIT ? 0.55f : Mathf.clamp(flight);
    float lightRadius = card.height * CARD_VISUAL_SCALE * 0.55f;

    Draw.blend(Blending.additive);
    // Fill.light uses the batch mix color, which Draw.color's alpha does not touch.
    // Bake the fade into the gradient colors so the card light can actually fade.
    Fill.light(
        cardPos.x,
        cardPos.y,
        12,
        lightRadius,
        Tmp.c1.set(CARD_LIGHT).a(0.13f * effectAlpha),
        Color.clear);
    float cardRingPulse = 0.5f + 0.5f * Mathf.absin(
        upgradeTimer * 0.21f + index * 1.3f, 1f, 1f);
    Lines.stroke(0.7f + cardRingPulse * 1.1f);
    Draw.color(CARD_LIGHT, effectAlpha * (0.12f + glow * 0.12f));
    Lines.circle(cardPos.x, cardPos.y, lightRadius * (1.02f + cardRingPulse * 0.1f));
    for (int i = 0; i < 3; i++) {
      float angle = upgradeTimer * (1.7f + i * 0.09f) + index * 41f + i * 72f;
      float radius = lightRadius * (0.65f + (i % 3) * 0.12f);
      Draw.color(
          i % 2 == 0 ? Color.white : CARD_LIGHT,
          effectAlpha * (0.45f + glow * 0.3f));
      Fill.circle(
          cardPos.x + Angles.trnsx(angle, radius),
          cardPos.y + Angles.trnsy(angle, radius),
          0.7f + (i % 3) * 0.35f);
    }
    Draw.blend();
    Draw.reset();

    float cardAlpha = alpha * lightAppear;
    Draw.alpha(cardAlpha);
    Draw.rect(
        card,
        cardPos.x,
        cardPos.y,
        card.width * CARD_VISUAL_SCALE * faceScale * assemblyScale,
        card.height * CARD_VISUAL_SCALE * assemblyScale,
        cardRotation);
    Draw.reset();

    if (morphProgress > 0f) {
      Draw.blend(Blending.additive);
      Draw.color(CARD_LIGHT, cardAlpha * morphProgress * 0.65f);
      Draw.rect(
          card,
          cardPos.x,
          cardPos.y,
          card.width * CARD_VISUAL_SCALE * faceScale * assemblyScale * 1.04f,
          card.height * CARD_VISUAL_SCALE * assemblyScale * 1.04f,
          cardRotation);
      Draw.blend();
      Draw.reset();
    }

    if (orbitGlow > 0f && upgradeTimer < ALL_PARTS_READY + 20f) {
      Draw.blend(Blending.additive);
      Draw.color(CARD_LIGHT, 0.24f * cardAlpha * orbitGlow);
      Draw.rect(
          card,
          cardPos.x,
          cardPos.y,
          card.width * CARD_VISUAL_SCALE * faceScale * assemblyScale * 1.04f,
          card.height * CARD_VISUAL_SCALE * assemblyScale * 1.04f,
          cardRotation);
      Draw.color(Color.white, 0.08f * cardAlpha * orbitGlow);
      Draw.rect(
          card,
          cardPos.x,
          cardPos.y,
          card.width * CARD_VISUAL_SCALE * faceScale * assemblyScale * 1.09f,
          card.height * CARD_VISUAL_SCALE * assemblyScale * 1.09f,
          cardRotation);
      Draw.blend();
      Draw.reset();
    }
  }

  private void drawMovementTrail(int index, float alpha) {
    float trailStep = upgradeTimer < ORBIT_END ? 4.5f : 6f;
    float maxAlpha = upgradeTimer >= ORBIT_END ? 0.42f : 0.62f;

    Draw.blend(Blending.additive);
    for (int i = 0; i < 4; i++) {
      float newer = upgradeTimer - i * trailStep;
      float older = upgradeTimer - (i + 1) * trailStep;
      if (older < LAUNCH_START + index * LAUNCH_STAGGER) {
        break;
      }

      sequencePosition(index, older, trailPrev);
      sequencePosition(index, newer, trailPos);
      float fade = 1f - i / 4f;
      Draw.color(Pal.accent, Color.white, fade * 0.72f);
      Draw.alpha(alpha * maxAlpha * fade * fade);
      Lines.stroke(0.8f + 3.6f * fade);
      Lines.line(trailPrev.x, trailPrev.y, trailPos.x, trailPos.y);
      Fill.circle(trailPrev.x, trailPrev.y, 0.8f + fade * 2.2f);
    }
    Draw.blend();
    Draw.reset();
  }

  private void drawIntroAura() {
    if (upgradeTimer >= INTRO_TIME) {
      return;
    }

    float fadeIn = Interp.smooth.apply(Mathf.clamp(upgradeTimer / 14f));
    float fadeOut = 1f - Interp.smooth.apply(
        Mathf.clamp((upgradeTimer - 24f) / (INTRO_TIME - 24f)));
    float effect = fadeIn * fadeOut;
    if (effect <= 0.001f) {
      return;
    }

    float charge = Interp.smooth.apply(Mathf.clamp(upgradeTimer / INTRO_TIME));
    Draw.blend(Blending.additive);

    Fill.light(
        x,
        y,
        20,
        hitSize * (1.2f + charge * 1.6f),
        Tmp.c1.set(CARD_LIGHT).a(0.18f * effect),
        Color.clear);

    for (int ring = 0; ring < 2; ring++) {
      float phase = Mathf.clamp((upgradeTimer + ring * 18f) / INTRO_TIME);
      float radius = hitSize * (0.55f + phase * 3.3f);
      Lines.stroke(2.8f * (1f - phase) + 0.7f);
      Draw.color(ring == 0 ? Color.white : CARD_LIGHT, (1f - phase) * 0.42f * effect);
      Lines.circle(x, y, radius);
    }

    for (int i = 0; i < 16; i++) {
      float flicker = 0.45f + 0.55f * Mathf.sin(upgradeTimer * 0.27f + i * 1.91f);
      float angle = i * 22.5f + upgradeTimer * 1.7f;
      float inner = hitSize * (0.7f + charge * 0.8f + flicker * 0.25f);
      float outer = inner + hitSize * (0.45f + flicker * 0.85f);
      Draw.color(i % 3 == 0 ? Color.white : CARD_LIGHT, flicker * 0.3f * effect);
      Lines.stroke(0.7f + flicker * 1.2f);
      Lines.line(
          x + Angles.trnsx(angle, inner),
          y + Angles.trnsy(angle, inner),
          x + Angles.trnsx(angle, outer),
          y + Angles.trnsy(angle, outer));
      Fill.circle(
          x + Angles.trnsx(angle, outer),
          y + Angles.trnsy(angle, outer),
          0.8f + flicker * 1.5f);
    }

    Draw.blend();
    Draw.reset();
  }

  private void drawFadingEngines() {
    UnitType target = CUnits.kingLutian;
    if (target == null || target.engines.isEmpty()) {
      return;
    }

    float engineElevation = target.useEngineElevation ? elevation : 1f;
    if (engineElevation <= 0.0001f) {
      return;
    }

    // Start the engines when the third- and fourth-from-last parts reach 70%.
    float enginePartOpacity = Math.min(
        partHandoff(CARD_COUNT - 4), partHandoff(CARD_COUNT - 3));
    if (enginePartOpacity < ENGINE_REVEAL_PART_OPACITY) {
      return;
    }
    float reveal = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ENGINE_START_TIME) / ENGINE_FADE_TIME));
    if (reveal <= 0.001f) {
      return;
    }

    float localRotation = rotation - 90f;
    for (var engine : target.engines) {
      Color color = engine.color != null ? engine.color
          : target.engineColor != null ? target.engineColor : team.color;
      Tmp.v1.set(engine.x, engine.y).rotate(localRotation);
      float radius = (engine.radius
          + Mathf.absin(Time.time, 2f, engine.radius / 4f)) * engineElevation;
      Draw.color(Tmp.c1.set(color).a(reveal * color.a));
      Fill.circle(x + Tmp.v1.x, y + Tmp.v1.y, radius);

      float innerRadius = radius / 4f;
      float innerAngle = localRotation + engine.rotation;
      Draw.color(Tmp.c1.set(target.engineColorInner).a(reveal));
      Fill.circle(
          x + Tmp.v1.x - Angles.trnsx(innerAngle, innerRadius),
          y + Tmp.v1.y - Angles.trnsy(innerAngle, innerRadius),
          radius / 2f);
    }
    Draw.reset();
  }

  private void drawFadingWeapons() {
    UnitType target = CUnits.kingLutian;
    if (target == null || target.weapons.isEmpty()) {
      return;
    }

    float localRotation = rotation - 90f;
    Draw.z(Layer.effect + 0.3f);
    for (int i = 0; i < target.weapons.size; i++) {
      Weapon weapon = target.weapons.get(i);
      float alpha = partHandoff(weaponPartIndex(weapon));
      if (alpha <= 0.001f) {
        continue;
      }

      float weaponRotation = localRotation
          + (weapon.rotate ? 0f : weapon.baseRotation);
      float weaponX = x + Angles.trnsx(localRotation, weapon.x, weapon.y);
      float weaponY = y + Angles.trnsy(localRotation, weapon.x, weapon.y);
      float oldXscl = Draw.xscl;
      if (weapon.flipSprite) {
        Draw.xscl = -oldXscl;
      }

      target.applyColor(this);
      Draw.alpha(alpha);
      if (weapon.region != null && weapon.region.found()) {
        Draw.rect(weapon.region, weaponX, weaponY, weaponRotation);
      }
      if (weapon.cellRegion != null && weapon.cellRegion.found()) {
        Draw.color(target.cellColor(this));
        Draw.alpha(alpha);
        Draw.rect(weapon.cellRegion, weaponX, weaponY, weaponRotation);
      }

      Draw.xscl = oldXscl;
      Draw.reset();
    }
  }

  /**
   * Finds the hull part occupying the weapon's local coordinates. Mirror copies
   * therefore automatically reveal with their own side's part.
   */
  private int weaponPartIndex(Weapon weapon) {
    float sourceScale = partSource == null ? 1f : partSource.scl();
    float bestDistance = Float.MAX_VALUE;
    int best = 0;

    for (int i = 0; i < CARD_COUNT; i++) {
      float partX = (PART_VISUAL_X[i] - partSourceWidth * 0.5f) * sourceScale;
      float partY = (partSourceHeight * 0.5f - PART_VISUAL_Y[i]) * sourceScale;
      float dx = partX - weapon.x;
      float dy = partY - weapon.y;
      float distance = dx * dx + dy * dy;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    }
    return best;
  }

  private void drawOrbitEnergy() {
    float fade = 1f - Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ORBIT_END) / ORBIT_ENERGY_FADE_TIME));
    if (upgradeTimer < ALL_ENTER_ORBIT) {
      return;
    }
    float energyIn = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ALL_ENTER_ORBIT) / ORBIT_ENERGY_FADE_IN));
    float effect = fade * energyIn;
    if (effect <= 0.001f) {
      return;
    }

    float pulse = 0.5f + 0.5f * Mathf.absin(upgradeTimer * 0.16f, 1f, 1f);
    float radius = orbitRadius();
    Draw.blend(Blending.additive);
    Lines.stroke(1.2f + pulse * 0.9f);
    Draw.color(CARD_LIGHT, (0.18f + pulse * 0.12f) * effect);
    Lines.circle(x, y, radius);
    Lines.stroke(0.7f);
    Draw.color(Pal.accent, (0.22f + pulse * 0.1f) * effect);
    Lines.circle(x, y, radius * (0.72f + pulse * 0.025f));

    float dashRadius = radius * (1.13f + pulse * 0.01f);
    float spin = upgradeTimer * 0.72f;
    Lines.stroke(0.9f + pulse * 0.8f);
    Draw.color(Color.white, 0.16f * effect);
    for (int i = 0; i < 12; i += 2) {
      float start = spin + i * 30f;
      Lines.line(
          x + Angles.trnsx(start, dashRadius),
          y + Angles.trnsy(start, dashRadius),
          x + Angles.trnsx(start + 17f, dashRadius),
          y + Angles.trnsy(start + 17f, dashRadius));
    }

    for (int i = 0; i < 10; i++) {
      float sparkle = 0.5f + 0.5f * Mathf.sin(upgradeTimer * 0.23f + i * 2.07f);
      float angle = -spin * 1.35f + i * 36f;
      float sparkRadius = radius * (0.84f + (i % 3) * 0.13f);
      Draw.color(i % 2 == 0 ? Color.white : CARD_LIGHT, sparkle * 0.42f * effect);
      Fill.circle(
          x + Angles.trnsx(angle, sparkRadius),
          y + Angles.trnsy(angle, sparkRadius),
          0.7f + sparkle * 1.4f);
    }
    Draw.blend();
    Draw.reset();
  }

  private void drawEnergyLink(int index, float alpha) {
    float entryTime = LAUNCH_START + index * LAUNCH_STAGGER + FLIGHT_TIME;
    float assemblyStart = ORBIT_END + index * ASSEMBLY_STAGGER;
    if (upgradeTimer < entryTime || upgradeTimer >= assemblyStart + ASSEMBLY_TIME * PART_SWAP_START) {
      return;
    }

    float fade = upgradeTimer < assemblyStart ? 1f
        : 1f - Mathf.clamp((upgradeTimer - assemblyStart) / (ASSEMBLY_TIME * PART_SWAP_START));
    float linkIn = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - entryTime) / ENERGY_LINK_FADE_IN));
    float linkAlpha = alpha * fade * linkIn;
    Draw.blend(Blending.additive);
    Draw.color(CARD_LIGHT, 0.3f * linkAlpha);
    Lines.stroke(0.8f + fade * 1.2f);
    Lines.beginLine();
    Lines.linePoint(x, y);

    float dx = cardPos.x - x;
    float dy = cardPos.y - y;
    float nx = -dy;
    float ny = dx;
    float length = Math.max(1f, Mathf.dst(dx, dy));
    nx /= length;
    ny /= length;

    for (int i = 1; i <= 5; i++) {
      float t = i / 6f;
      float wave = Mathf.sin(upgradeTimer * 0.22f + index * 1.7f + i * 1.3f) * 4.5f * Mathf.sin(t * Mathf.PI);
      Lines.linePoint(x + dx * t + nx * wave, y + dy * t + ny * wave);
    }
    Lines.endLine(false);

    for (int spark = 0; spark < 2; spark++) {
      float travel = (upgradeTimer * (0.042f + spark * 0.008f)
          + index * 0.17f + spark * 0.5f) % 1f;
      float wave = Mathf.sin(
          upgradeTimer * 0.22f + index * 1.7f + travel * Mathf.PI * 5f) * 4.5f
          * Mathf.sin(travel * Mathf.PI);
      float sparkX = x + dx * travel + nx * wave;
      float sparkY = y + dy * travel + ny * wave;
      Draw.color(spark == 0 ? Color.white : CARD_LIGHT, 0.55f * linkAlpha);
      Fill.circle(sparkX, sparkY, 2.2f + spark * 0.9f);
    }
    Draw.blend();
    Draw.reset();
  }

  private void drawWholeGlow() {
    float assembly = Mathf.clamp((upgradeTimer - ORBIT_END)
        / Math.max(1f, ALL_PARTS_READY - ORBIT_END));
    float charge = Interp.smooth.apply(assembly);
    float fin = Mathf.clamp((upgradeTimer - ALL_PARTS_READY) / FINAL_GLOW_TIME);
    float fadeStart = ALL_PARTS_READY + FINAL_GLOW_TIME;
    float fade = 1f - Interp.smooth.apply(
        Mathf.clamp((upgradeTimer - fadeStart) / EFFECT_FADE_TIME));
    float appear = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ORBIT_END) / WHOLE_GLOW_FADE_IN));
    float effect = fade * appear;
    if (effect <= 0.001f) {
      return;
    }
    float pulse = 0.5f + 0.5f * Mathf.absin(upgradeTimer * 0.13f, 1f, 1f);
    float intensity = 0.35f + charge * 0.75f + fin * 0.25f;
    float radius = hitSize * (2.1f + charge * 0.5f) + 76f;

    Draw.blend(Blending.additive);

    float outerLightAlpha = (0.07f + intensity * 0.1f) * effect;
    Fill.light(
        x,
        y,
        24,
        radius * (1.02f + pulse * 0.08f),
        Tmp.c1.set(CARD_LIGHT).a(outerLightAlpha),
        Color.clear);
    Draw.color(Color.white, (0.08f + charge * 0.12f + fin * 0.18f) * effect);
    Fill.circle(x, y, 13f + pulse * 7f + fin * 16f);

    float fadeRadius = radius * (1.18f + (1f - fade) * 0.24f);
    Fill.light(
        x,
        y,
        32,
        fadeRadius,
        Tmp.c1.set(CARD_LIGHT).a(0.18f * effect),
        Color.clear);
    Lines.stroke(2.2f + effect * 2.2f);
    Draw.color(Color.white, 0.38f * effect);
    Lines.circle(x, y, fadeRadius);

    for (int ring = 0; ring < 3; ring++) {
      float ringRadius = radius * (0.68f + ring * 0.21f + pulse * 0.015f);
      float spin = upgradeTimer * (0.75f + ring * 0.32f) * (ring % 2 == 0 ? 1f : -1f);
      int segments = 18 + ring * 6;
      Lines.stroke(1.4f + ring * 0.55f + charge * 0.7f);
      Draw.color(ring == 1 ? Color.white : CARD_LIGHT, (0.16f + charge * 0.2f) * effect);

      for (int i = 0; i < segments; i += 2) {
        float angle1 = spin + i * 360f / segments;
        float angle2 = angle1 + 360f / segments * 0.56f;
        Lines.line(
            x + Angles.trnsx(angle1, ringRadius),
            y + Angles.trnsy(angle1, ringRadius),
            x + Angles.trnsx(angle2, ringRadius),
            y + Angles.trnsy(angle2, ringRadius));
      }
    }

    float haloRadius = radius * (1.48f + pulse * 0.025f);
    float haloSpin = -upgradeTimer * 0.46f;
    Lines.stroke(0.8f + pulse * 1.1f);
    Draw.color(Color.white, (0.08f + charge * 0.08f) * effect);
    for (int i = 0; i < 8; i++) {
      float start = haloSpin + i * 45f;
      Lines.line(
          x + Angles.trnsx(start, haloRadius),
          y + Angles.trnsy(start, haloRadius),
          x + Angles.trnsx(start + 24f, haloRadius),
          y + Angles.trnsy(start + 24f, haloRadius));
    }

    for (int i = 0; i < 14; i++) {
      float flicker = 0.5f + 0.5f * Mathf.sin(upgradeTimer * 0.18f + i * 2.31f);
      float angle = -upgradeTimer * (0.55f + (i % 3) * 0.07f) + i * (360f / 14f);
      float sparkRadius = radius * (1.14f + (i % 4) * 0.07f + flicker * 0.03f);
      Draw.color(i % 3 == 0 ? Color.white : CARD_LIGHT, flicker * 0.45f * effect);
      Fill.circle(
          x + Angles.trnsx(angle, sparkRadius),
          y + Angles.trnsy(angle, sparkRadius),
          0.8f + flicker * 1.6f);
      if (i % 2 == 0) {
        Lines.stroke(0.6f + flicker * 0.8f);
        Draw.color(CARD_LIGHT, flicker * 0.18f * effect);
        Lines.line(
            x + Angles.trnsx(angle, sparkRadius * 0.92f),
            y + Angles.trnsy(angle, sparkRadius * 0.92f),
            x + Angles.trnsx(angle, sparkRadius * 1.12f),
            y + Angles.trnsy(angle, sparkRadius * 1.12f));
      }
    }

    float flare = (0.06f + fin * 0.16f) * effect;
    Lines.stroke(1.2f + pulse * 1.1f + fin * 1.8f);
    Draw.color(Color.white, flare);
    for (int i = 0; i < 4; i++) {
      float angle = upgradeTimer * 0.22f + i * 90f;
      float inner = radius * (0.52f + fin * 0.12f);
      float outer = radius * (1.26f + fin * 0.34f + pulse * 0.04f);
      Lines.line(
          x + Angles.trnsx(angle, inner),
          y + Angles.trnsy(angle, inner),
          x + Angles.trnsx(angle, outer),
          y + Angles.trnsy(angle, outer));
    }

    for (int i = 0; i < 12; i++) {
      float angle = upgradeTimer * 0.42f + i * 30f;
      float flicker = 0.5f + 0.5f * Mathf.sin(upgradeTimer * 0.19f + i * 1.73f);
      float inner = radius * (0.82f + flicker * 0.05f);
      float outer = radius * (1.04f + flicker * 0.16f + fin * 0.2f);
      Draw.color(i % 3 == 0 ? Color.white : CARD_LIGHT, (0.1f + charge * 0.2f) * flicker * effect);
      Lines.stroke(1f + flicker * 1.4f);
      Lines.line(
          x + Angles.trnsx(angle, inner),
          y + Angles.trnsy(angle, inner),
          x + Angles.trnsx(angle, outer),
          y + Angles.trnsy(angle, outer));
      Fill.circle(
          x + Angles.trnsx(angle, outer),
          y + Angles.trnsy(angle, outer),
          1.1f + flicker * 1.8f);
    }

    for (int i = 0; i < 2; i++) {
      float phase = ((upgradeTimer - ORBIT_END) / 75f + i * 0.5f) % 1f;
      float shockRadius = radius * (0.62f + phase * 1.12f);
      float shockAlpha = (1f - phase) * (0.18f + charge * 0.28f);
      Lines.stroke(1f + (1f - phase) * 3.2f);
      Draw.color(i == 0 ? Color.white : CARD_LIGHT, shockAlpha * effect);
      Lines.circle(x, y, shockRadius);
    }

    if (upgradeTimer >= ALL_PARTS_READY) {
      float burstIn = Interp.smooth.apply(Mathf.clamp(
          (upgradeTimer - ALL_PARTS_READY) / 16f));
      float burst = Mathf.clamp((upgradeTimer - ALL_PARTS_READY) / 95f);
      for (int ring = 0; ring < 3; ring++) {
        float phase = (burst + ring * 0.24f) % 1f;
        float burstRadius = radius + Interp.pow2Out.apply(phase) * 235f;
        Lines.stroke(6.5f * (1f - phase) + 0.9f);
        Draw.color(ring == 1 ? CARD_LIGHT : Color.white,
            (1f - phase) * (1f - phase) * 0.42f * burstIn * effect);
        Lines.circle(x, y, burstRadius);
      }

      Lines.stroke(1.1f + (1f - burst) * 2.4f);
      Draw.color(Color.white, (1f - burst) * 0.24f * burstIn * effect);
      for (int i = 0; i < 24; i++) {
        float angle = i * 15f + burst * 31f;
        float inner = radius * (0.58f + burst * 0.5f);
        float outer = radius * (1.18f + burst * 1.15f);
        Lines.line(
            x + Angles.trnsx(angle, inner),
            y + Angles.trnsy(angle, inner),
            x + Angles.trnsx(angle, outer),
            y + Angles.trnsy(angle, outer));
      }

      float flash = (1f - burst) * (1f - burst) * burstIn * effect;
      float flashRadius = radius * (0.58f + burst * 1.05f);
      Fill.light(
          x,
          y,
          28,
          flashRadius * 1.3f,
          Tmp.c1.set(Color.white).a(0.32f * flash),
          Color.clear);
      Draw.color(CARD_LIGHT, 0.42f * flash);
      Fill.circle(x, y, radius * (0.23f + (1f - burst) * 0.5f));
    }

    Draw.blend();
    Drawf.light(x, y, radius * 1.9f, Color.white, (0.5f + charge * 0.35f) * effect);
    Draw.reset();
  }

  private void drawFinalOutline() {
    if (fullOutline == null || !fullOutline.found()) {
      return;
    }

    float sweep = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ALL_PARTS_READY) / FULL_OUTLINE_GROW_TIME));
    float fadeStart = ALL_PARTS_READY + FULL_OUTLINE_GROW_TIME + FULL_OUTLINE_HOLD_TIME;
    float fade = 1f - Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - fadeStart) / FULL_OUTLINE_FADE_TIME));
    float appear = Interp.smooth.apply(Mathf.clamp(
        (upgradeTimer - ALL_PARTS_READY) / 18f));
    float alpha = fade * appear;
    if (alpha <= 0.001f) {
      return;
    }

    Draw.blend(Blending.additive);
    Draw.color(CARD_LIGHT, alpha * (0.2f + sweep * 0.3f));
    drawFullOutlineTiles(sweep);
    Draw.color(Color.white, alpha * (0.28f + sweep * 0.55f));
    drawFullOutlineTiles(sweep);
    Draw.blend();
    Draw.reset();
  }

  private void drawFullOutlineTiles(float contour) {
    int left = FULL_OUTLINE_CROP_LEFT;
    int top = FULL_OUTLINE_CROP_TOP;
    int width = FULL_OUTLINE_CROP_WIDTH;
    int height = FULL_OUTLINE_CROP_HEIGHT;
    int tileWidth = Mathf.round(width * 0.5f * contour);
    int tileHeight = Mathf.round(height * 0.5f * contour);
    if (tileWidth <= 0 || tileHeight <= 0) {
      return;
    }

    drawFullOutlineTile(left, top, tileWidth, tileHeight);
    drawFullOutlineTile(left + width - tileWidth, top, tileWidth, tileHeight);
    drawFullOutlineTile(left, top + height - tileHeight, tileWidth, tileHeight);
    drawFullOutlineTile(
        left + width - tileWidth, top + height - tileHeight, tileWidth, tileHeight);
  }

  private void drawFullOutlineTile(
      int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
    TextureRegion tile = fullOutlineTiles[
        (sourceX < fullOutline.width / 2 ? 0 : 1)
            + (sourceY < fullOutline.height / 2 ? 0 : 2)];
    tile.set(fullOutline, sourceX, sourceY, sourceWidth, sourceHeight);

    float sourceScale = fullOutline.scl();
    float fullWidth = fullOutline.width * sourceScale * Draw.xscl;
    float fullHeight = fullOutline.height * sourceScale * Draw.yscl;
    float centerX = sourceX + sourceWidth * 0.5f;
    float centerY = sourceY + sourceHeight * 0.5f;
    float localSide = centerX / fullOutline.width * fullWidth - fullWidth * 0.5f;
    float localTextureY = fullHeight * 0.5f - centerY / fullOutline.height * fullHeight;
    float drawX = x + Angles.trnsx(rotation - 90f, localSide)
        + Angles.trnsx(rotation, localTextureY);
    float drawY = y + Angles.trnsy(rotation - 90f, localSide)
        + Angles.trnsy(rotation, localTextureY);

    Draw.rect(
        tile,
        drawX,
        drawY,
        tile.width * tile.scl() * Draw.xscl,
        tile.height * tile.scl() * Draw.yscl,
        rotation - 90f);
  }

  private void launchPosition(Vec2 out) {
    float distance = orbitRadius() + 14f;
    out.set(
        x + Angles.trnsx(rotation + 90f, distance),
        y + Angles.trnsy(rotation + 90f, distance));
  }

  private void flightPosition(float flight, Vec2 out) {
    launchPosition(startPos);
    forwardPosition(targetPos);

    float progress = flightPathEase(flight);
    float inverse = 1f - progress;
    float orbitVelocity = orbitRadius() * Mathf.degRad * orbitAngularSpeed();
    float tangentDistance = orbitVelocity * FLIGHT_TIME / (3f * flightPathSpeed());
    controlPos.set(
        targetPos.x - Angles.trnsx(rotation - 90f, tangentDistance),
        targetPos.y - Angles.trnsy(rotation - 90f, tangentDistance));

    // Cubic Bezier with zero initial velocity and the orbit tangent at the end.
    float p0 = inverse * inverse * inverse;
    float p1 = 3f * inverse * inverse * progress;
    float p2 = 3f * inverse * progress * progress;
    float p3 = progress * progress * progress;
    out.set(
        startPos.x * (p0 + p1) + controlPos.x * p2 + targetPos.x * p3,
        startPos.y * (p0 + p1) + controlPos.y * p2 + targetPos.y * p3);
  }

  private void forwardPosition(Vec2 out) {
    float distance = orbitRadius();
    out.set(
        x + Angles.trnsx(rotation, distance),
        y + Angles.trnsy(rotation, distance));
  }

  private float flightPathSpeed() {
    final float accelerateTime = 0.2f;
    return 1f / (1f - accelerateTime * 0.5f);
  }

  private float flightPathEase(float progress) {
    final float accelerateTime = 0.2f;
    float speed = flightPathSpeed();

    if (progress < accelerateTime) {
      return speed * progress * progress / (2f * accelerateTime);
    }

    return speed * (progress - accelerateTime * 0.5f);
  }

  /**
   * Ease in, constant-speed flip, then ease out.
   */
  private float flipEase(float progress) {
    final float easeTime = 0.2f;
    final float cruiseSpeed = 1f / (1f - easeTime);

    if (progress < easeTime) {
      return cruiseSpeed * progress * progress / (2f * easeTime);
    }

    if (progress > 1f - easeTime) {
      float remaining = 1f - progress;
      return 1f - cruiseSpeed * remaining * remaining / (2f * easeTime);
    }

    return cruiseSpeed * (progress - easeTime * 0.5f);
  }

  private void sequencePosition(int index, float sequenceTime, Vec2 out) {
    float launchTime = LAUNCH_START + index * LAUNCH_STAGGER;
    float flight = Mathf.clamp((sequenceTime - launchTime) / FLIGHT_TIME);
    float assemblyStart = ORBIT_END + index * ASSEMBLY_STAGGER;

    if (sequenceTime < launchTime + FLIGHT_TIME) {
      flightPosition(flight, out);
    } else if (sequenceTime < assemblyStart) {
      orbitPosition(index, sequenceTime, out);
    } else {
      float assembly = Mathf.clamp((sequenceTime - assemblyStart) / ASSEMBLY_TIME);
      assemblyPosition(index, assemblyStart, assembly, out);
    }
  }

  private void orbitPosition(int index, float sequenceTime, Vec2 out) {
    float radius = orbitRadius();
    float orbitTime = Math.max(0f, sequenceTime - entryTime(index));
    float angle = rotation - orbitTime * orbitAngularSpeed();

    out.set(
        x + Angles.trnsx(angle, radius),
        y + Angles.trnsy(angle, radius));
  }

  private void assemblyPosition(
      int index, float assemblyStart, float progress, Vec2 out) {
    orbitPosition(index, assemblyStart, startPos);
    partTarget(index, targetPos);

    float orbitTime = Math.max(0f, assemblyStart - entryTime(index));
    float orbitAngle = rotation - orbitTime * orbitAngularSpeed();
    float orbitSpeed = orbitRadius() * Mathf.degRad * orbitAngularSpeed();
    float controlDistance = orbitSpeed * ASSEMBLY_TIME / 3f;
    assemblyControl.set(
        startPos.x + Angles.trnsx(orbitAngle - 90f, controlDistance),
        startPos.y + Angles.trnsy(orbitAngle - 90f, controlDistance));

    float t = Mathf.clamp(progress);
    float inverse = 1f - t;
    float p0 = inverse * inverse * inverse;
    float p1 = 3f * inverse * inverse * t;
    float p2 = 3f * inverse * t * t;
    float p3 = t * t * t;
    out.set(
        startPos.x * p0 + assemblyControl.x * p1 + targetPos.x * (p2 + p3),
        startPos.y * p0 + assemblyControl.y * p1 + targetPos.y * (p2 + p3));
  }

  private float entryTime(int index) {
    return LAUNCH_START + index * LAUNCH_STAGGER + FLIGHT_TIME;
  }

  private float orbitAngularSpeed() {
    return 360f / ORBIT_TURN_TIME;
  }

  private float partHandoff(int index) {
    float assemblyStart = ORBIT_END + index * ASSEMBLY_STAGGER;
    float revealTime = assemblyStart + ASSEMBLY_TIME + PART_OUTLINE_LEAD;
    return Mathf.clamp((upgradeTimer - revealTime) / PART_SWAP_FADE_TIME);
  }

  private float partOutline(int index) {
    float arrivalTime = ORBIT_END + index * ASSEMBLY_STAGGER + ASSEMBLY_TIME;
    float revealTime = arrivalTime + PART_OUTLINE_LEAD;

    if (upgradeTimer < arrivalTime) {
      return 0f;
    }

    if (upgradeTimer < revealTime) {
      return Mathf.clamp((upgradeTimer - arrivalTime) / PART_OUTLINE_LEAD);
    }

    return 1f - Mathf.clamp((upgradeTimer - revealTime) / PART_OUTLINE_FADE);
  }

  private float partOutlineSweep(int index) {
    float arrivalTime = ORBIT_END + index * ASSEMBLY_STAGGER + ASSEMBLY_TIME;
    return Mathf.clamp((upgradeTimer - arrivalTime) / PART_OUTLINE_LEAD);
  }

  private float orbitRadius() {
    return 88f + hitSize * 1.6f;
  }

  private void partTarget(int index, Vec2 out) {
    float sourceScale = partSource == null ? 1f : partSource.scl();
    float renderedWidth = partSourceWidth * sourceScale * Draw.xscl;
    float renderedHeight = partSourceHeight * sourceScale * Draw.yscl;
    float centerX = PART_VISUAL_X[index] / partSourceWidth;
    float centerY = PART_VISUAL_Y[index] / partSourceHeight;
    float localSide = centerX * renderedWidth - renderedWidth * 0.5f;
    // Texture rows grow downward, while unit-local rendering uses +Y as forward/up.
    float localTextureY = renderedHeight * 0.5f - centerY * renderedHeight;

    out.set(
        x + Angles.trnsx(rotation - 90f, localSide)
            + Angles.trnsx(rotation, localTextureY),
        y + Angles.trnsy(rotation - 90f, localSide)
            + Angles.trnsy(rotation, localTextureY));
  }

  private void drawPart(int index) {
    TextureRegion part = parts[index];
    drawPartRegion(
        index,
        part,
        PART_CROP_LEFT[index] + part.width * 0.5f,
        PART_CROP_TOP[index] + part.height * 0.5f);
  }

  private void drawPartRegion(
      int index, TextureRegion region, float sourceCenterX, float sourceCenterY) {
    float sourceScale = partSource == null ? 1f : partSource.scl();
    float fullWidth = partSourceWidth * sourceScale * Draw.xscl;
    float fullHeight = partSourceHeight * sourceScale * Draw.yscl;
    float localSide = sourceCenterX / partSourceWidth * fullWidth - fullWidth * 0.5f;
    float localTextureY = fullHeight * 0.5f - sourceCenterY / partSourceHeight * fullHeight;
    float drawX = x + Angles.trnsx(rotation - 90f, localSide)
        + Angles.trnsx(rotation, localTextureY);
    float drawY = y + Angles.trnsy(rotation - 90f, localSide)
        + Angles.trnsy(rotation, localTextureY);

    Draw.rect(
        region,
        drawX,
        drawY,
        region.width * region.scl() * Draw.xscl,
        region.height * region.scl() * Draw.yscl,
        rotation - 90f);
  }

  private void drawPartOutline(int index, float alpha, float sweep) {
    if (outlines[index] == null || !outlines[index].found()) {
      return;
    }

    float contour = Interp.smooth.apply(sweep);
    int tileWidth = Mathf.round(outlines[index].width * 0.5f * contour);
    int tileHeight = Mathf.round(outlines[index].height * 0.5f * contour);
    if (sweep > 0f) {
      tileWidth = Math.max(1, tileWidth);
      tileHeight = Math.max(1, tileHeight);
    }

    Draw.blend(Blending.additive);
    Draw.color(Color.white, alpha * (0.35f + contour * 0.65f));
    if (tileWidth > 0 && tileHeight > 0) {
      int width = outlines[index].width;
      int height = outlines[index].height;
      drawOutlineTile(index, 0, 0, tileWidth, tileHeight);
      drawOutlineTile(index, width - tileWidth, 0, tileWidth, tileHeight);
      drawOutlineTile(index, 0, height - tileHeight, tileWidth, tileHeight);
      drawOutlineTile(index, width - tileWidth, height - tileHeight, tileWidth, tileHeight);
    }

    Draw.blend();
    Draw.reset();
  }

  private void drawOutlineTile(
      int index, int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
    TextureRegion source = outlines[index];
    TextureRegion tile = outlineTiles[
        (sourceX == 0 ? 0 : 1) + (sourceY == 0 ? 0 : 2)];
    tile.set(source, sourceX, sourceY, sourceWidth, sourceHeight);

    drawPartRegion(
        index,
        tile,
        PART_CROP_LEFT[index] + sourceX + sourceWidth * 0.5f,
        PART_CROP_TOP[index] + sourceY + sourceHeight * 0.5f);
  }

  private static void loadRegions() {
    TextureRegion full = CUnits.kingLutian == null ? null : CUnits.kingLutian.region;
    if (cards != null && partSource == full) {
      return;
    }

    cards = new TextureRegion[CARD_COUNT];
    parts = new TextureRegion[CARD_COUNT];
    outlines = new TextureRegion[CARD_COUNT];
    partSource = full;
    fullOutline = Core.atlas.find("crystal-king-lutian-outline");

    for (int i = 0; i < CARD_COUNT; i++) {
      cards[i] = Core.atlas.find("crystal-card-spade-" + CARD_NAMES[i]);
    }

    partSourceWidth = 800;
    partSourceHeight = 896;
    for (int i = 0; i < CARD_COUNT; i++) {
      String name = String.format("crystal-lutian-part-%02d", i + 1);
      parts[i] = Core.atlas.find(name);
      outlines[i] = Core.atlas.find(name + "-outline");
    }
  }

  private boolean upgradeImmune() {
    return upgrading && !transformed;
  }

  @Override
  public void damage(float amount) {
    if (!upgradeImmune()) {
      super.damage(amount);
    }
  }

  @Override
  public void damage(float amount, boolean withEffect) {
    if (!upgradeImmune()) {
      super.damage(amount, withEffect);
    }
  }

  @Override
  public void damageArmorMult(float amount, float armorMultiplier) {
    if (!upgradeImmune()) {
      super.damageArmorMult(amount, armorMultiplier);
    }
  }

  @Override
  public void damageArmorMult(float amount, float armorMultiplier, boolean withEffect) {
    if (!upgradeImmune()) {
      super.damageArmorMult(amount, armorMultiplier, withEffect);
    }
  }

  @Override
  public void damageContinuous(float amount) {
    if (!upgradeImmune()) {
      super.damageContinuous(amount);
    }
  }

  @Override
  public void damageContinuousArmorMult(float amount, float armorMultiplier) {
    if (!upgradeImmune()) {
      super.damageContinuousArmorMult(amount, armorMultiplier);
    }
  }

  @Override
  public void damageContinuousPierce(float amount) {
    if (!upgradeImmune()) {
      super.damageContinuousPierce(amount);
    }
  }

  @Override
  public void damagePierce(float amount) {
    if (!upgradeImmune()) {
      super.damagePierce(amount);
    }
  }

  @Override
  public void damagePierce(float amount, boolean withEffect) {
    if (!upgradeImmune()) {
      super.damagePierce(amount, withEffect);
    }
  }

  @Override
  public void rawDamage(float amount) {
    if (!upgradeImmune()) {
      super.rawDamage(amount);
    }
  }

  @Override
  public void write(Writes write) {
    write.bool(upgrading);
    write.bool(transformed);
    write.f(upgradeTimer);
    write.f(upgradeRotation);
    super.write(write);
  }

  @Override
  public void read(Reads read) {
    upgrading = read.bool();
    transformed = read.bool();
    upgradeTimer = read.f();
    upgradeRotation = read.f();
    super.read(read);
  }

  @Override
  public void writeSync(Writes write) {
    write.bool(upgrading);
    write.bool(transformed);
    write.f(upgradeTimer);
    write.f(upgradeRotation);
    super.writeSync(write);
  }

  @Override
  public void readSync(Reads read) {
    upgrading = read.bool();
    transformed = read.bool();
    upgradeTimer = read.f();
    upgradeRotation = read.f();
    super.readSync(read);
  }
}
