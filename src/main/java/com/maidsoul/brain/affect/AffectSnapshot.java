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
        int curiosity
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
                profile.curiosity
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
                + ",curiosity=" + curiosity;
    }
}
