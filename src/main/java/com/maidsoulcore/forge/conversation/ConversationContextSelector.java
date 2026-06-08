package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文选择器。
 *
 * <p>对齐的核心不是把所有东西塞进 prompt，而是稳定地选择：
 * 1. 私有 reference block：认知、情绪、视角、长期记忆、最近事件；
 * 2. 可见对话窗口：玩家和女仆真正说过的话；
 * 3. 当前轮收集到的玩家输入：永远放在最后，保证模型回答玩家最新意思。</p>
 */
public final class ConversationContextSelector {
    private ConversationContextSelector() {
    }

    public static List<SimulationOpenAiChatClient.ChatMessage> selectForReply(EntityMaid maid,
                                                                              String referencePrompt,
                                                                              List<String> collectedOwnerMessages) {
        ArrayList<SimulationOpenAiChatClient.ChatMessage> result = new ArrayList<>();
        if (referencePrompt != null && !referencePrompt.isBlank()) {
            result.add(new SimulationOpenAiChatClient.ChatMessage("system", referencePrompt));
        }
        if (maid != null) {
            for (RuntimeMessage message : ConversationJournalService.selectVisibleDialogue(
                    maid,
                    MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get()
            )) {
                if (!message.role().isBlank() && !message.content().isBlank()) {
                    result.add(new SimulationOpenAiChatClient.ChatMessage(message.role(), message.content()));
                }
            }
        }
        String latestBlock = ownerMessageBlock(collectedOwnerMessages);
        if (!latestBlock.isBlank()) {
            result.add(new SimulationOpenAiChatClient.ChatMessage("user", latestBlock));
        }
        return List.copyOf(result);
    }

    public static String referenceTail(EntityMaid maid, int limit) {
        if (maid == null) {
            return "none";
        }
        List<RuntimeMessage> references = ConversationJournalService.selectRecentReferences(maid, limit);
        if (references.isEmpty()) {
            return "none";
        }
        ArrayList<String> lines = new ArrayList<>();
        for (RuntimeMessage message : references) {
            lines.add("- " + message.source() + "/" + message.eventType() + ": " + message.content());
        }
        return String.join("\n", lines);
    }

    private static String ownerMessageBlock(List<String> collectedOwnerMessages) {
        if (collectedOwnerMessages == null || collectedOwnerMessages.isEmpty()) {
            return "";
        }
        ArrayList<String> cleaned = new ArrayList<>();
        for (String message : collectedOwnerMessages) {
            String text = clean(message);
            if (!text.isBlank()) {
                cleaned.add(text);
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        StringBuilder builder = new StringBuilder("The owner sent these messages in one continuous turn. Reply to the whole turn, especially the last line:\n");
        for (int index = 0; index < cleaned.size(); index++) {
            builder.append(index + 1).append(". ").append(cleaned.get(index)).append('\n');
        }
        return builder.toString().trim();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
