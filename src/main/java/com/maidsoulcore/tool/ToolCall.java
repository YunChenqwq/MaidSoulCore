package com.maidsoulcore.tool;

import java.util.Map;

/**
 * 内部工具调用请求。
 */
public record ToolCall(
        String toolName,
        Map<String, Object> arguments
) {
}
