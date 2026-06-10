package crystal.ui.gal;

import arc.util.serialization.Json;
import arc.util.serialization.Json.JsonSerializable;
import arc.util.serialization.JsonValue;

// 实现 JsonSerializable，进入官方白名单
public class DialogueHistory implements JsonSerializable {
    public String moduleId;
    public String nodeId;
    public String characterName;
    public String content;
    public long timestamp;

    public DialogueHistory() {
    }

    public DialogueHistory(String moduleId, String nodeId, String characterName, String content) {
        this.moduleId = moduleId;
        this.nodeId = nodeId;
        this.characterName = characterName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // 写入JSON
    @Override
    public void write(Json json) {
        json.writeValue("moduleId", moduleId);
        json.writeValue("nodeId", nodeId);
        json.writeValue("characterName", characterName);
        json.writeValue("content", content);
        json.writeValue("timestamp", timestamp);
    }

    // 读取JSON
    @Override
    public void read(Json json, JsonValue jsonData) {
        moduleId = jsonData.getString("moduleId");
        nodeId = jsonData.getString("nodeId");
        characterName = jsonData.getString("characterName");
        content = jsonData.getString("content");
        timestamp = jsonData.getLong("timestamp", 0);
    }
}
