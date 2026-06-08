package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.runtime.MaidSoulTimingDecision;
import com.maidsoulcore.forge.service.MaidSoulCognitionService;
import com.maidsoulcore.forge.service.MaidSoulEmotionService;
import com.maidsoulcore.forge.service.MaidSoulUnderstandingService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.util.List;
import java.util.Locale;

/**
 * LLM 话轮门控。
 *
 * <p>这层只判断“现在该不该说”，不生成可见回复。它对应人类聊天里的话轮权：
 * 对方可能还没说完就 wait；这轮没必要说就 no_action；确实该回应或处理再 continue。
 * idle、沉默续话、视角摘要这类柔性事件必须先过这里，不能再由固定文案直接开口。</p>
 */
public final class ConversationLlmTimingGateService {
    private ConversationLlmTimingGateService() {
    }

    public static MaidSoulTimingDecision decideProactive(EntityMaid maid,
                                                         SimulationMaiBotRuntimeConfig runtimeConfig,
                                                         String eventType,
                                                         String detail) {
        if (maid == null || runtimeConfig == null || !runtimeConfig.available()) {
            return MaidSoulTimingDecision.noReply("timing_config_unavailable");
        }
        if (!MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_ENABLED.get()) {
            return MaidSoulTimingDecision.continueNow("llm_timing_disabled");
        }
        try {
            SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
            String systemPrompt = buildSystemPrompt(maid, runtimeConfig);
            String userPrompt = buildUserPrompt(maid, eventType, detail);
            MaidSoulStateRegistry.echoFullDebugToOwnerChat(
                    maid,
                    "timing_gate_request",
                    "system:\n" + systemPrompt + "\n\nuser:\n" + userPrompt
            );
            String raw = client.completeText(
                    runtimeConfig.plannerTask(),
                    List.of(
                            new SimulationOpenAiChatClient.ChatMessage("system", systemPrompt),
                            new SimulationOpenAiChatClient.ChatMessage("user", userPrompt)
                    ),
                    MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_TIMEOUT_SECONDS.get(),
                    1
            );
            MaidSoulStateRegistry.echoFullDebugToOwnerChat(maid, "timing_gate_raw", raw);
            MaidSoulTimingDecision decision = parseDecision(raw);
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.timing_gate.llm",
                    decision.action() + " | " + decision.reason()
            );
            return decision;
        } catch (RuntimeException exception) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.timing_gate.llm.error",
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
            return fallbackFor(eventType);
        }
    }

    /**
     * 门控 prompt 保持和参考实现同一职责：只做节奏判断，不直接聊天。
     */
    private static String buildSystemPrompt(EntityMaid maid, SimulationMaiBotRuntimeConfig runtimeConfig) {
        String name = maid.getName().getString();
        String owner = maid.getOwner() == null ? "主人" : maid.getOwner().getName().getString();
        return """
                你的任务是分析当前聊天节奏，只决定 %s 下一步应当 continue、wait 还是 no_action。
                你不是 %s 本人，不要生成可见台词，不要安慰，不要角色扮演，只做话轮判断。

                %s 的人设：%s
                主人：%s

                动作含义：
                - continue：现在应该进入完整回复流程，可能对主人发出一条可见回复。
                - wait：主人可能还在操作、思考、打字、战斗或整理，不要立刻说话；稍后再检查。
                - no_action：本轮不需要说话，保持陪伴和记录即可。

                节奏规则：
                1. 沉默不是“发呆”，也不是“无视女仆”。不要因为短暂沉默就建议开口。
                2. idle、环境平静、视角普通变化，通常应该 wait 或 no_action。
                3. 如果上一轮刚刚聊完，至少给主人留出自然余韵；不要催问“你怎么不理我”。
                4. 危险、受击、任务失败、明确动作反馈，可以更倾向 continue。
                5. 如果情绪关系仍未修复，普通闲聊不应跳出来关心早餐、天气或随便换话题。
                6. 你只输出 JSON，不要输出其他文字。

                JSON 格式：
                {"action":"continue|wait|no_action","reason":"简短原因","wait_ms":30000}
                """.formatted(
                name,
                name,
                name,
                blankToDefault(runtimeConfig.personality(), "温柔、软萌、会陪伴主人的女仆"),
                owner
        );
    }

    private static String buildUserPrompt(EntityMaid maid, String eventType, String detail) {
        return """
                当前候选事件：
                - event_type=%s
                - detail=%s

                最近可见对话：
                %s

                最近 reference：
                %s

                脑内认知：
                %s

                情绪与关系：
                %s

                稳定理解：
                %s

                请只判断话轮节奏：
                - 如果这只是短暂沉默、普通 idle、普通视角摘要，优先 wait/no_action。
                - 如果决定 continue，后续会有另一个回复模型生成台词；你不要在这里写台词。
                - 不要把“主人安静”解释成“主人发呆/无视/不理我”。
                """.formatted(
                blankToDefault(eventType, "unknown"),
                blankToDefault(detail, "none"),
                ConversationJournalService.debugTail(maid, 10),
                ConversationContextSelector.referenceTail(maid, 8),
                MaidSoulCognitionService.promptBlock(maid),
                MaidSoulEmotionService.promptBlock(maid),
                MaidSoulUnderstandingService.promptBlock(maid)
        );
    }

    private static MaidSoulTimingDecision parseDecision(String rawText) {
        String raw = rawText == null ? "" : rawText.trim();
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonObject json = JsonParser.parseString(raw.substring(start, end + 1)).getAsJsonObject();
                String action = stringValue(json, "action", "no_action").toLowerCase(Locale.ROOT);
                String reason = stringValue(json, "reason", "llm_timing_gate");
                long waitMillis = Math.max(1_000L, longValue(json, "wait_ms", MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_DEFAULT_WAIT_MILLIS.get()));
                return switch (action) {
                    case "continue" -> MaidSoulTimingDecision.continueNow(reason);
                    case "wait" -> MaidSoulTimingDecision.waitFor(reason, waitMillis);
                    default -> MaidSoulTimingDecision.noReply(reason);
                };
            }
        } catch (RuntimeException ignored) {
            // 解析失败时走下面的文本兜底；不要因为门控格式问题让主动聊天刷屏。
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        if (lowered.contains("continue")) {
            return MaidSoulTimingDecision.continueNow("text_continue");
        }
        if (lowered.contains("wait")) {
            return MaidSoulTimingDecision.waitFor(
                    "text_wait",
                    MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_DEFAULT_WAIT_MILLIS.get()
            );
        }
        return MaidSoulTimingDecision.noReply("text_no_action");
    }

    private static MaidSoulTimingDecision fallbackFor(String eventType) {
        String type = eventType == null ? "" : eventType;
        if (type.startsWith("maid.attacked")
                || type.contains("task.failed")
                || type.contains("hostile")
                || type.contains("death")) {
            return MaidSoulTimingDecision.continueNow("timing_error_critical_continue");
        }
        if (type.startsWith("conversation.followup") || type.startsWith("maid.idle.")) {
            return MaidSoulTimingDecision.noReply("timing_error_soft_no_action");
        }
        return MaidSoulTimingDecision.waitFor(
                "timing_error_wait",
                MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_DEFAULT_WAIT_MILLIS.get()
        );
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : fallback;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String blankToDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
