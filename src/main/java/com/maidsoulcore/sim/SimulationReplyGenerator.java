package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.event.MaidEvent;

import java.util.List;

/**
 * 基于真实 LLM 的回复器。
 */
public final class SimulationReplyGenerator {
    private final SimulationMaiBotRuntimeConfig runtimeConfig;
    private final SimulationOpenAiChatClient chatClient;
    private final SimulationPromptFactory promptFactory;

    public SimulationReplyGenerator(
            SimulationMaiBotRuntimeConfig runtimeConfig,
            SimulationOpenAiChatClient chatClient,
            SimulationPromptFactory promptFactory
    ) {
        this.runtimeConfig = runtimeConfig;
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
    }

    /**
     * 生成最终发给玩家的台词。
     */
    public String generate(
            MaidEvent event,
            BlackboardView blackboard,
            SimulationPlannerResult plannerResult,
            List<String> toolOutputs,
            List<String> history
    ) {
        String reply = chatClient.completeText(
                runtimeConfig.replyTask(),
                promptFactory.replySystemPrompt(),
                promptFactory.replyUserPrompt(event, blackboard, plannerResult, toolOutputs, history)
        );
        return reply.isBlank() ? "……我在。" : reply.trim();
    }
}
