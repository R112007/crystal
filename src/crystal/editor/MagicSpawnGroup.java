package crystal.editor;

import arc.func.Cons;
import arc.util.Log;
import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import crystal.entities.shentong.FaTianXiangDi;
import crystal.entities.shentong.ShenTong;
import crystal.entities.units.UnitEnum.XiuWei;
import crystal.gen.Magicc;
import mindustry.game.SpawnGroup;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * 扩展原版的 SpawnGroup，支持给魔法单位附加神通。
 * 当波次刷新、单位被创建时，会自动把选中的神通加到 Magicc 单位上。
 */
public class MagicSpawnGroup extends SpawnGroup {
    /** -1 表示没有神通，否则对应 ShenTong.shengTongMap 里的 key */
    public int shenTongId = -1;

    public MagicSpawnGroup() {
        // 序列化用
    }

    public MagicSpawnGroup(UnitType type) {
        super(type);
    }

    @Override
    public Unit createUnit(Team team, float x, float y, float rotation, int wave, Cons<Unit> cons) {
        Unit unit = super.createUnit(team, x, y, rotation, wave, cons);

        if (shenTongId >= 0 && unit instanceof Magicc magic) {
            ShenTong base = ShenTong.shengTongMap.get(shenTongId);
            if (base != null) {
                magic.shenTongs().add(get(base, magic));
            }
        }

        return unit;
    }

    @Override
    public void write(Json json) {
        super.write(json);
        if (shenTongId >= 0) {
            json.writeValue("shenTongId", shenTongId);
        }
    }

    @Override
    public void read(Json json, JsonValue data) {
        super.read(json, data);
        shenTongId = data.getInt("shenTongId", -1);
    }

    @Override
    public MagicSpawnGroup copy() {
        MagicSpawnGroup copy = (MagicSpawnGroup) super.copy();
        copy.shenTongId = shenTongId;
        return copy;
    }

    public ShenTong get(ShenTong s, Magicc m) {
        if (s instanceof FaTianXiangDi) {
            return new FaTianXiangDi(XiuWei.xiuWeiMultiplier(m.xiuWei()),
                    Math.max(1.5f, XiuWei.xiuWeiMultiplier(m.xiuWei())), m.health() / 15, 600, m.maxMagicPower() / 3);
        } else {
            Log.err("unknown shenTong" + s);
            return null;
        }
    }
}
