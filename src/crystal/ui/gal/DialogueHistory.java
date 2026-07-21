package crystal.ui.gal;

import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import arc.util.serialization.Json.JsonSerializable;

/**
 * 单条对话历史记录，用于剧情回溯与历史查看。
 */
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

    @Override
    public void write(Json json) {
        json.writeValue("moduleId", moduleId);
        json.writeValue("nodeId", nodeId);
        json.writeValue("characterName", characterName);
        json.writeValue("content", content);
        json.writeValue("timestamp", timestamp);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        moduleId = jsonData.getString("moduleId");
        nodeId = jsonData.getString("nodeId");
        characterName = jsonData.getString("characterName");
        content = jsonData.getString("content");
        timestamp = jsonData.getLong("timestamp", 0);
    }
}
