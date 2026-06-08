package com.maidsoulcore.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.decision.DecisionRoute;
import com.maidsoulcore.event.MaidEvent;

import java.util.List;

/**
 * 基于真实 LLM 的 planner 客户端。
 */
public final class SimulationPlannerClient {
    private final SimulationMaiBotRuntimeConfig runtimeConfig;
    private final SimulationOpenAiChatClient chatClient;
    private final SimulationPromptFactory promptFactory;

    public SimulationPlannerClient(
            SimulationMaiBotRuntimeConfig runtimeConfig,
            SimulationOpenAiChatClient chatClient,
            SimulationPromptFactory promptFactory
    ) {
        this.runtimeConfig = runtimeConfig;
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
    }

    /**
     * 调用真实 planner 模型生成结果。
     */
    public SimulationPlannerResult plan(MaidEvent event, BlackboardView blackboard, List<String> history, DecisionRoute heuristicRoute) {
        JsonObject json = chatClient.completeJson(
                runtimeConfig.plannerTask(),
                promptFactory.plannerSystemPrompt(),
                promptFactory.plannerUserPrompt(event, blackboard, history, heuristicRoute)
        );
        String routeName = stringValue(json, "route", heuristicRoute.name());
        DecisionRoute route;
        try {
            route = DecisionRoute.valueOf(routeName);
        } catch (Exception ignored) {
            route = heuristicRoute;
        }
        boolean shouldReply = booleanValue(json, "should_reply", route != DecisionRoute.DROP);
        return new SimulationPlannerResult(
                route,
                shouldReply,
                stringValue(json, "emotion", "平静"),
                stringValue(json, "intent", event.type()),
                stringValue(json, "plan_summary", "planner 未返回摘要"),
                stringValue(json, "tool_goal", ""),
                stringValue(json, "reply_focus", "回应当前事件"),
                json.toString()
        );
    }

    private String stringValue(JsonObject json, String key, String defaultValue) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
    }

    private boolean booleanValue(JsonObject json, String key, boolean defaultValue) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsBoolean();
    }
}
