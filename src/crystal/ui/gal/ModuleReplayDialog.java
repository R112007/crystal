package crystal.ui.gal;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.util.Log;
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
        setFillParent(true);
        // 创建隔离的回放 Manager，不注册战役事件、不创建继续按钮
        manager = new GalgameDialogueManager(true);
        manager.hostExternally(() -> {
            if (ModuleReplayDialog.this.visible) ModuleReplayDialog.this.hide();
        });

        // 副本播放不需要历史、快速推进和强制跳过按钮
        manager.ui.historyBtn.visible = false;

        addCloseButton();

        // 用户手动关闭对话框时一并清理 Manager
        hidden(() -> {
            Log.info("[GAL] replay dialog hidden");
            manager.externalHostClosed();
            if (manager.ui.hasParent()) {
                manager.ui.remove();
            }
        });
    }

    public float scl(float value) {
        // 同 GalgameDialogueUI：密度为 0 时不能做除数，否则面板位置变成 Infinity
        float density = Core.graphics.getDensity();
        if (!(density > 0f) || Float.isInfinite(density))
            return Scl.scl(value);
        return Scl.scl(value) / density;
    }

    @Override
    public Dialog show() {
        Log.info("[GAL] replay dialog show");
        Dialog result = super.show();
        if (!manager.ui.hasParent()) {
            Core.scene.root.addChild(manager.ui);
        }
        manager.ui.toFront();
        manager.ui.update(() -> {
            // BaseDialog brings itself to the front while shown. Keep the
            // externally hosted dialogue layer above that full-screen black
            // host so its text, sprites and choice buttons remain visible.
            manager.ui.toFront();
        });
        Log.info("[GAL] replay ui attached parent=@, x=@, y=@, w=@, h=@",
            manager.ui.parent != null, manager.ui.getX(Align.bottomLeft),
            manager.ui.getY(Align.bottomLeft),
            manager.ui.getWidth(), manager.ui.getHeight());
        // The hosted UI must be attached to the scene before playback starts,
        // otherwise its typing update and visibility state never begin.
        Core.app.post(() -> {
            Log.info("[GAL] replay post-start module=@", module.moduleId);
            manager.replayModule(module);
            Log.info("[GAL] replay started state=@", manager.playbackState());
        });
        return result;
    }
}
