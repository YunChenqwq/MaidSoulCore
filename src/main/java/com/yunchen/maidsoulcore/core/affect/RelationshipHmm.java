package com.yunchen.maidsoulcore.core.affect;

import com.yunchen.maidsoulcore.core.event.StructuredEventType;

import java.util.EnumMap;
import java.util.Map;

public final class RelationshipHmm {
    private final RelationshipDynamicsConfig config;

    public RelationshipHmm() {
        this(new RelationshipDynamicsConfig());
    }

    public RelationshipHmm(RelationshipDynamicsConfig config) {
        this.config = config;
    }

    public RelationshipStage observe(
            RelationshipStage currentStage,
            double[] currentBelief,
            StructuredEventType event,
            double intimacy,
            double conflict,
            int positiveEventStreak
    ) {
        RelationshipStage safeStage = currentStage == null ? RelationshipStage.COURTING : currentStage;
        double[] belief = normalizeBelief(currentBelief, safeStage);
        Map<RelationshipStage, Double> transition = computeTransitionProbabilities(safeStage, event, intimacy, conflict);
        double[] next = new double[RelationshipStage.values().length];
        RelationshipStage[] stages = RelationshipStage.values();
        for (int from = 0; from < stages.length; from++) {
            Map<RelationshipStage, Double> fromTransition = computeTransitionProbabilities(stages[from], event, intimacy, conflict);
            for (int to = 0; to < stages.length; to++) {
                next[to] += belief[from] * fromTransition.getOrDefault(stages[to], 0.0D);
            }
        }
        normalizeInPlace(next, safeStage);
        System.arraycopy(next, 0, currentBelief, 0, Math.min(currentBelief.length, next.length));
        RelationshipStage selected = selectStage(next);
        if (selected == RelationshipStage.PASSIONATE && (intimacy < 0.72D || positiveEventStreak < 3)) {
            return next[indexOf(RelationshipStage.SWEET)] >= next[indexOf(RelationshipStage.STABLE)]
                    ? RelationshipStage.SWEET
                    : RelationshipStage.STABLE;
        }
        return selected;
    }

    public void stepTime(RelationshipStage currentStage, double[] currentBelief, double intimacy, double conflict) {
        RelationshipStage safeStage = currentStage == null ? RelationshipStage.COURTING : currentStage;
        double[] belief = normalizeBelief(currentBelief, safeStage);
        if (conflict > 0.70D && isPositiveStage(safeStage)) {
            belief[indexOf(RelationshipStage.COLD)] += Math.min(0.25D, conflict * 0.20D);
            belief[indexOf(RelationshipStage.REPAIRING)] += Math.min(0.15D, conflict * 0.10D);
        } else if (conflict < 0.30D && intimacy > 0.50D && safeStage == RelationshipStage.COLD) {
            belief[indexOf(RelationshipStage.REPAIRING)] += 0.12D;
        } else if (intimacy < 0.35D && isPositiveStage(safeStage)) {
            belief[indexOf(RelationshipStage.STABLE)] += 0.08D;
            belief[indexOf(RelationshipStage.COURTING)] += 0.05D;
        }
        normalizeInPlace(belief, safeStage);
        System.arraycopy(belief, 0, currentBelief, 0, Math.min(currentBelief.length, belief.length));
    }

    public RelationshipStage selectStage(double[] belief) {
        double[] safe = normalizeBelief(belief, RelationshipStage.COURTING);
        RelationshipStage[] stages = RelationshipStage.values();
        int best = 0;
        for (int i = 1; i < safe.length; i++) {
            if (safe[i] > safe[best]) {
                best = i;
            }
        }
        return stages[best];
    }

    private Map<RelationshipStage, Double> computeTransitionProbabilities(
            RelationshipStage stage,
            StructuredEventType event,
            double intimacy,
            double conflict
    ) {
        Map<RelationshipStage, Double> probs = new EnumMap<>(config.baseTransitions(stage));
        if (intimacy > 0.70D) {
            multiply(probs, RelationshipStage.SWEET, 1.6D);
            multiply(probs, RelationshipStage.PASSIONATE, 1.6D);
            multiply(probs, RelationshipStage.COLD, 0.4D);
        } else if (intimacy < 0.30D) {
            multiply(probs, RelationshipStage.COLD, 2.0D);
            multiply(probs, RelationshipStage.SWEET, 0.4D);
            multiply(probs, RelationshipStage.PASSIONATE, 0.2D);
        }
        if (conflict > 0.50D) {
            multiply(probs, RelationshipStage.COLD, 3.0D);
            multiply(probs, RelationshipStage.REPAIRING, 2.0D);
            multiply(probs, RelationshipStage.SWEET, 0.3D);
            multiply(probs, RelationshipStage.PASSIONATE, 0.2D);
        } else if (conflict < 0.20D) {
            multiply(probs, RelationshipStage.COLD, 0.3D);
        }
        switch (event) {
            case FIGHT, OWNER_ATTACK -> {
                multiply(probs, RelationshipStage.COLD, 5.0D);
                multiply(probs, stage, 0.5D);
            }
            case APOLOGY, REPAIR_CHECK -> {
                multiply(probs, RelationshipStage.REPAIRING, 2.5D);
                multiply(probs, RelationshipStage.COLD, 0.4D);
                if (conflict < 0.18D) {
                    multiply(probs, RelationshipStage.STABLE, 1.4D);
                    multiply(probs, RelationshipStage.SWEET, 1.2D);
                }
            }
            case AFFECTION -> {
                if (intimacy > 0.62D) {
                    multiply(probs, RelationshipStage.PASSIONATE, 2.0D);
                    multiply(probs, RelationshipStage.SWEET, 1.5D);
                }
            }
            case PROMISE, MEMORY_ANCHOR -> {
                multiply(probs, RelationshipStage.STABLE, 1.4D);
                multiply(probs, RelationshipStage.SWEET, 1.3D);
                if (intimacy > 0.70D) {
                    multiply(probs, RelationshipStage.PASSIONATE, 1.25D);
                }
            }
            case BOUNDARY_REQUEST, FATIGUE -> {
                multiply(probs, RelationshipStage.STABLE, 1.2D);
                multiply(probs, RelationshipStage.PASSIONATE, 0.8D);
            }
            case LONG_SILENCE, REJECT -> multiply(probs, RelationshipStage.COLD, 2.0D);
            default -> {
            }
        }
        normalizeMap(probs);
        return probs;
    }

    private static void multiply(Map<RelationshipStage, Double> probs, RelationshipStage stage, double factor) {
        probs.put(stage, probs.getOrDefault(stage, 0.0D) * factor);
    }

    private static void normalizeMap(Map<RelationshipStage, Double> probs) {
        double total = probs.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0D) {
            return;
        }
        for (RelationshipStage stage : RelationshipStage.values()) {
            probs.put(stage, probs.getOrDefault(stage, 0.0D) / total);
        }
    }

    private static double[] normalizeBelief(double[] belief, RelationshipStage fallbackStage) {
        double[] safe = new double[RelationshipStage.values().length];
        if (belief != null) {
            System.arraycopy(belief, 0, safe, 0, Math.min(belief.length, safe.length));
        }
        normalizeInPlace(safe, fallbackStage);
        return safe;
    }

    private static void normalizeInPlace(double[] belief, RelationshipStage fallbackStage) {
        double total = 0.0D;
        for (int i = 0; i < belief.length; i++) {
            belief[i] = Math.max(0.0D, belief[i]);
            total += belief[i];
        }
        if (total <= 0.0D) {
            belief[indexOf(fallbackStage)] = 1.0D;
            return;
        }
        for (int i = 0; i < belief.length; i++) {
            belief[i] /= total;
        }
    }

    private static boolean isPositiveStage(RelationshipStage stage) {
        return stage == RelationshipStage.SWEET
                || stage == RelationshipStage.PASSIONATE
                || stage == RelationshipStage.STABLE;
    }

    public static int indexOf(RelationshipStage stage) {
        RelationshipStage[] stages = RelationshipStage.values();
        for (int i = 0; i < stages.length; i++) {
            if (stages[i] == stage) {
                return i;
            }
        }
        return 0;
    }
}
