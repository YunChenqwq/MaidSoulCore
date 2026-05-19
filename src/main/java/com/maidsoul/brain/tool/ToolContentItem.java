package com.maidsoul.brain.tool;

import java.util.Map;

public record ToolContentItem(
        ToolContentType contentType,
        String text,
        String data,
        String mimeType,
        String uri,
        String name,
        String description,
        Map<String, Object> metadata
) {
    public ToolContentItem {
        contentType = contentType == null ? ToolContentType.UNKNOWN : contentType;
        text = text == null ? "" : text;
        data = data == null ? "" : data;
        mimeType = mimeType == null ? "" : mimeType;
        uri = uri == null ? "" : uri;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String historyText() {
        return switch (contentType) {
            case TEXT -> text.strip();
            case IMAGE -> "[图片内容 " + (mimeType.isBlank() ? "unknown" : mimeType) + "]";
            case AUDIO -> "[音频内容 " + (mimeType.isBlank() ? "unknown" : mimeType) + "]";
            case RESOURCE_LINK -> "[资源链接] " + firstNonBlank(name, uri, "资源链接");
            case RESOURCE -> text.isBlank() ? "[嵌入资源] " + firstNonBlank(name, uri, "嵌入资源") : text.strip();
            case BINARY -> "[二进制内容 " + (mimeType.isBlank() ? "unknown" : mimeType) + "]";
            case UNKNOWN -> "[unknown 内容]";
        };
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}

