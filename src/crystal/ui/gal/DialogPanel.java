package crystal.ui.gal;

import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Align;
import mindustry.ui.Styles;

import static arc.scene.actions.Actions.*;

/**
 * Galgame对话主UI面板
 * 实现对话框、立绘、选择支、功能按钮
 */
public class DialogPanel extends Table {
  /** 角色立绘组件 */
  private final Image characterImage;
  /** 角色名称标签 */
  private final Label nameLabel;
  /** 对话内容标签 */
  private final Label contentLabel;
  /** 选择支按钮容器 */
  private final Table choiceContainer;
  /** 功能按钮容器（历史、自动播放、跳过） */
  private final Table functionContainer;

  public DialogPanel() {
    super(Styles.black8); // 半透明黑色背景，符合Galgame风格
    this.setFillParent(false);
    this.visible = false;
    this.touchable = arc.scene.event.Touchable.enabled;

    // 初始化组件
    characterImage = new Image();
    characterImage.setScaling(arc.util.Scaling.fit);
    characterImage.setAlign(Align.bottom);

    nameLabel = new Label("", Styles.outlineLabel);
    nameLabel.setFontScale(1.1f);

    contentLabel = new Label("", Styles.outlineLabel);
    contentLabel.setWrap(true); // 自动换行
    contentLabel.setAlignment(Align.topLeft);
    contentLabel.setFontScale(0.95f);

    choiceContainer = new Table();
    functionContainer = new Table();

    // 构建UI布局
    buildLayout();

    // 添加点击事件：点击对话框推进对话
    this.addListener(new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
        // 只有点击对话框本身才推进，点击子组件（按钮）不触发
        if (event.targetActor == DialogPanel.this) {
          DialogManager.instance.next();
          return true;
        }
        return false;
      }
    });
  }

  /**
   * 构建UI布局
   */
  private void buildLayout() {
    this.margin(12f);
    this.defaults().pad(4f);

    // 第一行：角色立绘 + 对话内容区
    Table contentTable = new Table();
    contentTable.left();

    // 左侧：立绘区域
    contentTable.add(characterImage).size(240f, 360f).left().padRight(16f);

    // 右侧：名称+对话内容
    Table textTable = new Table();
    textTable.left().top();
    textTable.add(nameLabel).left().padBottom(8f).row();
    textTable.add(contentLabel).growX().growY().left().top().row();
    textTable.add(choiceContainer).growX().left().padTop(8f).row();

    contentTable.add(textTable).growX().growY().left().top();

    this.add(contentTable).growX().growY().row();

    // 第二行：功能按钮（右对齐）
    this.add(functionContainer).right().growX().padTop(8f);
    buildFunctionButtons();
  }

  /**
   * 构建功能按钮
   */
  private void buildFunctionButtons() {
    functionContainer.clearChildren();
    functionContainer.defaults().size(100f, 40f).padLeft(8f);

    // 历史记录按钮
    functionContainer.button("历史", Styles.flatt, () -> {
      DialogManager.instance.historyPanel.show();
    });

    // 自动播放按钮
    functionContainer.button("自动播放", Styles.flatt, () -> {
      DialogManager.instance.toggleAutoPlay();
      updateAutoPlayButton();
    }).update(b -> {
      b.setText(DialogManager.instance.autoPlayEnabled ? "暂停自动" : "自动播放");
    });

    // 跳过按钮
    functionContainer.button("跳过", Styles.flatt, () -> {
      DialogManager.instance.stopDialog();
    });
  }

  /**
   * 更新自动播放按钮状态
   */
  private void updateAutoPlayButton() {
    functionContainer.getCells().get(1).setChecked(DialogManager.instance.autoPlayEnabled);
  }

  /**
   * 为指定对话节点更新UI
   */
  public void updateForNode(DialogNode node) {
    // 更新角色名称
    nameLabel.setText(node.character == null ? "" : node.character.name);
    nameLabel.setColor(node.character == null ? arc.graphics.Color.lightGray : arc.graphics.Color.white);

    // 清空内容，等待逐字显示
    contentLabel.setText("");

    // 清空选择支
    choiceContainer.clearChildren();

    // 如果有选择支，构建选择按钮
    if (!node.choices.isEmpty()) {
      buildChoiceButtons(node);
    }
  }

  /**
   * 构建选择支按钮
   * 核心实现：点击按钮不触发下一句对话
   */
  private void buildChoiceButtons(DialogNode node) {
    choiceContainer.defaults().growX().height(45f).padTop(6f).left();
    node.choices.each(choice -> {
      choiceContainer.button(choice.text, Styles.flatTogglet, () -> {
        // 选择支点击事件，仅触发选择回调，不推进对话
        DialogManager.instance.onChoiceSelect(choice);
      }).growX().left().addListener(new InputListener() {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
          // 停止事件冒泡，不让点击事件传到父级对话框
          event.stop();
          return true;
        }
      });
      choiceContainer.row();
    });
  }

  /**
   * 更新当前显示的文字（逐字显示用）
   */
  public void updateDisplayedText() {
    contentLabel.setText(DialogManager.instance.getCurrentDisplayedText());
  }

  /**
   * 更新角色表情
   */
  public void updateCharacterExpression(DialogCharacter character, String expression) {
    characterImage.setDrawable(character.getExpression(expression));
  }

  // ========== 立绘动画实现 ==========
  /**
   * 角色进入动画：淡入+从左位移进入
   */
  public void playCharacterEnterAnimation(DialogCharacter character, String expression) {
    characterImage.setDrawable(character.getExpression(expression));
    characterImage.setColor(1f, 1f, 1f, 0f);
    characterImage.setTranslation(-50f, 0f);

    characterImage.clearActions();
    characterImage.addAction(
        parallel(
            fadeIn(DialogManager.instance.animationDuration),
            moveBy(50f, 0f, DialogManager.instance.animationDuration, arc.math.Interp.pow3Out)));
  }

  /**
   * 角色离开动画：淡出+向左位移离开
   */
  public void playCharacterExitAnimation() {
    characterImage.clearActions();
    characterImage.addAction(
        sequence(
            parallel(
                fadeOut(DialogManager.instance.animationDuration),
                moveBy(-50f, 0f, DialogManager.instance.animationDuration, arc.math.Interp.pow3In)),
            run(() -> characterImage.setDrawable((arc.scene.style.Drawable) null))));
  }

  /**
   * 显示对话框
   */
  public void show() {
    this.visible = true;
    this.setSize(Core.graphics.getWidth() * 0.9f, 200f);
    this.setPosition(Core.graphics.getWidth() / 2f, 120f, Align.bottom);
    this.setColor(1f, 1f, 1f, 0f);
    this.clearActions();
    this.addAction(fadeIn(0.2f));
  }

  /**
   * 隐藏对话框
   */
  public void hide() {
    this.clearActions();
    this.addAction(sequence(
        fadeOut(0.2f),
        Actions.visible(false)));
    // 同时播放角色离开动画
    playCharacterExitAnimation();
  }

  /**
   * 将对话框添加到HUD组
   */
  public void build(WidgetGroup hudGroup) {
    hudGroup.addChild(this);
  }
}
