package crystal.type;

import crystal.ai.type.RammingAI;
import crystal.content.CUnitCommands;
import crystal.gen.*;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.type.UnitType;

/**
 * 冲撞单位类型：配置冲撞伤害、减速、特效等参数。
 * <p>
 * 核心公式：{@code damage = speed * ramDamageMultiplier}
 * <ul>
 * <li>速度 ≥ {@link #minRamSpeed} 时才触发冲撞伤害</li>
 * <li>伤害 ≥ 目标当前血量 → 击杀，速度 *= {@link #killSlowdown}（小幅减速，可连撞）</li>
 * <li>伤害 < 目标血量 → 造成伤害，速度 *= {@link #hitSlowdown}（大幅减速，被挡住）</li>
 * </ul>
 * <p>
 * 本类仅负责配置参数，实际冲撞逻辑在 {@code RammingComp} 组件中实现。
 * {@code RammingUnit} 实体类由注解处理器从
 * {@code @EntityDef({Unitc.class, Rammingc.class})}
 * 自动生成，包含所有单位基础功能 + 冲撞功能。
 * <p>
 * <b>注意：</b>{@code RammingUnit} 的包名取决于注解处理器的 {@code genPackage} 选项。
 * 如果你的 genPackage 不是 {@code mindustry.gen}，请修改上方 import。
 * <p>
 * 使用示例：
 * 
 * <pre>{@code
 * RammingUnitType rammer = new RammingUnitType("rammer") {
 * {
 * health = 1200;
 * speed = 5f;
 * flying = true;
 * hitSize = 12f;
 * ramDamageMultiplier = 80f; // 速度5时伤害=400
 * killSlowdown = 0.8f; // 击杀后保留80%速度
 * hitSlowdown = 0.3f; // 未击杀保留30%速度
 * }
 * };
 * }</pre>
 */
public class RammingUnitType extends UnitType {

    /** 伤害倍率：最终伤害 = 速度 × ramDamageMultiplier */
    public float ramDamageMultiplier = 500f;

    /** 最低冲撞速度（低于此速度不造成伤害） */
    public float minRamSpeed = 1.2f;

    /** 击杀目标后的速度保留率（0.75 = 保留 75% 速度，可继续连撞） */
    public float killSlowdown = 0.75f;

    /** 未击杀目标时的速度保留率（0.35 = 保留 35% 速度，被挡住） */
    public float hitSlowdown = 0.35f;

    /** 冲撞特效（在撞击点播放） */
    public Effect ramEffect = Fx.explosion;

    /** 特效缩放 */
    public float ramEffectScale = 1f;

    /** 是否无视护甲（true 时使用 damagePierce） */
    public boolean ignoreArmor = false;
    public float ramAngleTolerance;

    public RammingUnitType(String name) {
        super(name);
        constructor = MechRammingUnit::create;
        aiController = () -> new RammingAI();
    }

    @Override
    public void init() {
        super.init();
        commands.add(CUnitCommands.rammingCommand);
    }
}
