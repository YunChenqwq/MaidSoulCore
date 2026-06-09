package com.yunchen.maidsoulcore.core.reasoning;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.context.ContextPack;
import com.yunchen.maidsoulcore.core.event.StructuredEvent;
import com.yunchen.maidsoulcore.core.llm.LlmClient;
import com.yunchen.maidsoulcore.core.llm.LlmMessage;
import com.yunchen.maidsoulcore.core.prompt.PromptCatalog;
import com.yunchen.maidsoulcore.core.prompt.PromptRenderer;
import com.yunchen.maidsoulcore.core.util.JsonExtractor;

import java.util.List;
import java.util.Map;

public final class PlannerRunner {
    private static final Gson GSON = new Gson();
    private final DialogueCoreConfig config;
    private final PromptCatalog prompts;
    private final LlmClient llm;

    public PlannerRunner(DialogueCoreConfig config, PromptCatalog prompts, LlmClient llm) {
        this.config = config;
        this.prompts = prompts;
        this.llm = llm;
    }

    public PlanDecision plan(ContextPack context, String identity) {
        String prompt = PromptRenderer.render(prompts.load("planner"), Map.of(
                "bot_name", config.botName,
                "identity", identity,
                "context", context == null ? "" : context.text()
        ));
        String raw = llm.chat(List.of(new LlmMessage("system", prompt)), config.llmTimeoutMillis).content();
        PlanDecision decision = parseDecision(raw);
        decision.raw_response = raw == null ? "" : raw;
        return decision;
    }

    private PlanDecision parseDecision(String raw) {
        String objectText = JsonExtractor.object(raw);
        JsonObject object = JsonParser.parseString(objectText).getAsJsonObject();
        if (object.has("tool")) {
            return fromToolCall(GSON.fromJson(object, PlannerToolCall.class));
        }
        PlanDecision decision = GSON.fromJson(objectText, PlanDecision.class);
        return decision == null ? new PlanDecision() : decision;
    }

    private PlanDecision fromToolCall(PlannerToolCall call) {
        PlanDecision decision = new PlanDecision();
        if (call == null) {
            return decision;
        }
        String tool = call.tool == null ? "" : call.tool.trim().toLowerCase(java.util.Locale.ROOT);
        decision.tool_name = tool;
        decision.tool_arguments = call.arguments == null ? "{}" : call.arguments.toString();
        decision.thought = call.thought == null ? "" : call.thought;
        decision.reason = decision.thought;
        decision.action = switch (tool) {
            case "reply", "query_memory", "wait", "no_action", "finish" -> tool;
            default -> "tool";
        };
        decision.target_message_id = call.argumentString("msg_id");
        decision.wait_seconds = call.argumentInt("seconds", 0);
        decision.memory_query = call.argumentString("query");
        decision.reference_info = call.argumentString("reference_info");
        if (call.arguments != null && call.arguments.has("event") && call.arguments.get("event").isJsonObject()) {
            decision.event = GSON.fromJson(call.arguments.get("event"), StructuredEvent.class);
            if (decision.event != null) {
                decision.event.normalize();
                decision.affect_event = decision.event.type;
                decision.affect_confidence = decision.event.confidence;
                decision.affect_evidence = decision.event.evidence;
            }
        }
        return decision;
    }
}
