package com.maidsoulcore.sim;

/**
 * 单个 API 提供商配置。
 */
public record SimulationLlmProviderConfig(
        String name,
        String baseUrl,
        String apiKey,
        String clientType,
        int maxRetry,
        int timeoutSeconds,
        int retryIntervalSeconds
) {
}
