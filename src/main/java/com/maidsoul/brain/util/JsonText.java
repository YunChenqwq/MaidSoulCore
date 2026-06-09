package com.maidsoul.brain.util;

import com.maidsoul.brain.tool.ToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonText {
    private JsonText() {
    }

    public static String escape(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    public static String extractFirstMessageContent(String json) {
        String key = "\"content\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        return readJsonString(json, quote);
    }

    /**
     * 从 OpenAI 兼容流式 chunk 中读取 delta.content。
     *
     * <p>SSE 每一帧通常都是一个很小的 JSON 对象，完整 JSON 解析器后续会替换这里；
     * 当前轻量解析只用于回复器流式文本，不参与工具调用。</p>
     */
    public static String extractDeltaContent(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        int deltaIndex = json.indexOf("\"delta\"");
        int searchStart = deltaIndex >= 0 ? deltaIndex : 0;
        int contentIndex = json.indexOf("\"content\"", searchStart);
        if (contentIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', contentIndex);
        if (colon < 0) {
            return "";
        }
        int valueStart = skipSpaces(json, colon + 1);
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return "";
        }
        return readJsonString(json, valueStart);
    }

    /**
     * 从 OpenAI 兼容响应中提取工具调用。
     *
     * <p>这里是一个轻量解析器，足够覆盖聊天核心测试；后续接入完整模型服务层时会替换成结构化 JSON 解析。</p>
     */
    public static List<ToolCall> extractToolCalls(String json) {
        if (json == null || !json.contains("\"tool_calls\"")) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        int index = 0;
        while (true) {
            int functionIndex = json.indexOf("\"function\"", index);
            if (functionIndex < 0) {
                break;
            }
            int nameIndex = json.indexOf("\"name\"", functionIndex);
            int argumentsIndex = json.indexOf("\"arguments\"", functionIndex);
            if (nameIndex < 0 || argumentsIndex < 0) {
                break;
            }
            String callId = extractStringFieldBefore(json, functionIndex, "\"id\"");
            String name = extractStringValueAfterKey(json, nameIndex);
            String argumentText = extractStringValueAfterKey(json, argumentsIndex);
            Map<String, Object> args = parseFlatObject(argumentText);
            if (!name.isBlank()) {
                calls.add(new ToolCall(callId, name, args, ""));
            }
            index = argumentsIndex + 1;
        }
        return calls;
    }

    /**
     * 从 OpenAI 兼容响应的 usage 块中读取 token 数。
     *
     * <p>这里仍保持轻量解析，避免为原型机引入完整 JSON 依赖。字段不存在时返回 0；
     * 流式响应如果没有开启 include_usage，通常也会返回 0。</p>
     */
    public static int extractUsageInt(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return 0;
        }
        int usageIndex = json.indexOf("\"usage\"");
        if (usageIndex < 0) {
            return 0;
        }
        int keyIndex = json.indexOf("\"" + fieldName + "\"", usageIndex);
        if (keyIndex < 0) {
            return 0;
        }
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return 0;
        }
        int start = skipSpaces(json, colon + 1);
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String readJsonString(String json, int startQuote) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                switch (ch) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> builder.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                break;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String extractStringValueAfterKey(String json, int keyIndex) {
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        return readJsonString(json, quote);
    }

    private static String extractStringFieldBefore(String json, int beforeIndex, String key) {
        int start = Math.max(0, beforeIndex - 500);
        int keyIndex = json.lastIndexOf(key, beforeIndex);
        if (keyIndex < start) {
            return "";
        }
        return extractStringValueAfterKey(json, keyIndex);
    }

    private static Map<String, Object> parseFlatObject(String rawJson) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (rawJson == null || rawJson.isBlank()) {
            return values;
        }
        String object = rawJson.trim();
        if (object.startsWith("{") && object.endsWith("}")) {
            object = object.substring(1, object.length() - 1);
        }
        int index = 0;
        while (index < object.length()) {
            int keyQuote = object.indexOf('"', index);
            if (keyQuote < 0) {
                break;
            }
            String key = readJsonString(object, keyQuote);
            int keyEnd = findStringEnd(object, keyQuote);
            int colon = object.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            int valueStart = skipSpaces(object, colon + 1);
            Object value;
            if (valueStart < object.length() && object.charAt(valueStart) == '"') {
                value = readJsonString(object, valueStart);
                index = findStringEnd(object, valueStart) + 1;
            } else {
                int comma = object.indexOf(',', valueStart);
                int end = comma < 0 ? object.length() : comma;
                value = coerceScalar(object.substring(valueStart, end).trim());
                index = end + 1;
            }
            values.put(key, value);
        }
        return values;
    }

    private static Object coerceScalar(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return "";
        }
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
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
