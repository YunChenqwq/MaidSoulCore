package com.yunchen.maidsoulcore.core.util;

public final class JsonExtractor {
    private JsonExtractor() {
    }

    public static String object(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
