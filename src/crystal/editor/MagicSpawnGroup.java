package crystal.editor;

import arc.func.Cons;
import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.gen.Magicc;
import crystal.type.MagicUnitType;
import mindustry.game.SpawnGroup;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

import java.lang.reflect.Parameter;

/**
 * 扩展原版的 SpawnGroup，支持给魔法单位附加神通。
 * 当波次刷新、单位被创建时，会自动把选中的神通加到 Magicc 单位上。
 */
public class MagicSpawnGroup extends SpawnGroup {
    /** -1 表示没有神通，否则对应 ShenTong.shengTongMap 里的 key */
    public int shenTongId = -1;
    /** 自定义神通构造参数；null 表示沿用神通原有默认逻辑。 */
    public float[] shenTongValues;
    /** 该波次魔法单位使用的境界。 */
    public JingJie jingJie = JingJie.fan;

    public MagicSpawnGroup() {
        // 序列化用
    }

    public MagicSpawnGroup(UnitType type) {
        super(type);
        if (type instanceof MagicUnitType magic) {
            jingJie = magic.jingJie;
        }
    }

    @Override
    public Unit createUnit(Team team, float x, float y, float rotation, int wave, Cons<Unit> cons) {
        Unit unit = super.createUnit(team, x, y, rotation, wave, cons);

        if (unit instanceof Magicc magic) {
            magic.jingJie(jingJie);
        }

        if (shenTongId >= 0 && unit instanceof Magicc magic) {
            ShenTong base = ShenTong.shengTongMap.get(shenTongId);
            if (base != null) {
                ShenTong instance = get(base, magic);
                if (instance != null) {
                    magic.shenTongs().add(instance);
                }
            }
        }

        return unit;
    }

    @Override
    public void write(Json json) {
        super.write(json);
        json.writeValue("jingJie", jingJie == null ? JingJie.fan.name() : jingJie.name());
        if (shenTongId >= 0) {
            json.writeValue("shenTongId", shenTongId);
        }
        if (shenTongValues != null) {
            json.writeValue("shenTongValues", shenTongValues);
        }
    }

    @Override
    public void read(Json json, JsonValue data) {
        super.read(json, data);
        shenTongId = data.getInt("shenTongId", -1);
        shenTongValues = null;
        JsonValue values = data.get("shenTongValues");
        if (values != null && values.isArray()) {
            shenTongValues = new float[values.size];
            for (int i = 0; i < values.size; i++) {
                shenTongValues[i] = values.get(i).asFloat();
            }
        }
        jingJie = data.has("jingJie")
                ? JingJie.getByName(data.getString("jingJie"))
                : type instanceof MagicUnitType magic ? magic.jingJie : JingJie.fan;
    }

    @Override
    public MagicSpawnGroup copy() {
        MagicSpawnGroup copy = (MagicSpawnGroup) super.copy();
        copy.shenTongId = shenTongId;
        copy.shenTongValues = shenTongValues == null ? null : shenTongValues.clone();
        copy.jingJie = jingJie;
        return copy;
    }

    public ShenTong get(ShenTong s, Magicc m) {
        if (shenTongValues != null && shenTongValues.length > 0) {
            ShenTong configured = s.createConfigured(shenTongValues);
            if (configured != null) {
                return configured;
            }
        }
        if (s instanceof FaTianXiangDi) {
            float multiplier = JingJie.jingJieMultiplier(m.jingJie());
            return new FaTianXiangDi(multiplier,
                    Math.max(1.5f, multiplier), m.health() / 15, 600, m.maxMagicPower() / 3);
        }
        return s.create();
    }

    public float[] defaultShenTongValues(ShenTong shenTong) {
        float[] values = shenTong.defaultEditableValues();
        if (!(shenTong instanceof FaTianXiangDi) || !(type instanceof MagicUnitType magicType)) {
            return values;
        }

        float multiplier = JingJie.jingJieMultiplier(jingJie);
        Parameter[] parameters = shenTong.editableParameters();
        for (int i = 0; i < parameters.length; i++) {
            switch (parameters[i].getName()) {
                case "baseMultiplier" -> values[i] = multiplier;
                case "baseSizeMultiplier" -> values[i] = Math.max(1.5f, multiplier);
                case "regenAmount" -> values[i] = magicType.health / 15f;
                case "restoreTime" -> values[i] = 600f;
                case "createMagicCost" -> values[i] = magicType.magicPower / 3f;
                default -> {
                }
            }
        }
        return values;
    }
}
