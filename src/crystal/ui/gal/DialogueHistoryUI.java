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

public class DialogueHistoryUI extends BaseDialog {
  private final Table contentTable;
  private ScrollPane scrollPane;
  // 用 Table 替代 TabbedPane 做标签栏
  private final Table tabTable;

  public DialogueHistoryUI() {
    super("", Styles.defaultDialog);
    setResizable(true);
    setMovable(true);
    setModal(false);
    // 适配屏幕的安全尺寸，和调试面板统一
    float screenW = Core.graphics.getWidth();
    float screenH = Core.graphics.getHeight();
    setSize(Math.min(screenW * 0.85f, 900f), Math.min(screenH * 0.85f, 1200f));
    centerWindow();

    titleTable.clear();
    titleTable.add(new Label("对话历史", Styles.defaultLabel)).fontScale(1.3f).pad(12f);
    addCloseButton();

    // 标签栏容器
    tabTable = new Table();
    cont.add(tabTable).growX().padBottom(8f).row();

    // 内容容器
    contentTable = new Table();
    contentTable.left().top();
    scrollPane = new ScrollPane(contentTable);
    scrollPane.setFadeScrollBars(false);
    scrollPane.setScrollingDisabledX(true);
    cont.add(scrollPane).grow().pad(8f);

    closeOnBack();
    // 窗口大小自适应
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

  /** 刷新标签栏（Table 实现） */
  private void refreshModuleTabs() {
    tabTable.clear();
    GalgameDialogueManager mgr = GalgameDialogueManager.instance;
    // 获取所有有历史的模块ID
    Seq<String> moduleIds = new Seq<>();
    for (DialogueModule module : mgr.modules) {
      if (module != null && module.history != null && !module.history.isEmpty()) {
        moduleIds.add(module.moduleId);
      }
    }
    moduleIds.distinct().sort();

    // 全部标签
    addTab("全部", null);
    // 按模块添加标签
    moduleIds.each(moduleId -> {
      DialogueModule module = mgr.getModule(moduleId);
      String tabName = module != null ? module.moduleName : moduleId;
      addTab(tabName, moduleId);
    });

    // 默认显示全部
    refreshHistory(null);
  }

  /** 添加标签按钮 */
  private void addTab(String name, String moduleId) {
    tabTable.button(name, Styles.black3, () -> {
      refreshHistory(moduleId);
      // 切换高亮
      tabTable.getChildren().each(e -> {
        if (e instanceof Button btn) {
          btn.setChecked(name.equals(btn.name));
        }
      });
    }).growX().height(48).pad(2f).name(name);
  }

  /**
   * 刷新历史记录内容
   * 完全按你的计划实现：遍历每个模块的 Seq<DialogueHistory> history
   * 
   * @param moduleId 要显示的模块ID，null=显示全部模块的历史
   */
  private void refreshHistory(String moduleId) {
    contentTable.clear();
    contentTable.invalidateHierarchy();
    // 最终要显示的历史列表
    Seq<DialogueHistory> showHistory = new Seq<>();
    GalgameDialogueManager mgr = GalgameDialogueManager.instance;
    // 加载历史数据
    if (moduleId == null) {
      // 全部标签：遍历所有模块合并历史
      for (DialogueModule module : mgr.modules) {
        if (module != null && module.history != null && !module.history.isEmpty()) {
          showHistory.addAll(module.history);
        }
      }
    } else {
      // 单个模块标签：直接取对应模块历史
      DialogueModule targetModule = mgr.getModule(moduleId);
      if (targetModule != null && targetModule.history != null) {
        showHistory.addAll(targetModule.history);
      }
    }
    // 空数据处理
    if (showHistory.isEmpty()) {
      contentTable.add(new Label("暂无对话记录", Styles.defaultLabel))
          .center().pad(20f).row();
      return;
    }
    // 渲染历史条目+分割线
    int totalSize = showHistory.size;
    for (int i = 0; i < totalSize; i++) {
      DialogueHistory item = showHistory.get(i);
      Table lineTable = new Table();
      // 角色名
      Label nameLabel = new Label(item.characterName + ": ", Styles.defaultLabel);
      nameLabel.setColor(Color.valueOf("ffd37f"));
      nameLabel.setFontScale(1.1f);
      lineTable.add(nameLabel).left().top().padRight(8f).growX().pad(6f);
      // 对话内容
      Label contentLabel = new Label(item.content, Styles.defaultLabel);
      contentLabel.setWrap(true);
      contentLabel.setAlignment(Align.left);
      lineTable.add(contentLabel).growX().left().top();
      contentTable.add(lineTable).growX().left().padTop(6f).padBottom(6f).row();

      // ========== 新增：条目间添加分割线，最后一条不添加 ==========
      if (i != totalSize - 1) {
        Table divider = new Table();
        divider.background(Tex.whiteui);
        divider.setColor(Color.gray);
        contentTable.add(divider).growX().height(1f).padLeft(6f).padRight(6f).row();
      }
    }
    // 刷新滚动条并自动滚动到底部
    Core.app.post(() -> {
      contentTable.invalidateHierarchy();
      scrollPane.invalidate();
      scrollPane.layout();
      scrollPane.setScrollY(scrollPane.getMaxY());
    });
  }
}
