package com.maidsoul.brain.planner.hook;

import com.maidsoul.brain.tool.ToolSpec;

import java.util.List;

/**
 * planner.before_request 返回补丁。
 */
public record PlannerBeforeRequestResult(
        String additionalContext,
        List<ToolSpec> additionalTools
) {
    public PlannerBeforeRequestResult {
        additionalContext = additionalContext == null ? "" : additionalContext.trim();
        additionalTools = additionalTools == null ? List.of() : List.copyOf(additionalTools);
    }

    public static PlannerBeforeRequestResult empty() {
        return new PlannerBeforeRequestResult("", List.of());
    }
}
