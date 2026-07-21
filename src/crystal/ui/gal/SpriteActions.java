package crystal.ui.gal;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Rand;
import arc.scene.actions.Actions;
import arc.scene.ui.Image;
import arc.util.Align;
import arc.util.Time;
import arc.util.Timer;

/**
 * 预定义的立绘动作实例集合。
 * 用法示例：
 * <pre>
 *   new DialogueLine(yi, Expression.normal, "你好").withAction(SpriteActions.shake);
 * </pre>
 */
public final class SpriteActions {
    private SpriteActions() {
    }

    private static final Rand rand = new Rand();

    /** 重置目标立绘的变换与动作。 */
    public static final SpriteAction clear = ui -> {
        Image img = ui.activeSprite();
        if (img != null) {
            img.clearActions();
            img.setTranslation(0, 0);
            img.setScale(1f);
            img.setRotation(0f);
            img.color.a = 1f;
        }
    };

    /** 轻微颤抖（紧张/害怕），2 秒后自动停止。 */
    public static final SpriteAction shake = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0, 4, 0.05f),
                Actions.moveBy(0, -8, 0.1f),
                Actions.moveBy(0, 4, 0.05f))));
        Time.runTask(2f, () -> clear.run(ui));
    };

    /** 剧烈颤抖（惊吓/震怒），1.5 秒后自动停止。 */
    public static final SpriteAction shakeHard = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0, 10, 0.04f),
                Actions.moveBy(0, -20, 0.08f),
                Actions.moveBy(0, 10, 0.04f))));
        Time.runTask(1.5f, () -> clear.run(ui));
    };

    /** 上下浮动（呼吸/待机），永久循环。 */
    public static final SpriteAction floatUpDown = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0, 10, 0.8f),
                Actions.moveBy(0, -10, 0.8f))));
    };

    /** 快速跑来（从左侧冲入画面）。 */
    public static final SpriteAction runIn = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.setTranslation(-350, 0);
        img.addAction(Actions.parallel(
                Actions.moveBy(350, 0, 0.3f),
                Actions.fadeIn(0.3f)));
    };

    /** 快速跑开（向右侧冲出画面），结束后自动清空。 */
    public static final SpriteAction runOut = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.parallel(
                Actions.moveBy(400, 0, 0.3f),
                Actions.fadeOut(0.3f)));
        Time.runTask(0.4f, () -> clear.run(ui));
    };

    /** 摔倒（向后倒下淡出），结束后自动重置。 */
    public static final SpriteAction fallDown = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.parallel(
                Actions.rotateBy(-60, 0.4f),
                Actions.moveBy(0, -80, 0.5f),
                Actions.fadeOut(0.5f)));
        Time.runTask(0.6f, () -> clear.run(ui));
    };

    /** 被打后仰，结束后自动重置。 */
    public static final SpriteAction hitBack = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.parallel(
                Actions.moveBy(-40, -20, 0.2f),
                Actions.rotateBy(-25, 0.2f),
                Actions.fadeOut(0.2f)));
        Time.runTask(0.3f, () -> clear.run(ui));
    };

    /** 跳一下。 */
    public static final SpriteAction jump = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.sequence(
                Actions.moveBy(0, 60, 0.2f),
                Actions.moveBy(0, -60, 0.2f)));
    };

    /** 害羞小幅度抖动，2.5 秒后自动停止。 */
    public static final SpriteAction shyShake = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.forever(Actions.sequence(
                Actions.scaleBy(0.03f, -0.03f, 0.12f),
                Actions.scaleBy(-0.03f, 0.03f, 0.12f))));
        Time.runTask(2.5f, () -> clear.run(ui));
    };

    /** 点头（同意）。 */
    public static final SpriteAction nod = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.sequence(
                Actions.moveBy(0, -10, 0.1f),
                Actions.moveBy(0, 10, 0.1f),
                Actions.moveBy(0, -8, 0.1f),
                Actions.moveBy(0, 8, 0.1f)));
    };

    /** 摇头（拒绝）。 */
    public static final SpriteAction shakeHead = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.sequence(
                Actions.moveBy(10, 0, 0.08f),
                Actions.moveBy(-20, 0, 0.16f),
                Actions.moveBy(10, 0, 0.08f)));
    };

    /** 心跳缩放（紧张/心动），4 秒后自动停止。 */
    public static final SpriteAction heartbeat = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.08f, 1.08f, 0.25f),
                Actions.scaleTo(1.00f, 1.00f, 0.25f))));
        Time.runTask(4f, () -> clear.run(ui));
    };

    /** 害羞冒蒸汽，持续约 3.7 秒后自动停止。 */
    public static final SpriteAction shySteam = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.parallel(
                Actions.forever(Actions.sequence(
                        Actions.translateBy(6, 0, 0.12f, arc.math.Interp.smooth),
                        Actions.translateBy(-12, 0, 0.24f, arc.math.Interp.smooth),
                        Actions.translateBy(6, 0, 0.12f, arc.math.Interp.smooth))),
                Actions.forever(Actions.sequence(
                        Actions.scaleTo(0.97f, 1.03f, 0.15f),
                        Actions.scaleTo(1.03f, 0.97f, 0.15f)))));

        TextureRegion steamRegion = Core.atlas.find("particle");
        if (steamRegion.found()) {
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    Image steam = new Image(steamRegion);
                    steam.setOrigin(Align.center);
                    ui.addChild(steam);
                    steam.setPosition(
                            img.getX(Align.center) + rand.random(-15, 15) + 8,
                            img.getY(Align.top) + 10,
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
        Time.runTask(220f / 60f, () -> clear.run(ui));
    };

    /** 两侧立绘同时轻微震动（地震/威慑效果）。 */
    public static final SpriteAction shakeBoth = ui -> {
        for (Image img : ui.allSprites()) {
            if (img == null) continue;
            img.clearActions();
            img.addAction(Actions.forever(Actions.sequence(
                    Actions.moveBy(4, 0, 0.05f),
                    Actions.moveBy(-8, 0, 0.1f),
                    Actions.moveBy(4, 0, 0.05f))));
        }
        Time.runTask(2f, () -> clear.run(ui));
    };

    /** 当前说话角色短暂高亮（缩放强调），用于重要台词。 */
    public static final SpriteAction emphasize = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        img.addAction(Actions.sequence(
                Actions.scaleTo(1.1f, 1.1f, 0.15f),
                Actions.scaleTo(1f, 1f, 0.25f)));
    };

    /** 当前说话角色滑入（从边缘进入）。 */
    public static final SpriteAction slideIn = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        boolean fromLeft = ui.activeSide == Side.left;
        img.setTranslation(fromLeft ? -300 : 300, 0);
        img.color.a = 0f;
        img.addAction(Actions.parallel(
                Actions.moveBy(fromLeft ? 300 : -300, 0, 0.4f, arc.math.Interp.pow3Out),
                Actions.fadeIn(0.4f)));
    };

    /** 当前说话角色滑出（向边缘离开）。 */
    public static final SpriteAction slideOut = ui -> {
        Image img = ui.activeSprite();
        clear.run(ui);
        boolean toLeft = ui.activeSide == Side.left;
        img.addAction(Actions.parallel(
                Actions.moveBy(toLeft ? -300 : 300, 0, 0.35f),
                Actions.fadeOut(0.35f)));
        Time.runTask(0.4f, () -> clear.run(ui));
    };

    /** 对侧角色短暂变暗，突出当前说话角色。 */
    public static final SpriteAction dimOthers = ui -> {
        for (Image img : ui.allSprites()) {
            if (img == null) continue;
            if (img != ui.activeSprite()) {
                img.addAction(Actions.alpha(0.5f, 0.3f));
            } else {
                img.addAction(Actions.alpha(1f, 0.3f));
            }
        }
    };
}
