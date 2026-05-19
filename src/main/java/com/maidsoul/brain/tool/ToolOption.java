package com.maidsoul.brain.tool;

import java.util.List;
import java.util.Map;

/**
 * 交给模型层的工具定义。
 *
 * <p>当前先保存结构，后续 OpenAI/Gemini/插件模型客户端会把它转换成各自协议的 tool schema。</p>
 */
public record ToolOption(
        String name,
        String description,
        List<ToolParam> params,
        Map<String, Object> parametersSchemaOverride
) {
    public ToolOption {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        description = description == null || description.isBlank() ? "工具 " + name : description;
        params = params == null ? List.of() : List.copyOf(params);
        parametersSchemaOverride = parametersSchemaOverride == null ? Map.of() : Map.copyOf(parametersSchemaOverride);
    }
}

