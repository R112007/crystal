package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.TextButton;
import arc.util.Scaling;
import crystal.core.ScrollSystem;
import crystal.magic.Scroll;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** Small scroll panel: name, explanation, then the use button. */
public class ScrollDetailDialog extends BaseDialog {
  public ScrollDetailDialog(Scroll scroll, Runnable onUsed) {
    super(Core.bundle.get("scroll.details.title", "卷轴"));
    shouldPause = true;
    float width = Math.max(240f, Math.min(380f, Core.graphics.getWidth() - 64f));

    cont.table(Styles.black6, head -> {
      head.margin(12f);
      head.image(scroll.uiIcon()).size(52f).scaling(Scaling.fit).padRight(10f);
      head.add(scroll.localizedName).color(Color.gold).get().setFontScale(1.16f);
      head.add("×" + ScrollSystem.count(scroll)).color(Color.lightGray).padLeft(8f);
    }).width(width).padBottom(8f);
    cont.row();

    String description = scroll.description;
    cont.add(description == null || description.isEmpty()
        ? Core.bundle.get("scroll.noDescription", "这张卷轴没有额外介绍。")
        : description)
        .color(Color.lightGray)
        .wrap()
        .width(width - 24f)
        .pad(10f);
    cont.row();

    boolean unlocked = scroll.targetSkill == null || scroll.targetSkill.unlocked();
    TextButton use = cont.button(
        unlocked
            ? Core.bundle.get("scroll.alreadyUnlocked", "对应技能已解锁")
            : Core.bundle.get("scroll.use", "使用"),
        Icon.ok,
        Styles.flatt,
        () -> {
          if (ScrollSystem.use(scroll)) {
            hide();
            if (onUsed != null) {
              onUsed.run();
            }
          } else {
            Vars.ui.showInfo(Core.bundle.get(
                "scroll.cannotUse", "无法使用这张卷轴。"));
          }
        }).size(Math.min(240f, width - 24f), 56f).get();
    use.setDisabled(unlocked);

    buttons.button("@close", Icon.left, this::hide).size(150f, 52f);
  }
}
