package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmResponse;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.prompt.PromptRenderer;
import com.maidsoul.brain.tool.BuiltinToolSet;
import com.maidsoul.brain.tool.ToolCall;

import java.util.List;
import java.util.Map;

/**
 * 独立节奏判断器。
 *
 * <p>开启独立节奏判断时，它只决定 continue、wait、no_action，不负责生成回复。</p>
 */
final class TimingGate {
    private final BrainConfig config;
    private final PromptCatalog prompts;
    private final LlmClient llm;

    TimingGate(BrainConfig config, PromptCatalog prompts, LlmClient llm) {
        this.config = config;
        this.prompts = prompts;
        this.llm = llm;
    }

    TimingDecision decide(String context) {
        return decide(context, null);
    }

    TimingDecision decide(String context, InterruptFlag interruptFlag) {
        String prompt = PromptRenderer.render(prompts.load("maisaka_timing_gate.prompt"), Map.of(
                "bot_name", config.identity().botName(),
                "identity", config.identity().renderPrompt(),
                "group_chat_attention_block", "",
                "timing_gate_wait_rule", "- wait：固定再等待一段时间，时间到后再重新判断。"
        ));
        LlmResponse response = llm.chatWithTools("timing_gate", List.of(
                ChatPayload.system(prompt),
                ChatPayload.user("当前聊天记录与现场：\n" + context)
        ), BuiltinToolSet.timingTools(), config.model().timingTimeoutMillis(), interruptFlag);
        if (response.toolCalls().isEmpty()) {
            return TimingDecision.continueNow(response.content().isBlank() ? "节奏判断没有工具调用，按 continue 处理。" : response.content());
        }
        ToolCall call = response.toolCalls().get(0);
        String action = normalize(call.functionName());
        Map<String, Object> args = call.arguments();
        String reason = stringArg(args, "reason", response.content());
        if ("wait".equals(action)) {
            return new TimingDecision("wait", intArg(args, "seconds", config.flow().defaultWaitSeconds()), reason);
        }
        if ("no_action".equals(action)) {
            return new TimingDecision("no_action", 0, reason);
        }
        return TimingDecision.continueNow(reason);
    }

    private static String normalize(String action) {
        return action == null ? "continue" : action.trim().toLowerCase();
    }

    private static String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
