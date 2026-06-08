package com.yunchen.maidsoulcore.core.reasoning;

import com.yunchen.maidsoulcore.core.context.ContextPack;

public final class PlanDecisionValidator {
    public ValidationResult validate(PlanDecision decision, ContextPack context) {
        if (decision == null) {
            decision = new PlanDecision();
        }
        String originalAction = normalizeAction(decision.action);
        decision.action = originalAction;
        if ("reply".equals(originalAction) && isBlank(decision.target_message_id)) {
            String latest = context == null ? "" : context.latestMessageId();
            if (!isBlank(latest)) {
                decision.target_message_id = latest;
                return new ValidationResult(decision, true, "reply_missing_target_fixed_to_latest");
            }
            decision.action = "no_action";
            return new ValidationResult(decision, true, "reply_missing_target_downgraded");
        }
        if ("wait".equals(originalAction) && decision.wait_seconds <= 0) {
            decision.wait_seconds = 30;
            return new ValidationResult(decision, true, "wait_seconds_defaulted");
        }
        return new ValidationResult(decision, false, "ok");
    }

    private static String normalizeAction(String action) {
        String normalized = action == null || action.isBlank() ? "reply" : action.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "reply", "query_memory", "wait", "no_action", "finish" -> normalized;
            default -> "reply";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ValidationResult(PlanDecision decision, boolean changed, String reason) {
    }
}
