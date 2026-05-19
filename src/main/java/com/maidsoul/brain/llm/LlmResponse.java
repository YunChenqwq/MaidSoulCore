package com.maidsoul.brain.llm;

import com.maidsoul.brain.tool.ToolCall;

import java.util.List;

public record LlmResponse(
        String content,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int promptChars,
        int completionChars,
        int requestMessageCount,
        List<ToolCall> toolCalls
) {
    public LlmResponse(String content, String model, int promptTokens, int completionTokens) {
        this(content, model, promptTokens, completionTokens, promptTokens + completionTokens, 0, content == null ? 0 : content.length(), 0, List.of());
    }

    public LlmResponse(String content, String model, int promptTokens, int completionTokens, List<ToolCall> toolCalls) {
        this(content, model, promptTokens, completionTokens, promptTokens + completionTokens, 0, content == null ? 0 : content.length(), 0, toolCalls);
    }

    public LlmResponse withLocalStats(int promptChars, int requestMessageCount) {
        return new LlmResponse(
                content,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                promptChars,
                content.length(),
                requestMessageCount,
                toolCalls
        );
    }

    public LlmResponse {
        content = content == null ? "" : content;
        model = model == null ? "" : model;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * 生成链路观测摘要。
     *
     * <p>token 来自模型服务 usage；如果流式接口没返回 usage，则为 0。
     * 字符数是本地稳定可得的近似指标，用来比较 prompt/context 是否膨胀。</p>
     */
    public String metricsSummary() {
        return "messages=" + requestMessageCount
                + " promptChars=" + promptChars
                + " completionChars=" + completionChars
                + " promptTokens=" + promptTokens
                + " completionTokens=" + completionTokens
                + " totalTokens=" + totalTokens;
    }
}
