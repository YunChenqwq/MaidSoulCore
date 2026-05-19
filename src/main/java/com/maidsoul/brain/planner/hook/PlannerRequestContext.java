package com.maidsoul.brain.planner.hook;

import com.maidsoul.brain.tool.ToolSpec;

import java.util.List;

/**
 * planner 请求前上下文。
 */
public record PlannerRequestContext(
        String requestKind,
        String sessionId,
        String context,
        List<ToolSpec> tools
) {
    public PlannerRequestContext {
        requestKind = requestKind == null ? "planner" : requestKind;
        sessionId = sessionId == null ? "" : sessionId;
        context = context == null ? "" : context;
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
