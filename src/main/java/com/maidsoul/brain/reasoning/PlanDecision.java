package com.maidsoul.brain.reasoning;

record PlanDecision(String action, String targetMessageId, int waitSeconds, String reason, String referenceInfo) {
    static PlanDecision replyLatest(String reason) {
        return new PlanDecision("reply", "", 0, reason, "");
    }

    static PlanDecision replyLatest(String reason, String referenceInfo) {
        return new PlanDecision("reply", "", 0, reason, referenceInfo == null ? "" : referenceInfo);
    }
}
