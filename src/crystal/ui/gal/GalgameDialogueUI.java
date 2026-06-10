package crystal.ui.gal;

import arc.Core;
import arc.graphics.Color;
import arc.scene.actions.Actions;
import arc.scene.event.ResizeListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Scaling;
import crystal.ui.gal.DialogueLine.DialogueOption;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import static crystal.ui.gal.GalgameDialogueManager.*;

public class GalgameDialogueUI extends Table {
    public final Image characterSprite;
    public final Label nameLabel;
    public final Label contentLabel;
    public final Table optionTable;
    public final TextButton autoPlayBtn, historyBtn, fastForwardBtn, fastSkipBtn, forceSkipBtn;
    public DialogueLine currentLine;
    public String fullContent;
    public int typedIndex;
    public float typingTimer;

    public GalgameDialogueUI() {
        setBackground(Tex.pane);
        setSize(Core.graphics.getWidth() * 0.85f, Core.graphics.getHeight() * 0.28f);
        setPosition(Core.graphics.getWidth() / 2, scl(20f), Align.bottom);
        touchable = Touchable.childrenOnly;
        setTransform(true);

        characterSprite = new Image();
        characterSprite.setScaling(Scaling.fit);
        characterSprite.setOrigin(Align.center);

        nameLabel = new Label("", Styles.defaultLabel);
        nameLabel.setFontScale(1.2f);
        nameLabel.setColor(Color.valueOf("ffd37f"));

        contentLabel = new Label("", Styles.defaultLabel);
        contentLabel.setWrap(true);
        contentLabel.setAlignment(Align.topLeft);
        contentLabel.setFontScale(1.05f);

        optionTable = new Table();
        optionTable.visible = false;

        TextButton.TextButtonStyle btnStyle = Styles.flatt;
        autoPlayBtn = new TextButton("自动播放", btnStyle);
        autoPlayBtn.clicked(() -> instance.toggleAutoPlay());
        historyBtn = new TextButton("历史记录", btnStyle);
        historyBtn.clicked(() -> instance.historyUI.show());
        fastForwardBtn = new TextButton("快进", btnStyle);
        fastForwardBtn.clicked(() -> instance.fastForward());

        fastSkipBtn = new TextButton("快速推进", btnStyle);
        fastSkipBtn.clicked(() -> {
            Vars.ui.showConfirm("快速推进主线", "将自动跳过已读对话，快速推进主线剧情，遇到分支选项会自动停止。是否确认？",
                    () -> instance.fastSkipMainLine());
        });

        forceSkipBtn = new TextButton("强制跳过本章", btnStyle);
        forceSkipBtn.clicked(() -> {
            Vars.ui.showConfirm("跳过主线", "将完全跳过本段剧情。是否确认？",
                    () -> instance.skipAll());
        });

        buildLayout();

        clicked(() -> {
            if (optionTable.visible)
                return;
            instance.nextLine();
        });

        update(() -> {
            if (!instance.isTyping)
                return;
            typingTimer += Core.graphics.getDeltaTime();
            float iv = 1f / instance.typingSpeed;
            while (typingTimer >= iv && typedIndex < fullContent.length()) {
                typedIndex++;
                contentLabel.setText(fullContent.substring(0, typedIndex));
                typingTimer -= iv;
            }
            if (typedIndex >= fullContent.length()) {
                instance.isTyping = false;
            }
        });

        resized(() -> {
            setSize(Core.graphics.getWidth() * 0.85f, Core.graphics.getHeight() * 0.28f);
            setPosition(Core.graphics.getWidth() / 2, scl(20f), Align.bottom);
        });
    }

    public void buildLayout() {
        defaults().pad(scl(6f));
        margin(scl(8f));

        Table contentContainer = new Table();
        contentContainer.left();

        contentContainer.add(characterSprite)
                .size(scl(360))
                .left()
                .padRight(scl(12f));

        Table textContainer = new Table();
        textContainer.left().top();
        textContainer.add(nameLabel).left().padBottom(scl(4f)).row();
        textContainer.add(contentLabel).grow().left().top().row();
        textContainer.add(optionTable).growX().left().padTop(scl(10f)).row();
        contentContainer.add(textContainer).grow().left().top();
        add(contentContainer).grow().left().top().row();

        Table buttonTable = new Table();
        buttonTable.right();
        buttonTable.add(autoPlayBtn).size(scl(150f), scl(80f)).padRight(scl(6f));
        buttonTable.add(historyBtn).size(scl(150f), scl(80f)).padRight(scl(6f));
        buttonTable.add(fastForwardBtn).size(scl(150f), scl(80f)).padRight(scl(6f));
        buttonTable.add(fastSkipBtn).size(scl(150f), scl(80f)).padRight(scl(6f));
        buttonTable.add(forceSkipBtn).size(scl(160f), scl(80f));
        add(buttonTable).growX().right().bottom();
    }

    public void setDialogueLine(DialogueLine line) {
        currentLine = line;
        fullContent = line.content;
        typedIndex = 0;
        typingTimer = 0;
        nameLabel.setText(line.characterName == null ? "" : line.characterName);
        contentLabel.setText("");
        if (line.characterSprite != null) {
            characterSprite.setDrawable(line.characterSprite);
            characterSprite.visible = true;
            characterSprite.setTranslation(0f, 0f);
            characterSprite.setScale(1f);
            characterSprite.color.a = 1f;
        } else {
            characterSprite.visible = false;
        }
        optionTable.clear();
        optionTable.visible = false;
    }

    public void playSpriteEnterAction() {
        if (currentLine == null || currentLine.characterSprite == null)
            return;
        if (currentLine.spriteEnterAction != null) {
            currentLine.spriteEnterAction.run();
            return;
        }
        characterSprite.clearActions();
        characterSprite.color.a = 0f;
        characterSprite.setScale(0.8f);
        characterSprite.setTranslation(scl(-40f), 0f);
        characterSprite.addAction(Actions.parallel(
                Actions.fadeIn(0.4f),
                Actions.translateBy(scl(40f), 0f, 0.4f, arc.math.Interp.pow3Out),
                Actions.scaleTo(1f, 1f, 0.4f, arc.math.Interp.pow3Out)));
    }

    public void playSpriteExitAction() {
        if (currentLine == null || currentLine.characterSprite == null)
            return;
        if (currentLine.spriteExitAction != null) {
            currentLine.spriteExitAction.run();
            return;
        }
        characterSprite.clearActions();
        characterSprite.addAction(Actions.parallel(
                Actions.fadeOut(0.3f),
                Actions.translateBy(scl(25f), 0f, 0.3f),
                Actions.scaleTo(0.9f, 0.9f, 0.3f)));
    }

    public void startTyping() {
        instance.isTyping = true;
        typedIndex = 0;
        typingTimer = 0;
        contentLabel.setText("");
    }

    public void finishTyping() {
        instance.isTyping = false;
        typedIndex = fullContent.length();
        contentLabel.setText(fullContent);
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
                var mgr = instance;
                boolean restore = mgr.cachedAutoPlayBeforeOption;
                mgr.cachedAutoPlayBeforeOption = false;
                option.onSelect.get(option);
                if (!mgr.isTyping && !mgr.dialogueQueue.isEmpty()) {
                    Core.app.post(mgr::nextLine);
                }
                if (restore) {
                    mgr.isAutoPlay = true;
                    mgr.ui.updateAutoPlayButton();
                    mgr.startAutoPlay();
                }
            });
            optionTable.add(btn).growX()
                    .padBottom(scl(10f))
                    .height(scl(70f))
                    .pad(scl(8f)).row();
        }
    }

    public void updateAutoPlayButton() {
        autoPlayBtn.setChecked(instance.isAutoPlay);
        autoPlayBtn.setText(instance.isAutoPlay ? "取消自动" : "自动播放");
    }

    public void reset() {
        currentLine = null;
        fullContent = "";
        typedIndex = 0;
        nameLabel.setText("");
        contentLabel.setText("");
        characterSprite.setDrawable((Drawable) null);
        optionTable.clear();
        optionTable.visible = false;
        characterSprite.clearActions();
        updateAutoPlayButton();
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
