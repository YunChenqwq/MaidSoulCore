package com.yunchen.maidsoulcore.core.reasoning;

@FunctionalInterface
public interface PlannerToolExecutor {
    ToolResult execute(PlanDecision call);

    static PlannerToolExecutor noop() {
        return call -> new ToolResult(false, "tool_unavailable", "当前运行环境没有提供这个工具。");
    }
}
