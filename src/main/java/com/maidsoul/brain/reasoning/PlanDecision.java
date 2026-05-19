package com.maidsoul.brain.reasoning;

public record PlanDecision(String action, String targetMessageId, int waitSeconds, String reason, String referenceInfo) {
    public static PlanDecision replyLatest(String reason) {
        return new PlanDecision("reply", "", 0, reason, "");
    }

    public static PlanDecision replyLatest(String reason, String referenceInfo) {
        return new PlanDecision("reply", "", 0, reason, referenceInfo == null ? "" : referenceInfo);
    }
}
