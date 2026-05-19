package com.maidsoul.brain.llm;

import com.maidsoul.brain.tool.ToolSpec;

import java.util.List;
import java.util.function.Consumer;

public interface LlmClient {
    LlmResponse chat(List<ChatPayload> messages, long timeoutMillis);

    default LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis) {
        return chat(messages, timeoutMillis);
    }

    default LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
        return chat(requestKind, messages, timeoutMillis);
    }

    /**
     * 流式对话请求。
     *
     * <p>默认实现会退化成普通非流式请求，再一次性把完整内容交给调用方。
     * 这样测试桩和暂未支持流式的模型客户端不需要立刻实现新方法。</p>
     */
    default LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer) {
        LlmResponse response = chat(requestKind, messages, timeoutMillis);
        if (deltaConsumer != null && !response.content().isBlank()) {
            deltaConsumer.accept(response.content());
        }
        return response;
    }

    default LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
        return chatStream(requestKind, messages, timeoutMillis, deltaConsumer);
    }

    /**
     * 带工具定义的对话请求。
     *
     * <p>聊天核心的 Planner 应该通过工具调用选择下一步动作，而不是把内部动作伪装成普通 JSON 文本。</p>
     */
    default LlmResponse chatWithTools(List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis) {
        return chat(messages, timeoutMillis);
    }

    default LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis) {
        return chatWithTools(messages, tools, timeoutMillis);
    }

    default LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
        return chatWithTools(requestKind, messages, tools, timeoutMillis);
    }
}
