package com.maidsoulcore.mood;

/**
 * Shared affect snapshot for the old simulation blackboard.
 *
 * <p>This module now mirrors the v2 affect model: VAD emotion plus relationship
 * dynamics. The old "bond" concept is kept as a compatibility method and maps
 * to intimacy.</p>
 */
public record MoodState(
        double valence,
        double arousal,
        double dominance,
        double intimacy,
        double conflict,
        double trust,
        String relationshipStage,
        String emotion
) {
    public MoodState {
        valence = clampSigned(valence);
        arousal = clamp01(arousal);
        dominance = clamp01(dominance);
        intimacy = clamp01(intimacy);
        conflict = clamp01(conflict);
        trust = clamp01(trust);
        relationshipStage = relationshipStage == null || relationshipStage.isBlank() ? "courting" : relationshipStage;
        emotion = emotion == null || emotion.isBlank() ? "neutral" : emotion;
    }

    public MoodState(double valence, double arousal, double bond) {
        this(valence, arousal, 0.45D, bond, 0.08D, 0.55D, inferStage(bond, 0.08D), inferEmotion(valence, arousal, 0.45D));
    }

    public static MoodState neutral() {
        return new MoodState(0.20D, 0.45D, 0.40D, 0.50D, 0.08D, 0.55D, "courting", "neutral");
    }

    public double bond() {
        return intimacy;
    }

    public static String inferStage(double intimacy, double conflict) {
        if (conflict > 0.58D) {
            return "cold";
        }
        if (conflict > 0.32D) {
            return "repairing";
        }
        if (intimacy > 0.82D) {
            return "passionate";
        }
        if (intimacy > 0.64D) {
            return "sweet";
        }
        if (intimacy > 0.52D) {
            return "stable";
        }
        return "courting";
    }

    public static String inferEmotion(double valence, double arousal, double dominance) {
        if (valence > 0.50D) {
            if (arousal > 0.60D) {
                return dominance > 0.60D ? "joy" : "excitement";
            }
            if (arousal < 0.40D) {
                return "contentment";
            }
            return "trust";
        }
        if (valence < -0.50D) {
            if (arousal > 0.60D) {
                if (dominance > 0.60D) {
                    return "anger";
                }
                if (dominance < 0.40D) {
                    return "fear";
                }
                return "anxiety";
            }
            if (arousal < 0.40D) {
                return "sadness";
            }
            return "anxiety";
        }
        return arousal > 0.60D ? "anticipation" : "neutral";
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }
}
