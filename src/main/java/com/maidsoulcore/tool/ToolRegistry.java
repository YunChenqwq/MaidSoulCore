package com.maidsoulcore.tool;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内部工具注册表。
 * <p>
 * 当前主要用于保存工具元信息，后续也可以扩展到权限与路由表。
 */
public final class ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * 注册一个工具定义。
     */
    public void register(ToolDefinition definition) {
        definitions.put(definition.name(), definition);
    }

    /**
     * 按名称查找工具定义。
     */
    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    /**
     * 返回所有工具定义。
     */
    public Collection<ToolDefinition> all() {
        return definitions.values();
    }
}
