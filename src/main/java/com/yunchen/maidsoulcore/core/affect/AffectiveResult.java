package com.yunchen.maidsoulcore.core.affect;

import java.util.List;

public record AffectiveResult(
        boolean shouldSend,
        double baseProbability,
        double memoryTriggerScore,
        double longingBoost,
        double boostedProbability,
        AffectSnapshot snapshot,
        ReplyStyleGuide style,
        List<String> triggeredMemories,
        String reason
) {
    public String toPromptBlock() {
        return """
                affective_result:
                  should_send=%s
                  base_probability=%.2f
                  memory_trigger_score=%.2f
                  longing_boost=%.2f
                  boosted_probability=%.2f
                  reason=%s
                %s
                triggered_memories:
                %s
                """.formatted(
                shouldSend,
                baseProbability,
                memoryTriggerScore,
                longingBoost,
                boostedProbability,
                reason,
                style.toPromptBlock(),
                triggeredMemories.isEmpty() ? "- none" : "- " + String.join("\n- ", triggeredMemories)
        ).trim();
    }
}
