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
  private static final float retractAnimSpeed = 0.04f;
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

  @Replace
  public float defaultLegAngle(int index) {
    return baseRotation + (-90f) + 180f / legs.length + 360f / legs.length * index;
  }

  @Insert(value = "update()", block = Legsc.class)
  protected void updateRetractableLegs() {
    if (!(self() instanceof Corec core))
      return;
    if (legs.length == 0)
      return;
    boolean deployed = core.deployed();
    int totalLegs = legs.length;
    float rot = baseRotation;
    if (startJoints.length != totalLegs) {
      startJoints = new Vec2[totalLegs];
      startBases = new Vec2[totalLegs];
      for (int i = 0; i < totalLegs; i++) {
        startJoints[i] = new Vec2();
        startBases[i] = new Vec2();
      }
    }
    if (deployed != lastDeployed) {
      lastDeployed = deployed;
      for (int i = 0; i < totalLegs; i++) {
        Leg leg = legs[i];
        startJoints[i].set(leg.joint.x - x(), leg.joint.y - y()).rotate(-rot);
        startBases[i].set(leg.base.x - x(), leg.base.y - y()).rotate(-rot);
      }
    }
    float targetProgress = deployed ? 1f : 0f;
    retractProgress = Mathf.approach(retractProgress, targetProgress, retractAnimSpeed * Time.delta);
    if (retractProgress <= 0.001f)
      return;
    for (int i = 0; i < totalLegs; i++) {
      Leg leg = legs[i];
      Vec2 targetJoint = Tmp.v1;
      Vec2 targetBase = Tmp.v2;
      float lerpFactor;
      if (deployed) {
        targetJoint.setZero();
        targetBase.setZero();
        lerpFactor = retractProgress;
      } else {
        targetJoint.set(leg.joint.x - x(), leg.joint.y - y()).rotate(-rot);
        targetBase.set(leg.base.x - x(), leg.base.y - y()).rotate(-rot);
        lerpFactor = 1f - retractProgress;
      }
      Vec2 localJoint = Tmp.v3.set(startJoints[i]).lerp(targetJoint, lerpFactor);
      Vec2 localBase = Tmp.v4.set(startBases[i]).lerp(targetBase, lerpFactor);
      leg.joint.set(localJoint.rotate(rot).add(x(), y()));
      leg.base.set(localBase.rotate(rot).add(x(), y()));
    }
    if (retractProgress >= 0.999f) {
      for (Leg leg : legs) {
        leg.moving = false;
        leg.stage = 0f;
      }
    }
  }

  public float retractProgress() {
    return retractProgress;
  }
}
