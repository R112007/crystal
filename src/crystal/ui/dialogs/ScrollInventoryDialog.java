package crystal.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Button;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import crystal.core.ScrollSystem;
import crystal.magic.Scroll;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** Grid of every obtained scroll that has not been consumed yet. */
public class ScrollInventoryDialog extends BaseDialog {
  private Table grid;
  private float contentWidth = 420f;

  public ScrollInventoryDialog() {
    super(Core.bundle.get("scroll.inventory.title", "未使用卷轴"));
    shouldPause = true;
    addCloseButton();
    shown(this::rebuild);
    onResize(this::rebuild);
  }

  private void rebuild() {
    contentWidth = Math.max(240f, Math.min(420f, Core.graphics.getWidth() - 72f));
    cont.clear();
    cont.margin(10f);

    ScrollPane pane = new ScrollPane(grid = new Table(), Styles.smallPane);
    pane.setScrollingDisabled(true, false);
    pane.setFadeScrollBars(false);
    cont.add(pane).grow().width(contentWidth + 20f)
        .maxHeight(Math.max(240f, Core.graphics.getHeight() - 180f));

    Seq<Scroll> owned = ScrollSystem.owned();
    if (owned.isEmpty()) {
      grid.add(Core.bundle.get("scroll.inventory.empty", "尚未获得未使用的卷轴"))
          .color(Color.darkGray)
          .wrap()
          .width(contentWidth - 24f)
          .pad(24f);
      return;
    }

    int columns = contentWidth < 360f ? 2 : 3;
    float cardWidth = (contentWidth - 24f) / columns;
    int index = 0;
    for (Scroll scroll : owned) {
      Button card = new Button(Tex.button);
      card.margin(6f);
      card.image(scroll.uiIcon()).size(54f).scaling(Scaling.fit);
      card.row();
      card.add(scroll.localizedName).wrap().width(cardWidth - 18f)
          .get().setFontScale(0.82f);
      card.row();
      card.add("×" + ScrollSystem.count(scroll)).color(Color.lightGray)
          .get().setFontScale(0.78f);
      card.clicked(() -> new ScrollDetailDialog(scroll, this::rebuild).show());
      grid.add(card).size(cardWidth, 120f).pad(4f);
      if (++index % columns == 0) {
        grid.row();
      }
    }
  }
}
