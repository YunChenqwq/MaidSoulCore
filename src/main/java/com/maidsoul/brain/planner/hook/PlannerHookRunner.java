package com.maidsoul.brain.planner.hook;

import com.maidsoul.brain.reasoning.PlanDecision;
import com.maidsoul.brain.tool.ToolSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * planner hook 执行器。
 */
public final class PlannerHookRunner {
    private final PlannerHookRegistry registry;

    public PlannerHookRunner() {
        this(PlannerHookRegistry.global());
    }

    public PlannerHookRunner(PlannerHookRegistry registry) {
        this.registry = registry == null ? PlannerHookRegistry.global() : registry;
    }

    public BeforeOutcome beforeRequest(String requestKind, String sessionId, String context, List<ToolSpec> tools) {
        String currentContext = context == null ? "" : context;
        List<ToolSpec> currentTools = new ArrayList<>(tools == null ? List.of() : tools);
        List<String> appendedContext = new ArrayList<>();
        for (PlannerBeforeRequestHook hook : registry.beforeRequestHooks()) {
            PlannerBeforeRequestResult result;
            try {
                result = hook.beforeRequest(new PlannerRequestContext(requestKind, sessionId, currentContext, currentTools));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (result == null) {
                continue;
            }
            if (!result.additionalContext().isBlank()) {
                appendedContext.add(result.additionalContext());
            }
            currentTools.addAll(result.additionalTools());
        }
        if (!appendedContext.isEmpty()) {
            currentContext = currentContext + "\n\n外部信息参考：\n" + String.join("\n", appendedContext);
        }
        return new BeforeOutcome(currentContext, List.copyOf(currentTools));
    }

    public PlanDecision afterResponse(
            String requestKind,
            String sessionId,
            String rawResponse,
            PlanDecision decision,
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {
        PlanDecision current = decision;
        for (PlannerAfterResponseHook hook : registry.afterResponseHooks()) {
            PlannerAfterResponseResult result;
            try {
                result = hook.afterResponse(new PlannerResponseContext(
                        requestKind,
                        sessionId,
                        rawResponse,
                        current,
                        promptTokens,
                        completionTokens,
                        totalTokens
                ));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (result != null && result.decision() != null) {
                current = result.decision();
            }
        }
        return current;
    }

    public record BeforeOutcome(String context, List<ToolSpec> tools) {
    }
}
