package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.conversation.ConversationInterruptService;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

/**
 * The cheap "should I react?" gate for the living-agent loop.
 * <p>
 * Perception and emotion may update often, but speech should only happen when
 * the current thought needs an outward reaction.
 */
public final class MaidSoulMindLoopService {
    private MaidSoulMindLoopService() {
    }

    public static MindDecision decideProactiveReaction(EntityMaid maid,
                                                       String eventType,
                                                       String detail,
                                                       EventPriority priority,
                                                       MaidSoulCognitionService.CognitiveFrame frame) {
        if (maid == null || eventType == null || eventType.isBlank()) {
            return MindDecision.silence("invalid_event");
        }
        if (!MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get()) {
            return MindDecision.silence("proactive_disabled");
        }
        if (priority == EventPriority.P0 || ConversationInterruptService.isInterruptEvent(eventType, priority)) {
            return MindDecision.speakNow("urgent_or_interrupt_event");
        }
        if (isConversationFollowupEvent(eventType)) {
            return MindDecision.speakNow("same_conversation_followup");
        }
        if (MaidSoulSpeechService.hasPendingSpeech(maid)) {
            return MindDecision.silence("speech_queue_active");
        }
        if (isOwnerChatProtected(maid, eventType)) {
            return MindDecision.silence("owner_chat_focus");
        }
        if (frame != null && frame.repeatCount() > 1) {
            if (priority == EventPriority.P2) {
                return MindDecision.silence("same_low_priority_topic");
            }
            if (eventType.startsWith("owner.view.") && frame.repeatCount() > 1) {
                return MindDecision.silence("same_vision_topic");
            }
        }
        if (eventType.startsWith("owner.view.idle")) {
            return MindDecision.silence("idle_vision_context_only");
        }
        if (eventType.startsWith("owner.view.focus_changed") && !MaidSoulCompanionService.isRecentOwnerChatActive(maid)) {
            return MindDecision.silence("focus_change_without_chat_context");
        }
        if (eventType.startsWith("maid.interact") || eventType.startsWith("maid.ate")) {
            return MindDecision.silence("small_positive_event_context_only");
        }
        if (frame != null && "continue_or_silence".equals(frame.reactionMode()) && priority != EventPriority.P1) {
            return MindDecision.silence("mind_loop_continue_silently");
        }
        return MindDecision.speakNow("reaction_useful");
    }

    private static boolean isOwnerChatProtected(EntityMaid maid, String eventType) {
        if (!MaidSoulCompanionService.isChatFocusActive(maid)) {
            return false;
        }
        if (isConversationFollowupEvent(eventType)) {
            return false;
        }
        return !eventType.startsWith("maid.attacked")
                && !eventType.contains("hostile")
                && !eventType.contains("failed")
                && !eventType.contains("missing")
                && !eventType.contains("not_allowed")
                && !eventType.contains("target_missing")
                && !eventType.startsWith("owner.command");
    }

    private static boolean isConversationFollowupEvent(String eventType) {
        return "conversation.followup".equals(eventType) || "maid.chat.followup".equals(eventType);
    }

    public record MindDecision(MindAction action, String reason) {
        public static MindDecision speakNow(String reason) {
            return new MindDecision(MindAction.SPEAK_NOW, reason);
        }

        public static MindDecision silence(String reason) {
            return new MindDecision(MindAction.SILENCE, reason);
        }

        public static MindDecision waitBriefly(String reason) {
            return new MindDecision(MindAction.WAIT_BRIEFLY, reason);
        }
    }

    public enum MindAction {
        SPEAK_NOW,
        WAIT_BRIEFLY,
        SILENCE
    }
}
