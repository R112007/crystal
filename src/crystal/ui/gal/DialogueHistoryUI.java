package crystal.ui.gal;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 对话历史查看面板。按模块标签显示历史记录。
 */
public class DialogueHistoryUI extends BaseDialog {
  private final Table contentTable;
  private ScrollPane scrollPane;
  private final Table tabTable;

  public DialogueHistoryUI() {
    super("", Styles.defaultDialog);
    setResizable(true);
    setMovable(true);
    setModal(false);
    float screenW = Core.graphics.getWidth();
    float screenH = Core.graphics.getHeight();
    setSize(Math.min(screenW * 0.85f, 900f), Math.min(screenH * 0.85f, 1200f));
    centerWindow();

    titleTable.clear();
    titleTable.add(new Label("对话历史", Styles.defaultLabel)).fontScale(1.3f).pad(12f);
    addCloseButton();

    tabTable = new Table();
    cont.add(tabTable).growX().padBottom(8f).row();

    contentTable = new Table();
    contentTable.left().top();
    scrollPane = new ScrollPane(contentTable);
    scrollPane.setFadeScrollBars(false);
    scrollPane.setScrollingDisabledX(true);
    cont.add(scrollPane).grow().pad(8f);

    closeOnBack();
    resized(() -> {
      float w = Math.min(Core.graphics.getWidth() * 0.85f, 900f);
      float h = Math.min(Core.graphics.getHeight() * 0.85f, 1200f);
      setSize(w, h);
      centerWindow();
    });
  }

  @Override
  public Dialog show() {
    refreshModuleTabs();
    return super.show();
  }

  private void refreshModuleTabs() {
    tabTable.clear();
    GalgameDialogueManager mgr = GalgameDialogueManager.instance;
    Seq<String> moduleIds = new Seq<>();
    for (DialogueModule module : mgr.modules) {
      if (module != null && module.history != null && !module.history.isEmpty()) {
        moduleIds.add(module.moduleId);
      }
    }
    moduleIds.distinct().sort();

    addTab("全部", null);
    moduleIds.each(moduleId -> {
      DialogueModule module = mgr.getModule(moduleId);
      String tabName = module != null ? module.moduleName : moduleId;
      addTab(tabName, moduleId);
    });

    refreshHistory(null);
  }

  private void addTab(String name, String moduleId) {
    tabTable.button(name, Styles.black3, () -> {
      refreshHistory(moduleId);
      tabTable.getChildren().each(e -> {
        if (e instanceof Button btn) {
          btn.setChecked(name.equals(btn.name));
        }
      });
    }).growX().height(48).pad(2f).name(name);
  }

  private void refreshHistory(String moduleId) {
    contentTable.clear();
    contentTable.invalidateHierarchy();
    Seq<DialogueHistory> showHistory = new Seq<>();
    GalgameDialogueManager mgr = GalgameDialogueManager.instance;
    if (moduleId == null) {
      for (DialogueModule module : mgr.modules) {
        if (module != null && module.history != null && !module.history.isEmpty()) {
          showHistory.addAll(module.history);
        }
      }
    } else {
      DialogueModule targetModule = mgr.getModule(moduleId);
      if (targetModule != null && targetModule.history != null) {
        showHistory.addAll(targetModule.history);
      }
    }

    if (showHistory.isEmpty()) {
      contentTable.add(new Label("暂无对话记录", Styles.defaultLabel))
          .center().pad(20f).row();
      return;
    }

    int totalSize = showHistory.size;
    for (int i = 0; i < totalSize; i++) {
      DialogueHistory item = showHistory.get(i);
      Table lineTable = new Table();
      Label nameLabel = new Label(item.characterName + ": ", Styles.defaultLabel);
      nameLabel.setColor(Color.valueOf("ffd37f"));
      nameLabel.setFontScale(1.1f);
      lineTable.add(nameLabel).left().top().padRight(8f).pad(6f);
      Label contentLabel = new Label(item.content, Styles.defaultLabel);
      contentLabel.setWrap(true);
      contentLabel.setAlignment(Align.left);
      lineTable.add(contentLabel).growX().left().top();
      contentTable.add(lineTable).growX().left().padTop(6f).padBottom(6f).row();

      if (i != totalSize - 1) {
        Table divider = new Table();
        divider.background(Tex.whiteui);
        divider.setColor(Color.gray);
        contentTable.add(divider).growX().height(1f).padLeft(6f).padRight(6f).row();
      }
    }
    Core.app.post(() -> {
      contentTable.invalidateHierarchy();
      scrollPane.invalidate();
      scrollPane.layout();
      scrollPane.setScrollY(scrollPane.getMaxY());
    });
  }
}
