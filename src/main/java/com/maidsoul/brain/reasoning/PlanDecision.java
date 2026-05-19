package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.affect.AffectEvent;

public record PlanDecision(
        String action,
        String targetMessageId,
        int waitSeconds,
        String reason,
        String referenceInfo,
        AffectEvent affectEvent
) {
    public PlanDecision(String action, String targetMessageId, int waitSeconds, String reason, String referenceInfo) {
        this(action, targetMessageId, waitSeconds, reason, referenceInfo, null);
    }

    public static PlanDecision replyLatest(String reason) {
        return new PlanDecision("reply", "", 0, reason, "");
    }

    public static PlanDecision replyLatest(String reason, String referenceInfo) {
        return new PlanDecision("reply", "", 0, reason, referenceInfo == null ? "" : referenceInfo);
    }
}
