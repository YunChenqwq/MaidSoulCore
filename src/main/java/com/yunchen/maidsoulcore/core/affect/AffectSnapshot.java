package com.yunchen.maidsoulcore.core.affect;

public record AffectSnapshot(
        RelationshipStage relationshipStage,
        EmotionLabel emotion,
        double trust,
        double intimacy,
        double conflict,
        double attachment,
        double valence,
        double arousal,
        double dominance,
        double hurtDebt,
        double repairDebt,
        double memoryTriggerScore,
        double longing,
        double proactiveBias,
        String lastEvent
) {
}
