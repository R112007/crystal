package crystal.entities.mindustryX;

import arc.math.WindowedMean;
import arc.util.Time;
import mindustry.gen.Healthc;
import mindustry.gen.Shieldc;

import java.lang.reflect.Method;

/**
 * 每个单位独立的 MindustryX 适配器，负责健康统计和事件触发。
 * 需要单位提供 health()、shield() 方法，并实现 Healthc。
 */
public class MindustryXAdapter {
    // 健康统计滑动窗口
    private final WindowedMean healthBalanceMean = new WindowedMean(120);
    // 上一帧数据
    private float lastHealth;
    private float lastShield;
    // 上一次触发 healthChanged 时的生命值
    private float lastHealthChanged;

    // MindustryX 事件类（静态，但每个实例共享检测结果）
    private static final Class<?> HealthChanged;
    private static final boolean hasHealthChanged;

    static {
        Class<?> cls = null;
        boolean loaded = false;
        try {
            cls = Class.forName("mindustryX.events.HealthChangedEvent");
            loaded = true;
        } catch (ClassNotFoundException ignored) {}
        HealthChanged = cls;
        hasHealthChanged = loaded;
    }

    public MindustryXAdapter(Shieldc entity) {
        // 初始化记录值
        this.lastHealth = entity.health();
        this.lastShield = entity.shield();
        this.lastHealthChanged = entity.health();
    }

    public void init(Shieldc shield) {
        lastHealth = shield.health();
        lastShield = shield.shield();
        lastHealthChanged = shield.health();
    }

    /**
     * 每帧调用，更新健康滑动窗口。
     * @param entity 实现了 Healthc 的单位
     */
    public void update(Shieldc entity) {
        float delta = Time.delta;
        if (delta > 0.001f) {
            float rate = (entity.shield() - lastShield + (entity.health() - lastHealth)) / delta;
            healthBalanceMean.add(rate);
        }
        lastHealth = entity.health();
        lastShield = entity.shield();
    }

    /**
     * 在伤害/治疗后调用，触发 healthChanged 事件。
     * @param entity 实现了 Healthc 的单位
     */
    public void fireHealthChanged(Healthc entity) {
        if (!hasHealthChanged) return;
        float currentHealth = entity.health();
        float delta = lastHealthChanged - currentHealth;
        if (delta != 0) {
            try {
                Method m = HealthChanged.getMethod("fire", Healthc.class, float.class);
                m.invoke(null, entity, delta);
            } catch (Exception e) {
                // 反射失败，不再重试
                // 可考虑记录日志，但静默处理
            }
        }
        lastHealthChanged = currentHealth;
    }

    /**
     * 获取健康平衡值（滑动窗口均值）。
     */
    public float getHealthBalance() {
        return healthBalanceMean.mean();
    }

    // 如果需要返回 statuses，则需要传入 entity 的 statuses 字段，
    // 但 statuses 通常由组件提供，不在此适配器中管理。
}