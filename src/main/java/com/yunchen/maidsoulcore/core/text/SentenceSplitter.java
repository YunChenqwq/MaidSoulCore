package com.yunchen.maidsoulcore.core.text;

import java.util.ArrayList;
import java.util.List;

public final class SentenceSplitter {
    public List<String> split(String text) {
        String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current.append(c);
            if (isBoundary(c) && current.length() >= 6) {
                add(segments, current);
            }
        }
        add(segments, current);
        if (segments.size() <= 3) {
            return segments;
        }
        return List.of(normalized);
    }

    private static boolean isBoundary(char c) {
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '~' || c == '…';
    }

    private static void add(List<String> segments, StringBuilder current) {
        String value = current.toString().trim();
        current.setLength(0);
        if (!value.isBlank()) {
            segments.add(value);
        }
    }
}
