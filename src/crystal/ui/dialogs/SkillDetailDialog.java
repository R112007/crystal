package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.util.Scaling;
import crystal.magic.SkillUnlockable;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** Shared skill explanation used by the cultivation panel and scroll unlock flow. */
public class SkillDetailDialog extends BaseDialog {
  public SkillDetailDialog(SkillUnlockable skill, boolean justUnlocked) {
    super(Core.bundle.get(
        justUnlocked ? "skill.unlocked.title" : "skill.details.title",
        justUnlocked ? "技能已解锁" : "技能详情"));
    shouldPause = true;
    float width = Math.max(240f, Math.min(420f, Core.graphics.getWidth() - 64f));

    cont.table(Styles.black6, t -> {
      t.margin(14f);
      t.image(skill.unlockIcon()).size(64f).scaling(Scaling.fit).padRight(12f);
      t.table(info -> {
        info.left();
        info.add(skill.displayName()).color(Color.gold).get().setFontScale(1.25f);
        info.row();
        info.add(Core.bundle.get("skill.unlocked.state", "已解锁"))
            .color(Color.green).get().setFontScale(0.9f);
      }).left().growX();
    }).width(width).padBottom(10f);
    cont.row();

    String description = skill.displayDescription();
    cont.add(description == null || description.isEmpty()
        ? Core.bundle.get("skill.noDescription", "暂无技能介绍")
        : description)
        .color(Color.lightGray)
        .wrap()
        .width(width - 28f)
        .left()
        .pad(8f, 14f, 12f, 14f);

    buttons.button("@close", Icon.left, this::hide).size(180f, 56f);
  }
}
