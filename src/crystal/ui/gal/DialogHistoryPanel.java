package crystal.ui.gal;

import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

/**
 * 对话历史记录面板
 */
public class DialogHistoryPanel extends Dialog {

  private final Table contentTable;

  public DialogHistoryPanel() {
    super("", Styles.fullDialog);
    this.contentTable = new Table();

    // 构建面板
    build();
  }

  private void build() {
    // 标题
    this.titleTable.add("对话历史").center().padTop(12f).fontScale(1.2f).row();
    this.titleTable.image().color(arc.graphics.Color.valueOf("4a4a4a")).height(2f).growX().pad(8f);

    // 滚动内容区
    ScrollPane scrollPane = new ScrollPane(contentTable, Styles.defaultPane);
    scrollPane.setFadeScrollBars(false);
    scrollPane.setScrollingDisabled(true, false);
    this.cont.add(scrollPane).grow().pad(12f);

    // 关闭按钮
    this.buttons.button("关闭", Styles.flatt, this::hide).size(200f, 50f);
  }

  /**
   * 刷新历史记录并显示面板
   */
  @Override
  public Dialog show() {
    refreshHistory();
    return super.show();
  }

  /**
   * 刷新历史记录内容
   */
  private void refreshHistory() {
    contentTable.clearChildren();
    contentTable.left().top();
    contentTable.defaults().growX().pad(4f).left();

    DialogManager.instance.history.each(entry -> {
      Table entryTable = new Table();
      entryTable.left();

      // 角色名称
      Label nameLabel = new Label(entry.characterName + "：", Styles.outlineLabel);
      nameLabel.setColor(Pal.accent);
      nameLabel.setFontScale(0.9f);
      entryTable.add(nameLabel).left().padRight(8f);

      // 对话内容
      Label contentLabel = new Label(entry.content, Styles.outlineLabel);
      contentLabel.setWrap(true);
      contentLabel.setAlignment(Align.left);
      contentLabel.setFontScale(0.9f);
      entryTable.add(contentLabel).growX().left();

      contentTable.add(entryTable).row();
    });
  }
}
