package crystal.editor;

import arc.Events;
import arc.util.Log;
import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.editor.MapEditorDialog;
import mindustry.editor.MapInfoDialog;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.SpawnGroup;

import java.lang.reflect.Field;

/**
 * 魔法波次系统入口：
 * 1. 给 SpawnGroup 注册自定义序列化器，使 shenTongId 能随地图保存/读取。
 * 2. 用反射把原版的波次编辑对话框替换成 MagicWaveInfoDialog。
 */
public class MagicWaves {

    public static void registerSerializer() {
        // 旧版/旧地图保存的是 crystal.editor.ShenTongSpawnGroup，映射到新类（兜底）
        mindustry.io.JsonIO.classTag("crystal.editor.ShenTongSpawnGroup", ShenTongSpawnGroup.class);
        mindustry.io.JsonIO.classTag("ShenTongSpawnGroup", ShenTongSpawnGroup.class);
        mindustry.io.JsonIO.classTag("crystal.editor.MagicSpawnGroup", MagicSpawnGroup.class);
        mindustry.io.JsonIO.classTag("MagicSpawnGroup", MagicSpawnGroup.class);

        mindustry.io.JsonIO.json.setSerializer(SpawnGroup.class, new Json.Serializer<SpawnGroup>() {
            @Override
            public void write(Json json, SpawnGroup group, Class knownType) {
                json.writeObjectStart(SpawnGroup.class, SpawnGroup.class);
                group.write(json);
                json.writeObjectEnd();
            }

            @Override
            public SpawnGroup read(Json json, JsonValue data, Class type) {
                SpawnGroup group = data.has("shenTongId") ? new MagicSpawnGroup() : new SpawnGroup();
                group.read(json, data);
                return group;
            }
        });
    }

    public static void replaceWaveInfo() {
        try {
            Field infoField = MapEditorDialog.class.getDeclaredField("infoDialog");
            infoField.setAccessible(true);
            MapInfoDialog info = (MapInfoDialog) infoField.get(Vars.ui.editor);

            Field waveField = MapInfoDialog.class.getDeclaredField("waveInfo");
            waveField.setAccessible(true);
            waveField.set(info, new MagicWaveInfoDialog());

            Log.info("[MagicWaves] wave info dialog replaced.");
        } catch (Exception e) {
            Log.err("[MagicWaves] replaceWaveInfo failed: " + e);
            e.printStackTrace();
        }
    }

    public static void init() {
        registerSerializer();
        Events.on(ClientLoadEvent.class, e -> replaceWaveInfo());
    }
}
