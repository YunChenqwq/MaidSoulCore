package com.yunchen.maidsoulcore.core.llm;

public record LlmResponse(String content, String model, int promptTokens, int completionTokens) {
}
