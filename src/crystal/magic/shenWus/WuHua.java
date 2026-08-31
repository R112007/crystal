package crystal.magic.shenWus;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import crystal.magic.ShenWu;
import mindustry.graphics.Layer;

public class WuHua extends ShenWu {

  public WuHua() {
    super("wuHua");
    magicAmount = 120f;

    // 技能1：突进 —— 无方向，瞬发，无等级成长
    addSkill(new Skill("wuHua-dash") {
      {
        baseCooldown = 4f;
        baseCost = 15f;
        maxLevel = 1;
        level = 1;
        directional = false;
        iconColor = Color.sky;
      }

      @Override
      public boolean instance(ShenWu owner, float x, float y) {
        deactivate(); // 瞬发立即结束
        return true;
      }
    });

    // 技能2：连斩 —— 扇形方向，可升级
    addSkill(new Skill("wuHua-lianZhan") {
      {
        baseCooldown = 2f;
        baseCost = 10f;
        maxLevel = 3;
        directional = true;
        sectorAngle = 120f;
        iconColor = Color.scarlet;
        // 解锁条件：在科技树对话框里通过 require() 添加，或硬编码在这里
      }

      @Override
      public boolean instance(ShenWu owner, float x, float y) {
        return true;
      }

      @Override
      public void drawWorld(ShenWu owner) {
        if (!active)
          return;
        drawDirectional(aimX, aimY, 60f + level * 10f, angle, sectorAngle, Color.scarlet);
      }

      @Override
      public void onLevelUp(int newLevel) {
        baseCooldown *= 0.9f; // 每级减 10% 冷却
      }
    });

    // 技能3：花舞 —— 持续引导，圆形范围
    addSkill(new Skill("wuHua-huaWu") {
      {
        baseCooldown = 18f;
        baseCost = 60f;
        maxLevel = 2;
        directional = false;
        sectorAngle = 360f;
        iconColor = Color.pink;
      }
      float timer = 0f, duration = 3f;

      @Override
      public boolean instance(ShenWu owner, float x, float y) {
        timer = duration;
        return true;
      }

      @Override
      public void continuous(ShenWu owner, float delta) {
        timer -= delta / 60f;
        if (timer <= 0)
          deactivate();
      }

      @Override
      public void drawWorld(ShenWu owner) {
        if (!active)
          return;
        float progress = 1f - timer / duration;
        float r = (50f + level * 20f) * progress;
        Draw.z(Layer.flyingUnit + 1f);
        Draw.color(Color.pink, 0.15f);
        Fill.circle(aimX, aimY, r);
        Draw.color(Color.pink, 0.5f);
        Lines.stroke(2f);
        Lines.circle(aimX, aimY, r);
        Draw.color();
      }

      @Override
      public void onLevelUp(int newLevel) {
        duration += 0.5f; // 每级增加 0.5 秒持续时间
      }
    });
  }
}
