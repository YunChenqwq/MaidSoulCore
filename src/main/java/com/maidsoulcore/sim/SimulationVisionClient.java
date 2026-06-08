package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardView;

/**
 * 基于真实 VLM 槽位的视觉解释器。
 * <p>
 * 当前文字游戏没有真实图片，因此这里把“场景文字摘要”送给 VLM 槽位，
 * 让它承担“感知整理器”的角色，用来验证多模型分工。
 */
public final class SimulationVisionClient {
    private final SimulationMaiBotRuntimeConfig runtimeConfig;
    private final SimulationOpenAiChatClient chatClient;
    private final SimulationPromptFactory promptFactory;

    public SimulationVisionClient(
            SimulationMaiBotRuntimeConfig runtimeConfig,
            SimulationOpenAiChatClient chatClient,
            SimulationPromptFactory promptFactory
    ) {
        this.runtimeConfig = runtimeConfig;
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
    }

    /**
     * 将原始场景摘要转成更适合黑板写入的观察文本。
     */
    public String interpret(String rawSceneSummary, BlackboardView blackboard) {
        String text = chatClient.completeText(
                runtimeConfig.vlmTask(),
                promptFactory.visionSystemPrompt(),
                promptFactory.visionUserPrompt(rawSceneSummary, blackboard)
        );
        return text.isBlank() ? rawSceneSummary : text.trim();
    }
}
