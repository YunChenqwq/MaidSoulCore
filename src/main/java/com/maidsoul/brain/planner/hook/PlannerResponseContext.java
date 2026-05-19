package com.maidsoul.brain.planner.hook;

import com.maidsoul.brain.reasoning.PlanDecision;

/**
 * planner 响应后上下文。
 */
public record PlannerResponseContext(
        String requestKind,
        String sessionId,
        String rawResponse,
        PlanDecision decision,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
