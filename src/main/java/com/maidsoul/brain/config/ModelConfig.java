package com.maidsoul.brain.config;

import java.nio.file.Path;
import java.util.Properties;

public record ModelConfig(
        String baseUrl,
        String apiKey,
        String model,
        String plannerModel,
        String replyerModel,
        String timingModel,
        double temperature,
        int maxTokens,
        long timeoutMillis,
        long plannerTimeoutMillis,
        long replyerTimeoutMillis,
        long timingTimeoutMillis,
        long plannerSlowThresholdMillis,
        long replyerSlowThresholdMillis,
        long timingSlowThresholdMillis,
        int maxRetries,
        long retryBackoffMillis
) {
    public static ModelConfig load(Path path) {
        Properties p = ConfigFiles.load(path);
        String key = ConfigFiles.text(p, "apiKey", "");
        String envKey = System.getenv("MAIDSOUL_API_KEY");
        if ((key == null || key.isBlank()) && envKey != null && !envKey.isBlank()) {
            key = envKey.trim();
        }
        return new ModelConfig(
                ConfigFiles.text(p, "baseUrl", "https://api.openai.com/v1/chat/completions"),
                key,
                ConfigFiles.text(p, "model", "gpt-4o-mini"),
                ConfigFiles.text(p, "plannerModel", ConfigFiles.text(p, "model", "gpt-4o-mini")),
                ConfigFiles.text(p, "replyerModel", ConfigFiles.text(p, "model", "gpt-4o-mini")),
                ConfigFiles.text(p, "timingModel", ConfigFiles.text(p, "model", "gpt-4o-mini")),
                ConfigFiles.decimal(p, "temperature", 0.55),
                ConfigFiles.integer(p, "maxTokens", 500),
                ConfigFiles.number(p, "timeoutMillis", 25000),
                ConfigFiles.number(p, "plannerTimeoutMillis", ConfigFiles.number(p, "timeoutMillis", 25000)),
                ConfigFiles.number(p, "replyerTimeoutMillis", ConfigFiles.number(p, "timeoutMillis", 25000)),
                ConfigFiles.number(p, "timingTimeoutMillis", ConfigFiles.number(p, "timeoutMillis", 25000)),
                ConfigFiles.number(p, "plannerSlowThresholdMillis", 12000),
                ConfigFiles.number(p, "replyerSlowThresholdMillis", 20000),
                ConfigFiles.number(p, "timingSlowThresholdMillis", 5000),
                ConfigFiles.integer(p, "maxRetries", 0),
                ConfigFiles.number(p, "retryBackoffMillis", 800)
        );
    }
}
