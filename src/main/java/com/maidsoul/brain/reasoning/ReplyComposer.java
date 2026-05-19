package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.expression.ExpressionSelectionResult;
import com.maidsoul.brain.expression.ExpressionSelector;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.prompt.PromptRenderer;
import com.maidsoul.brain.reply.hook.ReplyerHookContext;
import com.maidsoul.brain.reply.hook.ReplyerHookRunner;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class ReplyComposer {
    private final BrainConfig config;
    private final PromptCatalog prompts;
    private final LlmClient llm;
    private final ReplyPostProcessor postProcessor = new ReplyPostProcessor();
    private final RiskFallbackPolicy fallbackPolicy = new RiskFallbackPolicy();
    private final ReplyerHookRunner hookRunner = new ReplyerHookRunner();
    private final ExpressionSelector expressionSelector = new ExpressionSelector();
    private static final int REPLYER_MAX_HOOK_RETRIES = 3;

    ReplyComposer(BrainConfig config, PromptCatalog prompts, LlmClient llm) {
        this.config = config;
        this.prompts = prompts;
        this.llm = llm;
    }

    String compose(String context, ChatMessage target, String replyReason, String referenceInfo) {
        return composeInternal(context, target, replyReason, referenceInfo, null);
    }

    String composeStreaming(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer) {
        return composeInternal(context, target, replyReason, referenceInfo, deltaConsumer);
    }

    LlmReply composeStreamingWithMeta(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer) {
        return composeInternalWithMeta(context, target, replyReason, referenceInfo, deltaConsumer, null);
    }

    LlmReply composeStreamingWithMeta(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
        return composeInternalWithMeta(context, target, replyReason, referenceInfo, deltaConsumer, interruptFlag);
    }

    LlmReply composeWithMeta(String context, ChatMessage target, String replyReason, String referenceInfo, InterruptFlag interruptFlag) {
        return composeInternalWithMeta(context, target, replyReason, referenceInfo, null, interruptFlag);
    }

    private String composeInternal(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer) {
        return composeInternalWithMeta(context, target, replyReason, referenceInfo, deltaConsumer).content();
    }

    private LlmReply composeInternalWithMeta(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer) {
        return composeInternalWithMeta(context, target, replyReason, referenceInfo, deltaConsumer, null);
    }

    private LlmReply composeInternalWithMeta(String context, ChatMessage target, String replyReason, String referenceInfo, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
        String targetText = target == null
                ? "没有新的用户发言目标。这是主动候选事件触发的回复，请承接最近聊天氛围，不要把历史用户消息当成刚刚又发了一遍。"
                : "msg_id=" + target.id() + "\n用户=" + target.speaker() + "\n内容=" + target.content();
        String systemPrompt = PromptRenderer.render(prompts.load("maisaka_replyer.prompt"), Map.of(
                "identity", config.identity().renderPrompt(),
                "reply_style", config.identity().replyStyle(),
                "group_chat_attention_block", "",
                "replyer_at_block", ""
        ));
        ExpressionSelectionResult expressionSelection = expressionSelector.selectForReply(
                "prototype-session",
                List.of(),
                target,
                replyReason
        );
        String effectiveReferenceInfo = joinReference(referenceInfo, expressionSelection.expressionHabits());
        String finalUserMessage = buildFinalUserMessage(targetText, replyReason, effectiveReferenceInfo, context);
        List<ChatPayload> messages = List.of(
                ChatPayload.system(systemPrompt),
                ChatPayload.user(finalUserMessage)
        );
        ReplyBuffer replyBuffer = new ReplyBuffer();
        var response = deltaConsumer == null
                ? llm.chatStream("replyer", messages, config.model().replyerTimeoutMillis(), replyBuffer::append, interruptFlag)
                : llm.chatStream("replyer", messages, config.model().replyerTimeoutMillis(), delta -> {
                    replyBuffer.append(delta);
                    // 纯流式模式：delta 交给运行时的可见分句发送器，尽快形成首句输出。
                    deltaConsumer.accept(delta);
                }, interruptFlag);
        String raw = response.content().isBlank() ? replyBuffer.content() : response.content();
        String cleaned = postProcessor.process(raw);
        if (cleaned.isBlank()) {
            return new LlmReply(fallbackPolicy.fallback(target, context, ""), response.model(), response.metricsSummary());
        }
        if (deltaConsumer != null) {
            // 可见流式已经边生成边发出，不能在完整生成后再做重试或兜底替换。
            // 这里只返回完整文本用于效果追踪和日志。
            return new LlmReply(cleaned, response.model(), response.metricsSummary());
        }
        return applyAfterResponseHooks(
                cleaned,
                response.model(),
                response.metricsSummary(),
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens(),
                target,
                targetText,
                replyReason,
                effectiveReferenceInfo,
                context,
                systemPrompt,
                interruptFlag
        );
    }

    private String buildFinalUserMessage(String targetText, String replyReason, String referenceInfo, String context) {
        // 回复阶段的具体约束放在 prompt 模板里，Java 只负责把运行时上下文填进去。
        // 这样更换角色或调整说话风格时，不需要在代码里继续堆“针对某一句话”的补丁。
        return PromptRenderer.render(prompts.load("replyer_user.prompt"), Map.of(
                "target_text", targetText,
                "reply_reason_block", block("【回复信息参考】\n【最新推理】", replyReason),
                "reference_info_block", block("【参考信息】", referenceInfo),
                "context", context == null ? "" : context
        ));
    }

    private String block(String title, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return title + "\n" + content + "\n\n";
    }

    private static String joinReference(String original, String extra) {
        String base = original == null ? "" : original.trim();
        String addition = extra == null ? "" : extra.trim();
        if (base.isBlank()) {
            return addition;
        }
        if (addition.isBlank()) {
            return base;
        }
        return base + "\n" + addition;
    }

    private LlmReply applyAfterResponseHooks(
            String initialReply,
            String initialModel,
            String initialMetrics,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            ChatMessage target,
            String targetText,
            String replyReason,
            String referenceInfo,
            String context,
            String systemPrompt,
            InterruptFlag interruptFlag
    ) {
        String currentReply = initialReply == null ? "" : initialReply.trim();
        String currentModel = initialModel == null ? "" : initialModel;
        String currentMetrics = initialMetrics == null ? "" : initialMetrics;
        String currentReference = referenceInfo == null ? "" : referenceInfo;
        int retryCount = 0;
        while (true) {
            ReplyerHookRunner.HookOutcome outcome = hookRunner.invoke(new ReplyerHookContext(
                    currentReply,
                    "prototype-session",
                    "maisaka_replyer",
                    retryCount + 1,
                    retryCount,
                    REPLYER_MAX_HOOK_RETRIES,
                    target == null ? "" : target.id(),
                    replyReason == null ? "" : replyReason,
                    currentReference,
                    promptTokens,
                    completionTokens,
                    totalTokens
            ));
            if (outcome.response() != null && !outcome.response().isBlank()) {
                currentReply = outcome.response().trim();
            }
            if (!outcome.retry() || retryCount >= REPLYER_MAX_HOOK_RETRIES) {
                return new LlmReply(currentReply, currentModel, currentMetrics);
            }

            String retryConstraint = buildRetryConstraint(outcome.retryReason(), currentReply);
            currentReference = joinReference(currentReference, retryConstraint);
            String retryUserMessage = buildFinalUserMessage(targetText, replyReason, currentReference, context);
            ReplyBuffer retryBuffer = new ReplyBuffer();
            var retryResponse = llm.chatStream("replyer", List.of(
                    ChatPayload.system(systemPrompt),
                    ChatPayload.user(retryUserMessage)
            ), config.model().replyerTimeoutMillis(), retryBuffer::append, interruptFlag);
            String retryRaw = retryResponse.content().isBlank() ? retryBuffer.content() : retryResponse.content();
            String retryCleaned = postProcessor.process(retryRaw);
            if (retryCleaned.isBlank()) {
                return new LlmReply(fallbackPolicy.fallback(target, context, outcome.retryReason()), retryResponse.model(), retryResponse.metricsSummary());
            }
            currentReply = retryCleaned;
            currentModel = retryResponse.model();
            currentMetrics = retryResponse.metricsSummary();
            promptTokens += retryResponse.promptTokens();
            completionTokens += retryResponse.completionTokens();
            totalTokens += retryResponse.totalTokens();
            retryCount++;
        }
    }

    private static String buildRetryConstraint(String retryReason, String rejectedResponse) {
        String reason = retryReason == null ? "" : retryReason.trim().replaceAll("[。！？!?；;，,]+$", "");
        if (reason.isBlank()) {
            return "";
        }
        String rejected = rejectedResponse == null ? "" : rejectedResponse.trim().replace("\"", "\\\"");
        return "【重生成约束】\n由于" + reason + "，之前生成的回复\"" + rejected + "\"不符合要求，你需要重新生成回复。";
    }

    record LlmReply(String content, String model, String metricsSummary) {
    }
}
