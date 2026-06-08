package com.yunchen.maidsoulcore.core.affect;

import com.yunchen.maidsoulcore.core.memory.MemoryTriggerScorer;

import java.util.List;

public final class AffectiveLongingEngine {
    private final AffectEngine affectEngine;
    private final MemoryTriggerScorer memoryTriggerScorer;
    private final ReplyStyleResolver styleResolver;

    public AffectiveLongingEngine() {
        this(new AffectEngine(), new MemoryTriggerScorer(), new ReplyStyleResolver());
    }

    public AffectiveLongingEngine(
            AffectEngine affectEngine,
            MemoryTriggerScorer memoryTriggerScorer,
            ReplyStyleResolver styleResolver
    ) {
        this.affectEngine = affectEngine;
        this.memoryTriggerScorer = memoryTriggerScorer;
        this.styleResolver = styleResolver;
    }

    public AffectEngine affectEngine() {
        return affectEngine;
    }

    public AffectiveResult tick(AffectProfile profile, String context, Iterable<String> candidateMemories, double baseProbability) {
        MemoryTriggerScorer.TriggerScore trigger = memoryTriggerScorer.score(context, candidateMemories);
        affectEngine.applyMemoryTrigger(profile, trigger.score());
        profile.normalize();

        double safeBase = AffectProfile.clamp01(baseProbability);
        double longingBoost = profile.longing * 0.22D + trigger.score() * 0.35D;
        double boosted = AffectProfile.clamp01(safeBase + longingBoost + profile.proactiveBias * 0.18D);
        boolean shouldSend = boosted >= 0.62D;
        ReplyStyleGuide style = styleResolver.resolve(profile);
        String reason = reason(profile, trigger, boosted);

        return new AffectiveResult(
                shouldSend,
                safeBase,
                trigger.score(),
                longingBoost,
                boosted,
                profile.snapshot(),
                style,
                trigger.memories(),
                reason
        );
    }

    public AffectiveResult tick(AffectProfile profile, String context, List<String> candidateMemories) {
        return tick(profile, context, candidateMemories, 0.35D);
    }

    private static String reason(AffectProfile profile, MemoryTriggerScorer.TriggerScore trigger, double boosted) {
        if (trigger.score() > 0.28D) {
            return "memory_triggered_longing";
        }
        if (profile.repairDebt > 0.30D || profile.hurtDebt > 0.30D) {
            return "repair_debt_active";
        }
        if (AffectiveEvent.fromId(profile.lastEvent) == AffectiveEvent.DANGER) {
            return "safety_event";
        }
        if (boosted >= 0.75D) {
            return "high_longing_or_intimacy";
        }
        return "normal_affective_state";
    }
}
