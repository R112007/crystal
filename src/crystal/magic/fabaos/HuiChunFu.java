package crystal.magic.fabaos;

import arc.graphics.Color;
import crystal.magic.FaBao;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;

/** 回春符：8 格半径内友方单位按最大生命百分比回复（瞄准显示用默认圈） */
public class HuiChunFu extends FaBao {
  public float healPercent = 0.5f;

  public HuiChunFu() {
    super("huichunfu", "回春符");
    range = 8 * 8f;
    rangeColor = Color.valueOf("84f491");
  }

  @Override
  public void release(float x, float y) {
    if (Vars.player == null)
      return;
    Units.nearby(Vars.player.team(), x - range, y - range, range * 2f, range * 2f, u -> {
      if (u.within(x, y, range + u.hitSize / 2f)) {
        u.heal(u.maxHealth() * healPercent);
      }
    });
    Fx.healWave.at(x, y);
  }
}
