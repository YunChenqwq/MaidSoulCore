package com.maidsoul.brain.planner.hook;

import com.maidsoul.brain.reasoning.PlanDecision;

/**
 * planner.after_response 返回补丁。
 */
public record PlannerAfterResponseResult(
        PlanDecision decision
) {
    public static PlannerAfterResponseResult keep() {
        return new PlannerAfterResponseResult(null);
    }
}
