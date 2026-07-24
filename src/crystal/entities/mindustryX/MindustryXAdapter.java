package crystal.entities.mindustryX;

import arc.math.WindowedMean;
import arc.util.Time;
import crystal.gen.MindustryXc;
import mindustry.gen.Healthc;
import mindustry.gen.Shieldc;

import java.lang.reflect.Method;

/**
 * 每个单位独立的 MindustryX 适配器，负责健康统计和事件触发。
 * 需要单位提供 health()、shield() 方法，并实现 Healthc。
 */
public class MindustryXAdapter {
    // MindustryX 事件类（静态，但每个实例共享检测结果）
    private static Class<?> HealthChanged;
    private static boolean hasHealthChanged;

    static {
        Class<?> cls = null;
        boolean loaded = false;
        try {
            HealthChanged = Class.forName("mindustryX.events.HealthChangedEvent");
            hasHealthChanged = true;
        } catch (ClassNotFoundException ignored) {
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

    // 如果需要返回 statuses，则需要传入 entity 的 statuses 字段，
    // 但 statuses 通常由组件提供，不在此适配器中管理。
}