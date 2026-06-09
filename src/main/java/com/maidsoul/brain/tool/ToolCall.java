package com.maidsoul.brain.tool;

import java.util.Map;

public record ToolCall(
        String callId,
        String functionName,
        Map<String, Object> arguments,
        String extraContent
) {
    public ToolCall {
        callId = callId == null ? "" : callId;
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("工具调用名称不能为空");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        extraContent = extraContent == null ? "" : extraContent;
    }
}

