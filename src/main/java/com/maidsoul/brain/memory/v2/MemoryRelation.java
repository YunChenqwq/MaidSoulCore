package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * relation：图谱三元组。
 *
 * <p>对应 A_Memorix 的 relations 表。这里保留 subject/predicate/object/confidence/sourceParagraph
 * 等关键字段，让“谁和谁有什么关系”能够从段落里独立出来。</p>
 */
public final class MemoryRelation {
    public String hash = "";
    public String subject = "";
    public String predicate = "";
    public String object = "";
    public double confidence = 1.0;
    public String sourceParagraph = "";
    public String metadata = "";
    public boolean permanent = false;
    public boolean inactive = false;
    public double protectedUntil = 0;
    public int accessCount = 0;
    public double createdAt = MemoryParagraph.now();

    static MemoryRelation create(String subject, String predicate, String object, String sourceParagraph, double confidence) {
        MemoryRelation relation = new MemoryRelation();
        relation.subject = clean(subject);
        relation.predicate = clean(predicate);
        relation.object = clean(object);
        relation.sourceParagraph = clean(sourceParagraph);
        relation.confidence = Math.max(0.0, Math.min(1.0, confidence));
        relation.hash = MemoryHash.of("relation\n" + relation.subject + "\n" + relation.predicate + "\n" + relation.object);
        return relation;
    }

    String toJsonLine() {
        return "{"
                + "\"hash\":\"" + JsonText.escape(hash) + "\","
                + "\"subject\":\"" + JsonText.escape(subject) + "\","
                + "\"predicate\":\"" + JsonText.escape(predicate) + "\","
                + "\"object\":\"" + JsonText.escape(object) + "\","
                + "\"confidence\":" + confidence + ","
                + "\"sourceParagraph\":\"" + JsonText.escape(sourceParagraph) + "\","
                + "\"metadata\":\"" + JsonText.escape(metadata) + "\","
                + "\"permanent\":" + permanent + ","
                + "\"inactive\":" + inactive + ","
                + "\"protectedUntil\":" + protectedUntil + ","
                + "\"accessCount\":" + accessCount + ","
                + "\"createdAt\":" + createdAt
                + "}";
    }

    static MemoryRelation fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        MemoryRelation relation = new MemoryRelation();
        relation.hash = data.getOrDefault("hash", "");
        relation.subject = data.getOrDefault("subject", "");
        relation.predicate = data.getOrDefault("predicate", "");
        relation.object = data.getOrDefault("object", "");
        relation.confidence = decimal(data.get("confidence"), 1.0);
        relation.sourceParagraph = data.getOrDefault("sourceParagraph", "");
        relation.metadata = data.getOrDefault("metadata", "");
        relation.permanent = Boolean.parseBoolean(data.getOrDefault("permanent", "false"));
        relation.inactive = Boolean.parseBoolean(data.getOrDefault("inactive", "false"));
        relation.protectedUntil = decimal(data.get("protectedUntil"), 0);
        relation.accessCount = SimpleJson.integer(data.get("accessCount"), 0);
        relation.createdAt = decimal(data.get("createdAt"), MemoryParagraph.now());
        return relation;
    }

    String readable() {
        return subject + " " + predicate + " " + object;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
