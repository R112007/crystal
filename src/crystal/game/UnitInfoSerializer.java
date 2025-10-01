package crystal.game;

import arc.util.Log;
import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import crystal.game.UnitInfo.ExportStat;
import crystal.type.UnitStack;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.type.UnitType;
import arc.struct.ObjectMap;
import arc.struct.Seq;

public class UnitInfoSerializer implements Json.Serializer<UnitInfo> {
  @Override
  public void write(Json json, UnitInfo object, Class knownType) {
    json.writeObjectStart();
    json.writeField(object, "id", "id");
    json.writeField(object, "planetName", "planetName");
    json.writeField(object, "sectorId", "sectorId");
    json.writeValue("possessed", serializeUnitStack(object.possessed));
    json.writeValue("export", serializeExportMap(object.export));
    json.writeValue("imports", serializeExportMap(object.imports));
    json.writeObjectEnd();
  }

  @Override
  public UnitInfo read(Json json, JsonValue jsonData, Class type) {
    // 1. 读取JSON中的字段值
    UnitInfo unitInfo;
    String planetName;
    int sectorId;
    int id;
    try {
      planetName = jsonData.getString("planetName");
      sectorId = jsonData.getInt("sectorId");
      id = jsonData.getInt("id");
      unitInfo = new UnitInfo(planetName, sectorId, id);
      unitInfo.possessed = deserializeUnitStack(json, jsonData.get("possessed"));
      unitInfo.export = deserializeExportMap(json, jsonData.get("export"));
      unitInfo.imports = deserializeExportMap(json, jsonData.get("imports"));
    } catch (Exception e) {
      unitInfo = null;
      // Log.err("创建实例失败" + e + "分割线" + e.getMessage());
    }
    return unitInfo;
  }

  private Seq<StringIntStack> serializeUnitStack(Seq<UnitStack> stacks) {
    Seq<StringIntStack> serialized = new Seq<>();
    for (var stack : stacks) {
      serialized.add(new StringIntStack(stack.unit.name, stack.amount));
    }
    return serialized;
  }

  private Seq<UnitStack> deserializeUnitStack(Json json, JsonValue jsonValue) {
    Seq<UnitStack> deserialize = new Seq<>();
    if (jsonValue == null || !jsonValue.isObject())
      return deserialize;
    for (JsonValue child = jsonValue.child; child != null; child = child.next) {
      UnitType unitType = Vars.content.getByName(ContentType.unit, child.name);
      if (unitType == null) {
        Log.warn("跳过未知的UnitType: " + child.name);
        continue;
      }
      int amount = child.asInt();
      deserialize.add(new UnitStack(unitType, amount));
    }
    return deserialize;
  }

  private ObjectMap<String, Float> serializeExportMap(ObjectMap<UnitType, ExportStat> map) {
    ObjectMap<String, Float> serialized = new ObjectMap<>();
    for (ObjectMap.Entry<UnitType, ExportStat> entry : map) {
      serialized.put(entry.key.name, entry.value.mean);
    }
    return serialized;
  }

  private ObjectMap<UnitType, ExportStat> deserializeExportMap(Json json, JsonValue jsonValue) {
    ObjectMap<UnitType, ExportStat> map = new ObjectMap<>();
    if (jsonValue == null || !jsonValue.isObject())
      return map;

    for (JsonValue child = jsonValue.child; child != null; child = child.next) {
      UnitType unitType = Vars.content.getByName(ContentType.unit, child.name);
      Log.info("恢复单位" + unitType);
      if (unitType == null) {
        Log.warn("跳过未知的UnitType: " + child.name);
        continue;
      }
      ExportStat stat = new ExportStat();
      stat.mean = child.asFloat();
      map.put(unitType, stat);
    }
    return map;
  }

  public class StringIntStack {
    public String name;
    public int amount;

    public StringIntStack(String name, int amount) {
      this.name = name;
      this.amount = amount;
    }
  }
}
