package com.maidsoul.brain.memory;

import java.util.List;

/**
 * 记忆候选闸门。
 *
 * <p>这里不再用中文词表判断“偏好、道歉、纠错、关系”等语义类别。
 * 它只判断文本是否值得进入记忆流水线；具体语义必须来自上游结构化事件、
 * 工具调用或显式 tag。这样底层记忆系统不会因为一句“不是讨厌”就误打
 * error_mark。</p>
 */
public final class MemoryCandidateExtractor {
    public Candidate extractUserMessage(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || isTooSmall(value)) {
            return Candidate.skip();
        }
        return new Candidate(true, MemoryType.DIALOGUE, 2, List.of("raw_dialogue"));
    }

    public Candidate extractAssistantMessage(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || value.length() < 8) {
            return Candidate.skip();
        }
        return new Candidate(true, MemoryType.DIALOGUE, 1, List.of("assistant_reply"));
    }

    private static boolean isTooSmall(String text) {
        return text.trim().length() <= 1;
    }

    public record Candidate(boolean shouldRemember, MemoryType type, int importance, List<String> tags) {
        static Candidate skip() {
            return new Candidate(false, MemoryType.DIALOGUE, 0, List.of());
        }
    }
}
