package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime hot/cold path classifier.
 */
public final class MaidSoulRuntimeRouterService {
    private static final Set<String> HOT_PROACTIVE_EVENTS = Set.of(
            "maid.attacked",
            "maid.attacked.by_owner",
            "maid.action.follow",
            "maid.action.sit",
            "maid.action.schedule",
            "maid.action.task",
            "world.weather.changed",
            "world.time_phase.changed"
    );

    private MaidSoulRuntimeRouterService() {
    }

    public static ChatPath classifyChatPath(List<LLMMessage> messages) {
        String latest = extractLatestUserMessage(messages).toLowerCase(Locale.ROOT);
        if (latest.isBlank()) {
            return ChatPath.COLD_CHAT;
        }
        if (containsAny(latest, MaidSoulCommonConfig.CONVERSATION_HOT_COMMAND_KEYWORDS.get())) {
            return ChatPath.HOT_COMMAND;
        }
        return ChatPath.COLD_CHAT;
    }

    public static ProactivePath classifyProactivePath(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return ProactivePath.COLD_LLM;
        }
        if (HOT_PROACTIVE_EVENTS.contains(eventType)) {
            return ProactivePath.HOT_POOLED;
        }
        if (eventType.startsWith("maid.idle.")
                || eventType.startsWith("owner.view.")
                || eventType.startsWith("world.hostile_summary")) {
            return ProactivePath.COLD_LLM;
        }
        return ProactivePath.HOT_POOLED;
    }

    public static boolean supportsEventLinePool(String eventType) {
        return classifyProactivePath(eventType) == ProactivePath.HOT_POOLED;
    }

    private static boolean containsAny(String text, List<? extends String> keys) {
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null
                    && message.role() == Role.USER
                    && message.message() != null
                    && MaidSoulChatSanitizerService.isRealOwnerMessage(message.message())) {
                return MaidSoulChatSanitizerService.sanitizeLatestUserMessage(message.message());
            }
        }
        return "";
    }

    public enum ChatPath {
        HOT_COMMAND,
        COLD_CHAT
    }

    public enum ProactivePath {
        HOT_POOLED,
        COLD_LLM
    }
}
