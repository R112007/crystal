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

public class MagicWaves {

    public static void registerSerializer() {
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
                SpawnGroup group = data.has("shenTongId") || data.has("jingJie")
                        ? new MagicSpawnGroup()
                        : new SpawnGroup();
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
