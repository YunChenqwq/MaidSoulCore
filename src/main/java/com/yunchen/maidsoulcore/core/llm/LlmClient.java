package com.yunchen.maidsoulcore.core.llm;

import java.util.List;

public interface LlmClient {
    LlmResponse chat(List<LlmMessage> messages, long timeoutMillis);
}
