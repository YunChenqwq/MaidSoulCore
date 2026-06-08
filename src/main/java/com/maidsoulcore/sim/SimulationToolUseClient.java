package com.maidsoulcore.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.tool.ToolCall;
import com.maidsoulcore.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于真实 LLM 的工具决策器。
 */
public final class SimulationToolUseClient {
    private final SimulationMaiBotRuntimeConfig runtimeConfig;
    private final SimulationOpenAiChatClient chatClient;
    private final SimulationPromptFactory promptFactory;

    public SimulationToolUseClient(
            SimulationMaiBotRuntimeConfig runtimeConfig,
            SimulationOpenAiChatClient chatClient,
            SimulationPromptFactory promptFactory
    ) {
        this.runtimeConfig = runtimeConfig;
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
    }

    /**
     * 让真实 tool_use 模型决定该调哪些工具。
     */
    public List<ToolCall> decide(
            MaidEvent event,
            BlackboardView blackboard,
            SimulationPlannerResult plannerResult,
            Collection<ToolDefinition> tools
    ) {
        if (plannerResult.route() == com.maidsoulcore.decision.DecisionRoute.DROP) {
            return List.of();
        }
        if (plannerResult.toolGoal().isBlank() && plannerResult.route() == com.maidsoulcore.decision.DecisionRoute.REPLY_ONLY) {
            return List.of();
        }
        JsonObject json = chatClient.completeJson(
                runtimeConfig.toolTask(),
                promptFactory.toolSystemPrompt(tools),
                promptFactory.toolUserPrompt(event, blackboard, plannerResult)
        );
        JsonArray toolCallsJson = json.getAsJsonArray("tool_calls");
        if (toolCallsJson == null || toolCallsJson.isEmpty()) {
            return List.of();
        }
        List<ToolCall> result = new ArrayList<>();
        for (JsonElement element : toolCallsJson) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String toolName = stringValue(object, "tool", "");
            if (toolName.isBlank()) {
                continue;
            }
            Map<String, Object> arguments = new LinkedHashMap<>();
            JsonObject argumentsObject = object.getAsJsonObject("arguments");
            if (argumentsObject != null) {
                for (Map.Entry<String, JsonElement> entry : argumentsObject.entrySet()) {
                    arguments.put(entry.getKey(), flatten(entry.getValue()));
                }
            }
            result.add(new ToolCall(toolName, arguments));
        }
        return result;
    }

    private Object flatten(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            }
            return element.getAsString();
        }
        return element.toString();
    }

    private String stringValue(JsonObject json, String key, String defaultValue) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
    }
}
