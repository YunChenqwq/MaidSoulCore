package com.maidsoul.brain.tool;

import java.util.Map;

/**
 * 对 Planner 可见的工具声明。
 *
 * <p>这里保存的是模型请求层需要的函数名、描述和 JSON Schema。执行逻辑由 ToolRegistry 负责。</p>
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, Object> parametersSchema,
        Map<String, Object> metadata
) {
    public ToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        description = description == null ? "" : description;
        parametersSchema = parametersSchema == null ? Map.of("type", "object", "properties", Map.of()) : Map.copyOf(parametersSchema);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean enabled() {
        Object value = metadata.get("enabled");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return !"false".equalsIgnoreCase(text.trim());
        }
        return true;
    }
}
