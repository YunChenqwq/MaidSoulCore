package com.yunchen.maidsoulcore.core.event;

public final class EventImportancePolicy {
    private EventImportancePolicy() {
    }

    public static double defaultImportance(StructuredEventType type, double confidence) {
        double base = switch (type) {
            case PROMISE, MEMORY_ANCHOR, OWNER_ATTACK, MAID_DEATH, WORLD_CHANGE -> 0.90D;
            case FIGHT, REJECT, APOLOGY, REPAIR_CHECK -> 0.76D;
            case AFFECTION, CARE, DANGER -> 0.66D;
            case FATIGUE, BOUNDARY_REQUEST -> 0.58D;
            case INITIATE, LONG_MESSAGE, MAID_INTERACT -> 0.42D;
            default -> 0.20D;
        };
        return clamp01(base * 0.70D + clamp01(confidence) * 0.30D);
    }

    public static String defaultMemoryCategory(StructuredEventType type, double importance) {
        return switch (type) {
            case PROMISE -> "promise";
            case MEMORY_ANCHOR -> "memory_anchor";
            case APOLOGY, REPAIR_CHECK, FIGHT, REJECT, OWNER_ATTACK -> "repair_record";
            case FATIGUE, BOUNDARY_REQUEST -> importance >= 0.72D ? "owner_profile" : "short_context";
            case DANGER, WORLD_CHANGE, MAID_DEATH -> "world_fact";
            case AFFECTION, CARE, MAID_INTERACT -> "relation_event";
            default -> "";
        };
    }

    public static String defaultMemoryCategory(StructuredEventType type) {
        return defaultMemoryCategory(type, defaultImportance(type, 0.70D));
    }

    public static boolean shouldUpdateAffect(StructuredEventType type, double confidence) {
        if (confidence < 0.65D) {
            return false;
        }
        return type != StructuredEventType.NEUTRAL_WORLD
                && type != StructuredEventType.OWNER_MESSAGE
                && type != StructuredEventType.LONG_MESSAGE;
    }

    public static boolean shouldWriteMemory(StructuredEventType type, double confidence, double importance) {
        if (confidence < 0.70D || importance < 0.50D) {
            return false;
        }
        return switch (type) {
            case PROMISE, MEMORY_ANCHOR, OWNER_ATTACK, MAID_DEATH, WORLD_CHANGE,
                 APOLOGY, REPAIR_CHECK, FIGHT, REJECT, DANGER -> true;
            case AFFECTION, CARE -> importance >= 0.82D;
            case FATIGUE, BOUNDARY_REQUEST -> importance >= 0.72D;
            default -> false;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
