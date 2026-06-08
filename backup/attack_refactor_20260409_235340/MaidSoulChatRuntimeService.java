package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MaidSoulCore ??????????
 * <p>
 * ????? MaiBot ??????????
 * 1. ?? planner?????????????????????????
 * 2. ?? reply ???? planner ??????????????
 * 3. ??????????????????????????????
 */
public final class MaidSoulChatRuntimeService {
    /**
     * reply ??????????????????????????
     */
    private static final int REPLY_HISTORY_LIMIT = 6;

    private MaidSoulChatRuntimeService() {
    }

    /**
     * ?????????????
     */
    public static ChatTurnResult runChatTurn(EntityMaid maid, ChatClientInfo clientInfo, List<LLMMessage> messages) {
        MaidSoulCompanionService.markOwnerChat(maid);
        SimulationMaiBotRuntimeConfig runtimeConfig = SimulationMaiBotConfigLoader.loadFromDirectory(
                Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get())
        );
        if (!runtimeConfig.available()) {
            throw new IllegalStateException("MaiBot config unavailable: " + runtimeConfig.status());
        }

        SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
        String latestUserMessage = MaidSoulChatSanitizerService.sanitizeLatestUserMessage(extractLatestUserMessage(messages));
        List<String> recentHistory = MaidSoulChatSanitizerService.sanitizeHistoryForReply(messages, REPLY_HISTORY_LIMIT);
        List<String> recentTopics = MaidSoulTopicCooldownService.tailRecentTopics(maid, 3);

        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.input.latest", latestUserMessage.isBlank() ? "(empty)" : latestUserMessage);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.input.history", recentHistory.isEmpty() ? "(empty)" : String.join(" || ", recentHistory));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.input.topics", recentTopics.isEmpty() ? "(none)" : String.join(" || ", recentTopics));

        String plannerSystemPrompt = MaidSoulPromptService.buildPlannerSystemPrompt(maid, runtimeConfig);
        String plannerUserPrompt = MaidSoulPromptService.buildChatPlannerUserPrompt(
                maid,
                clientInfo,
                latestUserMessage,
                recentHistory,
                recentTopics
        ) + "\n\n" + MaidSoulTlmBridgeService.buildPlannerBridgeBlock(maid);
        JsonObject plannerJson = client.completeJson(runtimeConfig.plannerTask(), plannerSystemPrompt, plannerUserPrompt);

        PlannerDecision plannerDecision = PlannerDecision.fromJson(plannerJson);
        MaidSoulStateRegistry.record(maid, "maidsoul.chat.plan", plannerDecision.summary(), EventPriority.P1);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.plan", plannerDecision.summary());

        if (!plannerDecision.shouldReply()) {
            return new ChatTurnResult("I am here, Master.", plannerDecision);
        }

        List<SimulationOpenAiChatClient.ChatMessage> chatMessages = buildReplyMessages(
                maid,
                clientInfo,
                runtimeConfig,
                plannerDecision,
                recentHistory,
                recentTopics,
                latestUserMessage
        );
        String rawReply = client.completeText(runtimeConfig.replyTask(), chatMessages).trim();
        if (rawReply.isBlank()) {
            rawReply = "I am here, Master.";
        }
        String finalReply = MaidSoulReplyPostProcessor.process(rawReply, plannerDecision);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.reply.raw", rawReply);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.chat.reply.final", finalReply);
        return new ChatTurnResult(finalReply, plannerDecision);
    }

    /**
     * ?? reply ??????????????? role transcript ??????
     */
    private static List<SimulationOpenAiChatClient.ChatMessage> buildReplyMessages(
            EntityMaid maid,
            ChatClientInfo clientInfo,
            SimulationMaiBotRuntimeConfig runtimeConfig,
            PlannerDecision plannerDecision,
            List<String> recentHistory,
            List<String> recentTopics,
            String latestUserMessage
    ) {
        ArrayList<SimulationOpenAiChatClient.ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SimulationOpenAiChatClient.ChatMessage(
                "system",
                MaidSoulPromptService.buildTlmCustomSetting(maid, runtimeConfig)
        ));
        chatMessages.add(new SimulationOpenAiChatClient.ChatMessage(
                "system",
                MaidSoulPromptService.buildChatReplyAugmentPrompt(maid, clientInfo, plannerDecision, recentHistory, recentTopics)
                        + "\n\n" + MaidSoulTlmBridgeService.buildReplyBridgeBlock(maid)
        ));
        String compactHistory = buildCompactHistoryContext(recentHistory);
        if (!compactHistory.isBlank()) {
            chatMessages.add(new SimulationOpenAiChatClient.ChatMessage("user", compactHistory));
        }
        String latest = latestUserMessage;
        chatMessages.add(new SimulationOpenAiChatClient.ChatMessage(
                Role.USER.getId(),
                latest.isBlank() ? "The master is waiting for your natural reply." : latest
        ));
        return List.copyOf(chatMessages);
    }

    /**
     * ??????????????????????????????
     */
    private static String buildCompactHistoryContext(List<String> recentHistory) {
        if (recentHistory != null && !recentHistory.isEmpty()) {
            return "Recent chat summary:\n" + String.join("\n", recentHistory);
        }
        return "";
    }

    /**
     * ????????????? planner ???????
     */
    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message.role() == Role.USER && message.message() != null && !message.message().isBlank()) {
                return message.message();
            }
        }
        return "";
    }

    /**
     * ????????
     */
    public record ChatTurnResult(String reply, PlannerDecision plannerDecision) {
    }

    /**
     * Planner ???????
     * <p>
     * ??????????????????????
     * ??? MaiBot ?????????????????
     */
    public record PlannerDecision(
            boolean shouldReply,
            String emotion,
            String intent,
            String replyFocus,
            String planSummary,
            boolean askFollowUp,
            String followUpQuestion,
            String actionType,
            String actionValue,
            int targetEntityId
    ) {
        /**
         * ? planner JSON ?????
         */
        static PlannerDecision fromJson(JsonObject json) {
            return new PlannerDecision(
                    booleanValue(json, "should_reply", true),
                    stringValue(json, "emotion", "calm"),
                    stringValue(json, "intent", "chat"),
                    stringValue(json, "reply_focus", "reply to the owner naturally"),
                    stringValue(json, "plan_summary", "normal chat"),
                    booleanValue(json, "ask_follow_up", false),
                    stringValue(json, "follow_up_question", ""),
                    stringValue(json, "action_type", "NONE"),
                    stringValue(json, "action_value", ""),
                    intValue(json, "target_entity_id", -1)
            );
        }

        /**
         * ???? trace ????????????????
         */
        public String summary() {
            return "intent=" + intent
                    + ", emotion=" + emotion
                    + ", focus=" + replyFocus
                    + ", plan=" + planSummary
                    + ", askFollowUp=" + askFollowUp
                    + ", followUpQuestion=" + followUpQuestion
                    + ", action=" + actionType
                    + ", value=" + actionValue
                    + ", target=" + targetEntityId;
        }

        /**
         * ??????????????????
         */
        private static boolean booleanValue(JsonObject json, String key, boolean fallback) {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        }

        /**
         * ??????????????????
         */
        private static String stringValue(JsonObject json, String key, String fallback) {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        }

        /**
         * ?????????????????
         */
        private static int intValue(JsonObject json, String key, int fallback) {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        }
    }
}
