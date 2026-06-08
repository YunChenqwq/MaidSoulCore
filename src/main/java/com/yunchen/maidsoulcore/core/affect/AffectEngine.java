package com.yunchen.maidsoulcore.core.affect;

import com.yunchen.maidsoulcore.core.event.StructuredEvent;
import com.yunchen.maidsoulcore.core.event.StructuredEventScope;
import com.yunchen.maidsoulcore.core.event.StructuredEventType;

import java.util.Random;

/**
 * v3 情绪引擎：结构化事件 -> OU 慢变量 -> HMM 关系阶段 -> VAD 情绪。
 *
 * <p>这个类不解析自然语言，不包含“对不起/喜欢/骂人”之类关键词。
 * 所有语义都必须由 planner 或工具循环先变成 StructuredEvent。</p>
 */
public final class AffectEngine {
    private static final double HOUR_MILLIS = 3_600_000.0D;
    private final RelationshipDynamicsConfig config;
    private final RelationshipHmm hmm;
    private final Random random;

    public AffectEngine() {
        this(new RelationshipDynamicsConfig(), new Random(42L));
    }

    public AffectEngine(RelationshipDynamicsConfig config, Random random) {
        this.config = config;
        this.hmm = new RelationshipHmm(config);
        this.random = random;
    }

    public void syncFavorability(AffectProfile profile, int favorability) {
        if (profile == null) {
            return;
        }
        decayByElapsedTime(profile);
        double signal = AffectProfile.clamp01(favorability / 100.0D);
        profile.externalBondSignal = signal;
        profile.trust = approach(profile.trust, 0.48D + signal * 0.30D, 0.010D);
        profile.attachment = approach(profile.attachment, 0.56D + signal * 0.14D, 0.008D);
        finish(profile, StructuredEventType.NEUTRAL_WORLD, "");
    }

    public void observeOwnerMessage(AffectProfile profile, String text) {
        if (profile == null || text == null || text.isBlank()) {
            return;
        }
        int visibleLength = text.codePointCount(0, text.length());
        apply(profile, visibleLength >= 36 ? AffectiveEvent.LONG_MESSAGE : AffectiveEvent.OWNER_MESSAGE);
    }

    public void observeWorldEvent(AffectProfile profile, String eventType) {
        apply(profile, mapWorldEvent(eventType));
    }

    public void apply(AffectProfile profile, AffectiveEvent event) {
        apply(profile, toStructuredType(event), "");
    }

    public void apply(AffectProfile profile, StructuredEvent event) {
        if (profile == null || event == null) {
            return;
        }
        event.normalize();
        apply(profile, event.typeEnum(), event.evidence);
    }

    public void applyMemoryTrigger(AffectProfile profile, double triggerScore) {
        if (profile == null) {
            return;
        }
        String previousEvent = profile.lastEvent;
        String previousSemanticEvent = profile.lastSemanticEvent;
        String previousEvidence = profile.lastEventEvidence;
        long previousEventAt = profile.lastEventAtEpochMillis;
        decayByElapsedTime(profile);
        profile.memoryTriggerScore = AffectProfile.clamp01(triggerScore);
        finish(profile, StructuredEventType.NEUTRAL_WORLD, previousEvidence);
        profile.lastEvent = previousEvent;
        profile.lastSemanticEvent = previousSemanticEvent;
        profile.lastEventEvidence = previousEvidence;
        profile.lastEventAtEpochMillis = previousEventAt;
    }

    private void apply(AffectProfile profile, StructuredEventType type, String evidence) {
        if (profile == null) {
            return;
        }
        StructuredEventType safeType = type == null ? StructuredEventType.NEUTRAL_WORLD : type;
        decayByElapsedTime(profile);
        applyEventBump(profile, safeType);
        updateStage(profile, safeType);
        finish(profile, safeType, evidence);
    }

    private AffectiveEvent mapWorldEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return AffectiveEvent.NEUTRAL_WORLD;
        }
        return switch (eventType) {
            case "maid.attacked.by_owner" -> AffectiveEvent.OWNER_ATTACK;
            case "maid.attacked" -> AffectiveEvent.DANGER;
            case "maid.death" -> AffectiveEvent.MAID_DEATH;
            case "maid.ate" -> AffectiveEvent.CARE;
            case "maid.interact" -> AffectiveEvent.MAID_INTERACT;
            case "world.change", "world.moved", "maid.world_moved" -> AffectiveEvent.WORLD_CHANGE;
            default -> eventType.startsWith("risk.")
                    ? AffectiveEvent.DANGER
                    : AffectiveEvent.NEUTRAL_WORLD;
        };
    }

    private void applyEventBump(AffectProfile p, StructuredEventType type) {
        RelationshipDynamicsConfig.EventBump rb = config.relationshipBump(type);
        p.intimacy = bump01(p.intimacy, dampenBump(p.intimacy, rb.intimacy()));
        p.conflict = bump01(p.conflict, rb.conflict());

        RelationshipDynamicsConfig.VadBump vb = config.vadBump(type);
        p.valence = AffectProfile.clampSigned(p.valence + dampenSignedBump(p.valence, vb.valence()));
        p.arousal = bump01(p.arousal, vb.arousal());
        p.dominance = bump01(p.dominance, vb.dominance());

        updateSlowVariables(p, type);
        updateDebts(p, type);
        updateStreaks(p, type);
    }

    private void updateStage(AffectProfile profile, StructuredEventType type) {
        RelationshipStage current = RelationshipStage.fromId(profile.relationshipStage);
        RelationshipStage next = hmm.observe(
                current,
                profile.stageBelief,
                type,
                profile.intimacy,
                profile.conflict,
                profile.positiveEventStreak
        );
        profile.relationshipStage = next.id();
    }

    private void decayByElapsedTime(AffectProfile p) {
        long now = System.currentTimeMillis();
        p.normalize();
        double hours = Math.max(0.0D, (now - p.updatedAtEpochMillis) / HOUR_MILLIS);
        if (hours <= 0.001D) {
            return;
        }
        double cappedHours = Math.min(168.0D, hours);
        RelationshipStage stage = RelationshipStage.fromId(p.relationshipStage);
        RelationshipDynamicsConfig.StageBaseline baseline = config.stageBaseline(stage);

        OuProcess intimacy = new OuProcess(p.intimacy, config.intimacyBaseline, config.intimacyReversion, config.intimacyVolatility);
        OuProcess conflict = new OuProcess(p.conflict, config.conflictBaseline, config.conflictReversion, config.conflictVolatility);
        OuProcess valence = new OuProcess(p.valence, baseline.valence(), config.valenceReversion, config.vadVolatility, true);
        OuProcess arousal = new OuProcess(p.arousal, baseline.arousal(), config.arousalReversion, config.vadVolatility);
        OuProcess dominance = new OuProcess(p.dominance, baseline.dominance(), config.dominanceReversion, config.vadVolatility);

        intimacy.step(cappedHours, random);
        conflict.step(cappedHours, random);
        valence.step(cappedHours, random);
        arousal.step(cappedHours, random);
        dominance.step(cappedHours, random);

        p.intimacy = intimacy.value;
        p.conflict = conflict.value;
        p.valence = valence.value;
        p.arousal = arousal.value;
        p.dominance = dominance.value;
        p.hurtDebt = approach(p.hurtDebt, 0.0D, Math.min(0.35D, 0.012D * cappedHours));
        p.repairDebt = approach(p.repairDebt, 0.0D, Math.min(0.45D, 0.016D * cappedHours));
        p.memoryTriggerScore = approach(p.memoryTriggerScore, 0.0D, Math.min(0.60D, 0.035D * cappedHours));
        p.trust = approach(p.trust, 0.50D + p.externalBondSignal * 0.22D, Math.min(0.10D, 0.003D * cappedHours));
        p.attachment = approach(p.attachment, 0.58D, Math.min(0.08D, 0.002D * cappedHours));
        hmm.stepTime(stage, p.stageBelief, p.intimacy, p.conflict);
        p.relationshipStage = hmm.selectStage(p.stageBelief).id();
        p.updatedAtEpochMillis = now;
    }

    private void finish(AffectProfile p, StructuredEventType event, String evidence) {
        p.trust = AffectProfile.clamp01(p.trust);
        p.intimacy = AffectProfile.clamp01(p.intimacy);
        p.conflict = AffectProfile.clamp01(p.conflict);
        p.attachment = AffectProfile.clamp01(p.attachment);
        p.hurtDebt = AffectProfile.clamp01(p.hurtDebt);
        p.repairDebt = AffectProfile.clamp01(p.repairDebt);
        p.valence = AffectProfile.clampSigned(p.valence);
        p.arousal = AffectProfile.clamp01(p.arousal);
        p.dominance = AffectProfile.clamp01(p.dominance);

        RelationshipStage stage = RelationshipStage.fromId(p.relationshipStage);
        RelationshipDynamicsConfig.StageBaseline baseline = config.stageBaseline(stage);
        if (event == StructuredEventType.NEUTRAL_WORLD) {
            p.valence = approach(p.valence, baseline.valence(), 0.015D);
            p.arousal = approach(p.arousal, baseline.arousal(), 0.018D);
            p.dominance = approach(p.dominance, baseline.dominance(), 0.012D);
        }

        p.lastEvent = event.id();
        if (event != StructuredEventType.NEUTRAL_WORLD
                && event != StructuredEventType.OWNER_MESSAGE
                && event != StructuredEventType.LONG_MESSAGE) {
            p.lastSemanticEvent = event.id();
            p.lastEventEvidence = evidence == null ? "" : evidence.trim();
        }
        p.emotion = EmotionLabel.fromVad(p.valence, p.arousal, p.dominance).id();
        p.longing = computeLonging(p);
        p.proactiveBias = computeProactiveBias(p);
        p.lastEventAtEpochMillis = System.currentTimeMillis();
        p.updatedAtEpochMillis = p.lastEventAtEpochMillis;
        p.normalize();
    }

    private static void updateSlowVariables(AffectProfile p, StructuredEventType type) {
        switch (type) {
            case AFFECTION -> {
                p.trust += 0.006D;
                p.attachment += 0.006D;
            }
            case CARE -> {
                p.trust += 0.008D;
                p.attachment += 0.004D;
            }
            case APOLOGY, REPAIR_CHECK -> p.trust += 0.012D;
            case PROMISE -> {
                p.trust += 0.010D;
                p.attachment += 0.012D;
            }
            case MEMORY_ANCHOR -> {
                p.trust += 0.008D;
                p.attachment += 0.010D;
            }
            case INITIATE -> {
                p.trust += 0.003D;
                p.attachment += 0.003D;
            }
            case FATIGUE, BOUNDARY_REQUEST -> p.trust += 0.002D;
            case LONG_SILENCE -> {
                p.trust -= 0.010D;
                p.attachment += 0.008D;
            }
            case FIGHT -> {
                p.trust -= 0.035D;
                p.attachment += 0.005D;
            }
            case REJECT -> {
                p.trust -= 0.030D;
                p.attachment -= 0.020D;
            }
            case OWNER_ATTACK -> {
                p.trust -= 0.120D;
                p.attachment -= 0.030D;
            }
            case MAID_DEATH -> p.trust -= 0.060D;
            default -> {
            }
        }
    }

    private static void updateDebts(AffectProfile p, StructuredEventType type) {
        switch (type) {
            case AFFECTION, CARE, PROMISE, MEMORY_ANCHOR -> {
                p.hurtDebt -= 0.025D;
                p.repairDebt -= 0.025D;
            }
            case APOLOGY -> {
                p.hurtDebt -= 0.120D;
                p.repairDebt -= 0.150D;
            }
            case REPAIR_CHECK -> {
                p.hurtDebt -= 0.060D;
                p.repairDebt -= 0.100D;
            }
            case FIGHT -> {
                p.hurtDebt += 0.180D;
                p.repairDebt += 0.160D;
            }
            case REJECT -> {
                p.hurtDebt += 0.100D;
                p.repairDebt += 0.090D;
            }
            case DANGER -> p.repairDebt += 0.050D;
            case OWNER_ATTACK -> {
                p.hurtDebt += 0.420D;
                p.repairDebt += 0.400D;
            }
            case MAID_DEATH -> {
                p.hurtDebt += 0.280D;
                p.repairDebt += 0.280D;
            }
            default -> {
            }
        }
    }

    private static void updateStreaks(AffectProfile p, StructuredEventType type) {
        if (isPositive(type)) {
            p.positiveEventStreak += 1;
            p.repairEventStreak = 0;
            p.conflictEventStreak = 0;
        } else if (type == StructuredEventType.APOLOGY || type == StructuredEventType.REPAIR_CHECK) {
            p.repairEventStreak += 1;
            p.positiveEventStreak = Math.max(0, p.positiveEventStreak - 1);
            p.conflictEventStreak = 0;
        } else if (type == StructuredEventType.FIGHT || type == StructuredEventType.REJECT || type == StructuredEventType.OWNER_ATTACK) {
            p.conflictEventStreak += 1;
            p.positiveEventStreak = 0;
            p.repairEventStreak = 0;
        }
    }

    private static boolean isPositive(StructuredEventType type) {
        return switch (type) {
            case INITIATE, AFFECTION, CARE, PROMISE, MEMORY_ANCHOR, MAID_INTERACT -> true;
            default -> false;
        };
    }

    private static StructuredEventType toStructuredType(AffectiveEvent event) {
        if (event == null) {
            return StructuredEventType.NEUTRAL_WORLD;
        }
        return StructuredEventType.fromId(event.id());
    }

    private static double computeLonging(AffectProfile p) {
        double boundaryCooldown = AffectiveEvent.BOUNDARY_REQUEST.id().equals(p.lastSemanticEvent)
                || AffectiveEvent.FATIGUE.id().equals(p.lastSemanticEvent) ? 1.0D : 0.0D;
        double value = 0.35D
                + p.attachment * 0.24D
                + p.intimacy * 0.14D
                + p.memoryTriggerScore * 0.22D
                + p.repairDebt * 0.08D
                - boundaryCooldown * 0.12D;
        RelationshipStage stage = RelationshipStage.fromId(p.relationshipStage);
        if (stage == RelationshipStage.SWEET || stage == RelationshipStage.PASSIONATE) {
            value += 0.05D;
        }
        if (stage == RelationshipStage.COLD) {
            value -= 0.08D;
        }
        return AffectProfile.clamp01(value);
    }

    private static double computeProactiveBias(AffectProfile p) {
        double recentBoundaryNeed = AffectiveEvent.BOUNDARY_REQUEST.id().equals(p.lastSemanticEvent)
                || AffectiveEvent.FATIGUE.id().equals(p.lastSemanticEvent) ? 1.0D : 0.0D;
        double value = 0.30D
                + p.longing * 0.35D
                + p.attachment * 0.18D
                + p.memoryTriggerScore * 0.15D
                + p.repairDebt * 0.10D
                - recentBoundaryNeed * 0.25D;
        if (p.arousal > 0.72D && p.valence < 0.0D) {
            value += 0.06D;
        }
        if (RelationshipStage.fromId(p.relationshipStage) == RelationshipStage.COLD) {
            value -= 0.10D;
        }
        return AffectProfile.clamp01(value);
    }

    private static double approach(double value, double target, double ratio) {
        double safeRatio = Math.max(0.0D, Math.min(1.0D, ratio));
        return value + (target - value) * safeRatio;
    }

    private static double bump01(double value, double bump) {
        return AffectProfile.clamp01(value + bump);
    }

    private static double dampenBump(double current, double bump) {
        if (bump <= 0.0D) {
            return bump;
        }
        return bump * Math.max(0.22D, 1.0D - current * 0.58D);
    }

    private static double dampenSignedBump(double current, double bump) {
        if (bump > 0.0D) {
            return bump * Math.max(0.20D, 1.0D - Math.max(0.0D, current) * 0.65D);
        }
        if (bump < 0.0D) {
            return bump * Math.max(0.20D, 1.0D - Math.max(0.0D, -current) * 0.65D);
        }
        return 0.0D;
    }
}
