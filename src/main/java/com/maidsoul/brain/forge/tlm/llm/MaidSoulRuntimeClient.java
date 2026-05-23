package com.maidsoul.brain.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;

import java.util.List;

public final class MaidSoulRuntimeClient implements LLMClient {
    private final MaidSoulRuntimeSite site;

    public MaidSoulRuntimeClient(MaidSoulRuntimeSite site) {
        this.site = site;
    }

    @Override
    public void chat(LLMCallback callback) {
        if (callback == null || callback.getMaid() == null) {
            return;
        }
        EntityMaid maid = callback.getMaid();
        String latestUserMessage = extractLatestUserMessage(callback.getMessages());
        if (!latestUserMessage.isBlank()) {
            MaidBrainRuntimeRegistry.receiveOwnerChat(maid, latestUserMessage);
        }
        callback.runOnServerThread(() -> maid.getChatBubbleManager().removeChatBubble(callback.getWaitingChatBubbleId()));
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null && message.role() == Role.USER && message.message() != null) {
                String text = sanitize(message.message());
                if (!text.isBlank() && isRealOwnerMessage(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private static boolean isRealOwnerMessage(String text) {
        String normalized = text.strip();
        return !normalized.startsWith("<context>")
                && !normalized.startsWith("Time:")
                && !normalized.startsWith("Weather:")
                && !normalized.contains("Nearby entities:");
    }

    private static String sanitize(String text) {
        return text == null ? "" : text
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
