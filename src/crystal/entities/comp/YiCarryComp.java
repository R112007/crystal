package crystal.entities.comp;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import crystal.content.CIcons;
import crystal.gen.Corec;
import crystal.gen.YiCarryc;
import crystal.world.blocks.stroage.MobileCoreControlSystem;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.EntityDef;
import ent.anno.Annotations.Import;
import mindustry.game.Team;
import mindustry.gen.Drawc;
import mindustry.gen.Timedc;
import mindustry.graphics.Layer;
import mindustry.input.InputHandler;
import mindustry.type.ItemStack;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import static mindustry.Vars.*;

@EntityDef(value = { YiCarryc.class }, serialize = false)
@EntityComponent
public abstract class YiCarryComp implements Drawc, Timedc, YiCarryc {
  @Import
  public float x, y;
  @Import
  public float lifetime;

  public ItemStack[] items = new ItemStack[0];
  public float targetX, targetY;
  public float startX;
  public float stageTime;
  public int stage;
  public int pickupIndex;
  public boolean pickupInFlight;

  private static final int STAGE_APPROACH = 0;
  private static final int STAGE_FADE_IN = 1;
  private static final int STAGE_STEP = 2;
  private static final int STAGE_TOSS = 3;
  private static final int STAGE_RELAX = 4;
  private static final int STAGE_FADE_OUT = 5;
  private static final int STAGE_WAITING = 6;

  private static final float APPROACH_TIME = 45f;
  private static final float FADE_IN_TIME = 24f;
  private static final float STEP_TIME = 30f;
  private static final float TOSS_TIME = 42f;
  private static final float RELAX_TIME = 90f;
  private static final float FADE_OUT_TIME = 36f;

  private static final float APPROACH_DISTANCE = 120f;
  private static final float HOLD_OFFSET = 50f;
  private static final float STEP_OFFSET = 30f;
  private static final float PICKUP_RANGE = 28f;

  private static final float YI_WIDTH = 20f;
  private static final float YI_HEIGHT = 30.833334f;
  private static final float BOX_WIDTH = 18f;
  private static final float BOX_HEIGHT = 17.389832f;

  public void setup(float targetX, float targetY, ItemStack[] sourceItems) {
    this.targetX = targetX;
    this.targetY = targetY;
    this.startX = targetX - APPROACH_DISTANCE;
    this.x = startX;
    this.y = targetY;
    this.lifetime = Float.MAX_VALUE;
    this.stage = STAGE_APPROACH;
    this.stageTime = 0f;
    this.pickupIndex = 0;
    this.pickupInFlight = false;
    this.items = copyItems(sourceItems);
  }

  @Override
  public void update() {
    stageTime += Time.delta;

    if (stage == STAGE_APPROACH) {
      float progress = Mathf.clamp(stageTime / APPROACH_TIME, 0f, 1f);
      x = Mathf.lerp(startX, targetX - HOLD_OFFSET, easeOut(progress));
      y = targetY;
      if (progress >= 1f) {
        x = targetX - HOLD_OFFSET;
        enterStage(STAGE_FADE_IN);
      }
    } else if (stage == STAGE_FADE_IN) {
      if (stageTime >= FADE_IN_TIME) {
        enterStage(STAGE_STEP);
      }
    } else if (stage == STAGE_STEP) {
      float progress = Mathf.clamp(stageTime / STEP_TIME, 0f, 1f);
      x = Mathf.lerp(targetX - HOLD_OFFSET, targetX - STEP_OFFSET, easeInOut(progress));
      if (progress >= 1f) {
        x = targetX - STEP_OFFSET;
        enterStage(STAGE_TOSS);
      }
    } else if (stage == STAGE_TOSS) {
      if (stageTime >= TOSS_TIME) {
        enterStage(STAGE_RELAX);
      }
    } else if (stage == STAGE_RELAX) {
      if (stageTime >= RELAX_TIME) {
        enterStage(STAGE_FADE_OUT);
      }
    } else if (stage == STAGE_FADE_OUT) {
      if (stageTime >= FADE_OUT_TIME) {
        x = targetX;
        y = targetY;
        enterStage(STAGE_WAITING);
        if (!hasItems()) {
          remove();
        }
      }
    } else if (stage == STAGE_WAITING) {
      if (!hasItems()) {
        remove();
        return;
      }
      tryPickup();
    }
  }

  private void enterStage(int nextStage) {
    stage = nextStage;
    stageTime = 0f;
  }

  private boolean hasItems() {
    if (items == null || items.length == 0) {
      return false;
    }
    for (int i = pickupIndex; i < items.length; i++) {
      ItemStack stack = items[i];
      if (stack != null && stack.item != null && stack.amount > 0) {
        return true;
      }
    }
    return false;
  }

  private void tryPickup() {
    if (pickupInFlight || player == null || player.unit() == null || player.team() == null) {
      return;
    }

    float playerX = player.unit().x();
    float playerY = player.unit().y();
    if (Mathf.dst(playerX, playerY, targetX, targetY) > PICKUP_RANGE) {
      return;
    }

    CoreBuild core = closestUsableCore(player.team());
    if (core == null || core.items == null) {
      return;
    }

    int space = Math.max(0, core.storageCapacity - core.items.total());
    if (space <= 0) {
      return;
    }

    ItemStack found = null;
    while (pickupIndex < items.length) {
      found = items[pickupIndex];
      if (found != null && found.item != null && found.amount > 0) {
        break;
      }
      pickupIndex++;
      found = null;
    }

    if (found == null) {
      remove();
      return;
    }

    final ItemStack stack = found;
    final CoreBuild targetCore = core;
    final int transferAmount = Math.min(stack.amount, space);
    final var item = stack.item;
    pickupInFlight = true;

    InputHandler.createItemTransfer(item, transferAmount, targetX, targetY, targetCore, () -> {
      pickupInFlight = false;

      if (targetCore.isValid() && targetCore.items != null) {
        int accepted = Math.min(transferAmount,
            Math.max(0, targetCore.storageCapacity - targetCore.items.total()));
        if (accepted > 0) {
          targetCore.items.add(item, accepted);
          stack.amount -= accepted;
        }
      }

      if (stack.amount <= 0) {
        pickupIndex++;
      }
      if (!hasItems()) {
        items = new ItemStack[0];
        remove();
      }
    });
  }

  private CoreBuild closestUsableCore(Team team) {
    CoreBuild nearest = state.teams.closestCore(targetX, targetY, team);
    if (nearest != null && !nearest.isValid()) {
      nearest = null;
    }

    Corec mobileCore = MobileCoreControlSystem.findPlayerCore(player);
    if (mobileCore == null) {
      return nearest;
    }

    CoreBuild proxy = mobileCore.proxy();
    if (proxy == null || !proxy.isValid() || proxy.team != team || proxy.items == null) {
      return nearest;
    }

    if (nearest == null
        || Mathf.dst2(targetX, targetY, proxy.x, proxy.y) < Mathf.dst2(targetX, targetY, nearest.x, nearest.y)) {
      return proxy;
    }
    return nearest;
  }

  private static ItemStack[] copyItems(ItemStack[] source) {
    if (source == null || source.length == 0) {
      return new ItemStack[0];
    }

    int count = 0;
    for (ItemStack stack : source) {
      if (stack != null && stack.item != null && stack.amount > 0) {
        count++;
      }
    }

    ItemStack[] result = new ItemStack[count];
    int index = 0;
    for (ItemStack stack : source) {
      if (stack != null && stack.item != null && stack.amount > 0) {
        result[index++] = new ItemStack(stack.item, stack.amount);
      }
    }
    return result;
  }

  @Override
  public void draw() {
    if (CIcons.yi_normal == null || CIcons.yi_relax == null || CIcons.box == null) {
      return;
    }

    float previousZ = Draw.z();
    Draw.z(Layer.flyingUnit + 5f);

    if (stage == STAGE_APPROACH) {
      Draw.z(previousZ);
      return;
    }

    boolean yiVisible = stage != STAGE_WAITING;
    boolean boxVisible = stage != STAGE_APPROACH;
    float yiAlpha = 1f;
    float boxAlpha = 1f;
    if (stage == STAGE_FADE_IN) {
      float fade = stageProgress(FADE_IN_TIME);
      yiAlpha = fade;
      boxAlpha = fade;
    } else if (stage == STAGE_FADE_OUT) {
      yiAlpha = 1f - stageProgress(FADE_OUT_TIME);
    }

    if (boxVisible) {
      drawBox(boxAlpha);
    }

    if (yiVisible) {
      drawYi(yiAlpha);
    }

    Draw.color();
    Draw.alpha(1f);
    Draw.z(previousZ);
  }

  private void drawBox(float alpha) {
    TextureRegion boxTexture = CIcons.box.getRegion();
    float boxX = targetX;
    float boxY = targetY;
    float rotation = 0f;

    if (stage == STAGE_FADE_IN || stage == STAGE_STEP) {
      boxX = x - 3f;
      boxY = y + 8f;
    } else if (stage == STAGE_TOSS) {
      float progress = stageProgress(TOSS_TIME);
      boxX = Mathf.lerp(x - 3f, targetX, progress);
      boxY = Mathf.lerp(y + 8f, targetY,
          progress) + Mathf.sin(progress * Mathf.pi) * 40f;
      rotation = progress * 270f;
    }

    float boxScale = 1f;
    if (stage == STAGE_FADE_IN) {
      boxScale = 0.85f + alpha * 0.15f;
    }

    Draw.color(Color.white);
    Draw.alpha(alpha);
    Draw.rect(boxTexture, boxX, boxY, BOX_WIDTH * boxScale,
        BOX_HEIGHT * boxScale, rotation);
  }

  private void drawYi(float alpha) {
    TextureRegion yiTexture = stage >= STAGE_RELAX
        ? CIcons.yi_relax.getRegion()
        : CIcons.yi_normal.getRegion();
    float scale = 1f;

    if (stage == STAGE_STEP) {
      float progress = stageProgress(STEP_TIME);
      scale += Mathf.sin(progress * Mathf.pi) * 0.12f;
      scale += Mathf.sin(progress * Mathf.pi * 5f) * 0.025f * (1f - progress);
    }

    Draw.color(Color.white);
    Draw.alpha(alpha);
    Draw.rect(yiTexture, x, y + YI_HEIGHT * 0.5f,
        YI_WIDTH * scale, YI_HEIGHT * scale, 0f);
  }

  private float stageProgress(float duration) {
    return Mathf.clamp(stageTime / duration, 0f, 1f);
  }

  private static float easeOut(float value) {
    float inverse = 1f - value;
    return 1f - inverse * inverse;
  }

  private static float easeInOut(float value) {
    return value * value * (3f - 2f * value);
  }
}
