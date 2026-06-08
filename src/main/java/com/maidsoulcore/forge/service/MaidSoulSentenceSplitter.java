package com.maidsoulcore.forge.service;

import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可见回复分段器。
 *
 * <p>这里复刻的是“先拆碎，再按概率合并”的体感：
 * 不是机械地按句号输出完整句子，而是把逗号、句号、分号、空格和换行都视为
 * 可能的停顿点，再根据回复长度决定是否把相邻片段重新合并。这样短回复不碎，
 * 长回复又不会一整段砸出来。</p>
 */
public final class MaidSoulSentenceSplitter {
    private MaidSoulSentenceSplitter() {
    }

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        if (!boolConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_ENABLED, true)) {
            return List.of(cleanSegment(normalized));
        }

        List<Segment> segments = splitToSegments(normalized);
        if (segments.isEmpty()) {
            return List.of(cleanSegment(normalized));
        }

        List<Segment> merged = probabilisticMerge(segments, normalized.length());
        ArrayList<String> result = new ArrayList<>();
        for (Segment segment : merged) {
            String content = cleanSegment(segment.content());
            if (!content.isBlank()) {
                result.add(content);
            }
        }
        if (result.isEmpty()) {
            result.add(cleanSegment(normalized));
        }

        int maxSegments = Math.max(1, intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_MAX_SEGMENTS, 4));
        if (result.size() <= maxSegments) {
            return List.copyOf(result);
        }
        if (boolConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL, true)) {
            return List.of(cleanSegment(normalized));
        }
        return List.copyOf(result.subList(0, maxSegments));
    }

    /**
     * 把文本切成“内容 + 后置分隔符”。
     *
     * <p>分隔符本身不直接作为最终句尾输出；只有在概率合并时，才会被夹在两个
     * 片段中间。这正是 maibotdev 那种“不总是带标点，但节奏还在”的感觉。</p>
     */
    private static List<Segment> splitToSegments(String text) {
        ArrayList<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteOpen = 0;

        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (isQuote(ch)) {
                if (!inQuote) {
                    inQuote = true;
                    quoteOpen = ch;
                } else if (quoteMatches(quoteOpen, ch)) {
                    inQuote = false;
                    quoteOpen = 0;
                }
                current.append(ch);
                continue;
            }

            if (!inQuote && isSeparator(ch) && canSplitAt(text, index, ch)) {
                if (!current.isEmpty()) {
                    segments.add(new Segment(cleanSegment(current.toString()), String.valueOf(ch)));
                } else if (ch == ' ' || ch == '\n') {
                    segments.add(new Segment("", String.valueOf(ch)));
                }
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }

        if (!current.isEmpty()) {
            segments.add(new Segment(cleanSegment(current.toString()), ""));
        }
        return segments.stream()
                .filter(segment -> !segment.content().isBlank() || !segment.separator().isBlank())
                .toList();
    }

    private static List<Segment> probabilisticMerge(List<Segment> segments, int totalLength) {
        if (segments.size() <= 1) {
            return segments;
        }

        double splitStrength;
        if (totalLength < intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_TEXT_CHARS, 12)) {
            splitStrength = intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT, 20) / 100.0d;
        } else if (totalLength < intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS, 32)) {
            splitStrength = intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT, 60) / 100.0d;
        } else {
            splitStrength = intConfig(MaidSoulCommonConfig.CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT, 70) / 100.0d;
        }
        double mergeProbability = 1.0d - Math.max(0.0d, Math.min(1.0d, splitStrength));

        ArrayList<Segment> merged = new ArrayList<>();
        int index = 0;
        while (index < segments.size()) {
            Segment current = segments.get(index);
            if (index + 1 < segments.size()
                    && !current.content().isBlank()
                    && !"\n".equals(current.separator())
                    && ThreadLocalRandom.current().nextDouble() < mergeProbability) {
                Segment next = segments.get(index + 1);
                if (!next.content().isBlank()) {
                    merged.add(new Segment(cleanSegment(current.content() + current.separator() + next.content()), next.separator()));
                } else {
                    merged.add(new Segment(current.content(), next.separator()));
                }
                index += 2;
            } else {
                merged.add(current);
                index++;
            }
        }
        return merged;
    }

    private static boolean canSplitAt(String text, int index, char separator) {
        if (separator == '\n') {
            return true;
        }
        char previous = index > 0 ? text.charAt(index - 1) : 0;
        char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
        if (previous == ':' || previous == '：' || next == ':' || next == '：') {
            return false;
        }
        if (separator == ' ') {
            return !(isAsciiLetterOrDigit(previous) && isAsciiLetterOrDigit(next));
        }
        if (separator == ',' && isAsciiLetterOrDigit(previous) && isAsciiLetterOrDigit(next)) {
            return false;
        }
        return true;
    }

    private static boolean isSeparator(char ch) {
        return ch == '，'
                || ch == ','
                || ch == ' '
                || ch == '。'
                || ch == ';'
                || ch == '；'
                || ch == '\n'
                || ch == '！'
                || ch == '？'
                || ch == '!'
                || ch == '?';
    }

    private static boolean isQuote(char ch) {
        return ch == '"' || ch == '\'' || ch == '“' || ch == '”' || ch == '‘' || ch == '’'
                || ch == '「' || ch == '」' || ch == '『' || ch == '』';
    }

    private static boolean quoteMatches(char open, char close) {
        return open == close
                || open == '“' && close == '”'
                || open == '‘' && close == '’'
                || open == '「' && close == '」'
                || open == '『' && close == '』';
    }

    private static boolean isAsciiLetterOrDigit(char ch) {
        return ch < 128 && Character.isLetterOrDigit(ch);
    }

    private static String normalize(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\n\\s*\\n+", "\n")
                .replaceAll("[\\t ]+", " ")
                .trim();
    }

    private static String cleanSegment(String text) {
        return text == null ? "" : text
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\s*\\n\\s*", " ")
                .trim();
    }

    private static boolean boolConfig(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private static int intConfig(net.minecraftforge.common.ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private record Segment(String content, String separator) {
    }
}
