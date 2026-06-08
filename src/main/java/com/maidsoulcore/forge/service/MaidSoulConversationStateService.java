package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight conversation persona state for more natural replies.
 */
public final class MaidSoulConversationStateService {
    private static final int TOPIC_WINDOW = 4;
    private static final ConcurrentMap<UUID, ConversationState> STATES = new ConcurrentHashMap<>();

    private MaidSoulConversationStateService() {
    }

    public static void observeUserMessage(EntityMaid maid, String latestUserMessage) {
        if (maid == null || latestUserMessage == null || latestUserMessage.isBlank()) {
            return;
        }
        ConversationState state = stateOf(maid);
        state.ownerMoodHint = inferMood(latestUserMessage);
        state.lastUserMessage = latestUserMessage;
        state.lastUpdatedMillis = System.currentTimeMillis();
        MaidSoulEmotionService.observeOwnerMessage(maid, latestUserMessage);
    }

    public static void observePlannerDecision(EntityMaid maid, MaidSoulChatRuntimeService.PlannerDecision decision) {
        if (maid == null || decision == null) {
            return;
        }
        ConversationState state = stateOf(maid);
        if (decision.planSummary() != null && !decision.planSummary().isBlank()) {
            pushTopic(state, decision.planSummary());
        }
        if (decision.followUpQuestion() != null && !decision.followUpQuestion().isBlank()) {
            state.lastFollowUp = decision.followUpQuestion();
        }
        if (decision.actionType() != null && !"NONE".equalsIgnoreCase(decision.actionType())) {
            state.pendingPromise = decision.actionType() + (decision.actionValue().isBlank() ? "" : ":" + decision.actionValue());
        } else {
            state.pendingPromise = "";
        }
        state.lastUpdatedMillis = System.currentTimeMillis();
    }

    public static void observeAssistantReply(EntityMaid maid, String reply) {
        if (maid == null || reply == null || reply.isBlank()) {
            return;
        }
        ConversationState state = stateOf(maid);
        pushTopic(state, shortText(reply, 36));
        state.lastUpdatedMillis = System.currentTimeMillis();
    }

    public static String summaryForPrompt(EntityMaid maid) {
        ConversationState state = STATES.get(maid.getUUID());
        if (state == null) {
            return "mood=neutral | pending_promise=none | topics=none | follow_up=none";
        }
        String topics = state.recentTopics.isEmpty() ? "none" : String.join(" -> ", state.recentTopics);
        String pending = state.pendingPromise == null || state.pendingPromise.isBlank() ? "none" : state.pendingPromise;
        String followUp = state.lastFollowUp == null || state.lastFollowUp.isBlank() ? "none" : shortText(state.lastFollowUp, 32);
        return "owner_mood_hint=" + state.ownerMoodHint
                + " | pending_promise=" + pending
                + " | topics=" + topics
                + " | follow_up=" + followUp
                + " | emotion=" + MaidSoulEmotionService.debugSummary(maid);
    }

    private static ConversationState stateOf(EntityMaid maid) {
        return STATES.computeIfAbsent(maid.getUUID(), id -> new ConversationState());
    }

    private static void pushTopic(ConversationState state, String topic) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        String normalized = shortText(topic, 36);
        state.recentTopics.removeIf(existing -> existing.equalsIgnoreCase(normalized));
        state.recentTopics.addFirst(normalized);
        while (state.recentTopics.size() > TOPIC_WINDOW) {
            state.recentTopics.removeLast();
        }
    }

    private static String inferMood(String text) {
        if (text.contains("!") || text.contains("！")) {
            return "excited";
        }
        if (text.contains("?") || text.contains("？")) {
            return "curious";
        }
        if (text.contains("快") || text.contains("马上")) {
            return "urgent";
        }
        return "neutral";
    }

    private static String shortText(String text, int max) {
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() <= max) {
            return clean;
        }
        return clean.substring(0, max) + "...";
    }

    private static final class ConversationState {
        private String ownerMoodHint = "neutral";
        private String pendingPromise = "";
        private String lastFollowUp = "";
        private String lastUserMessage = "";
        private long lastUpdatedMillis = 0L;
        private final Deque<String> recentTopics = new ArrayDeque<>();
    }
}
