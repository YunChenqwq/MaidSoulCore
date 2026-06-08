package com.maidsoulcore.forge.v2;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.conversation.ConversationFlowService;
import com.maidsoulcore.forge.conversation.ConversationReplyEffectService;
import com.maidsoulcore.forge.conversation.ConversationReplyGuardService;
import com.maidsoulcore.forge.conversation.ConversationStyleService;
import com.maidsoulcore.forge.conversation.ConversationTimingGateService;
import com.maidsoulcore.forge.service.MaidSoulChatRuntimeService;
import com.maidsoulcore.forge.service.MaidSoulChatSanitizerService;
import com.maidsoulcore.forge.service.MaidSoulConversationStateService;
import com.maidsoulcore.forge.service.MaidSoulReplyPostProcessor;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天心流 replyer。
 *
 * <p>这个类只负责生成自然回复。玩家输入、主动事件、打断、过期丢弃、
 * 以及聊天/人生记忆写入，都由外层统一 runtime 处理。</p>
 */
public final class MaidSoulHeartflowChatService {
    private MaidSoulHeartflowChatService() {
    }

    public static ChatTurnResult chat(EntityMaid maid, List<LLMMessage> messages) {
        return chat(maid, messages, List.of(extractLatestUserMessage(messages)));
    }

    public static ChatTurnResult chat(EntityMaid maid,
                                      List<LLMMessage> messages,
                                      List<String> collectedOwnerMessages) {
        long startedNanos = System.nanoTime();
        SimulationMaiBotRuntimeConfig runtimeConfig = SimulationMaiBotConfigLoader.loadFromDirectory(
                Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get())
        );
        if (!runtimeConfig.available()) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.heartflow.decision", "fallback | config_unavailable:" + runtimeConfig.status());
            return ChatTurnResult.reply(MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get(), "config_unavailable");
        }

        // TLM callback 里的 messages 可能包含旧历史或被原版链路整理过的内容。
        // 统一 runtime 已经把本轮主人输入收集到 collectedOwnerMessages 里，
        // 所以心流判断、节奏门、风格选择必须以这里的最后一句为准。
        String latestUserMessage = latestCollectedOwnerMessage(collectedOwnerMessages);
        if (latestUserMessage.isBlank()) {
            latestUserMessage = MaidSoulChatSanitizerService.sanitizeLatestUserMessage(extractLatestUserMessage(messages));
        }
        List<String> recentHistory = MaidSoulChatSanitizerService.sanitizeHistoryForReply(
                messages,
                MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get()
        );

        // 这里的状态只影响当前心流，不负责把玩家话写进长期时间线。
        MaidSoulConversationStateService.observeUserMessage(maid, latestUserMessage);
        ConversationFlowService.TurnFrame turnFrame = ConversationFlowService.beginOwnerTurn(maid, latestUserMessage);

        ConversationTimingGateService.TimingDecision timingDecision =
                ConversationTimingGateService.decide(maid, latestUserMessage, turnFrame);
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.timing_gate",
                timingDecision.action() + " | " + timingDecision.reason()
        );
        if (timingDecision.action() == ConversationTimingGateService.TimingAction.WAIT) {
            // 外层 runtime 已经做过 quiet/wait；replyer 内部不再二次等待。
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.timing_gate.demote", "wait_as_continue | " + timingDecision.reason());
        }
        if (timingDecision.action() == ConversationTimingGateService.TimingAction.NO_ACTION
                || timingDecision.action() == ConversationTimingGateService.TimingAction.FINISH) {
            // 不在 replyer 内吞掉玩家消息；真正的 no_reply/finish 由外层 runtime 决定。
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.timing_gate.demote", "silent_as_continue | " + timingDecision.reason());
        }

        String expressionStyle = ConversationStyleService.chooseStyle(maid, latestUserMessage);
        SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
        ArrayList<SimulationOpenAiChatClient.ChatMessage> chatMessages = buildReplyMessages(
                maid,
                runtimeConfig,
                latestUserMessage,
                recentHistory,
                turnFrame,
                expressionStyle,
                collectedOwnerMessages
        );
        MaidSoulStateRegistry.echoFullDebugToOwnerChat(
                maid,
                "owner_reply_request",
                "latest_owner=" + latestUserMessage
                        + "\ncollected_owner=" + String.join(" / ", collectedOwnerMessages)
                        + "\nrecent_history=" + String.join("\n", recentHistory)
                        + "\nmessages:\n" + renderMessages(chatMessages)
        );

        String finalReply = generatePolishedReply(client, runtimeConfig, maid, latestUserMessage, chatMessages);
        ConversationReplyEffectService.EffectDecision effectDecision =
                ConversationReplyEffectService.evaluate(maid, latestUserMessage, turnFrame, finalReply);
        ConversationReplyEffectService.trace(maid, effectDecision);

        int modelCalls = 1;
        if (effectDecision.action() == ConversationReplyEffectService.EffectAction.RETRY
                && MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED.get()) {
            ArrayList<SimulationOpenAiChatClient.ChatMessage> retryMessages = new ArrayList<>(chatMessages);
            retryMessages.add(new SimulationOpenAiChatClient.ChatMessage("assistant", finalReply));
            retryMessages.add(new SimulationOpenAiChatClient.ChatMessage(
                    "user",
                    ConversationReplyEffectService.buildRetryInstruction(
                            maid,
                            latestUserMessage,
                            turnFrame,
                            finalReply,
                            effectDecision.reason()
                    )
            ));
            finalReply = generatePolishedReply(client, runtimeConfig, maid, latestUserMessage, retryMessages);
            modelCalls++;
        } else if (effectDecision.action() == ConversationReplyEffectService.EffectAction.RETRY) {
            // 游戏内聊天优先保证响应速度。质量检查发现问题时记录原因，
            // 但默认不再追加第二次 LLM 请求，避免一次聊天变成两次网络等待。
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.reply_effect.second_pass.skip",
                    effectDecision.reason()
            );
        }

        // 回复生成完成后，只更新心流节奏。真正的 history/memory 写入在 runtime 出口完成。
        ConversationFlowService.observeAssistantReply(maid, finalReply);
        MaidSoulConversationStateService.observeAssistantReply(maid, finalReply);
        MaidSoulStateRegistry.echoTraceToOwnerChat(
                maid,
                "maidsoul.heartflow.timing",
                "model_calls=" + modelCalls + " | elapsed_ms=" + elapsedMillis(startedNanos)
        );
        return ChatTurnResult.reply(finalReply, "reply");
    }

    private static ArrayList<SimulationOpenAiChatClient.ChatMessage> buildReplyMessages(EntityMaid maid,
                                                                                       SimulationMaiBotRuntimeConfig runtimeConfig,
                                                                                       String latestUserMessage,
                                                                                       List<String> recentHistory,
                                                                                       ConversationFlowService.TurnFrame turnFrame,
                                                                                       String expressionStyle,
                                                                                       List<String> collectedOwnerMessages) {
        ArrayList<SimulationOpenAiChatClient.ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SimulationOpenAiChatClient.ChatMessage(
                "system",
                MaidSoulV2PromptAssemblerService.buildSystemPrompt(maid, runtimeConfig, expressionStyle)
        ));
        chatMessages.addAll(MaidSoulV2PromptAssemblerService.buildContextMessages(
                maid,
                latestUserMessage,
                recentHistory,
                turnFrame,
                collectedOwnerMessages
        ));
        return chatMessages;
    }

    private static String generatePolishedReply(SimulationOpenAiChatClient client,
                                                SimulationMaiBotRuntimeConfig runtimeConfig,
                                                EntityMaid maid,
                                                String latestUserMessage,
                                                List<SimulationOpenAiChatClient.ChatMessage> chatMessages) {
        String raw = client.completeText(
                runtimeConfig.replyTask(),
                List.copyOf(chatMessages),
                MaidSoulCommonConfig.CONVERSATION_MODEL_TIMEOUT_SECONDS.get(),
                MaidSoulCommonConfig.CONVERSATION_MODEL_MAX_RETRY.get()
        );
        MaidSoulStateRegistry.echoFullDebugToOwnerChat(maid, "owner_reply_raw", raw);
        String sanitized = MaidSoulChatSanitizerService.sanitizeModelOutput(raw == null ? "" : raw.trim());
        if (sanitized.isBlank()) {
            sanitized = MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get();
        }
        MaidSoulChatRuntimeService.PlannerDecision noopDecision = new MaidSoulChatRuntimeService.PlannerDecision(
                true,
                "calm",
                "chat",
                "natural reply",
                "heartflow_reply",
                false,
                "",
                "NONE",
                "",
                -1
        );
        return ConversationReplyGuardService.polish(
                maid,
                MaidSoulReplyPostProcessor.process(sanitized, noopDecision),
                latestUserMessage
        );
    }

    private static String renderMessages(List<SimulationOpenAiChatClient.ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            SimulationOpenAiChatClient.ChatMessage message = messages.get(index);
            builder.append("[").append(index).append("] ")
                    .append(message.role())
                    .append(":\n")
                    .append(message.content())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null
                    && message.message() != null
                    && !message.message().isBlank()
                    && message.role() != null
                    && "user".equalsIgnoreCase(message.role().getId())
                    && MaidSoulChatSanitizerService.isRealOwnerMessage(message.message())) {
                return message.message();
            }
        }
        return "";
    }

    private static String latestCollectedOwnerMessage(List<String> collectedOwnerMessages) {
        if (collectedOwnerMessages == null || collectedOwnerMessages.isEmpty()) {
            return "";
        }
        for (int index = collectedOwnerMessages.size() - 1; index >= 0; index--) {
            String message = MaidSoulChatSanitizerService.sanitizeLatestUserMessage(collectedOwnerMessages.get(index));
            if (!message.isBlank()) {
                return message;
            }
        }
        return "";
    }

    public record ChatTurnResult(boolean silent, boolean waiting, String reply, String reason, long waitMillis) {
        public static ChatTurnResult reply(String reply, String reason) {
            return new ChatTurnResult(false, false, reply == null ? "" : reply, reason, 0L);
        }

        public static ChatTurnResult silent(String reason) {
            return new ChatTurnResult(true, false, "", reason, 0L);
        }

        public static ChatTurnResult waitFor(String reason, long waitMillis) {
            return new ChatTurnResult(false, true, "", reason, waitMillis);
        }
    }
}
