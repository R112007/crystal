package crystal.util;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.game.EventType.*;

public final class BerlinNoiseOverlay {

    /* ============== 对外唯一 API ============== */
    /**
     * 世界坐标 2D 特效
     * 
     * @param worldX   世界左下角 X
     * @param worldY   世界左下角 Y
     * @param duration 持续秒数
     * @param wCells   横向方块数（世界单位）
     * @param hCells   纵向方块数（世界单位）
     */
    public static void draw(float worldX, float worldY, float duration, int wCells, int hCells) {
        if (active)
            return;
        active = true;
        life = duration;

        /* 1. 生成瓦片纹理（每格 1 世界单位） */
        int texW = wCells;
        int texH = hCells;
        Pixmap map = new Pixmap(texW, texH);
        for (int py = 0; py < texH; py++) {
            for (int px = 0; px < texW; px++) {
                float g = perlin2d(px * 0.1f, py * 0.1f);
                int gray = (int) (g * 255);
                int argb = (gray << 24) | (gray << 16) | (gray << 8) | 0xFF;
                map.fillRect(px, texH - 1 - py, 1, 1, argb);
            }
        }
        texture = new Texture(map);
        region = new TextureRegion(texture);
        map.dispose();

        /* 2. 记录世界范围 */
        worldLeft = worldX;
        worldBottom = worldY;
        worldWidth = wCells;
        worldHeight = hCells;

        /* 3. 注册绘制（不保存句柄，靠时间结束） */
        Events.run(Trigger.draw, BerlinNoiseOverlay::render);
    }

    /* ============== 内部状态 ============== */
    private static boolean active;
    private static float life;
    private static Texture texture;
    private static TextureRegion region;
    private static float worldLeft, worldBottom, worldWidth, worldHeight;

    /* ============== 每帧绘制 ============== */
    private static void render() {
        if (!active || texture == null)
            return;
        life -= arc.util.Time.delta / 60f;
        if (life <= 0) {
            if (texture != null)
                texture.dispose();
            active = false; // 自动停止，不再绘制
            return;
        }

        float fade = (life < 1f) ? life : 1f;
        Draw.color(fade, fade, fade, 1f);
        Draw.rect(region,
                worldLeft + worldWidth / 2f,
                worldBottom + worldHeight / 2f,
                worldWidth,
                worldHeight);
        Draw.reset();
    }

    /* ============== 柏林噪声 ============== */
    private static float perlin2d(float x, float y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        float xf = x - (int) Math.floor(x);
        float yf = y - (int) Math.floor(y);
        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);
        float a = grad(xi, yi, xf, yf);
        float b = grad(xi + 1, yi, xf - 1, yf);
        float c = grad(xi, yi + 1, xf, yf - 1);
        float d = grad(xi + 1, yi + 1, xf - 1, yf - 1);
        return Mathf.lerp(Mathf.lerp(a, b, u), Mathf.lerp(c, d, u), v) * 0.5f + 0.5f;
    }

    private static float grad(int ix, int iy, float x, float y) {
        long h = ix * 137L + iy * 149L;
        float angle = (h & 1023) * 0.006135923f;
        return x * Mathf.cos(angle) + y * Mathf.sin(angle);
    }
}
