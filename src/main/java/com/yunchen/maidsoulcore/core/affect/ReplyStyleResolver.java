package com.yunchen.maidsoulcore.core.affect;

import java.util.ArrayList;
import java.util.List;

public final class ReplyStyleResolver {
    public ReplyStyleGuide resolve(AffectProfile profile) {
        return resolve(profile.snapshot());
    }

    public ReplyStyleGuide resolve(AffectSnapshot snapshot) {
        List<String> topicBias = new ArrayList<>();
        List<String> avoid = new ArrayList<>(List.of("formal_report", "debug_terms", "system_explanation"));
        List<String> must = new ArrayList<>(List.of("respond_to_latest_owner_input"));

        String tone = "soft_companion";
        String repairMode = "none";
        String intimacyLevel = intimacyLevel(snapshot.intimacy());
        String reason = "balanced_affect";
        AffectiveEvent event = AffectiveEvent.fromId(snapshot.lastEvent());

        if (snapshot.hurtDebt() > 0.55D || snapshot.repairDebt() > 0.55D || event == AffectiveEvent.OWNER_ATTACK) {
            tone = "hurt_guarded";
            repairMode = "unresolved";
            topicBias.add("need_apology_or_comfort");
            avoid.add("sweet_reset");
            avoid.add("casual_topic_jump");
            must.add("acknowledge_hurt_or_fear");
            reason = "high_repair_debt";
        } else if (snapshot.relationshipStage() == RelationshipStage.COLD) {
            tone = "restrained_boundary";
            repairMode = "cold";
            topicBias.add("set_boundary");
            avoid.add("clingy_flirt");
            must.add("keep_distance");
            reason = "cold_stage";
        } else if (snapshot.relationshipStage() == RelationshipStage.REPAIRING
                || event == AffectiveEvent.FIGHT
                || snapshot.hurtDebt() > 0.16D
                || snapshot.repairDebt() > 0.16D) {
            tone = "soft_cautious";
            repairMode = event == AffectiveEvent.APOLOGY ? "accepting_apology" : "needs_repair";
            topicBias.add("repair_relationship");
            avoid.add("instant_forgive");
            must.add("keep_emotional_residue");
            reason = "repair_or_recent_conflict";
        } else if (event == AffectiveEvent.LONG_SILENCE) {
            tone = "relieved_attached";
            topicBias.add("ask_owner_if_tired");
            topicBias.add("light_reassurance");
            must.add("show_relief_without_blame");
            reason = "long_silence";
        } else if (event == AffectiveEvent.DANGER || (snapshot.arousal() > 0.76D && snapshot.valence() < 0.78D)) {
            tone = "protective_alert";
            topicBias.add("safety_check");
            topicBias.add("stay_close");
            avoid.add("ignore_danger");
            must.add("prioritize_safety");
            reason = "danger_or_tension";
        } else if (snapshot.memoryTriggerScore() > 0.28D) {
            tone = "nostalgic_clingy";
            topicBias.add("triggered_memory");
            topicBias.add("gentle_recall");
            must.add("make_memory_feel_natural");
            reason = "memory_trigger";
        } else if (event == AffectiveEvent.CARE && snapshot.arousal() < 0.68D) {
            tone = "calm_warm";
            topicBias.add("quiet_company");
            topicBias.add("care_owner");
            must.add("lower_energy");
            reason = "care_event";
        } else if (snapshot.valence() > 0.82D && snapshot.arousal() < 0.68D && snapshot.dominance() < 0.45D) {
            tone = "sweet_dependent";
            topicBias.add("stay_close");
            avoid.add("overexcited");
            must.add("soft_clingy");
            reason = "happy_dependent";
        } else if (snapshot.valence() > 0.82D && snapshot.arousal() >= 0.68D) {
            tone = "bright_clingy";
            topicBias.add("express_happiness");
            avoid.add("long_overacting");
            must.add("short_warm_energy");
            reason = "happy_excited";
        } else if (snapshot.relationshipStage() == RelationshipStage.SWEET
                || snapshot.relationshipStage() == RelationshipStage.PASSIONATE) {
            tone = "soft_clingy";
            topicBias.add("care_owner");
            topicBias.add("stay_close");
            must.add("gentle_initiative");
            reason = "high_intimacy";
        } else {
            topicBias.add("natural_company");
            must.add("keep_appropriate_distance");
        }

        return new ReplyStyleGuide(
                tone,
                snapshot.emotion().id(),
                repairMode,
                intimacyLevel,
                List.copyOf(topicBias),
                List.copyOf(avoid),
                List.copyOf(must),
                reason
        );
    }

    private static String intimacyLevel(double intimacy) {
        if (intimacy >= 0.82D) {
            return "very_high";
        }
        if (intimacy >= 0.64D) {
            return "high";
        }
        if (intimacy >= 0.45D) {
            return "medium";
        }
        return "low";
    }
}
