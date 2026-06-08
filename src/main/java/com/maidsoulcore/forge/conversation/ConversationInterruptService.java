package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Short lived interruption state for events that should take over the current
 * conversational topic, such as combat damage or failed actions.
 */
public final class ConversationInterruptService {
    private static final ConcurrentMap<UUID, InterruptState> ACTIVE_INTERRUPTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, LastInterrupt> LAST_INTERRUPTS = new ConcurrentHashMap<>();

    private ConversationInterruptService() {
    }

    public static boolean record(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (maid == null || eventType == null || !MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ENABLED.get()) {
            return false;
        }
        if (!isInterruptEvent(eventType, priority)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = eventType + "|" + normalize(detail);
        LastInterrupt previous = LAST_INTERRUPTS.get(maid.getUUID());
        long cooldownMillis = MaidSoulCommonConfig.CONVERSATION_INTERRUPT_COOLDOWN_MILLIS.get();
        if (previous != null && previous.key().equals(key) && now - previous.millis() < cooldownMillis) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.conversation.interrupt.skip", eventType);
            return false;
        }
        long expiresAt = now + MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ACTIVE_SECONDS.get() * 1000L;
        ACTIVE_INTERRUPTS.put(maid.getUUID(), new InterruptState(eventType, normalize(detail), priority, now, expiresAt));
        LAST_INTERRUPTS.put(maid.getUUID(), new LastInterrupt(key, now));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.conversation.interrupt", eventType + " | " + normalize(detail));
        return true;
    }

    public static boolean hasActiveInterrupt(EntityMaid maid) {
        return activeState(maid) != null;
    }

    public static String promptBlock(EntityMaid maid) {
        InterruptState state = activeState(maid);
        if (state == null) {
            return "(none)";
        }
        return """
                immediate_event=%s
                priority=%s
                detail=%s
                instruction=First acknowledge this immediate event naturally. Do not continue an older topic unless the owner explicitly returns to it.
                """.formatted(state.eventType(), state.priority(), state.detail().isBlank() ? "(none)" : state.detail());
    }

    public static void clear(EntityMaid maid, String reason) {
        if (maid == null) {
            return;
        }
        InterruptState removed = ACTIVE_INTERRUPTS.remove(maid.getUUID());
        if (removed != null) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.conversation.interrupt.clear", reason);
        }
    }

    public static boolean isInterruptEvent(String eventType, EventPriority priority) {
        if (priority == EventPriority.P0) {
            return true;
        }
        String normalized = eventType == null ? "" : eventType.toLowerCase(Locale.ROOT);
        for (String pattern : MaidSoulCommonConfig.CONVERSATION_INTERRUPT_EVENT_TYPES.get()) {
            String candidate = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.endsWith(".*")) {
                String prefix = candidate.substring(0, candidate.length() - 1);
                if (normalized.startsWith(prefix)) {
                    return true;
                }
            } else if (candidate.endsWith("*")) {
                String prefix = candidate.substring(0, candidate.length() - 1);
                if (normalized.startsWith(prefix)) {
                    return true;
                }
            } else if (normalized.equals(candidate)) {
                return true;
            }
        }
        return normalized.contains("failed")
                || normalized.contains("missing")
                || normalized.contains("not_allowed")
                || normalized.contains("target_missing");
    }

    private static InterruptState activeState(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        InterruptState state = ACTIVE_INTERRUPTS.get(maid.getUUID());
        if (state == null) {
            return null;
        }
        if (System.currentTimeMillis() > state.expiresAtMillis()) {
            ACTIVE_INTERRUPTS.remove(maid.getUUID(), state);
            return null;
        }
        return state;
    }

    private static String normalize(String detail) {
        if (detail == null) {
            return "";
        }
        String trimmed = detail.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }

    private record InterruptState(
            String eventType,
            String detail,
            EventPriority priority,
            long createdMillis,
            long expiresAtMillis
    ) {
    }

    private record LastInterrupt(String key, long millis) {
    }
}
