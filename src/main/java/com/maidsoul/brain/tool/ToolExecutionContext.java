package com.maidsoul.brain.tool;

import java.util.Map;

/**
 * 工具执行上下文。
 *
 * <p>和可见性上下文分开，是为了让工具执行时可以拿到 planner 的理由、
 * 当前轮超时预算和平台附加信息，但工具声明阶段不需要知道这些细节。</p>
 */
public record ToolExecutionContext(
        String sessionId,
        String stage,
        String reasoning,
        long timeoutMillis,
        Map<String, Object> metadata
) {
    public ToolExecutionContext {
        sessionId = sessionId == null ? "" : sessionId;
        stage = stage == null ? "action" : stage;
        reasoning = reasoning == null ? "" : reasoning;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolExecutionContext action(String reasoning, long timeoutMillis) {
        return new ToolExecutionContext("prototype-session", "action", reasoning, timeoutMillis, Map.of());
    }
}
