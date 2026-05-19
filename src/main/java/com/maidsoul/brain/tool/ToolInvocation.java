package com.maidsoul.brain.tool;

import java.util.Map;

public record ToolInvocation(
        String toolName,
        Map<String, Object> arguments,
        String callId,
        String reasoning
) {
    public ToolInvocation {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("工具调用名称不能为空");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        callId = callId == null ? "" : callId;
        reasoning = reasoning == null ? "" : reasoning;
    }
}

