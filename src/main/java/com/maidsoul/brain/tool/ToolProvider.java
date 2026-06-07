package com.maidsoul.brain.tool;

import java.util.List;

/**
 * 统一工具提供者。
 *
 * <p>每个能力域实现一个 provider：记忆、视觉、Forge 世界、调试、GUI 等。
 * ReasoningEngine 只面向 ToolRegistry，不再知道每个工具的具体实现。</p>
 */
public interface ToolProvider {
    String providerName();

    default String providerType() {
        return "builtin";
    }

    List<ToolSpec> listTools(ToolAvailabilityContext context);

    ToolExecutionResult invoke(ToolInvocation invocation, ToolExecutionContext context);
}
