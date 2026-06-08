package com.yunchen.maidsoulcore.core.reasoning;

import com.google.gson.Gson;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.context.ContextPack;
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
        PlanDecision decision = GSON.fromJson(JsonExtractor.object(raw), PlanDecision.class);
        return decision == null ? new PlanDecision() : decision;
    }
}
