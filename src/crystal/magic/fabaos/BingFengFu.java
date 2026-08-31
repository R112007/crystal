package crystal.magic.fabaos;

import arc.graphics.Color;
import crystal.magic.FaBao;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;

/** 冰封符：8 格半径内敌方冻结 + 减速（瞄准显示用默认圈） */
public class BingFengFu extends FaBao {
  public float freezeSec = 12f;
  public float slowSec = 20f;

  public BingFengFu() {
    super("bingfengfu", "冰封符");
    range = 8 * 8f;
    rangeColor = Color.valueOf("87ceeb");
  }

  @Override
  public void release(float x, float y) {
    if (Vars.player == null)
      return;
    Units.nearbyEnemies(Vars.player.team(), x - range, y - range, range * 2f, range * 2f, u -> {
      if (u.within(x, y, range + u.hitSize / 2f)) {
        u.apply(StatusEffects.freezing, freezeSec * 60f);
        u.apply(StatusEffects.slow, slowSec * 60f);
      }
    });
    Fx.freezing.at(x, y);
  }
}
