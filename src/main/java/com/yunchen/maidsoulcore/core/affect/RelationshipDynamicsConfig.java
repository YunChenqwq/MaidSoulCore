package com.yunchen.maidsoulcore.core.affect;

import com.yunchen.maidsoulcore.core.event.StructuredEventType;

import java.util.EnumMap;
import java.util.Map;

public final class RelationshipDynamicsConfig {
    public double intimacyBaseline = 0.50D;
    public double intimacyReversion = 0.05D;
    public double intimacyVolatility = 0.0D;

    public double conflictBaseline = 0.10D;
    public double conflictReversion = 0.08D;
    public double conflictVolatility = 0.0D;

    public double valenceReversion = 0.06D;
    public double arousalReversion = 0.10D;
    public double dominanceReversion = 0.08D;
    public double vadVolatility = 0.0D;

    private final Map<RelationshipStage, Map<RelationshipStage, Double>> transitionMatrix = new EnumMap<>(RelationshipStage.class);
    private final Map<StructuredEventType, EventBump> relationshipBumps = new EnumMap<>(StructuredEventType.class);
    private final Map<StructuredEventType, VadBump> vadBumps = new EnumMap<>(StructuredEventType.class);

    public RelationshipDynamicsConfig() {
        initTransitions();
        initRelationshipBumps();
        initVadBumps();
    }

    public Map<RelationshipStage, Double> baseTransitions(RelationshipStage stage) {
        return transitionMatrix.getOrDefault(stage, transitionMatrix.get(RelationshipStage.COURTING));
    }

    public EventBump relationshipBump(StructuredEventType type) {
        return relationshipBumps.getOrDefault(type, new EventBump(0.0D, 0.0D));
    }

    public VadBump vadBump(StructuredEventType type) {
        return vadBumps.getOrDefault(type, new VadBump(0.0D, 0.0D, 0.0D));
    }

    public StageBaseline stageBaseline(RelationshipStage stage) {
        return switch (stage) {
            case COURTING -> new StageBaseline(0.20D, 0.55D, 0.38D);
            case SWEET -> new StageBaseline(0.62D, 0.50D, 0.45D);
            case PASSIONATE -> new StageBaseline(0.78D, 0.65D, 0.50D);
            case STABLE -> new StageBaseline(0.50D, 0.35D, 0.52D);
            case COLD -> new StageBaseline(-0.35D, 0.55D, 0.34D);
            case REPAIRING -> new StageBaseline(0.05D, 0.48D, 0.40D);
        };
    }

    private void initTransitions() {
        put(RelationshipStage.COURTING, Map.of(
                RelationshipStage.COURTING, 0.60D,
                RelationshipStage.SWEET, 0.30D,
                RelationshipStage.COLD, 0.10D
        ));
        put(RelationshipStage.SWEET, Map.of(
                RelationshipStage.COURTING, 0.05D,
                RelationshipStage.SWEET, 0.50D,
                RelationshipStage.PASSIONATE, 0.35D,
                RelationshipStage.COLD, 0.10D
        ));
        put(RelationshipStage.PASSIONATE, Map.of(
                RelationshipStage.SWEET, 0.10D,
                RelationshipStage.PASSIONATE, 0.40D,
                RelationshipStage.STABLE, 0.40D,
                RelationshipStage.COLD, 0.10D
        ));
        put(RelationshipStage.STABLE, Map.of(
                RelationshipStage.SWEET, 0.05D,
                RelationshipStage.PASSIONATE, 0.05D,
                RelationshipStage.STABLE, 0.70D,
                RelationshipStage.COLD, 0.15D,
                RelationshipStage.REPAIRING, 0.05D
        ));
        put(RelationshipStage.COLD, Map.of(
                RelationshipStage.STABLE, 0.10D,
                RelationshipStage.COLD, 0.60D,
                RelationshipStage.REPAIRING, 0.30D
        ));
        put(RelationshipStage.REPAIRING, Map.of(
                RelationshipStage.SWEET, 0.15D,
                RelationshipStage.STABLE, 0.35D,
                RelationshipStage.COLD, 0.20D,
                RelationshipStage.REPAIRING, 0.30D
        ));
    }

    private void initRelationshipBumps() {
        relationshipBumps.put(StructuredEventType.OWNER_MESSAGE, new EventBump(0.006D, 0.000D));
        relationshipBumps.put(StructuredEventType.LONG_MESSAGE, new EventBump(0.016D, -0.004D));
        relationshipBumps.put(StructuredEventType.INITIATE, new EventBump(0.032D, -0.008D));
        relationshipBumps.put(StructuredEventType.AFFECTION, new EventBump(0.045D, -0.024D));
        relationshipBumps.put(StructuredEventType.CARE, new EventBump(0.028D, -0.020D));
        relationshipBumps.put(StructuredEventType.APOLOGY, new EventBump(0.020D, -0.085D));
        relationshipBumps.put(StructuredEventType.REPAIR_CHECK, new EventBump(0.018D, -0.050D));
        relationshipBumps.put(StructuredEventType.PROMISE, new EventBump(0.035D, -0.016D));
        relationshipBumps.put(StructuredEventType.MEMORY_ANCHOR, new EventBump(0.032D, -0.008D));
        relationshipBumps.put(StructuredEventType.FATIGUE, new EventBump(0.015D, -0.005D));
        relationshipBumps.put(StructuredEventType.BOUNDARY_REQUEST, new EventBump(-0.010D, 0.015D));
        relationshipBumps.put(StructuredEventType.FIGHT, new EventBump(-0.120D, 0.180D));
        relationshipBumps.put(StructuredEventType.REJECT, new EventBump(-0.100D, 0.100D));
        relationshipBumps.put(StructuredEventType.LONG_SILENCE, new EventBump(-0.100D, 0.050D));
        relationshipBumps.put(StructuredEventType.DANGER, new EventBump(0.015D, 0.060D));
        relationshipBumps.put(StructuredEventType.WORLD_CHANGE, new EventBump(0.025D, 0.010D));
        relationshipBumps.put(StructuredEventType.MAID_INTERACT, new EventBump(0.018D, -0.005D));
        relationshipBumps.put(StructuredEventType.OWNER_ATTACK, new EventBump(-0.250D, 0.350D));
        relationshipBumps.put(StructuredEventType.MAID_DEATH, new EventBump(-0.080D, 0.180D));
    }

    private void initVadBumps() {
        vadBumps.put(StructuredEventType.OWNER_MESSAGE, new VadBump(0.014D, 0.010D, 0.003D));
        vadBumps.put(StructuredEventType.LONG_MESSAGE, new VadBump(0.028D, 0.018D, 0.005D));
        vadBumps.put(StructuredEventType.INITIATE, new VadBump(0.040D, 0.025D, 0.014D));
        vadBumps.put(StructuredEventType.AFFECTION, new VadBump(0.055D, 0.020D, 0.008D));
        vadBumps.put(StructuredEventType.CARE, new VadBump(0.042D, -0.018D, 0.012D));
        vadBumps.put(StructuredEventType.APOLOGY, new VadBump(0.035D, -0.050D, 0.016D));
        vadBumps.put(StructuredEventType.REPAIR_CHECK, new VadBump(0.025D, -0.030D, 0.012D));
        vadBumps.put(StructuredEventType.PROMISE, new VadBump(0.045D, 0.012D, 0.012D));
        vadBumps.put(StructuredEventType.MEMORY_ANCHOR, new VadBump(0.038D, 0.016D, 0.008D));
        vadBumps.put(StructuredEventType.FATIGUE, new VadBump(0.010D, -0.070D, 0.005D));
        vadBumps.put(StructuredEventType.BOUNDARY_REQUEST, new VadBump(-0.020D, -0.050D, 0.020D));
        vadBumps.put(StructuredEventType.FIGHT, new VadBump(-0.160D, 0.150D, -0.050D));
        vadBumps.put(StructuredEventType.REJECT, new VadBump(-0.120D, 0.080D, -0.070D));
        vadBumps.put(StructuredEventType.LONG_SILENCE, new VadBump(-0.100D, 0.100D, -0.080D));
        vadBumps.put(StructuredEventType.DANGER, new VadBump(-0.100D, 0.180D, -0.080D));
        vadBumps.put(StructuredEventType.WORLD_CHANGE, new VadBump(0.000D, 0.040D, -0.010D));
        vadBumps.put(StructuredEventType.OWNER_ATTACK, new VadBump(-0.320D, 0.280D, -0.180D));
        vadBumps.put(StructuredEventType.MAID_DEATH, new VadBump(-0.420D, 0.300D, -0.160D));
    }

    private void put(RelationshipStage from, Map<RelationshipStage, Double> values) {
        Map<RelationshipStage, Double> filled = new EnumMap<>(RelationshipStage.class);
        for (RelationshipStage stage : RelationshipStage.values()) {
            filled.put(stage, values.getOrDefault(stage, 0.0D));
        }
        transitionMatrix.put(from, filled);
    }

    public record EventBump(double intimacy, double conflict) {
    }

    public record VadBump(double valence, double arousal, double dominance) {
    }

    public record StageBaseline(double valence, double arousal, double dominance) {
    }
}
