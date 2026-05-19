package com.maidsoul.brain.tool;

import java.util.List;
import java.util.Map;

public record ToolParam(
        String name,
        ToolParamType type,
        String description,
        boolean required,
        List<String> enumValues,
        Map<String, Object> itemsSchema,
        Map<String, Object> properties
) {
    public ToolParam {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具参数名称不能为空");
        }
        type = type == null ? ToolParamType.STRING : type;
        description = description == null ? "" : description;
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        itemsSchema = itemsSchema == null ? Map.of() : Map.copyOf(itemsSchema);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        if (type == ToolParamType.ARRAY && itemsSchema.isEmpty()) {
            throw new IllegalArgumentException("数组参数必须提供 items schema");
        }
    }
}

