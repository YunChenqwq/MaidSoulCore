package com.maidsoul.brain.affect;

/**
 * 情绪关系动力学引擎。
 *
 * <p>这里不做自然语言 pattern 判断，只根据上游已经判定好的结构化事件调整状态。
 * 这样情绪层不会和中文词表、某个角色口吻或临时 prompt 补丁绑死。</p>
 */
public final class AffectEngine {
    private final RelationshipHmm relationshipHmm = new RelationshipHmm();

    public void observeOwnerMessage(AffectProfile profile, String text) {
        apply(profile, AffectEvent.of(AffectEventKind.OWNER_MESSAGE, 35, "chat", ""));
    }

    public void observeWorldEvent(AffectProfile profile, String eventType) {
        String type = eventType == null ? "" : eventType.trim().toLowerCase();
        if (type.contains("maid.attacked.by_owner") || type.contains("owner_attack")) {
            apply(profile, AffectEvent.of(AffectEventKind.MAID_HURT_BY_OWNER, 75, "world", eventType));
        } else if (type.contains("maid.attacked") || type.contains("hurt")) {
            apply(profile, AffectEvent.of(AffectEventKind.MAID_HURT_BY_WORLD, 55, "world", eventType));
        }
    }

    public void observeAssistantReply(AffectProfile profile, String text) {
        apply(profile, AffectEvent.of(AffectEventKind.ASSISTANT_REPLY, 30, "chat", ""));
    }

    public void recoverAfterQuietTime(AffectProfile profile) {
        apply(profile, AffectEvent.of(AffectEventKind.QUIET_RECOVERY, 30, "runtime", ""));
    }

    public void apply(AffectProfile profile, AffectEvent event) {
        if (profile == null || event == null || event.kind() == null) {
            return;
        }
        profile.normalize();
        double weight = Math.max(0.0D, Math.min(1.0D, event.intensity() / 100.0D));
        applyEventImpulse(profile, event.kind(), weight);
        profile.relationshipStage = relationshipHmm.observe(
                profile.stage(),
                event.kind(),
                profile.intimacy,
                profile.conflict,
                profile.positiveEventStreak
        ).id();
        profile.lastEvent = event.kind().name();
        profile.normalize();
    }

    public void syncBaseAffection(AffectProfile profile, int affection) {
        double normalized = Math.max(0.0D, Math.min(1.0D, affection / 100.0D));
        profile.intimacy = AffectProfile.clamp01(0.18D + normalized * 0.68D);
        profile.conflict = AffectProfile.clamp01(profile.conflict * 0.70D);
        profile.longing = AffectProfile.clamp01(0.35D + profile.intimacy * 0.40D);
        profile.curiosity = Math.max(0, Math.min(100, 45 + affection / 5));
        profile.normalize();
    }

    private static void applyEventImpulse(AffectProfile profile, AffectEventKind kind, double weight) {
        switch (kind) {
            case OWNER_MESSAGE -> {
                // 普通来信表示主人仍在互动：轻微拉近、轻微降冲突，不做语义判断。
                profile.intimacy = approach01(profile.intimacy, profile.intimacy + 0.018D * weight);
                profile.conflict = approach01(profile.conflict, profile.conflict - 0.012D * weight);
                profile.arousal = approach01(profile.arousal, profile.arousal + 0.020D * weight);
                profile.longing = approach01(profile.longing, 0.46D, 0.020D * weight);
                profile.curiosity = approachInt(profile.curiosity, 65, 2);
            }
            case ASSISTANT_REPLY -> {
                // 说出口以后情绪会释放一点，但关系状态不应被女仆自己的回复大幅改写。
                profile.arousal = approach01(profile.arousal, 0.34D, 0.035D * weight);
                profile.repairDebt = approach01(profile.repairDebt, 0.0D, 0.018D * weight);
                profile.longing = approach01(profile.longing, 0.40D, 0.020D * weight);
                profile.curiosity = Math.max(0, profile.curiosity - (int) Math.round(3 * weight));
            }
            case OWNER_APOLOGY -> {
                profile.valence = clampSigned(profile.valence + 0.10D * weight);
                profile.arousal = approach01(profile.arousal, 0.32D, 0.12D * weight);
                profile.dominance = clamp01(profile.dominance + 0.04D * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.060D * weight);
                profile.conflict = clamp01(profile.conflict - 0.160D * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt - 0.140D * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.180D * weight);
                profile.positiveEventStreak++;
                profile.curiosity = Math.min(100, profile.curiosity + (int) Math.round(3 * weight));
            }
            case OWNER_ATTACK, MAID_HURT_BY_OWNER -> {
                profile.valence = clampSigned(profile.valence - 0.22D * weight);
                profile.arousal = clamp01(profile.arousal + 0.20D * weight);
                profile.dominance = clamp01(profile.dominance - 0.10D * weight);
                profile.intimacy = clamp01(profile.intimacy - 0.150D * weight);
                profile.conflict = clamp01(profile.conflict + 0.240D * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.220D * weight);
                profile.repairDebt = clamp01(profile.repairDebt + 0.260D * weight);
                profile.positiveEventStreak = 0;
                profile.curiosity = Math.max(0, profile.curiosity - (int) Math.round(12 * weight));
            }
            case OWNER_DISTRESS -> {
                profile.valence = clampSigned(profile.valence - 0.030D * weight);
                profile.arousal = clamp01(profile.arousal + 0.080D * weight);
                profile.dominance = clamp01(profile.dominance + 0.020D * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.045D * weight);
                profile.longing = clamp01(profile.longing + 0.080D * weight);
                profile.positiveEventStreak++;
                profile.curiosity = Math.min(100, profile.curiosity + (int) Math.round(8 * weight));
            }
            case OWNER_AFFECTION -> {
                profile.valence = clampSigned(profile.valence + 0.160D * weight);
                profile.arousal = clamp01(profile.arousal + 0.080D * weight);
                profile.dominance = clamp01(profile.dominance + 0.020D * weight);
                profile.intimacy = clamp01(profile.intimacy + 0.110D * weight);
                profile.conflict = clamp01(profile.conflict - 0.050D * weight);
                profile.longing = clamp01(profile.longing + 0.070D * weight);
                profile.repairDebt = clamp01(profile.repairDebt - 0.030D * weight);
                profile.positiveEventStreak++;
                profile.curiosity = Math.min(100, profile.curiosity + (int) Math.round(5 * weight));
            }
            case OWNER_QUESTION, OWNER_SHORT_FEEDBACK -> {
                profile.arousal = clamp01(profile.arousal + 0.035D * weight);
                profile.longing = clamp01(profile.longing + 0.035D * weight);
                profile.curiosity = Math.min(100, profile.curiosity + (int) Math.round(6 * weight));
            }
            case MAID_HURT_BY_WORLD -> {
                profile.valence = clampSigned(profile.valence - 0.080D * weight);
                profile.arousal = clamp01(profile.arousal + 0.120D * weight);
                profile.dominance = clamp01(profile.dominance - 0.040D * weight);
                profile.hurtDebt = clamp01(profile.hurtDebt + 0.100D * weight);
                profile.longing = clamp01(profile.longing + 0.040D * weight);
                profile.curiosity = Math.max(0, profile.curiosity - (int) Math.round(3 * weight));
            }
            case QUIET_RECOVERY -> stepTime(profile, weight);
        }
    }

    private static void stepTime(AffectProfile profile, double weight) {
        // OU 风格回落：连续状态缓慢回到阶段基线，而不是靠一次性清零。
        double[] baseline = stageBaseline(profile.stage());
        profile.valence = approach(profile.valence, baseline[0], 0.045D * weight);
        profile.arousal = approach(profile.arousal, baseline[1], 0.040D * weight);
        profile.dominance = approach(profile.dominance, baseline[2], 0.030D * weight);
        profile.conflict = approach01(profile.conflict, 0.04D, 0.030D * weight);
        profile.hurtDebt = approach01(profile.hurtDebt, 0.0D, 0.020D * weight);
        profile.repairDebt = approach01(profile.repairDebt, 0.0D, 0.020D * weight);
        profile.longing = approach01(profile.longing, 0.42D + profile.intimacy * 0.24D, 0.025D * weight);
    }

    private static double[] stageBaseline(RelationshipStage stage) {
        return switch (stage) {
            case SWEET -> new double[]{0.62D, 0.46D, 0.50D};
            case PASSIONATE -> new double[]{0.72D, 0.64D, 0.56D};
            case STABLE -> new double[]{0.36D, 0.30D, 0.50D};
            case COLD -> new double[]{-0.38D, 0.48D, 0.34D};
            case REPAIRING -> new double[]{0.02D, 0.46D, 0.42D};
            default -> new double[]{0.12D, 0.34D, 0.48D};
        };
    }

    private static double approach(double value, double target, double step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }

    private static double approach01(double value, double target, double step) {
        return clamp01(approach(value, target, step));
    }

    private static double approach01(double value, double target) {
        return clamp01(target);
    }

    private static double clamp01(double value) {
        return AffectProfile.clamp01(value);
    }

    private static double clampSigned(double value) {
        return AffectProfile.clamp(value, -1.0D, 1.0D);
    }

    private static int approachInt(int value, int target, int step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }
}
