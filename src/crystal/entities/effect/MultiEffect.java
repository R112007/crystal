package crystal.entities.effect;

import arc.graphics.Color;
import mindustry.entities.Effect;

public class MultiEffect extends Effect {
  public Effect[] effects = {};

  public MultiEffect() {
  }

  public MultiEffect(Effect... effects) {
    this.effects = effects;
    this.lifetime = 0;
    for (var effect : effects) {
      effect.init();
      this.lifetime = Math.max(this.lifetime, effect.lifetime);
    }
  }

  @Override
  public void create(float x, float y, float rotation, Color color, Object data) {
    if (!shouldCreate())
      return;

    for (var effect : effects) {
      effect.create(x, y, rotation, color, data);
    }
  }
}
