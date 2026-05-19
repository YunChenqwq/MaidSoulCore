package com.maidsoul.brain.tool;

import java.util.List;
import java.util.Map;

public record ToolExecutionResult(
        String toolName,
        boolean success,
        String content,
        String errorMessage,
        Object structuredContent,
        List<ToolContentItem> contentItems,
        Map<String, Object> metadata
) {
    public ToolExecutionResult {
        toolName = toolName == null ? "" : toolName;
        content = content == null ? "" : content;
        errorMessage = errorMessage == null ? "" : errorMessage;
        contentItems = contentItems == null ? List.of() : List.copyOf(contentItems);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String historyContent() {
        if (!content.isBlank()) {
            return content.strip();
        }
        if (!contentItems.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (ToolContentItem item : contentItems) {
                String text = item.historyText();
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString();
            }
        }
        if (structuredContent != null) {
            return String.valueOf(structuredContent).strip();
        }
        return errorMessage.strip();
    }
}

