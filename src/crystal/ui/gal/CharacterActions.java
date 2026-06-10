package crystal.ui.gal;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Rand;
import arc.scene.actions.Actions;
import arc.scene.ui.Image;
import arc.util.Align;
import arc.util.Time;
import arc.util.Timer;

public class CharacterActions {
    public static Rand rand = new Rand();

    public static void clear(Image img) {
        img.clearActions();
        img.setTranslation(0, 0);
        img.setScale(1f);
        img.setRotation(0f);
        img.color.a = 1f;
    }

    public static void clear(GalgameDialogueUI ui) {
        clear(ui.characterSprite);
    }

    /** 轻微颤抖（紧张/害怕），2秒后自动停止 */
    public static void shake(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.forever(
                        Actions.sequence(
                                Actions.moveBy(0, 4, 0.05f),
                                Actions.moveBy(0, -8, 0.1f),
                                Actions.moveBy(0, 4, 0.05f))));
        Time.runTask(2f, () -> clear(img));
    }

    /** 剧烈颤抖（惊吓/震怒），1.5秒后自动停止 */
    public static void shakeHard(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.forever(
                        Actions.sequence(
                                Actions.moveBy(0, 10, 0.04f),
                                Actions.moveBy(0, -20, 0.08f),
                                Actions.moveBy(0, 10, 0.04f))));
        Time.runTask(1.5f, () -> clear(img));
    }

    /** 上下浮动（呼吸/待机），永久循环，切换对话时自动清空 */
    public static void floatUpDown(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.forever(
                        Actions.sequence(
                                Actions.moveBy(0, 10, 0.8f),
                                Actions.moveBy(0, -10, 0.8f))));
    }

    /** 快速跑来（从左侧冲入画面） */
    public static void runIn(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.setTranslation(-350, 0);
        img.addAction(
                Actions.parallel(
                        Actions.moveBy(350, 0, 0.3f),
                        Actions.fadeIn(0.3f)));
    }

    /** 快速跑开（向右侧冲出画面），结束后自动清空 */
    public static void runOut(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.parallel(
                        Actions.moveBy(400, 0, 0.3f),
                        Actions.fadeOut(0.3f)));
        Time.runTask(0.4f, () -> clear(img));
    }

    /** 摔倒（向后倒下淡出），结束后自动重置 */
    public static void fallDown(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.parallel(
                        Actions.rotateBy(-60, 0.4f),
                        Actions.moveBy(0, -80, 0.5f),
                        Actions.fadeOut(0.5f)));
        Time.runTask(0.6f, () -> clear(img));
    }

    /** 被打后仰，结束后自动重置 */
    public static void hitBack(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.parallel(
                        Actions.moveBy(-40, -20, 0.2f),
                        Actions.rotateBy(-25, 0.2f),
                        Actions.fadeOut(0.2f)));
        Time.runTask(0.3f, () -> clear(img));
    }

    /** 跳一下 */
    public static void jump(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.sequence(
                        Actions.moveBy(0, 60, 0.2f),
                        Actions.moveBy(0, -60, 0.2f)));
    }

    /** 害羞小幅度抖动，2.5秒后自动停止 */
    public static void shyShake(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.forever(
                        Actions.sequence(
                                Actions.scaleBy(0.03f, -0.03f, 0.12f),
                                Actions.scaleBy(-0.03f, 0.03f, 0.12f))));
        Time.runTask(2.5f, () -> clear(img));
    }

    /** 点头（同意） */
    public static void nod(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.sequence(
                        Actions.moveBy(0, -10, 0.1f),
                        Actions.moveBy(0, 10, 0.1f),
                        Actions.moveBy(0, -8, 0.1f),
                        Actions.moveBy(0, 8, 0.1f)));
    }

    /** 摇头（拒绝） */
    public static void shakeHead(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.sequence(
                        Actions.moveBy(10, 0, 0.08f),
                        Actions.moveBy(-20, 0, 0.16f),
                        Actions.moveBy(10, 0, 0.08f)));
    }

    /** 心跳缩放（紧张/心动），4秒后自动停止 */
    public static void heartbeat(GalgameDialogueUI ui) {
        Image img = ui.characterSprite;
        clear(img);
        img.addAction(
                Actions.forever(
                        Actions.sequence(
                                Actions.scaleTo(1.08f, 1.08f, 0.25f),
                                Actions.scaleTo(1.00f, 1.00f, 0.25f))));
        Time.runTask(4f, () -> clear(img));
    }

    public static void shySteam(GalgameDialogueUI ui) {
        ui.characterSprite.clearActions();
        ui.characterSprite.addAction(Actions.parallel(
                Actions.forever(Actions.sequence(Actions.translateBy(6, 0, 0.12f, arc.math.Interp.smooth),
                        Actions.translateBy(-12, 0, 0.24f, arc.math.Interp.smooth),
                        Actions.translateBy(6, 0, 0.12f, arc.math.Interp.smooth))),
                Actions.forever(
                        Actions.sequence(Actions.scaleTo(0.97f, 1.03f, 0.15f), Actions.scaleTo(1.03f, 0.97f, 0.15f)))));
        TextureRegion steamRegion = Core.atlas.find("particle");
        if (steamRegion.found()) {
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    Image steam = new Image(steamRegion);
                    steam.setOrigin(Align.center);
                    ui.addChild(steam);
                    steam.setPosition(
                            ui.characterSprite.getX(Align.center) + rand.random(-15, 15) + 8,
                            ui.characterSprite.getY(Align.top) + 10,
                            Align.bottom);
                    steam.addAction(Actions.sequence(
                            Actions.parallel(
                                    Actions.moveBy(0, 30, 0.8f, arc.math.Interp.pow2Out),
                                    Actions.scaleBy(0.4f, 0.4f, 0.7f),
                                    Actions.fadeOut(0.8f)),
                            Actions.remove()));
                }
            }, 0.0f, 0.5f, 6);
        }
        Time.runTask(220f, () -> {
            ui.characterSprite.clearActions();
        });
    }

    // TODO 等
    public static void sweat(GalgameDialogueUI ui) {
        TextureRegion sweatRegion = Core.atlas.find("crystal-sweat");

    }
}
