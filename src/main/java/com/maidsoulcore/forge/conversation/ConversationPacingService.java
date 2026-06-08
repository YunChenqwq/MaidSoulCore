package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.service.MaidSoulChatSanitizerService;

import java.util.List;
import java.util.Locale;

/**
 * Fast local pacing rules for natural chat.
 * <p>
 * This is intentionally cheap: it decides whether the assistant should answer,
 * wait, or stay quiet without spending an extra model request.
 */
public final class ConversationPacingService {
    private ConversationPacingService() {
    }

    public static ConversationPacingDecision decide(List<LLMMessage> messages) {
        String latest = latestUserMessage(messages);
        if (latest.isBlank()) {
            return ConversationPacingDecision.noReply("empty_input");
        }
        String normalized = normalize(latest);
        if (matchesExplicitTrigger(normalized, MaidSoulCommonConfig.CONVERSATION_NO_REPLY_TRIGGERS.get())) {
            return ConversationPacingDecision.noReply("owner_requested_silence");
        }
        if (matchesExplicitTrigger(normalized, MaidSoulCommonConfig.CONVERSATION_FINISH_TRIGGERS.get())) {
            return ConversationPacingDecision.finish("owner_finished_turn");
        }
        if (looksIncomplete(latest, normalized)) {
            return ConversationPacingDecision.waitFor("owner_may_continue", MaidSoulCommonConfig.CONVERSATION_PACING_WAIT_MILLIS.get());
        }
        return ConversationPacingDecision.continueNow("ready");
    }

    public static String latestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null && message.role() == Role.USER && message.message() != null) {
                String sanitized = MaidSoulChatSanitizerService.sanitizeLatestUserMessage(message.message());
                if (!sanitized.isBlank()) {
                    return sanitized;
                }
            }
        }
        return "";
    }

    private static boolean looksIncomplete(String raw, String normalized) {
        String trimmed = raw.trim();
        if (trimmed.endsWith("，") || trimmed.endsWith(",") || trimmed.endsWith("、")) {
            return true;
        }
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            return true;
        }
        return matchesWaitTrigger(normalized, MaidSoulCommonConfig.CONVERSATION_WAIT_TRIGGERS.get());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, List<? extends String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesExplicitTrigger(String text, List<? extends String> keywords) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String key = keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (key.length() <= 2) {
                if (compact.equals(key)) {
                    return true;
                }
            } else if (compact.equals(key) || compact.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesWaitTrigger(String text, List<? extends String> keywords) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String key = keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (compact.equals(key) || compact.endsWith(key)) {
                return true;
            }
        }
        return false;
    }
}
