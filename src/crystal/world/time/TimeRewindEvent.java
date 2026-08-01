package crystal.world.time;

public class TimeRewindEvent {
    /** 目标回溯时长（秒）。<=0 表示回退到历史最早可用帧。 */
    public final float duration;
    /** 若 true，则立即中断当前回溯并恢复正向模拟。 */
    public final boolean interrupt;

    public TimeRewindEvent(float duration) {
        this(duration, false);
    }

    public TimeRewindEvent(float duration, boolean interrupt) {
        this.duration = duration;
        this.interrupt = interrupt;
    }

    /** 创建一个打断事件。 */
    public static TimeRewindEvent interrupt() {
        return new TimeRewindEvent(0f, true);
    }
}
