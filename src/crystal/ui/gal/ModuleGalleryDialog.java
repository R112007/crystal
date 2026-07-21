package crystal.ui.gal;

import arc.Core;
import arc.audio.Music;
import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.ui.Dialog;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 模块副本陈列馆：列出所有已注册模块，已完成模块可点击回放，未完成显示灰色。
 * 支持在打开时播放指定背景音乐，关闭时停止。
 */
public class ModuleGalleryDialog extends BaseDialog {

    public static final int columns = 4;
    public static final float cardSize = 150f;
    public static final float iconSize = 90f;

    /** 直接传入的背景音乐对象，优先级高于 musicPath。 */
    public Music music;
    /** 背景音乐文件路径，相对于 mod 根目录，例如 "music/gallery.ogg"。为 null 时不播放。 */
    public String musicPath;
    /** 当前播放的背景音乐实例。 */
    public Music currentMusic;
    /** 当前播放的音乐是否由本对话框创建（需要 dispose）。 */
    protected boolean musicOwned;
    /** 是否循环播放背景音乐。 */
    public boolean musicLooping = true;
    /** 背景音乐音量，0~1。 */
    public float musicVolume = 1f;

    public ModuleGalleryDialog() {
        super("模块陈列馆");
        shouldPause = true;
        addCloseButton();
    }

    /** 链式设置背景音乐对象。 */
    public ModuleGalleryDialog music(Music music) {
        this.music = music;
        this.musicPath = null;
        return this;
    }

    /** 链式设置背景音乐路径。 */
    public ModuleGalleryDialog music(String path) {
        this.musicPath = path;
        this.music = null;
        return this;
    }

    /** 链式设置是否循环。 */
    public ModuleGalleryDialog looping(boolean looping) {
        this.musicLooping = looping;
        return this;
    }

    /** 链式设置音量。 */
    public ModuleGalleryDialog volume(float volume) {
        this.musicVolume = volume;
        return this;
    }

    public void buildContent() {
        cont.clear();
        cont.top();

        GalgameDialogueManager manager = GalgameDialogueManager.instance;
        Seq<DialogueModule> modules = manager.modules;

        Table grid = new Table();
        grid.defaults().pad(8f).size(cardSize);

        int index = 0;
        for (DialogueModule module : modules) {
            if (module == null)
                continue;
            module.loadModuleData();

            boolean unlocked = module.isCompleted;
            String name = module.moduleName != null ? module.moduleName : module.moduleId;
            Drawable icon = resolveIcon(module, unlocked);

            Table card = new Table(Tex.pane);
            card.defaults().center();

            ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.clearNonei);
            ImageButton button = new ImageButton(icon, style);
            button.resizeImage(iconSize);

            if (!unlocked) {
                button.setDisabled(true);
                button.setColor(Color.gray);
            } else {
                final DialogueModule target = module;
                button.clicked(() -> {
                    // 在黑色 BaseDialog 中独立播放副本，不影响主线 Manager
                    new ModuleReplayDialog(target).show();
                });
            }

            card.add(button).size(iconSize).padTop(8f).row();
            card.add(new Label(name, Styles.outlineLabel)).padTop(6f).row();
            card.add(new Label(unlocked ? "[lightgray]已解锁" : "[crimson]未解锁", Styles.outlineLabel))
                    .fontScale(0.8f).padTop(2f);

            grid.add(card);

            index++;
            if (index % columns == 0) {
                grid.row();
            }
        }

        cont.pane(grid).scrollX(false);
    }

    /** 为模块选择图标：优先使用第一句的说话角色立绘，未解锁或没有角色时用默认白块。 */
    private Drawable resolveIcon(DialogueModule module, boolean unlocked) {
        if (unlocked && module.dialogueNodes.size > 0) {
            DialogueLine first = module.dialogueNodes.first();
            if (first != null) {
                if (first.leftSprite != null)
                    return first.leftSprite;
                if (first.rightSprite != null)
                    return first.rightSprite;
            }
        }
        return Tex.whiteui;
    }

    @Override
    public Dialog show() {
        buildContent();
        playMusic();
        return super.show();
    }

    @Override
    public void hide() {
        stopMusic();
        super.hide();
    }

    /** 开始播放指定的背景音乐。 */
    protected void playMusic() {
        stopMusic();

        if (music != null) {
            currentMusic = music;
            musicOwned = false;
        } else if (musicPath != null && !musicPath.isEmpty()) {
            try {
                currentMusic = Core.audio.newMusic(Vars.tree.get(musicPath));
                musicOwned = true;
            } catch (Exception e) {
                Log.err("[ModuleGalleryDialog] 无法加载音乐: " + musicPath, e);
                return;
            }
        } else {
            return;
        }

        if (currentMusic == null) {
            Log.warn("[ModuleGalleryDialog] 音乐对象为空，跳过播放。");
            return;
        }

        currentMusic.setLooping(musicLooping);
        currentMusic.setVolume(musicVolume);
        currentMusic.play();
    }

    /** 停止背景音乐。只有路径加载的音乐会被 dispose，外部传入的 Music 对象不释放。 */
    protected void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            if (musicOwned) {
                currentMusic.dispose();
            }
            currentMusic = null;
            musicOwned = false;
        }
    }
}
