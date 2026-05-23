package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.memory.StructuredMemoryEvent;

public record PlanDecision(
        String action,
        String targetMessageId,
        int waitSeconds,
        String reason,
        String referenceInfo,
        AffectEvent affectEvent,
        StructuredMemoryEvent memoryEvent
) {
    public PlanDecision(String action, String targetMessageId, int waitSeconds, String reason, String referenceInfo) {
        this(action, targetMessageId, waitSeconds, reason, referenceInfo, null, null);
    }

    public PlanDecision(String action, String targetMessageId, int waitSeconds, String reason, String referenceInfo, AffectEvent affectEvent) {
        this(action, targetMessageId, waitSeconds, reason, referenceInfo, affectEvent, null);
    }

    public static PlanDecision replyLatest(String reason) {
        return new PlanDecision("reply", "", 0, reason, "");
    }

    public static PlanDecision replyLatest(String reason, String referenceInfo) {
        return new PlanDecision("reply", "", 0, reason, referenceInfo == null ? "" : referenceInfo);
    }
}
