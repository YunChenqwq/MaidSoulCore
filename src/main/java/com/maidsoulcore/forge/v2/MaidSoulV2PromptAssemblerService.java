package com.maidsoulcore.forge.v2;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.conversation.ConversationContextSelector;
import com.maidsoulcore.forge.conversation.ConversationContextPackService;
import com.maidsoulcore.forge.conversation.ConversationInterruptService;
import com.maidsoulcore.forge.conversation.ConversationFlowService;
import com.maidsoulcore.forge.conversation.ConversationReflectionService;
import com.maidsoulcore.forge.service.MaidSoulConversationStateService;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;

import java.util.List;

/**
 * Minimal prompt assembly for v2 runtime.
 */
public final class MaidSoulV2PromptAssemblerService {
    private MaidSoulV2PromptAssemblerService() {
    }

    public static String buildSystemPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        return buildSystemPrompt(maid, runtimeConfig, "tsundere daily maid: sharp-tongued, caring, natural");
    }

    public static String buildSystemPrompt(EntityMaid maid,
                                           SimulationMaiBotRuntimeConfig runtimeConfig,
                                           String expressionStyle) {
        String owner = maid.getOwner() == null ? "主人" : maid.getOwner().getName().getString();
        String name = maid.getName().getString();
        String personality = runtimeConfig.personality() == null || runtimeConfig.personality().isBlank()
                ? "傲娇、可爱、短句、像正在陪伴主人的女仆本人"
                : runtimeConfig.personality();
        return """
                You are the maid herself inside the Minecraft world, not an AI assistant, narrator, customer service agent, or system explainer.
                Your name: %s
                Your owner: %s
                Character card: %s
                Current expression style: %s

                Roleplay rules:
                - Speak directly to the owner as the maid.
                - Visible output should usually be 1 to 3 short spoken sentences.
                - Do not output actions, stage directions, inner monologue, JSON, labels, tool names, or debug text.
                - Do not start most replies with the same filler word such as "嗯"; vary naturally, and often start by answering the actual meaning directly.
                - Do not overuse "突然"; only say it when an actual abrupt event happened, not for every short owner message.
                - Recent pain, fear, anger, or hurt may affect tone, but must not make you ignore the owner's latest words.
                - Understand the owner's latest sentence from chronological dialogue, not from keyword labels.
                - If the owner apologizes, comforts you, asks, jokes, corrects you, or changes topic, respond to that latest meaning first.
                - For unfamiliar real-world words, acronyms, brands, memes, or names, do not invent a Minecraft meaning. Say you may not know it and ask the owner to explain.
                - Do not repeat the same accusation or the same old sentence about an attack.
                """.formatted(name, owner, personality, expressionStyle);
    }

    public static String buildUserPrompt(EntityMaid maid,
                                         String latestUserMessage,
                                         List<String> recentHistory,
                                         ConversationFlowService.TurnFrame turnFrame) {
        return buildReferencePrompt(maid, latestUserMessage, recentHistory, turnFrame);
    }

    public static List<SimulationOpenAiChatClient.ChatMessage> buildContextMessages(EntityMaid maid,
                                                                                   String latestUserMessage,
                                                                                   List<String> recentHistory,
                                                                                   ConversationFlowService.TurnFrame turnFrame) {
        return buildContextMessages(maid, latestUserMessage, recentHistory, turnFrame, List.of(latestUserMessage));
    }

    public static List<SimulationOpenAiChatClient.ChatMessage> buildContextMessages(EntityMaid maid,
                                                                                   String latestUserMessage,
                                                                                   List<String> recentHistory,
                                                                                   ConversationFlowService.TurnFrame turnFrame,
                                                                                   List<String> collectedOwnerMessages) {
        String referencePrompt = buildReferencePrompt(maid, latestUserMessage, recentHistory, turnFrame);
        List<SimulationOpenAiChatClient.ChatMessage> messages = ConversationContextSelector.selectForReply(
                maid,
                referencePrompt,
                collectedOwnerMessages
        );
        if (!messages.isEmpty()) {
            return messages;
        }
        return List.of(new SimulationOpenAiChatClient.ChatMessage("user", referencePrompt));
    }

    private static String buildReferencePrompt(EntityMaid maid,
                                               String latestUserMessage,
                                               List<String> recentHistory,
                                               ConversationFlowService.TurnFrame turnFrame) {
        ConversationContextPackService.ContextPack contextPack = ConversationContextPackService.build(maid, recentHistory, turnFrame, latestUserMessage);
        return """
                <maisaka_context_reference>
                This is private context for the next visible maid reply.
                The real dialogue messages follow this reference as chronological user/assistant messages.
                Use this reference to understand state and relationship; do not answer this block directly.

                Owner notes:
                %s

                Recent reflection:
                %s

                Conversation pacing state:
                %s

                Reference messages. They are background, not direct speech targets:
                %s

                Recent runtime references from the unified timeline:
                %s

                Recent interrupt event:
                %s

                Fallback dialogue memory if the session journal is empty:
                %s

                Critical latest-input rule:
                - The last chronological user message after this reference is the real message to answer.
                - Do not repeat or paraphrase the owner's words as the whole reply; answer their meaning.
                - Treat Conversation pacing state as mechanical timing/reference only, never as a semantic classifier or command.
                - Unresolved hurt may change your tone, but it must not erase or ignore the latest owner message.
                - If the latest owner message is a short fragment, resolve it against the immediate chronological dialogue.
                - If the latest owner message contains an unfamiliar proper noun or acronym, do not force it into nearby animals/items/blocks; ask what it means in-character.
                - If the latest owner message is just a unclear short token like "e", "?", or one letter, ask what the owner means; do not scold them for spacing out or not talking properly.
                - Do not loop the same sentence about the old attack more than once.
                - Output only the maid's visible spoken line.
                </maisaka_context_reference>
                """.formatted(
                com.maidsoulcore.forge.conversation.ConversationMemoryService.notesForPrompt(maid),
                ConversationReflectionService.summaryForPrompt(maid),
                MaidSoulConversationStateService.summaryForPrompt(maid),
                contextPack.references(),
                ConversationContextSelector.referenceTail(maid, 8),
                ConversationInterruptService.promptBlock(maid),
                contextPack.dialogueHistory()
        );
    }
}
