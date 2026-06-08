package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cheap background reflection over the local chat memory.
 */
public final class ConversationReflectionService {
    private static final ConcurrentMap<UUID, ReflectionState> STATES = new ConcurrentHashMap<>();

    private ConversationReflectionService() {
    }

    public static void maybeReflect(EntityMaid maid) {
        if (!MaidSoulCommonConfig.CONVERSATION_REFLECTION_ENABLED.get()) {
            return;
        }
        if (maid == null) {
            return;
        }
        ReflectionState state = STATES.computeIfAbsent(maid.getUUID(), id -> new ReflectionState());
        state.observedLines++;
        if (state.observedLines - state.lastReflectionAt < MaidSoulCommonConfig.CONVERSATION_REFLECTION_EVERY_LINES.get()) {
            return;
        }
        List<String> recent = ConversationMemoryService.recentLines(maid, MaidSoulCommonConfig.CONVERSATION_REFLECTION_RECENT_LINES.get());
        if (recent.isEmpty()) {
            return;
        }
        state.lastReflectionAt = state.observedLines;
        String summary = summarize(recent);
        if (!summary.isBlank()) {
            state.lastSummary = summary;
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.conversation.reflection", summary);
        }
    }

    public static String summaryForPrompt(EntityMaid maid) {
        if (!MaidSoulCommonConfig.CONVERSATION_REFLECTION_ENABLED.get()) {
            return "none";
        }
        ReflectionState state = STATES.get(maid.getUUID());
        if (state == null || state.lastSummary == null || state.lastSummary.isBlank()) {
            return "none";
        }
        return state.lastSummary;
    }

    private static String summarize(List<String> recent) {
        String lastOwnerLine = "";
        String lastAssistantLine = "";
        for (int index = recent.size() - 1; index >= 0; index--) {
            String line = recent.get(index);
            if (lastOwnerLine.isBlank() && line.startsWith("主人:")) {
                lastOwnerLine = line;
            }
            if (lastAssistantLine.isBlank() && !line.startsWith("主人:")) {
                lastAssistantLine = line;
            }
        }
        if (lastOwnerLine.isBlank() && lastAssistantLine.isBlank()) {
            return "";
        }
        return "recent_focus="
                + trimPrefix(lastOwnerLine, 60)
                + " | last_reply="
                + trimPrefix(lastAssistantLine, 60);
    }

    private static String trimPrefix(String text, int max) {
        if (text == null || text.isBlank()) {
            return "none";
        }
        String cleaned = text.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }

    private static final class ReflectionState {
        private int observedLines;
        private int lastReflectionAt;
        private String lastSummary = "";
    }
}
