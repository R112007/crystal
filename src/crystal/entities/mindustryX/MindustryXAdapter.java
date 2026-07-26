package crystal.entities.mindustryX;

import arc.math.WindowedMean;
import arc.util.Time;
import crystal.gen.MindustryXc;
import mindustry.gen.Healthc;
import mindustry.gen.Shieldc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 每个单位独立的 MindustryX 适配器，负责健康统计和事件触发。
 * 需要单位提供 health()、shield() 方法，并实现 Healthc。
 */
public class MindustryXAdapter {
    // MindustryX 事件类（静态，但每个实例共享检测结果）
    private static Class<?> HealthChanged;
    private static boolean hasHealthChanged;
    private static Method fire;

    static {
        Class<?> cls = null;
        boolean loaded = false;
        try {
            HealthChanged = Class.forName("mindustryX.events.HealthChangedEvent");
            hasHealthChanged = true;
            fire = HealthChanged.getMethod("fire", Healthc.class, float.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            hasHealthChanged = false;
        }
    }
    /**
     * 在伤害/治疗后调用，触发 healthChanged 事件。
     * @param entity 实现了 Healthc 的单位
     */
    public static void fireHealthChanged(MindustryXc entity) {
        if (!hasHealthChanged) return;
        float currentHealth = entity.health();
        float delta = entity.lastHealthChanged() - currentHealth;
        if (delta != 0) {
            try {
                Method m = HealthChanged.getMethod("fire", Healthc.class, float.class);
                m.invoke(null, entity, delta);
            } catch (Exception e) {
                // 反射失败，不再重试
                // 可考虑记录日志，但静默处理
            }
        }

        entity.lastHealthChanged(entity.health());
    }

    /**
     *应该可以用
     * @param entity 生命变化的实体
     * @param damage 生命变化的量，受到伤害就写正数，治疗就写负数
     */
    public static void fireHealthChanged(Healthc entity, float damage) {
        if (!hasHealthChanged) return;
        try {
            fire.invoke(null, entity, damage);
        } catch (Exception ignored) {
        }
    }

}