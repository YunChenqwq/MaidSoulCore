package com.maidsoul.brain.character;

import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * 角色包里的长期参考记忆。
 *
 * <p>这里暂时使用 JSONL，一行一条。它和现有 LifeMemory 不冲突：LifeMemory 是聊天中
 * 自动沉淀的人生记忆；这里是更贴近角色包的“关系事实、边界、重要节点”。</p>
 */
record CharacterMemoryEntry(
        String type,
        String text,
        int salience,
        String tags
) {
    static CharacterMemoryEntry fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        return new CharacterMemoryEntry(
                value(data.get("type"), "note"),
                value(data.get("text"), ""),
                SimpleJson.integer(data.get("salience"), 5),
                value(data.get("tags"), "")
        );
    }

    boolean matches(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isBlank()) {
            return true;
        }
        String haystack = (type + " " + text + " " + tags).toLowerCase(java.util.Locale.ROOT);
        if (haystack.contains(q)) {
            return true;
        }
        for (int i = 0; i + 1 < q.length(); i++) {
            if (haystack.contains(q.substring(i, i + 2))) {
                return true;
            }
        }
        return false;
    }

    String render() {
        return "- [" + type + "/salience=" + salience + "] " + text;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
