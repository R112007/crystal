package crystal.entities.abilities;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import crystal.game.WaitTime;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import static mindustry.Vars.*;

public class ReduceBoostAbility extends Ability {
  public float range, percent;
  public Color baseColor = Pal.accent;
  public Effect activeEffect, damageEffect = Fx.chainLightning;

  public ReduceBoostAbility(float range, float percent) {
    this.range = range;
    this.percent = percent;
    this.activeEffect = new Effect(4f, e -> {
      e.color = Pal.accent;
      Draw.color(e.color, 0.7f);
      Lines.stroke(e.fout() * 2f);
      Lines.circle(e.x, e.y, 4f + this.range);
    });
  }

  @Override
  public void addStats(Table t) {
    t.add(Core.bundle.format("bullet.range", Strings.autoFixed(range / tilesize, 2)));
    t.row();
    t.add(Core.bundle.format("reduceboost") + percent * 100 + "%");
    t.row();
  }

  @Override
  public String localized() {
    return Core.bundle.get("ability.reduceboostability");
  }

  @Override
  public void update(Unit unit) {
    boolean t = WaitTime.waittime(120);
    indexer.eachBlock(null, unit.x, unit.y, this.range, b -> b.team != unit.team && b.block.canOverdrive, b -> {
      b.applySlowdown(this.percent, 10f);
      activeEffect.at(unit.x, unit.y);
      if (t) {
        damageEffect.at(unit.x, unit.y, 0f, Pal.accent, b);
      }
    });
  }
}
