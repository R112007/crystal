package crystal.entities.abilities;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import crystal.CVars;
import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Building;
import mindustry.gen.Payloadc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.RegenProjector;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.payloads.Payload;
import static mindustry.Vars.*;

public class PayloadFieldAbility extends Ability {
  float timer = 0;
  public Seq<Building> targets = new Seq<>();
  public boolean hasOver, hasRegen;

  public PayloadFieldAbility() {
  }

  @Override
  public void update(Unit unit) {
    timer += Time.delta;
    hasOver = false;
    hasRegen = false;

    updateOverrider(unit);
    updateMendProjector(unit);
    updateRegenProjector(unit);
  }

  public void updateOverrider(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload b && b.build.block instanceof OverdriveProjector over) {
          indexer.eachBlock(null, unit.x, unit.y, over.range,
              other -> other.team == unit.team && other.block.canOverdrive, other -> {
                other.applyBoost(over.speedBoost, over.reload + 1f);
              });
          hasOver = true;
        }
      }
    }
  }

  public void updateMendProjector(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof MendProjector mend) {
          if (timer >= mend.reload) {
            indexer.eachBlock(unit, mend.range, b -> b.damaged() && !b.isHealSuppressed(), other -> {
              other.heal(other.maxHealth() * (mend.healPercent) / 100f);
              other.recentlyHealed();
              Fx.healBlockFull.at(other.x, other.y, other.block.size, mend.baseColor, other.block);
              timer = 0f;
            });
          }
          hasRegen = true;
        }
      }
    }
  }

  public void updateRegenProjector(Unit unit) {
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof RegenProjector regen) {
          updateTargets(unit, regen);
          for (var b : targets) {
            if (b.damaged())
              b.heal(regen.healPercent);
          }
          hasRegen = true;
        }
      }
    }

  }

  public void updateTargets(Unit unit, RegenProjector regen) {
    targets.clear();
    indexer.eachBlock(unit.team, Tmp.r1.setCentered(unit.x, unit.y, regen.range * tilesize), b -> true, targets::add);
  }

  @Override
  public void draw(Unit unit) {
    TextureRegion back = Core.atlas.find("crystal-function");
    TextureRegion over1 = Core.atlas.find("crystal-overrider");
    TextureRegion regen1 = Core.atlas.find("crystal-regen");
    float z = unit.elevation > 0.5f ? unit.type.flyingLayer : unit.type.groundLayer + unit.hitSize / 4000f;
    Draw.z(z + 0.01f);
    float rotation = unit.rotation - 90;
    float wx = unit.x + Angles.trnsx(rotation, 0, -18),
        wy = unit.y + Angles.trnsy(rotation, 0, -18);
    float wx1 = unit.x + Angles.trnsx(rotation, 0, -16),
        wy1 = unit.y + Angles.trnsy(rotation, 0, -16);
    Draw.color(Color.valueOf("#FFD37F"));
    Draw.rect(back, wx, wy, rotation);
    Draw.blend(Blending.additive);
    if (hasOver) {
      Draw.z(z + 0.02f);
      Draw.color(Color.valueOf("#FF9166"));
      Draw.rect(over1, wx1, wy1, rotation);
    }
    if (hasRegen) {
      Draw.z(z + 0.03f);
      Draw.color(Pal.heal);
      Draw.rect(regen1, wx1, wy1, rotation);
    }
    Draw.blend();
    Draw.reset();
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload b && b.build.block instanceof OverdriveProjector over) {
          indexer.eachBlock(unit, over.range, other -> other.block.canOverdrive,
              other -> Drawf.selected(other, Tmp.c1.set(over.baseColor).a(Mathf.absin(4f, 1f))));

          Drawf.dashCircle(unit.x, unit.y, over.range, over.baseColor);
          hasOver = true;
        }
      }
    }
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof MendProjector mend) {
          indexer.eachBlock(unit, mend.range, other -> other.block.canOverdrive,
              other -> Drawf.selected(other, Tmp.c1.set(mend.baseColor).a(Mathf.absin(4f, 1f))));

          Drawf.dashCircle(unit.x, unit.y, mend.range, mend.baseColor);
          hasRegen = true;
        }
      }
    }
    if (unit instanceof Payloadc pay) {
      for (Payload p : pay.payloads()) {
        if (p instanceof BuildPayload bp && bp.build.block instanceof RegenProjector regen) {
          Drawf.dashSquare(regen.baseColor, unit.x, unit.y, regen.range * tilesize);
          for (var target : targets) {
            Drawf.selected(target, Tmp.c1.set(regen.baseColor).a(Mathf.absin(4f, 1f)));
          }
        }
      }
    }
  }
}
