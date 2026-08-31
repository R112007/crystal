package crystal.magic.fabaos;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import crystal.magic.FaBao;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;

/** 落雷符：10 格半径雷击，伤害受修为档位加成 */
public class LuoLeiFu extends FaBao {
  public float baseDamage = 1200f;

  public LuoLeiFu() {
    super("luoleifu", "落雷符");
    range = 8 * 10f;
    rangeColor = Color.gold;
    effectedByXiuWei = true;
  }

  @Override
  public void release(float x, float y) {
    if (Vars.player == null)
      return;
    Damage.damage(Vars.player.team(), x, y, range, baseDamage * powerScale());
    Fx.chainLightning.at(x, y);
    Fx.bigShockwave.at(x, y, Color.gold);
  }

  /** 自定义瞄准显示：默认圈之外再加八道雷纹辐条 */
  @Override
  public void drawBeforeRelease(float x, float y) {
    super.drawBeforeRelease(x, y);
    Draw.z(Layer.flyingUnit + 1f);
    Draw.color(rangeColor, 0.6f);
    Lines.stroke(1f);
    for (int i = 0; i < 8; i++) {
      float cx = Mathf.cosDeg(i * 45f), cy = Mathf.sinDeg(i * 45f);
      Lines.line(x + cx * range * 0.4f, y + cy * range * 0.4f,
          x + cx * range * 0.95f, y + cy * range * 0.95f);
    }
    Draw.reset();
  }
}
