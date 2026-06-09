package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.affect.AffectEventKind;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmResponse;
import com.maidsoul.brain.memory.MemoryType;
import com.maidsoul.brain.memory.StructuredMemoryEvent;
import com.maidsoul.brain.planner.hook.PlannerHookRunner;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.prompt.PromptRenderer;
import com.maidsoul.brain.tool.BuiltinToolSet;
import com.maidsoul.brain.tool.ToolCall;
import com.maidsoul.brain.tool.ToolSpec;

import java.util.List;
import java.util.Map;

/**
 * 主规划器。
 *
 * <p>规划器只负责决定动作，不直接生成可见台词。它通过工具调用选择 reply、wait、no_action、
 * finish 或 query_memory，和参考聊天核心的职责保持一致。</p>
 */
final class PlannerAgent {
    private final BrainConfig config;
    private final PromptCatalog prompts;
    private final LlmClient llm;
    private final PlannerHookRunner hookRunner = new PlannerHookRunner();
    private final boolean viewObservationAvailable;

    PlannerAgent(BrainConfig config, PromptCatalog prompts, LlmClient llm) {
        this(config, prompts, llm, false);
    }

    PlannerAgent(BrainConfig config, PromptCatalog prompts, LlmClient llm, boolean viewObservationAvailable) {
        this.config = config;
        this.prompts = prompts;
        this.llm = llm;
        this.viewObservationAvailable = viewObservationAvailable;
    }

    PlanDecision plan(String context) {
        return planWithMeta(context, null).decision();
    }

    PlannerResult planWithMeta(String context) {
        return planWithMeta(context, null);
    }

    PlannerResult planWithMeta(String context, InterruptFlag interruptFlag) {
        boolean mergedTiming = !config.flow().enableIndependentTimingGate();
        String promptName = mergedTiming ? "maisaka_chat_merged_timing.prompt" : "maisaka_chat.prompt";
        String prompt = PromptRenderer.render(prompts.load(promptName), Map.of(
                "bot_name", config.identity().botName(),
                "identity", config.identity().renderPrompt(),
                "group_chat_attention_block", "",
                "file_tools_section", buildFileToolsSection(),
                "timing_gate_wait_rule", "- wait：固定再等待一段时间，时间到后再重新判断。\n"
                        + "- 长期记忆工具已经接入，但必须克制使用。只有用户明确说“之前、上次、还记得吗、我说过、我的偏好”等依赖过去的信息，或回复必须依赖承诺/关系历史/长期偏好时，才调用 query_memory。即时情绪、沉默后的追问、普通接话只看最近聊天记录和长期状态参考。"
        ));
        List<ToolSpec> tools = BuiltinToolSet.plannerTools(mergedTiming, viewObservationAvailable).stream()
                .filter(tool -> config.memory().queryMemoryToolEnabled() || !"query_memory".equals(tool.name()))
                .toList();
        PlannerHookRunner.BeforeOutcome beforeOutcome = hookRunner.beforeRequest(
                "planner",
                "prototype-session",
                context,
                tools
        );
        LlmResponse response = llm.chatWithTools("planner", List.of(
                ChatPayload.system(prompt),
                ChatPayload.user("当前聊天记录与现场：\n" + beforeOutcome.context())
        ), beforeOutcome.tools(), config.model().plannerTimeoutMillis(), interruptFlag);
        PlanDecision decision;
        if (!response.toolCalls().isEmpty()) {
            decision = fromToolCall(response.toolCalls().get(0), response.content());
        } else {
            PlanDecision textAction = parseTextAction(response.content());
            decision = textAction != null ? textAction : fallbackFromText(response.content());
        }
        decision = hookRunner.afterResponse(
                "planner",
                "prototype-session",
                response.content(),
                decision,
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens()
        );
        return new PlannerResult(decision, response.model(), response.metricsSummary());
    }

    private String buildFileToolsSection() {
        if (!viewObservationAvailable) {
            return "";
        }
        return """
                - observe_view(reason)：当本轮回复依赖主人当前 Minecraft 第一人称画面、附近危险、可见实体/方块、地点氛围或现场状态时，可以调用这个工具获取文字视角摘要。
                  如果上下文里已经有最近可靠的 [视角摘要] 或 owner.view.vision_summary，可以先直接使用；如果没有可靠证据，不要编造当前画面。
                  这不是强制工具：普通情绪接话、寒暄、关系推进、仅依赖聊天文本的回应，直接使用 reply/wait/no_action。
                """;
    }

    private PlanDecision fromToolCall(ToolCall call, String reasoning) {
        String action = normalize(call.functionName());
        Map<String, Object> args = call.arguments();
        String reason = stringArg(args, "reason", reasoning);
        if ("reply".equals(action) && isNoSpeechText(reason)) {
            return new PlanDecision("no_action", "", 0, "规划器理由明确要求停止发言，覆盖错误的 reply 工具调用。", "");
        }
        String compactReason = compactReason(reason);
        AffectEvent affectEvent = affectEventFromArgs(args);
        StructuredMemoryEvent memoryEvent = memoryEventFromArgs(args);
        return switch (action) {
            case "reply" -> new PlanDecision(
                    "reply",
                    stringArg(args, "target_message_id", ""),
                    0,
                    compactReason,
                    stringArg(args, "reference_info", ""),
                    affectEvent,
                    memoryEvent
            );
            case "wait" -> new PlanDecision("wait", "", intArg(args, "seconds", config.flow().defaultWaitSeconds()), compactReason, "", affectEvent, memoryEvent);
            case "no_action" -> new PlanDecision("no_action", "", 0, compactReason, "", affectEvent, memoryEvent);
            case "finish" -> new PlanDecision("no_action", "", 0, compactReason, "");
            case "query_memory" -> new PlanDecision("query_memory", stringArg(args, "query", ""), 0, compactReason, "");
            case "observe_view" -> new PlanDecision("observe_view", "", 0, compactReason, "");
            case "play_pose" -> new PlanDecision("play_pose", "", 0, compactReason,
                    stringArg(args, "poseName", "") + "|duration=" + floatArg(args, "duration", 2.0f));
            case "play_animation" -> new PlanDecision("play_animation", "", 0, compactReason,
                    stringArg(args, "animName", ""));
            default -> PlanDecision.replyLatest("规划器调用了未知工具，按最新消息回复。");
        };
    }

    private static StructuredMemoryEvent memoryEventFromArgs(Map<String, Object> args) {
        String content = stringArg(args, "memory_event_content", "");
        if (content.isBlank()) {
            return null;
        }
        String typeText = stringArg(args, "memory_event_type", "DIALOGUE");
        MemoryType type;
        try {
            type = MemoryType.valueOf(typeText.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            type = MemoryType.DIALOGUE;
        }
        int importance = Math.max(1, Math.min(5, intArg(args, "memory_event_importance", 3)));
        return new StructuredMemoryEvent(
                type,
                stringArg(args, "memory_event_layer", ""),
                "planner",
                content,
                importance,
                splitTags(stringArg(args, "memory_event_tags", "")),
                "planner"
        );
    }

    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static AffectEvent affectEventFromArgs(Map<String, Object> args) {
        String kindText = stringArg(args, "affect_event_kind", "");
        if (kindText.isBlank()) {
            return null;
        }
        try {
            AffectEventKind kind = AffectEventKind.valueOf(kindText.trim().toUpperCase());
            int intensity = Math.max(0, Math.min(100, intArg(args, "affect_event_intensity", 50)));
            if (intensity <= 0) {
                return null;
            }
            return AffectEvent.of(kind, intensity, "planner", stringArg(args, "affect_event_note", ""));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 后备文本解析：LLM 不支持原生 function calling 时，从文本中提取动作调用。
     * 支持模式：play_pose("拥抱")、play_pose(拥抱)、play_animation("surprise1")
     */
    static PlanDecision parseTextAction(String content) {
        if (content == null) return null;
        String text = content.toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(play_pose|play_animation)\\s*\\(\\s*\"?([^\")\\s,]+)\"?[^)]*\\)"
        ).matcher(content);
        if (m.find()) {
            String action = m.group(1);
            String name = m.group(2).trim();
            if (!name.isEmpty()) {
                return new PlanDecision(action, "", 0,
                        "从文本中提取的工具调用: " + action + "(" + name + ")",
                        name);
            }
        }
        return null;
    }

    private PlanDecision fallbackFromText(String content) {
        String text = content == null ? "" : content.trim();
        if (isNoSpeechText(text)) {
            return new PlanDecision("no_action", "", 0, "规划器文本判断不发言。", "");
        }
        if (text.contains("wait")) {
            return new PlanDecision("wait", "", config.flow().defaultWaitSeconds(), "规划器文本提到等待。", "");
        }
        return new PlanDecision("reply", "", 0, text.isBlank() ? "没有工具调用，按最新消息回复。" : compactReason(text), "");
    }

    private static String compactReason(String reason) {
        // 规划器理由只是给回复器的方向提示，不能把长篇“局面分析”继续灌进回复阶段。
        String text = reason == null ? "" : reason
                .replace("**", "")
                .replace("##", "")
                .trim();
        String[] noisyPrefixes = {"分析：", "分析:", "建议：", "建议:", "当前局面：", "当前情况：", "判断：", "判断:"};
        boolean changed;
        do {
            changed = false;
            for (String prefix : noisyPrefixes) {
                if (text.startsWith(prefix)) {
                    text = text.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        } while (changed);
        int lineBreak = firstPositive(text.indexOf('\n'), text.indexOf('\r'));
        if (lineBreak >= 0) {
            text = text.substring(0, lineBreak).trim();
        }
        if (text.length() > 80) {
            text = text.substring(0, 80).trim();
        }
        return text.isBlank() ? "按最新消息自然回应。" : text;
    }

    private static int firstPositive(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static boolean isNoSpeechText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("no_action")
                || text.contains("no reply")
                || text.contains("不发言")
                || text.contains("不回应")
                || text.contains("不做回应")
                || text.contains("本轮不做")
                || text.contains("等待用户")
                || text.contains("等待对方")
                || text.contains("给用户空间")
                || text.contains("停止发言")
                || text.contains("停止继续发言")
                || text.contains("停止主动")
                || text.contains("停止刷屏")
                || text.contains("不要再")
                || text.contains("不宜再")
                || text.contains("不宜继续")
                || text.contains("不适合继续")
                || text.contains("不应该再")
                || text.contains("应该等待")
                || text.contains("应等待")
                || text.contains("继续发言只会")
                || text.contains("继续说只会")
                || text.contains("继续追问会")
                || text.contains("继续主动发言")
                || text.contains("无需继续")
                || text.contains("没有必要继续")
                || text.contains("把话语权")
                || text.contains("交还给用户")
                || text.contains("等用户主动")
                || text.contains("用户主动开口")
                || text.contains("刷屏")
                || text.contains("自言自语");
    }

    private static String normalize(String action) {
        return action == null ? "reply" : action.trim().toLowerCase();
    }

    private static String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static float floatArg(Map<String, Object> args, String key, float fallback) {
        Object value = args.get(key);
        if (value instanceof Number number) { return number.floatValue(); }
        try { return Float.parseFloat(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
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

    record PlannerResult(PlanDecision decision, String model, String metricsSummary) {
    }
}
