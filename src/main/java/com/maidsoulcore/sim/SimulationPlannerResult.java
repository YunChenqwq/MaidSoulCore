package com.maidsoulcore.sim;

import com.maidsoulcore.decision.DecisionRoute;

/**
 * planner 真实模型输出结果。
 */
public record SimulationPlannerResult(
        DecisionRoute route,
        boolean shouldReply,
        String emotion,
        String intent,
        String planSummary,
        String toolGoal,
        String replyFocus,
        String rawText
) {
}
