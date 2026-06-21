package crystal.entities.comp;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import ent.anno.Annotations.EntityComponent;
import ent.anno.Annotations.Import;
import ent.anno.Annotations.Insert;
import ent.anno.Annotations.Replace;
import mindustry.entities.Leg;
import mindustry.gen.Legsc;
import mindustry.type.UnitType;
import crystal.gen.Corec;

@EntityComponent
abstract class RetractableLegsComp implements Legsc {
  /** 收放腿动画速度，数值越大过渡越快 */
  private static final float retractAnimSpeed = 0.075f;
  // ========== 内部状态字段 ==========
  /** 收缩进度：0=完全伸出（正常状态），1=完全收缩（部署状态） */
  private transient float retractProgress = 0f;
  /** 上一帧部署状态，用于检测状态切换瞬间 */
  private transient boolean lastDeployed = false;
  /** 动画起始姿态：关节局部坐标（相对单位中心与朝向） */
  private transient Vec2[] startJoints = new Vec2[0];
  /** 动画起始姿态：脚底局部坐标（相对单位中心与朝向） */
  private transient Vec2[] startBases = new Vec2[0];
  // ========== 从LegsComp导入字段 ==========
  @Import
  Leg[] legs;
  @Import
  UnitType type;
  @Import
  float baseRotation;

  /** 覆盖默认腿角度分布：改为关于y轴对称的360度均匀分布 */
  @Replace
  public float defaultLegAngle(int index) {
    // 起始角度 = -90 + 180/n，保证第i条与第n-1-i条角度之和为180度（关于y轴对称）
    return baseRotation + (-90f) + 180f / legs.length + 360f / legs.length * index;
  }

  /** 在原版腿部update末尾追加收放腿逻辑 */
  @Insert(value = "update()", block = Legsc.class, after = true)
  protected void updateRetractableLegs() {
    if (!(self() instanceof Corec core))
      return;
    // 安全检查：腿还没初始化时直接跳过
    if (legs.length == 0)
      return;
    boolean deployed = core.deployed();
    int totalLegs = legs.length;
    float rot = baseRotation;
    // ===== 确保起点数组长度与腿数量匹配 =====
    if (startJoints.length != totalLegs) {
      startJoints = new Vec2[totalLegs];
      startBases = new Vec2[totalLegs];
      for (int i = 0; i < totalLegs; i++) {
        startJoints[i] = new Vec2();
        startBases[i] = new Vec2();
      }
    }
    // ===== 状态切换瞬间：记录当前姿态作为【固定动画起点】，全程不变 =====
    if (deployed != lastDeployed) {
      lastDeployed = deployed;
      // 记录切换瞬间的腿部局部坐标，作为插值的固定起点
      for (int i = 0; i < totalLegs; i++) {
        Leg leg = legs[i];
        startJoints[i].set(leg.joint.x - x(), leg.joint.y - y()).rotate(-rot);
        startBases[i].set(leg.base.x - x(), leg.base.y - y()).rotate(-rot);
      }
    }
    // ===== 平滑更新收缩进度 =====
    // deployed=true（部署/缩腿）→ 目标进度 1（完全收缩）
    // deployed=false（正常/伸腿）→ 目标进度 0（完全伸出，交给原版物理）
    float targetProgress = deployed ? 1f : 0f;
    retractProgress = Mathf.approach(retractProgress, targetProgress, retractAnimSpeed * Time.delta);
    // 完全伸出时：完全交还给原版物理，腿正常走路、自然停下
    if (retractProgress <= 0.001f)
      return;
    // ===== 逐腿插值计算（仅部署/收缩过程中覆盖） =====
    for (int i = 0; i < totalLegs; i++) {
      Leg leg = legs[i];
      Vec2 targetJoint = Tmp.v1;
      Vec2 targetBase = Tmp.v2;
      float lerpFactor;
      if (deployed) {
        // —— 部署/缩腿：所有腿完全收回到中心
        targetJoint.setZero();
        targetBase.setZero();
        // progress从0升到1，lerpFactor从0升到1，从起点向完全收缩过渡
        lerpFactor = retractProgress;
      } else {
        // —— 伸腿/恢复：目标为当前帧原版计算结果，逐步靠拢无感交接
        targetJoint.set(leg.joint.x - x(), leg.joint.y - y()).rotate(-rot);
        targetBase.set(leg.base.x - x(), leg.base.y - y()).rotate(-rot);
        // progress从1降到0，lerpFactor从0升到1，从收缩姿态向原版姿态过渡
        lerpFactor = 1f - retractProgress;
      }
      // 局部坐标插值
      Vec2 localJoint = Tmp.v3.set(startJoints[i]).lerp(targetJoint, lerpFactor);
      Vec2 localBase = Tmp.v4.set(startBases[i]).lerp(targetBase, lerpFactor);
      // 转回世界坐标赋值
      leg.joint.set(localJoint.rotate(rot).add(x(), y()));
      leg.base.set(localBase.rotate(rot).add(x(), y()));
    }
    // 完全收缩后锁死腿部状态，杜绝闪烁空转
    if (retractProgress >= 0.999f) {
      for (Leg leg : legs) {
        leg.moving = false;
        leg.stage = 0f;
      }
    }
  }

  /** 对外暴露收缩进度，供渲染透明度控制 */
  public float retractProgress() {
    return retractProgress;
  }
}
