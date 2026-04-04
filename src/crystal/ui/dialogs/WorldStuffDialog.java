package crystal.ui.dialogs;

import crystal.ui.WorldStuff;
import mindustry.ui.dialogs.BaseDialog;

import static arc.Core.*;

public class WorldStuffDialog extends BaseDialog {
  public int row = 0;

  public WorldStuffDialog() {
    super("");
    row = settings.getInt("stuffdialog.row", 0);
    init();
    addCloseButton();
  }

  public void init() {
    for (var stuff : WorldStuff.stuffs) {
      addStuff(stuff);
    }
  }

  // TODO 横屏竖屏显示的个数换行不同
  public void addStuff(WorldStuff stuff) {
    this.cont.pane(table1 -> {
      table1.left();
      if (row % 2 == 0)
        table1.row();
      table1.button("", stuff.icon, () -> {
        var dialog = new BaseDialog(stuff.name);
        dialog.cont.pane(table2 -> {
          table2.image(atlas.find("crystal-" + stuff.name)).size(280f).center().row();
          table2.row();
          table2.add(stuff.description).row();
        });
        dialog.addCloseButton();
        dialog.show();
      }).right().pad(2f).size(80);
    });
    row++;
    settings.put("stuffdialog.row", row);
    settings.manualSave();
  }
}
