package crystal.ui.gal;

import arc.scene.ui.Image;

/**
 * 旧版立绘动作工具类。保留以兼容已有代码，
 * 但新代码推荐直接使用 {@link SpriteActions} 中的实例常量。
 *
 * @see SpriteActions
 */
public final class CharacterActions {
    private CharacterActions() {
    }

    private static Image target(GalgameDialogueUI ui) {
        return ui.activeSprite();
    }

    public static void clear(Image img) {
        SpriteActions.clear.run(uiFrom(img));
    }

    public static void clear(GalgameDialogueUI ui) {
        SpriteActions.clear.run(ui);
    }

    public static void shake(GalgameDialogueUI ui) {
        SpriteActions.shake.run(ui);
    }

    public static void shakeHard(GalgameDialogueUI ui) {
        SpriteActions.shakeHard.run(ui);
    }

    public static void floatUpDown(GalgameDialogueUI ui) {
        SpriteActions.floatUpDown.run(ui);
    }

    public static void runIn(GalgameDialogueUI ui) {
        SpriteActions.runIn.run(ui);
    }

    public static void runOut(GalgameDialogueUI ui) {
        SpriteActions.runOut.run(ui);
    }

    public static void fallDown(GalgameDialogueUI ui) {
        SpriteActions.fallDown.run(ui);
    }

    public static void hitBack(GalgameDialogueUI ui) {
        SpriteActions.hitBack.run(ui);
    }

    public static void jump(GalgameDialogueUI ui) {
        SpriteActions.jump.run(ui);
    }

    public static void shyShake(GalgameDialogueUI ui) {
        SpriteActions.shyShake.run(ui);
    }

    public static void nod(GalgameDialogueUI ui) {
        SpriteActions.nod.run(ui);
    }

    public static void shakeHead(GalgameDialogueUI ui) {
        SpriteActions.shakeHead.run(ui);
    }

    public static void heartbeat(GalgameDialogueUI ui) {
        SpriteActions.heartbeat.run(ui);
    }

    public static void shySteam(GalgameDialogueUI ui) {
        SpriteActions.shySteam.run(ui);
    }

    /** 从 Image 反查 UI 的辅助方法（旧 API 兼容）。 */
    private static GalgameDialogueUI uiFrom(Image img) {
        return GalgameDialogueManager.instance.ui;
    }
}
