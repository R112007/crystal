package crystal.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Camera;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;

/**
 * 在 Mindustry 渲染管线中注入黑洞后处理效果。
 * 默认绑定到 postDraw，从效果缓冲区采样并在全屏做一次引力透镜。
 */
public class BlackHoleRenderer {
    public static BlackHoleShader shader;

    /** 是否启用渲染 */
    public static boolean active = false;

    /** 黑洞的世界坐标目标 */
    public static float targetX, targetY;

    public static void init() {
        shader = new BlackHoleShader();
        Events.run(EventType.Trigger.postDraw, BlackHoleRenderer::renderFrame);
    }

    /** 启用并移动到指定世界坐标 */
    public static void enableAt(float worldX, float worldY) {
        targetX = worldX;
        targetY = worldY;
        active = true;
    }

    /** 关闭效果 */
    public static void disable() {
        active = false;
    }

    /** 切换开关 */
    public static void toggle() {
        active = !active;
    }

    public static boolean enabled() {
        return active;
    }

    /** 由 Trigger.postDraw 调用 */
    public static void renderFrame() {
        if (!active || shader == null || Vars.renderer.effectBuffer == null)
            return;
        if (Vars.state.isMenu())
            return;

        Camera cam = Core.camera;
        float sx = (targetX - cam.position.x) / cam.width + 0.5f;
        float sy = (targetY - cam.position.y) / cam.height + 0.5f;

        shader.center.set(sx, sy);
        shader.time = Time.time / 60f;

        Vars.renderer.effectBuffer.blit(shader);
    }
}
