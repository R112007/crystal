package crystal.game;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Cons;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.game.EventType.*;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;

import static mindustry.Vars.*;

/**
 * 多区块独立波数触发器（完全复用版）
 * 支持同时监听N个不同区块，每个区块独立配置触发规则，互不干扰
 */
@SuppressWarnings("unchecked")
public class MultiSectorWaveTrigger {
  // 单例实例
  private static final MultiSectorWaveTrigger INSTANCE = new MultiSectorWaveTrigger();
  // 防重复初始化标记
  private boolean initialized = false;
  // 所有要监听的区块任务列表
  private final Seq<SectorWatchTask> watchTasks = new Seq<>();

  // ============================================== 对外核心API
  // ==============================================

  /**
   * 获取管理器单例
   */
  public static MultiSectorWaveTrigger get() {
    return INSTANCE;
  }

  /**
   * 初始化管理器，全局仅需调用一次！
   * 建议在模组主类init()方法中执行
   */
  public void init() {
    if (initialized) {
      Log.warn("MultiSectorWaveTrigger 已初始化，禁止重复注册！");
      return;
    }
    initialized = true;
    // 全局事件仅注册一次，所有区块任务共用，性能最优
    registerGlobalEvents();
    Log.info("多区块波数触发器初始化完成，当前监听任务数：" + watchTasks.size);
  }

  /**
   * 添加区块监听任务（核心复用方法）
   * 想监听多少个区块，就调用多少次这个方法
   *
   * @param task 区块监听任务配置
   */
  public MultiSectorWaveTrigger addTask(SectorWatchTask task) {
    if (task == null)
      return this;
    // 防重复添加相同区块的任务
    if (watchTasks.contains(t -> t.isSameTask(task))) {
      Log.warn("已存在相同的区块监听任务，跳过添加：" + task.taskName);
      return this;
    }
    watchTasks.add(task);
    Log.info("已添加区块监听任务：" + task.taskName);
    return this;
  }

  /**
   * 重置指定区块的所有触发记录
   */
  public void resetTask(String taskName) {
    watchTasks.each(t -> {
      if (t.taskName.equals(taskName)) {
        t.resetAllTriggerState();
      }
    });
  }

  /**
   * 重置所有区块的触发记录
   */
  public void resetAllTasks() {
    watchTasks.each(SectorWatchTask::resetAllTriggerState);
    Log.info("所有区块监听任务的触发记录已重置");
  }

  // ============================================== 全局事件注册（仅一次）
  // ==============================================

  private void registerGlobalEvents() {
    // 1. 世界加载完成，校验所有任务的区块状态
    Events.on(WorldLoadEndEvent.class, event -> {
      Sector currentSector = state.getSector();
      watchTasks.each(task -> task.onSectorEnter(currentSector));
    });

    // 2. 核心：波数刷新时，分发给对应区块的任务
    Events.on(WaveEvent.class, event -> {
      int currentWave = state.wave;
      Sector currentSector = state.getSector();
      watchTasks.each(task -> task.onWaveUpdate(currentSector, currentWave));
    });

    // 3. 玩家发射到新区块时，更新所有任务状态
    Events.on(SectorLaunchEvent.class, event -> {
      watchTasks.each(task -> task.onSectorEnter(event.sector));
    });

    // 4. 区块失守时，对应任务自动重置
    Events.on(SectorLoseEvent.class, event -> {
      watchTasks.each(task -> task.onSectorLose(event.sector));
    });

    // 5. 区块占领时，日志记录
    Events.on(SectorCaptureEvent.class, event -> {
      watchTasks.each(task -> task.onSectorCapture(event.sector));
    });
  }

  // ============================================== 区块监听任务类（核心复用单元）
  // ==============================================

  /**
   * 单个区块的监听任务配置
   * 每个实例对应一个要监听的区块，完全独立，互不干扰
   */
  public static class SectorWatchTask {
    // 任务名称（唯一标识，用于日志和状态管理）
    public final String taskName;
    // 目标区块所属星球
    public final Planet targetPlanet;
    // 目标区块ID
    public final int targetSectorId;
    // 该区块的波数触发规则列表（可添加N个不同波数的规则）
    public final Seq<WaveTriggerConfig> triggerConfigs;
    // 加载存档时是否补触发未执行的规则
    public final boolean checkOnLoad;
    // 区块失守后是否重置触发记录
    public final boolean resetOnLose;

    // 运行时状态
    private boolean isInTargetSector = false;
    private Sector currentSector = null;

    /**
     * 完整构造器
     */
    public SectorWatchTask(String taskName, Planet targetPlanet, int targetSectorId, boolean checkOnLoad,
        boolean resetOnLose) {
      this.taskName = taskName;
      this.targetPlanet = targetPlanet;
      this.targetSectorId = targetSectorId;
      this.triggerConfigs = new Seq<>();
      this.checkOnLoad = checkOnLoad;
      this.resetOnLose = resetOnLose;
    }

    /**
     * 快捷构造器（默认开启加载补触发、失守重置）
     */
    public SectorWatchTask(String taskName, Planet targetPlanet, int targetSectorId) {
      this(taskName, targetPlanet, targetSectorId, true, true);
    }

    /**
     * 从战役预设快速创建任务
     */
    public SectorWatchTask(String taskName, SectorPreset preset) {
      this(taskName, preset.planet, preset.sector.id, true, true);
    }

    /**
     * 给该区块添加波数触发规则
     */
    public SectorWatchTask addRule(int targetWave, boolean triggerOnce, Cons2<Sector, Integer> logic) {
      this.triggerConfigs.add(new WaveTriggerConfig(targetWave, triggerOnce, logic));
      return this;
    }

    // 进入区块时调用
    protected void onSectorEnter(Sector sector) {
      this.currentSector = sector;
      this.isInTargetSector = checkIsTargetSector(sector);
      if (isInTargetSector) {
        Log.info("任务【" + taskName + "】：已进入目标区块，当前波数：" + state.wave);
        if (checkOnLoad) {
          checkAndTriggerAll(state.wave);
        }
      }
    }

    public SectorWatchTask addWaveEnemyRule(int minWave, int maxEnemies, boolean triggerOnce,
        Cons2<Sector, Integer> logic) {
      this.triggerConfigs.add(new WaveTriggerConfig(minWave, triggerOnce, (sector, wave) -> {
        // 用 forceRun，传入条件：满足则执行逻辑并让 boolp 返回 true
        forceRun(() -> {
          if (checkIsTargetSector(sector) && wave >= minWave && state.enemies <= maxEnemies) {
            logic.get(sector, wave);
            return true; // 返回 true → forceRun 自动 cancel
          }
          return false;
        });
      }));
      return this;
    }

    public static void forceRun(Boolp boolp) {
      Timer.schedule((Timer.Task) new Timer.Task() {
        public void run() {
          try {
            if (boolp.get()) {
              this.cancel();
            }
          } catch (final Throwable e) {
            Log.err(e);
            this.cancel();
          }
        }
      }, 0.0f, 0.5f, -1);
    }

    // 波数更新时调用
    protected void onWaveUpdate(Sector sector, int currentWave) {
      if (!isInTargetSector || !checkIsTargetSector(sector))
        return;
      Log.info("任务【" + taskName + "】：波数刷新至第" + currentWave + "波，开始校验规则");
      checkAndTriggerAll(currentWave);
    }

    // 区块失守时调用
    protected void onSectorLose(Sector sector) {
      if (resetOnLose && checkIsTargetSector(sector)) {
        resetAllTriggerState();
        Log.info("任务【" + taskName + "】：目标区块失守，所有触发记录已重置");
      }
    }

    // 区块占领时调用
    protected void onSectorCapture(Sector sector) {
      if (checkIsTargetSector(sector)) {
        Log.info("任务【" + taskName + "】：目标区块已占领，最终波数：" + state.wave);
      }
    }

    // 校验是否为目标区块
    private boolean checkIsTargetSector(Sector sector) {
      return sector != null && sector.planet == targetPlanet && sector.id == targetSectorId;
    }

    // 校验并执行所有符合条件的规则
    private void checkAndTriggerAll(int currentWave) {
      if (currentSector == null || !isInTargetSector)
        return;

      for (WaveTriggerConfig config : triggerConfigs) {
        if (!checkTriggerCondition(config, currentWave))
          continue;

        try {
          config.triggerLogic().get(currentSector, currentWave);
          Log.info("任务【" + taskName + "】：波数" + config.targetWave() + "规则执行成功");
        } catch (Exception e) {
          Log.err("任务【" + taskName + "】：波数" + config.targetWave() + "规则执行异常", e);
        }

        if (config.triggerOnce()) {
          markTriggered(config);
        }
      }
    }

    // 校验触发条件
    private boolean checkTriggerCondition(WaveTriggerConfig config, int currentWave) {
      if (currentWave < config.targetWave())
        return false;

      if (!config.triggerOnce())
        return currentWave % config.targetWave() == 0;

      return !isAlreadyTriggered(config);
    }

    private String getTriggerKey(WaveTriggerConfig config) {
      return "wave-trigger-" + taskName + "-" + targetPlanet.name + "-" + targetSectorId + "-wave-"
          + config.targetWave();
    }

    private boolean isAlreadyTriggered(WaveTriggerConfig config) {
      return Core.settings.getBool(getTriggerKey(config), false);
    }

    private void markTriggered(WaveTriggerConfig config) {
      Core.settings.put(getTriggerKey(config), true);
      Core.settings.forceSave();
    }

    protected void resetAllTriggerState() {
      for (WaveTriggerConfig config : triggerConfigs) {
        Core.settings.remove(getTriggerKey(config));
      }
      Core.settings.forceSave();
    }

    protected boolean isSameTask(SectorWatchTask other) {
      return other.taskName.equals(this.taskName)
          || (other.targetPlanet == this.targetPlanet && other.targetSectorId == this.targetSectorId);
    }
  }

  // ============================================== 原record替换为普通类
  // ==============================================
  public static class WaveTriggerConfig {
    private final int targetWave;
    private final boolean triggerOnce;
    private final Cons2<Sector, Integer> triggerLogic;

    public WaveTriggerConfig(int targetWave, boolean triggerOnce, Cons2<Sector, Integer> triggerLogic) {
      this.targetWave = targetWave;
      this.triggerOnce = triggerOnce;
      this.triggerLogic = triggerLogic;
    }

    public int targetWave() {
      return targetWave;
    }

    public boolean triggerOnce() {
      return triggerOnce;
    }

    public Cons2<Sector, Integer> triggerLogic() {
      return triggerLogic;
    }
  }

  // ============================================== 双入参函数式接口
  // ==============================================
  @FunctionalInterface
  public interface Cons2<A, B> {
    void get(A a, B b);
  }
}
