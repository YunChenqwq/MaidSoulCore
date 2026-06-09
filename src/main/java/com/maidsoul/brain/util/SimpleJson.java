package com.maidsoul.brain.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SimpleJson {
    private SimpleJson() {
    }

    public static Map<String, String> object(String raw) {
        String json = extractObject(raw == null ? "" : raw);
        Map<String, String> map = new LinkedHashMap<>();
        int index = 0;
        while (index < json.length()) {
            int keyQuote = json.indexOf('"', index);
            if (keyQuote < 0) {
                break;
            }
            String key = JsonText.readJsonString(json, keyQuote);
            int keyEnd = findStringEnd(json, keyQuote);
            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            int valueStart = skipSpaces(json, colon + 1);
            String value;
            if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                value = JsonText.readJsonString(json, valueStart);
                index = findStringEnd(json, valueStart) + 1;
            } else {
                int comma = json.indexOf(',', valueStart);
                int end = comma < 0 ? json.length() : comma;
                value = json.substring(valueStart, end).replace("}", "").trim();
                index = end + 1;
            }
            map.put(key, value);
        }
        return map;
    }

    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String extractObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private static int skipSpaces(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findStringEnd(String json, int startQuote) {
        boolean escaping = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                return i;
            }
        }
        return json.length() - 1;
    }
}

