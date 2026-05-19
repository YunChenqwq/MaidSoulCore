package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.time.Instant;
import java.util.Map;

/**
 * paragraph：长期记忆的最小文本单元。
 *
 * <p>对应 A_Memorix 的 paragraphs 表。它保存一段可检索文本，以及来源、时间、
 * 参与者、标签和软删除/保护状态。第一版没有向量字段，但保留 hash/source/externalId
 * 这些后续升级需要的主键。</p>
 */
public final class MemoryParagraph {
    public String hash = "";
    public String externalId = "";
    public String sourceType = "chat";
    public String chatId = "";
    public String personIds = "";
    public String participants = "";
    public String role = "";
    public String content = "";
    public String tags = "";
    public String metadata = "";
    public double createdAt = now();
    public double updatedAt = createdAt;
    public double eventTimeStart = createdAt;
    public double eventTimeEnd = createdAt;
    public int salience = 3;
    public int accessCount = 0;
    public double lastAccessed = 0;
    public boolean permanent = false;
    public boolean deleted = false;
    public double protectedUntil = 0;

    public static MemoryParagraph create(
            String externalId,
            String sourceType,
            String chatId,
            String role,
            String content,
            String participants,
            String tags,
            String metadata,
            int salience
    ) {
        MemoryParagraph paragraph = new MemoryParagraph();
        paragraph.externalId = value(externalId);
        paragraph.sourceType = value(sourceType, "chat");
        paragraph.chatId = value(chatId);
        paragraph.role = value(role);
        paragraph.content = clip(MemoryHash.normalize(content), 1200);
        paragraph.participants = value(participants);
        paragraph.tags = value(tags);
        paragraph.metadata = value(metadata);
        paragraph.salience = Math.max(1, Math.min(10, salience));
        paragraph.hash = MemoryHash.of(paragraph.externalId + "\n" + paragraph.sourceType + "\n" + paragraph.content);
        return paragraph;
    }

    public String toJsonLine() {
        return "{"
                + "\"hash\":\"" + JsonText.escape(hash) + "\","
                + "\"externalId\":\"" + JsonText.escape(externalId) + "\","
                + "\"sourceType\":\"" + JsonText.escape(sourceType) + "\","
                + "\"chatId\":\"" + JsonText.escape(chatId) + "\","
                + "\"personIds\":\"" + JsonText.escape(personIds) + "\","
                + "\"participants\":\"" + JsonText.escape(participants) + "\","
                + "\"role\":\"" + JsonText.escape(role) + "\","
                + "\"content\":\"" + JsonText.escape(content) + "\","
                + "\"tags\":\"" + JsonText.escape(tags) + "\","
                + "\"metadata\":\"" + JsonText.escape(metadata) + "\","
                + "\"createdAt\":" + createdAt + ","
                + "\"updatedAt\":" + updatedAt + ","
                + "\"eventTimeStart\":" + eventTimeStart + ","
                + "\"eventTimeEnd\":" + eventTimeEnd + ","
                + "\"salience\":" + salience + ","
                + "\"accessCount\":" + accessCount + ","
                + "\"lastAccessed\":" + lastAccessed + ","
                + "\"permanent\":" + permanent + ","
                + "\"deleted\":" + deleted + ","
                + "\"protectedUntil\":" + protectedUntil
                + "}";
    }

    public static MemoryParagraph fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        MemoryParagraph paragraph = new MemoryParagraph();
        paragraph.hash = text(data, "hash", "");
        paragraph.externalId = text(data, "externalId", "");
        paragraph.sourceType = text(data, "sourceType", "chat");
        paragraph.chatId = text(data, "chatId", "");
        paragraph.personIds = text(data, "personIds", "");
        paragraph.participants = text(data, "participants", "");
        paragraph.role = text(data, "role", "");
        paragraph.content = text(data, "content", "");
        paragraph.tags = text(data, "tags", "");
        paragraph.metadata = text(data, "metadata", "");
        paragraph.createdAt = decimal(data.get("createdAt"), now());
        paragraph.updatedAt = decimal(data.get("updatedAt"), paragraph.createdAt);
        paragraph.eventTimeStart = decimal(data.get("eventTimeStart"), paragraph.createdAt);
        paragraph.eventTimeEnd = decimal(data.get("eventTimeEnd"), paragraph.eventTimeStart);
        paragraph.salience = SimpleJson.integer(data.get("salience"), 3);
        paragraph.accessCount = SimpleJson.integer(data.get("accessCount"), 0);
        paragraph.lastAccessed = decimal(data.get("lastAccessed"), 0);
        paragraph.permanent = Boolean.parseBoolean(data.getOrDefault("permanent", "false"));
        paragraph.deleted = Boolean.parseBoolean(data.getOrDefault("deleted", "false"));
        paragraph.protectedUntil = decimal(data.get("protectedUntil"), 0);
        return paragraph;
    }

    static double now() {
        return Instant.now().toEpochMilli() / 1000.0;
    }

    private static String text(Map<String, String> data, String key, String fallback) {
        return value(data.get(key), fallback);
    }

    private static String value(String value) {
        return value(value, "");
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
