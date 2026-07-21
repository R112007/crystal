package crystal.ui.gal;

import arc.graphics.Color;
import arc.scene.ui.Dialog;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 模块副本回放对话框。
 * 点击陈列馆卡片后打开的黑色全屏 BaseDialog，
 * 内部使用独立的 GalgameDialogueManager 播放模块副本，
 * 播放结束后自动隐藏。
 */
public class ModuleReplayDialog extends BaseDialog {
    private final DialogueModule module;
    private final GalgameDialogueManager manager;

    public ModuleReplayDialog(DialogueModule module) {
        super("");
        this.module = module;
        shouldPause = true;

        // 全黑背景
        setBackground(Styles.black);
        cont.setBackground(Styles.black);

        // 创建隔离的回放 Manager，不注册战役事件、不创建继续按钮
        manager = new GalgameDialogueManager(true) {
            @Override
            public void show() {
                if (isShowing)
                    return;
                isShowing = true;
                ui.updateVisibility();
            }

            @Override
            public void hide() {
                if (!isShowing)
                    return;
                isShowing = false;
                isPlaying = false;
                isTyping = false;
                isAutoPlay = false;
                cachedAutoPlayBeforeOption = false;
                stopAutoPlay();
                dialogueQueue.clear();
                currentLine = null;
                lastPlayedCharacterId = null;
                ui.reset();
                isPlayingModule = false;
                // UI 由对话框托管，这里不 remove；只在对话框仍显示时关闭它
                if (ModuleReplayDialog.this.visible) {
                    ModuleReplayDialog.this.hide();
                }
            }
        };

        // 副本播放不需要历史、快速推进和强制跳过按钮
        manager.ui.historyBtn.visible = false;
        manager.ui.fastSkipBtn.visible = false;
        manager.ui.forceSkipBtn.visible = false;

        cont.add(manager.ui).grow();

        // 用户手动关闭对话框时一并清理 Manager
        hidden(() -> {
            if (manager.isShowing)
                manager.hide();
        });
    }

    @Override
    public Dialog show() {
        manager.replayModule(module);
        return super.show();
    }
}
