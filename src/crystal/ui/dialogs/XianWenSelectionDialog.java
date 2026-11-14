package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Button;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import crystal.core.CultivationState;
import crystal.core.XianWenSystem;
import crystal.magic.XianWen;
import mindustry.gen.Tex;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** 核心发射前的仙纹选择页。 */
public class XianWenSelectionDialog extends BaseDialog {
  private float contentWidth = 420f;

  public XianWenSelectionDialog() {
    super(Core.bundle.get("xianwen.dialog.title", "仙纹配置"));
    shouldPause = true;
    addCloseButton();
    shown(this::rebuild);
    onResize(this::rebuild);
  }

  private void rebuild() {
    contentWidth = Math.max(240f,
        Math.min(460f, Core.graphics.getWidth()
            - (Core.graphics.isPortrait() ? 56f : 96f)));

    cont.clear();
    cont.margin(10f);
    cont.defaults().pad(4f);

    cont.add(Core.bundle.format(
        "xianwen.dialog.realm",
        CultivationState.currentXiuWei().str)).left().row();
    cont.add(new Bar(
        () -> Core.bundle.format(
            "xianwen.loadout.capacity",
            XianWenSystem.usedCapacity(),
            XianWenSystem.capacity()),
        () -> Color.gold,
        () -> XianWenSystem.capacity() <= 0 ? 0f
            : Math.min(1f, XianWenSystem.usedCapacity()
                / (float) XianWenSystem.capacity())))
        .growX().height(34f).row();
    cont.add(Core.bundle.get(
        "xianwen.dialog.hint",
        "点击仙纹切换携带状态；确认核心发射后，当前配置将成为本次运行的快照。"))
        .color(Color.lightGray).wrap().width(contentWidth - 24f).left().row();

    Table grid = new Table();
    ScrollPane pane = new ScrollPane(grid, Styles.smallPane);
    pane.setFadeScrollBars(false);
    pane.setScrollingDisabled(true, false);
    cont.add(pane).growX()
        .height(Math.max(190f, Core.graphics.getHeight() - 300f))
        .row();

    if (XianWen.list.isEmpty()) {
      grid.add(Core.bundle.get("xianwen.empty", "尚未获得任何仙纹"))
          .color(Color.darkGray)
          .wrap()
          .width(contentWidth - 44f)
          .pad(28f);
      return;
    }

    int columns = contentWidth < 390f ? 1 : 2;
    float cardWidth = (contentWidth - 32f) / columns;
    int index = 0;
    for (XianWen xianWen : XianWen.list) {
      boolean selected = XianWenSystem.isSelected(xianWen);
      boolean selectable = selected || XianWenSystem.canSelect(xianWen);

      Button card = new Button(Tex.button);
      card.margin(7f);
      card.left();
      card.image(xianWen.uiIcon()).size(42f).scaling(Scaling.fit)
          .color(xianWen.usingFallbackIcon() ? xianWen.iconColor : Color.white)
          .padRight(8f);
      card.table(info -> {
        info.left();
        info.add(xianWen.localizedName)
            .color(selected ? Color.green : Color.white)
            .left().row();
        info.add(Core.bundle.format("xianwen.loadCost", xianWen.loadCost))
            .color(Color.lightGray).fontScale(0.78f).left().row();
        info.add(selected
            ? Core.bundle.get("xianwen.state.selected", "已携带")
            : xianWen.unlocked()
                ? Core.bundle.get("xianwen.state.select", "点击携带")
                : Core.bundle.get("xianwen.state.locked", "未解锁"))
            .color(selected ? Color.green : Color.lightGray)
            .fontScale(0.78f).left();
      }).left().growX();

      String description = xianWen.description;
      if (description != null && !description.isBlank()) {
        card.row();
        card.add(description).color(Color.lightGray)
            .wrap().width(cardWidth - 24f)
            .fontScale(0.78f).padTop(5f).left();
      }

      card.clicked(() -> {
        XianWenSystem.toggle(xianWen);
        rebuild();
      });
      card.setDisabled(!selectable);
      grid.add(card).width(cardWidth).height(112f).pad(4f).left();
      if (++index % columns == 0) {
        grid.row();
      }
    }
  }
}
