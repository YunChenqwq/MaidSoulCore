package com.yunchen.maidsoulcore.core.llm;

import java.util.List;
import java.util.function.Consumer;

public interface LlmClient {
    LlmResponse chat(List<LlmMessage> messages, long timeoutMillis);

    default LlmResponse chatStream(List<LlmMessage> messages, long timeoutMillis, Consumer<String> deltaConsumer) {
        LlmResponse response = chat(messages, timeoutMillis);
        if (deltaConsumer != null && response.content() != null && !response.content().isBlank()) {
            deltaConsumer.accept(response.content());
        }
        return response;
    }
}
