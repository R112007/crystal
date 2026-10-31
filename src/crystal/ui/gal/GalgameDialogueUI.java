package crystal.ui.gal;

import arc.Core;
import arc.graphics.Color;
import arc.scene.actions.Actions;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.ResizeListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Cell;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import crystal.ui.gal.DialogueLine.DialogueOption;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;

/**
 * Galgame 对话主 UI。
 * 支持左右双立绘、文本内嵌图片/表情包、实例化动作、历史查看。
 */
public class GalgameDialogueUI extends Table {
    public final Image leftSprite;
    public final Image rightSprite;
    public final Label nameLabel;
    public final Label contentLabel;
    public final Table inlineImageTable;
    public final Table optionTable;
    public final TextButton autoPlayBtn, historyBtn, fastForwardBtn, forceSkipBtn;
    /** 左右立绘的布局单元：面板/窗口尺寸变化时要重新收敛。 */
    protected Cell<Image> leftSpriteCell, rightSpriteCell;

    /** 驱动本 UI 的 Manager。不再硬编码单例，可被独立对话框复用。 */
    public final GalgameDialogueManager manager;

    public DialogueLine currentLine;
    public Side activeSide = Side.left;
    public String fullContent;
    public int typedIndex;
    public float typingTimer;
    /** 防止一句对话的 onComplete 被重复触发 */
    public boolean completeCalled;
    /** 当前句首次显示时，指定侧立绘是否刚从隐藏状态出现。 */
    protected boolean leftSpriteAppeared, rightSpriteAppeared;

    public GalgameDialogueUI() {
        this(GalgameDialogueManager.instance);
    }

    public GalgameDialogueUI(GalgameDialogueManager manager) {
        this.manager = manager;
        setBackground(Tex.pane);
        setSize(Core.graphics.getWidth() * 0.9f, panelHeight());
        setPosition(Core.graphics.getWidth() / 2, scl(20f), Align.bottom);
        touchable = Touchable.childrenOnly;
        setTransform(true);

        leftSprite = new Image();
        leftSprite.setScaling(Scaling.fit);
        leftSprite.setOrigin(Align.center);

        rightSprite = new Image();
        rightSprite.setScaling(Scaling.fit);
        rightSprite.setOrigin(Align.center);

        nameLabel = new Label("", Styles.defaultLabel);
        nameLabel.setFontScale(1.2f);
        nameLabel.setColor(Color.valueOf("ffd37f"));

        contentLabel = new Label("", Styles.defaultLabel);
        contentLabel.setWrap(true);
        contentLabel.setAlignment(Align.topLeft);
        contentLabel.setFontScale(1.05f);

        inlineImageTable = new Table();
        inlineImageTable.left();

        optionTable = new Table();
        optionTable.visible = false;

        TextButton.TextButtonStyle btnStyle = Styles.flatt;
        autoPlayBtn = new TextButton("自动播放", btnStyle);
        autoPlayBtn.clicked(() -> manager.toggleAutoPlay());
        historyBtn = new TextButton("历史记录", btnStyle);
        if (manager.historyUI != null) {
            historyBtn.clicked(() -> manager.historyUI.show());
        } else {
            historyBtn.visible = false;
        }
        fastForwardBtn = new TextButton("快进到分支", btnStyle);
        fastForwardBtn.clicked(() -> manager.fastForward());

        forceSkipBtn = new TextButton("强制跳过本章", btnStyle);
        forceSkipBtn.clicked(() -> {
            Vars.ui.showConfirm("跳过主线", "将完全跳过本段剧情。是否确认？",
                    () -> manager.skipAll());
        });

        buildLayout();

        // 点击对话框推进剧情。按钮（自动播放/历史/快进等）的事件会冒泡到这里
        // （arc 的 Button 不会 stop 事件），必须识别出"这次点击是从按钮来的"，
        // 否则按一次按钮会既执行按钮功能、又顺手推进一句剧情。
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (optionTable.visible)
                    return;
                for (arc.scene.Element e = event.targetActor; e != null; e = e.parent) {
                    if (e instanceof Button)
                        return;
                    if (e == GalgameDialogueUI.this)
                        break;
                }
                manager.nextLine();
            }
        });

        update(() -> {
            if (!manager.isTyping())
                return;
            typingTimer += Core.graphics.getDeltaTime();
            float iv = 1f / manager.typingSpeed;
            while (typingTimer >= iv && typedIndex < fullContent.length()) {
                typedIndex++;
                contentLabel.setText(fullContent.substring(0, typedIndex));
                typingTimer -= iv;
            }
            if (typedIndex >= fullContent.length()) {
                manager.markTypingFinished();
                triggerComplete();
            }
        });

        resized(() -> {
            setSize(Core.graphics.getWidth() * 0.9f, panelHeight());
            setPosition(Core.graphics.getWidth() / 2, scl(20f), Align.bottom);
            applyLayoutMetrics();
        });
    }

    /**
     * 面板高度：屏幕高度的 30% 起，但要保证"立绘 + 选项行 + 按钮行"都装得下。
     * 手机（密度高，scl 数值小）维持原来的 30%；桌面（密度 1）立绘 360 会撑爆 30%，
     * 之前选项按钮和底部按钮行叠在一起、还被屏幕底边裁掉。
     */
    public float panelHeight() {
        return Math.max(Core.graphics.getHeight() * 0.3f, scl(420f));
    }

    /** 立绘边长：不超过面板给立绘留出的空间（其余要留给选项行和按钮行）。 */
    public float spriteSize() {
        return Math.max(scl(96f), Math.min(scl(360f), panelHeight() - scl(200f)));
    }

    public void applyLayoutMetrics() {
        float size = spriteSize();
        if (leftSpriteCell != null)
            leftSpriteCell.size(size);
        if (rightSpriteCell != null)
            rightSpriteCell.size(size);
    }

    public void buildLayout() {
        defaults().pad(scl(6f));
        margin(scl(8f));

        // 左侧立绘
        leftSpriteCell = add(leftSprite).size(spriteSize()).left().padRight(scl(12f));

        // 中间文本容器
        Table textContainer = new Table();
        textContainer.left().top();
        // Table 默认 touchable=childrenOnly，而里面的 Label/空行都不吃点击，
        // 于是"点对话框文字区推进"会完全没有反应；让这一块自己可点。
        textContainer.touchable = Touchable.enabled;
        textContainer.add(nameLabel).left().padBottom(scl(4f)).row();
        textContainer.add(contentLabel).grow().left().top().row();
        textContainer.add(inlineImageTable).growX().left().padTop(scl(8f)).row();
        add(textContainer).grow().left().top().padLeft(scl(12f)).padRight(scl(12f));

        // 右侧立绘
        rightSpriteCell = add(rightSprite).size(spriteSize()).right().padLeft(scl(12f));
        row();

        // 选项单独占一行：塞在文字列里会被立绘撑高的行推到面板外面
        add(optionTable).colspan(3).growX().left().padTop(scl(6f)).row();

        // 底部按钮：统一大小并居中，避免分布不均
        Table buttonTable = new Table();
        buttonTable.center();
        buttonTable.add(autoPlayBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(historyBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(fastForwardBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(forceSkipBtn).size(scl(130f), scl(80f));
        add(buttonTable).colspan(3).growX().center().bottom().padTop(scl(8f));
    }

    /** 当前说话侧对应的立绘。 */
    public Image activeSprite() {
        return activeSide == Side.right ? rightSprite : leftSprite;
    }

    /** 所有已显示的非空立绘。 */
    public Image[] allSprites() {
        if (leftSprite.visible && rightSprite.visible) {
            return new Image[] { leftSprite, rightSprite };
        } else if (leftSprite.visible) {
            return new Image[] { leftSprite };
        } else if (rightSprite.visible) {
            return new Image[] { rightSprite };
        }
        return new Image[0];
    }

    public void setDialogueLine(DialogueLine line) {
        currentLine = line;
        activeSide = line.activeSide == null ? Side.left : line.activeSide;
        fullContent = line.content;
        typedIndex = 0;
        typingTimer = 0;
        nameLabel.setText(line.characterName == null ? "" : line.characterName);
        contentLabel.setText("");
        optionTable.clear();
        optionTable.visible = false;

        leftSpriteAppeared = applyLineSprite(leftSprite, line.leftSprite);
        rightSpriteAppeared = applyLineSprite(rightSprite, line.rightSprite);

        // 内嵌图片/表情包
        inlineImageTable.clear();
        if (line.inlineImages != null && !line.inlineImages.isEmpty()) {
            inlineImageTable.visible = true;
            for (Drawable d : line.inlineImages) {
                Image img = new Image(d);
                img.setScaling(Scaling.fit);
                inlineImageTable.add(img).size(scl(64f)).pad(scl(4f));
            }
        } else {
            inlineImageTable.visible = false;
        }
    }

    private void resetSpriteTransforms(Image img) {
        img.setTranslation(0f, 0f);
        img.setScale(1f);
        img.setRotation(0f);
        img.color.a = 1f;
    }

    /**
     * 只更新本句实际提供的立绘。同一侧继续出场时保留当前变换和动作，
     * 避免换表情或切换说话人时整个立绘重新入场。
     */
    private boolean applyLineSprite(Image image, Drawable sprite) {
        if (sprite == null) {
            if (image.visible) {
                image.clearActions();
                image.visible = false;
                image.setDrawable((Drawable) null);
                resetSpriteTransforms(image);
            }
            return false;
        }

        boolean appeared = !image.visible || image.getDrawable() == null;
        if (image.getDrawable() != sprite) image.setDrawable(sprite);
        image.visible = true;
        if (appeared) resetSpriteTransforms(image);
        return appeared;
    }

    /** 当前说话侧是否在本句首次出现，只有这种情况需要播放入场。 */
    public boolean activeSpriteAppeared() {
        if (activeSide == Side.right) return rightSpriteAppeared;
        if (activeSide == Side.left) return leftSpriteAppeared;
        if (activeSide == Side.both) return leftSpriteAppeared || rightSpriteAppeared;
        return false;
    }

    /** 播放进入动作。仅对当前说话侧生效；若两侧均未变化则不重复进入。 */
    public void playSpriteEnterAction() {
        Image target = activeSprite();
        if (target == null || !target.visible)
            return;
        if (currentLine != null && currentLine.spriteEnterAction != null) {
            currentLine.spriteEnterAction.run();
            return;
        }
        target.clearActions();
        target.color.a = 0f;
        target.setScale(0.8f);
        boolean fromLeft = activeSide == Side.left;
        target.setTranslation(fromLeft ? scl(-40f) : scl(40f), 0f);
        target.addAction(Actions.parallel(
                Actions.fadeIn(0.4f),
                Actions.translateBy(fromLeft ? scl(40f) : scl(-40f), 0f, 0.4f, arc.math.Interp.pow3Out),
                Actions.scaleTo(1f, 1f, 0.4f, arc.math.Interp.pow3Out)));
    }

    /** 播放离开动作。仅对当前说话侧生效。 */
    public void playSpriteExitAction() {
        Image target = activeSprite();
        if (target == null || !target.visible)
            return;
        if (currentLine != null && currentLine.spriteExitAction != null) {
            currentLine.spriteExitAction.run();
            return;
        }
        target.clearActions();
        boolean toLeft = activeSide == Side.left;
        target.addAction(Actions.parallel(
                Actions.fadeOut(0.3f),
                Actions.translateBy(toLeft ? scl(25f) : scl(-25f), 0f, 0.3f),
                Actions.scaleTo(0.9f, 0.9f, 0.3f)));
    }

    public void startTyping() {
        manager.beginTyping();
        typedIndex = 0;
        typingTimer = 0;
        contentLabel.setText("");
        completeCalled = false;
    }

    public void finishTyping() {
        manager.markTypingFinished();
        typedIndex = fullContent.length();
        contentLabel.setText(fullContent);
        triggerComplete();
    }

    /** 触发当前句的 onComplete，并保证只触发一次。 */
    private void triggerComplete() {
        if (completeCalled)
            return;
        completeCalled = true;
        if (currentLine != null && currentLine.onComplete != null) {
            currentLine.onComplete.run();
        }
    }

    public void showOptions(DialogueOption[] options) {
        optionTable.clear();
        optionTable.visible = true;
        for (int idx = 0; idx < options.length; idx++) {
            DialogueOption option = options[idx];
            TextButton btn = new TextButton(option.optionText, Styles.flatBordert);
            btn.getLabel().setWrap(true);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    event.stop();
                    optionTable.visible = false;
                    optionTable.clear();
                    manager.selectOption(option);
                }
            });
            optionTable.add(btn).growX()
                    .padBottom(scl(10f))
                    .height(scl(70f))
                    .pad(scl(8f)).row();
        }
    }

    public void updateAutoPlayButton() {
        autoPlayBtn.setChecked(manager.isAutoPlay());
        autoPlayBtn.setText(manager.isAutoPlay() ? "取消自动" : "自动播放");
    }

    public void reset() {
        currentLine = null;
        activeSide = Side.left;
        fullContent = "";
        typedIndex = 0;
        nameLabel.setText("");
        contentLabel.setText("");
        leftSprite.setDrawable((Drawable) null);
        rightSprite.setDrawable((Drawable) null);
        leftSprite.visible = false;
        rightSprite.visible = false;
        leftSprite.clearActions();
        rightSprite.clearActions();
        leftSpriteAppeared = false;
        rightSpriteAppeared = false;
        inlineImageTable.clear();
        inlineImageTable.visible = false;
        optionTable.clear();
        optionTable.visible = false;
        updateAutoPlayButton();
    }

    public void updateVisibility() {
        visible = true;
    }

    public void resized(Runnable r) {
        resized(false, r);
    }

    public void resized(boolean invoke, Runnable r) {
        if (invoke)
            r.run();
        addListener(new ResizeListener() {
            @Override
            public void resized() {
                r.run();
                updateScrollFocus();
            }
        });
    }

    public void updateScrollFocus() {
        boolean[] d = { false };
        Core.app.post(() -> forEach(c -> {
            if (d[0])
                return;
            if (c instanceof ScrollPane) {
                Core.scene.setScrollFocus(c);
                d[0] = true;
            }
        }));
    }

    public float scl(float value) {
        // Mindustry 的 Scl.scl 本身已经包含 UI 缩放；再除一次屏幕密度时，
        // 某些环境（离屏渲染、SDL 拿不到 DPI 的设备）getDensity() 会返回 0，
        // 结果整个面板的位置/尺寸变成 Infinity —— 对话面板飞出屏幕，看不见也点不到。
        float density = Core.graphics.getDensity();
        if (!(density > 0f) || Float.isInfinite(density))
            return Scl.scl(value);
        return Scl.scl(value) / density;
    }
}
