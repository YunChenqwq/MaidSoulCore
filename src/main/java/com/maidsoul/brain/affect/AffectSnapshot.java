package com.maidsoul.brain.affect;

/**
 * 写入记忆时使用的情绪快照。
 */
public record AffectSnapshot(
        int mood,
        int anger,
        int hurt,
        int tension,
        int trust,
        int familiarity,
        int affection,
        int security,
        int curiosity,
        double valence,
        double arousal,
        double dominance,
        double intimacy,
        double conflict,
        double hurtDebt,
        double repairDebt,
        String relationshipStage,
        String emotionLabel
) {
    public static AffectSnapshot from(AffectProfile profile) {
        return new AffectSnapshot(
                profile.mood,
                profile.anger,
                profile.hurt,
                profile.tension,
                profile.trust,
                profile.familiarity,
                profile.affection,
                profile.security,
                profile.curiosity,
                profile.valence,
                profile.arousal,
                profile.dominance,
                profile.intimacy,
                profile.conflict,
                profile.hurtDebt,
                profile.repairDebt,
                profile.relationshipStage,
                profile.emotionLabel
        );
    }

    public String compact() {
        return "mood=" + mood
                + ",anger=" + anger
                + ",hurt=" + hurt
                + ",tension=" + tension
                + ",trust=" + trust
                + ",familiarity=" + familiarity
                + ",affection=" + affection
                + ",security=" + security
                + ",curiosity=" + curiosity
                + ",vad=" + round(valence) + "/" + round(arousal) + "/" + round(dominance)
                + ",intimacy=" + round(intimacy)
                + ",conflict=" + round(conflict)
                + ",stage=" + relationshipStage
                + ",emotion=" + emotionLabel;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
