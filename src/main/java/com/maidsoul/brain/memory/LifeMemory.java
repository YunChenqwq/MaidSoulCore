package com.maidsoul.brain.memory;

import com.maidsoul.brain.affect.AffectSnapshot;
import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 酒狐人生记忆中的一条事件。
 */
public final class LifeMemory {
    public String id = UUID.randomUUID().toString();
    public String maidId = "";
    public String ownerId = "";
    public String worldId = "";
    public String time = Instant.now().toString();
    public MemoryType type = MemoryType.DIALOGUE;
    public String source = "chat";
    public String role = "";
    public String content = "";
    public int importance = 1;
    public double emotionalWeight = 0;
    public int mood = 60;
    public int anger = 0;
    public int hurt = 0;
    public int tension = 10;
    public int trust = 50;
    public int affection = 50;
    public int curiosity = 45;
    public List<String> tags = new ArrayList<>();

    public String toJsonLine() {
        return "{"
                + "\"id\":\"" + JsonText.escape(id) + "\","
                + "\"maidId\":\"" + JsonText.escape(maidId) + "\","
                + "\"ownerId\":\"" + JsonText.escape(ownerId) + "\","
                + "\"worldId\":\"" + JsonText.escape(worldId) + "\","
                + "\"time\":\"" + JsonText.escape(time) + "\","
                + "\"type\":\"" + type.name() + "\","
                + "\"source\":\"" + JsonText.escape(source) + "\","
                + "\"role\":\"" + JsonText.escape(role) + "\","
                + "\"content\":\"" + JsonText.escape(content) + "\","
                + "\"importance\":" + importance + ","
                + "\"emotionalWeight\":" + emotionalWeight + ","
                + "\"mood\":" + mood + ","
                + "\"anger\":" + anger + ","
                + "\"hurt\":" + hurt + ","
                + "\"tension\":" + tension + ","
                + "\"trust\":" + trust + ","
                + "\"affection\":" + affection + ","
                + "\"curiosity\":" + curiosity + ","
                + "\"tags\":\"" + JsonText.escape(String.join(",", tags)) + "\""
                + "}";
    }

    public static LifeMemory fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        LifeMemory memory = new LifeMemory();
        memory.id = text(data, "id", memory.id);
        memory.maidId = text(data, "maidId", "");
        memory.ownerId = text(data, "ownerId", "");
        memory.worldId = text(data, "worldId", "");
        memory.time = text(data, "time", memory.time);
        memory.source = text(data, "source", "chat");
        memory.role = text(data, "role", "");
        memory.content = text(data, "content", "");
        memory.importance = SimpleJson.integer(data.get("importance"), 1);
        memory.emotionalWeight = decimal(data.get("emotionalWeight"), 0);
        memory.mood = SimpleJson.integer(data.get("mood"), 60);
        memory.anger = SimpleJson.integer(data.get("anger"), 0);
        memory.hurt = SimpleJson.integer(data.get("hurt"), 0);
        memory.tension = SimpleJson.integer(data.get("tension"), 10);
        memory.trust = SimpleJson.integer(data.get("trust"), 50);
        memory.affection = SimpleJson.integer(data.get("affection"), 50);
        memory.curiosity = SimpleJson.integer(data.get("curiosity"), 45);
        try {
            memory.type = MemoryType.valueOf(text(data, "type", "DIALOGUE"));
        } catch (IllegalArgumentException ignored) {
            memory.type = MemoryType.DIALOGUE;
        }
        String tags = text(data, "tags", "");
        if (!tags.isBlank()) {
            memory.tags = new ArrayList<>(Arrays.asList(tags.split(",")));
        }
        return memory;
    }

    public static LifeMemory of(
            String maidId,
            String ownerId,
            String worldId,
            MemoryType type,
            String source,
            String role,
            String content,
            int importance,
            List<String> tags,
            AffectSnapshot affect
    ) {
        LifeMemory memory = new LifeMemory();
        memory.maidId = maidId;
        memory.ownerId = ownerId;
        memory.worldId = worldId;
        memory.type = type;
        memory.source = source == null ? "" : source;
        memory.role = role == null ? "" : role;
        memory.content = clip(content, 500);
        memory.importance = Math.max(1, Math.min(5, importance));
        memory.tags = tags == null ? List.of() : new ArrayList<>(tags);
        if (affect != null) {
            memory.mood = affect.mood();
            memory.anger = affect.anger();
            memory.hurt = affect.hurt();
            memory.tension = affect.tension();
            memory.trust = affect.trust();
            memory.affection = affect.affection();
            memory.curiosity = affect.curiosity();
            memory.emotionalWeight = Math.max(affect.anger(), affect.hurt()) / 100.0;
        }
        return memory;
    }

    private static String text(Map<String, String> data, String key, String fallback) {
        String value = data.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
