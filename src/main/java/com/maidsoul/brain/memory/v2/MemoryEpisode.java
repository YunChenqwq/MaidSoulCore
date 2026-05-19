package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * episode：一段情景记忆。
 *
 * <p>A_Memorix 会后台聚合段落并由 LLM 切分。Java 原型第一版先按每次 ingest 创建
 * 轻量 episode，保留 title/summary/participants/keywords/evidenceIds/time 字段，
 * 后续可以把切分器换成真正的窗口聚合或 LLM。</p>
 */
public final class MemoryEpisode {
    public String episodeId = "";
    public String source = "";
    public String title = "";
    public String summary = "";
    public String participants = "";
    public String keywords = "";
    public String evidenceIds = "";
    public double eventTimeStart = MemoryParagraph.now();
    public double eventTimeEnd = eventTimeStart;
    public double confidence = 0.6;
    public int paragraphCount = 1;
    public double createdAt = eventTimeStart;
    public double updatedAt = eventTimeStart;

    static MemoryEpisode fromParagraph(MemoryParagraph paragraph) {
        MemoryEpisode episode = new MemoryEpisode();
        episode.source = paragraph.sourceType + ":" + paragraph.chatId;
        episode.title = buildTitle(paragraph);
        episode.summary = paragraph.content;
        episode.participants = paragraph.participants;
        episode.keywords = paragraph.tags;
        episode.evidenceIds = paragraph.hash;
        episode.eventTimeStart = paragraph.eventTimeStart;
        episode.eventTimeEnd = paragraph.eventTimeEnd;
        episode.createdAt = paragraph.createdAt;
        episode.updatedAt = paragraph.updatedAt;
        episode.episodeId = MemoryHash.of("episode\n" + episode.source + "\n" + episode.evidenceIds);
        return episode;
    }

    String toJsonLine() {
        return "{"
                + "\"episodeId\":\"" + JsonText.escape(episodeId) + "\","
                + "\"source\":\"" + JsonText.escape(source) + "\","
                + "\"title\":\"" + JsonText.escape(title) + "\","
                + "\"summary\":\"" + JsonText.escape(summary) + "\","
                + "\"participants\":\"" + JsonText.escape(participants) + "\","
                + "\"keywords\":\"" + JsonText.escape(keywords) + "\","
                + "\"evidenceIds\":\"" + JsonText.escape(evidenceIds) + "\","
                + "\"eventTimeStart\":" + eventTimeStart + ","
                + "\"eventTimeEnd\":" + eventTimeEnd + ","
                + "\"confidence\":" + confidence + ","
                + "\"paragraphCount\":" + paragraphCount + ","
                + "\"createdAt\":" + createdAt + ","
                + "\"updatedAt\":" + updatedAt
                + "}";
    }

    static MemoryEpisode fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        MemoryEpisode episode = new MemoryEpisode();
        episode.episodeId = data.getOrDefault("episodeId", "");
        episode.source = data.getOrDefault("source", "");
        episode.title = data.getOrDefault("title", "");
        episode.summary = data.getOrDefault("summary", "");
        episode.participants = data.getOrDefault("participants", "");
        episode.keywords = data.getOrDefault("keywords", "");
        episode.evidenceIds = data.getOrDefault("evidenceIds", "");
        episode.eventTimeStart = decimal(data.get("eventTimeStart"), MemoryParagraph.now());
        episode.eventTimeEnd = decimal(data.get("eventTimeEnd"), episode.eventTimeStart);
        episode.confidence = decimal(data.get("confidence"), 0.6);
        episode.paragraphCount = SimpleJson.integer(data.get("paragraphCount"), 1);
        episode.createdAt = decimal(data.get("createdAt"), episode.eventTimeStart);
        episode.updatedAt = decimal(data.get("updatedAt"), episode.createdAt);
        return episode;
    }

    private static String buildTitle(MemoryParagraph paragraph) {
        String text = paragraph.content == null ? "" : paragraph.content.trim();
        if (text.length() > 32) {
            text = text.substring(0, 32) + "...";
        }
        return (paragraph.role == null || paragraph.role.isBlank() ? "记忆片段" : paragraph.role + " 的记忆") + "：" + text;
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
