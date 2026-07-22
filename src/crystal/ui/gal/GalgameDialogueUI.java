package crystal.ui.gal;

import arc.Core;
import arc.graphics.Color;
import arc.scene.actions.Actions;
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
    public final TextButton autoPlayBtn, historyBtn, fastForwardBtn, fastSkipBtn, forceSkipBtn;

    /** 驱动本 UI 的 Manager。不再硬编码单例，可被独立对话框复用。 */
    public final GalgameDialogueManager manager;

    public DialogueLine currentLine;
    public Side activeSide = Side.left;
    public String fullContent;
    public int typedIndex;
    public float typingTimer;
    /** 防止一句对话的 onComplete 被重复触发 */
    public boolean completeCalled;

    public GalgameDialogueUI() {
        this(GalgameDialogueManager.instance);
    }

    public GalgameDialogueUI(GalgameDialogueManager manager) {
        this.manager = manager;
        setBackground(Tex.pane);
        setSize(Core.graphics.getWidth() * 0.9f, Core.graphics.getHeight() * 0.3f);
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
        fastForwardBtn = new TextButton("快进", btnStyle);
        fastForwardBtn.clicked(() -> manager.fastForward());

        fastSkipBtn = new TextButton("快速推进", btnStyle);
        fastSkipBtn.clicked(() -> {
            Vars.ui.showConfirm("快速推进主线", "将自动跳过已读对话，快速推进主线剧情，遇到分支选项会自动停止。是否确认？",
                    () -> manager.fastSkipMainLine());
        });

        forceSkipBtn = new TextButton("强制跳过本章", btnStyle);
        forceSkipBtn.clicked(() -> {
            Vars.ui.showConfirm("跳过主线", "将完全跳过本段剧情。是否确认？",
                    () -> manager.skipAll());
        });

        buildLayout();

        clicked(() -> {
            if (optionTable.visible)
                return;
            manager.nextLine();
        });

        update(() -> {
            if (!manager.isTyping)
                return;
            typingTimer += Core.graphics.getDeltaTime();
            float iv = 1f / manager.typingSpeed;
            while (typingTimer >= iv && typedIndex < fullContent.length()) {
                typedIndex++;
                contentLabel.setText(fullContent.substring(0, typedIndex));
                typingTimer -= iv;
            }
            if (typedIndex >= fullContent.length()) {
                manager.isTyping = false;
                triggerComplete();
            }
        });

        resized(() -> {
            setSize(Core.graphics.getWidth() * 0.9f, Core.graphics.getHeight() * 0.3f);
            setPosition(Core.graphics.getWidth() / 2, scl(20f), Align.bottom);
        });
    }

    public void buildLayout() {
        defaults().pad(scl(6f));
        margin(scl(8f));

        // 左侧立绘
        add(leftSprite).size(scl(360)).left().padRight(scl(12f));

        // 中间文本容器
        Table textContainer = new Table();
        textContainer.left().top();
        textContainer.add(nameLabel).left().padBottom(scl(4f)).row();
        textContainer.add(contentLabel).grow().left().top().row();
        textContainer.add(inlineImageTable).growX().left().padTop(scl(8f)).row();
        textContainer.add(optionTable).growX().left().padTop(scl(10f)).row();
        add(textContainer).grow().left().top().padLeft(scl(12f)).padRight(scl(12f));

        // 右侧立绘
        add(rightSprite).size(scl(360)).right().padLeft(scl(12f));
        row();

        // 底部按钮：统一大小并居中，避免分布不均
        Table buttonTable = new Table();
        buttonTable.center();
        buttonTable.add(autoPlayBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(historyBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(fastForwardBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
        buttonTable.add(fastSkipBtn).size(scl(130f), scl(80f)).padRight(scl(6f));
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

        // 左侧立绘
        if (line.leftSprite != null) {
            leftSprite.setDrawable(line.leftSprite);
            leftSprite.visible = true;
            resetSpriteTransforms(leftSprite);
        } else {
            leftSprite.visible = false;
            leftSprite.setDrawable((Drawable) null);
        }

        // 右侧立绘
        if (line.rightSprite != null) {
            rightSprite.setDrawable(line.rightSprite);
            rightSprite.visible = true;
            resetSpriteTransforms(rightSprite);
        } else {
            rightSprite.visible = false;
            rightSprite.setDrawable((Drawable) null);
        }

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
        manager.isTyping = true;
        typedIndex = 0;
        typingTimer = 0;
        contentLabel.setText("");
        completeCalled = false;
    }

    public void finishTyping() {
        manager.isTyping = false;
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
        for (DialogueOption option : options) {
            TextButton btn = new TextButton(option.optionText, Styles.flatBordert);
            btn.getLabel().setWrap(true);
            btn.clicked(() -> {
                optionTable.visible = false;
                optionTable.clear();
                boolean restore = manager.cachedAutoPlayBeforeOption;
                manager.cachedAutoPlayBeforeOption = false;
                if (option.branch != null) {
                    if (manager.isReplayManager) {
                        // 副本回放：直接在当前队列开头插入分支副本，可播放之前未选分支
                        Seq<DialogueLine> copies = option.branch.createReplayCopies();
                        for (int i = copies.size - 1; i >= 0; i--) {
                            manager.dialogueQueue.insert(0, copies.get(i));
                        }
                    } else if (option.onSelect == null) {
                        // 正常模式：没有额外回调时，直接走 Manager 追加分支
                        manager.addBranch(option.branch);
                    }
                }
                if (option.onSelect != null) {
                    option.onSelect.get(option);
                }
                if (!manager.isTyping && !manager.dialogueQueue.isEmpty()) {
                    Core.app.post(manager::nextLine);
                }
                if (restore) {
                    manager.isAutoPlay = true;
                    updateAutoPlayButton();
                    manager.startAutoPlay();
                }
            });
            optionTable.add(btn).growX()
                    .padBottom(scl(10f))
                    .height(scl(70f))
                    .pad(scl(8f)).row();
        }
    }

    public void updateAutoPlayButton() {
        autoPlayBtn.setChecked(manager.isAutoPlay);
        autoPlayBtn.setText(manager.isAutoPlay ? "取消自动" : "自动播放");
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
        return Scl.scl(value) / Core.graphics.getDensity();
    }
}
