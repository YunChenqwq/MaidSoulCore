package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * entity：记忆图谱里的节点。
 *
 * <p>第一版只记录名称、类型和出现次数，后续可以加别名、向量、人物主档案映射。</p>
 */
public final class MemoryEntity {
    public String hash = "";
    public String name = "";
    public String kind = "concept";
    public int appearanceCount = 1;
    public double createdAt = MemoryParagraph.now();
    public String metadata = "";
    public boolean deleted = false;

    static MemoryEntity create(String name, String kind) {
        MemoryEntity entity = new MemoryEntity();
        entity.name = name == null ? "" : name.trim();
        entity.kind = kind == null || kind.isBlank() ? "concept" : kind.trim();
        entity.hash = MemoryHash.of("entity\n" + entity.name);
        return entity;
    }

    String toJsonLine() {
        return "{"
                + "\"hash\":\"" + JsonText.escape(hash) + "\","
                + "\"name\":\"" + JsonText.escape(name) + "\","
                + "\"kind\":\"" + JsonText.escape(kind) + "\","
                + "\"appearanceCount\":" + appearanceCount + ","
                + "\"createdAt\":" + createdAt + ","
                + "\"metadata\":\"" + JsonText.escape(metadata) + "\","
                + "\"deleted\":" + deleted
                + "}";
    }

    static MemoryEntity fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        MemoryEntity entity = new MemoryEntity();
        entity.hash = data.getOrDefault("hash", "");
        entity.name = data.getOrDefault("name", "");
        entity.kind = data.getOrDefault("kind", "concept");
        entity.appearanceCount = SimpleJson.integer(data.get("appearanceCount"), 1);
        entity.createdAt = decimal(data.get("createdAt"), MemoryParagraph.now());
        entity.metadata = data.getOrDefault("metadata", "");
        entity.deleted = Boolean.parseBoolean(data.getOrDefault("deleted", "false"));
        return entity;
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
