package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardStore;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.mood.MoodState;

import java.util.Map;

public final class SimulationBlackboardUpdater {
    public void update(BlackboardStore blackboardStore, SimulationEnvironment environment, MaidEvent event) {
        blackboardStore.put("last.event.type", event.type());
        blackboardStore.put("last.event.payload", event.payload());
        blackboardStore.put("world.tick", environment.tick());
        blackboardStore.put("world.is_night", environment.isNight());
        blackboardStore.put("owner.name", environment.ownerName());
        blackboardStore.put("owner.position", environment.ownerPosition().shortText());
        blackboardStore.put("maid.name", environment.maidName());
        blackboardStore.put("maid.position", environment.maidPosition().shortText());
        blackboardStore.put("maid.health", environment.maidHealth());
        blackboardStore.put("maid.energy", environment.maidEnergy());
        blackboardStore.put("maid.energy_state", environment.energyState());
        blackboardStore.put("maid.can_start_work", environment.canStartWorkTask());
        blackboardStore.put("maid.hunger", environment.maidHunger());
        blackboardStore.put("maid.sleeping", environment.maidSleeping());
        blackboardStore.put("maid.sitting", environment.maidSitting());
        blackboardStore.put("maid.work_task", environment.currentWorkTask());
        blackboardStore.put("maid.schedule", environment.currentSchedule());
        blackboardStore.put("relation.favorability_total", environment.favorabilityTotal());
        blackboardStore.put("relation.favorability_daily_gain", environment.favorabilityDailyGain());
        blackboardStore.put("relation.favorability_chat_gain", environment.favorabilityChatGain());
        blackboardStore.put("relation.favorability_action_gain", environment.favorabilityActionGain());
        blackboardStore.put("relation.favorability_care_gain", environment.favorabilityCareGain());
        blackboardStore.put("policy.follow_policy", environment.followPolicy());
        blackboardStore.put("policy.follow_policy_source", environment.followPolicySource());
        blackboardStore.put("policy.follow_policy_reason", environment.followPolicyReason());
        blackboardStore.put("policy.last_owner_explicit_command", environment.lastOwnerExplicitCommand());
        blackboardStore.put("chat.session_active", environment.chatSessionActive());
        blackboardStore.put("chat.speak_cooldown_ticks", environment.currentSpeakCooldownTicks());
        blackboardStore.put("vision.capture_interval_ticks", environment.captureIntervalTicks());
        blackboardStore.put("memory.last_player_text", environment.lastPlayerUtterance());
        blackboardStore.put("memory.last_reply", environment.lastReply());
        blackboardStore.put("memory.last_action", environment.lastActionSummary());
        blackboardStore.put("spatial.home", environment.homePosition().shortText());
        blackboardStore.put("spatial.owner_distance", environment.ownerDistanceLabel());
        blackboardStore.put("context.world", environment.worldStateSummary());
        blackboardStore.put("context.self", environment.selfStateSummary());
        blackboardStore.put("context.owner", environment.ownerStateSummary());
        blackboardStore.put("context.position", environment.positionStateSummary());
        blackboardStore.put("context.inventory", environment.inventoryStateSummary());
        blackboardStore.put("context.nearby_entities", environment.nearbyEntitiesSummary());
        blackboardStore.put("perception.vision", environment.lastVisionSummary());
        blackboardStore.put("perception.threat", environment.nearbyThreat());
        blackboardStore.put("debug.environment", Map.of(
                "maid", environment.maidPosition().shortText(),
                "owner", environment.ownerPosition().shortText(),
                "home", environment.homePosition().shortText()
        ));
        blackboardStore.setMood(deriveMood(environment, event));
    }

    private MoodState deriveMood(SimulationEnvironment environment, MaidEvent event) {
        double valence = 0.15D;
        double arousal = 0.35D;
        double dominance = 0.42D;
        double intimacy = Math.min(1.0D, environment.favorabilityTotal() / 100.0D);
        double conflict = 0.08D;
        double trust = 0.52D + intimacy * 0.24D;

        if (environment.maidHunger() > 0.70D) {
            valence -= 0.25D;
            arousal += 0.10D;
            conflict += 0.02D;
        }
        if (environment.maidEnergy() < 0.30D) {
            valence -= 0.20D;
            arousal -= 0.12D;
            dominance -= 0.04D;
        }
        if (environment.maidSleeping()) {
            arousal -= 0.40D;
        }
        if (!"none".equals(environment.nearbyThreat())) {
            valence -= 0.45D;
            arousal += 0.55D;
            dominance -= 0.12D;
            conflict += 0.08D;
        }

        switch (event.type()) {
            case "owner.talk", "owner.interact" -> {
                valence += 0.10D;
                intimacy += 0.10D;
                trust += 0.02D;
            }
            case "owner.feed" -> {
                valence += 0.35D;
                intimacy += 0.18D;
                trust += 0.05D;
                conflict -= 0.04D;
            }
            case "maid.attacked" -> {
                valence -= 0.60D;
                arousal += 0.70D;
                dominance -= 0.16D;
                conflict += 0.18D;
            }
            case "maid.sleep.enter" -> {
                valence += 0.08D;
                arousal -= 0.35D;
            }
            case "maid.sleep.exit" -> {
                valence += 0.12D;
                arousal += 0.15D;
            }
            case "world.hostile_detected" -> {
                valence -= 0.40D;
                arousal += 0.65D;
                dominance -= 0.10D;
                conflict += 0.08D;
            }
            default -> {
            }
        }

        valence = clampSigned(valence);
        arousal = clamp01(arousal);
        dominance = clamp01(dominance);
        intimacy = clamp01(intimacy);
        conflict = clamp01(conflict);
        trust = clamp01(trust);
        return new MoodState(
                valence,
                arousal,
                dominance,
                intimacy,
                conflict,
                trust,
                MoodState.inferStage(intimacy, conflict),
                MoodState.inferEmotion(valence, arousal, dominance)
        );
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
