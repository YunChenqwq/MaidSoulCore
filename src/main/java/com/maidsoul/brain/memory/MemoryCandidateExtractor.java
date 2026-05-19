package com.maidsoul.brain.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从聊天文本中提取值得长期保存的候选信息。
 *
 * <p>这里先用规则保证可控：寒暄不写，偏好、承诺、关系、冒犯和强情绪才写。
 * 后续可以加 LLM 打分，但规则层仍然保留作防抖。</p>
 */
public final class MemoryCandidateExtractor {
    public Candidate extractUserMessage(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || isSmallTalk(value)) {
            return Candidate.skip();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        MemoryType type = MemoryType.DIALOGUE;
        int importance = 2;

        if (containsAny(normalized, "喜欢", "讨厌", "不喜欢", "希望", "想要", "我是", "我叫")) {
            type = MemoryType.PREFERENCE;
            importance = 4;
            tags.add("preference");
        }
        if (containsAny(normalized, "约定", "答应", "记住", "别忘", "结婚", "以后")) {
            type = MemoryType.PROMISE;
            importance = 5;
            tags.add("promise");
        }
        if (containsAny(normalized, "对不起", "抱歉", "笨蛋", "傻", "打你", "打了你", "生气")) {
            type = MemoryType.EMOTION;
            importance = Math.max(importance, 4);
            tags.add("emotion");
        }
        if (containsAny(normalized, "聊天", "主动", "复读", "刷屏", "口癖", "自然")) {
            tags.add("conversation_style");
            importance = Math.max(importance, 3);
        }
        return new Candidate(true, type, importance, tags);
    }

    public Candidate extractAssistantMessage(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || value.length() < 8) {
            return Candidate.skip();
        }
        return new Candidate(true, MemoryType.DIALOGUE, 1, List.of("assistant_reply"));
    }

    private static boolean isSmallTalk(String text) {
        String normalized = text.trim();
        return normalized.length() <= 4
                && ("你好".equals(normalized)
                || "嗯".equals(normalized)
                || "哦".equals(normalized)
                || "哈哈".equals(normalized)
                || "喵".equals(normalized));
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public record Candidate(boolean shouldRemember, MemoryType type, int importance, List<String> tags) {
        static Candidate skip() {
            return new Candidate(false, MemoryType.DIALOGUE, 0, List.of());
        }
    }
}
