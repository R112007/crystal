package crystal.ui.gal;

import arc.scene.ui.Image;

/**
 * 实例化的立绘动作接口。
 * 相比 {@link CharacterActions} 中基于字符串反射的静态方法，
 * 使用 {@code SpriteAction} 实例可以避免拼写错误，并能在 Lua / Java 中直接引用。
 */
@FunctionalInterface
public interface SpriteAction {
    /**
     * 执行动作。
     *
     * @param ui 当前对话 UI，可访问左右立绘
     */
    void run(GalgameDialogueUI ui);

    /**
     *  convenience：获取当前说话侧对应的立绘。
     */
    default Image target(GalgameDialogueUI ui) {
        return ui.activeSprite();
    }
}
