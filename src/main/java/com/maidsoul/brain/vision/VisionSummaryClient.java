package com.maidsoul.brain.vision;

import com.maidsoul.brain.util.JsonText;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容视觉模型客户端。
 *
 * <p>这个类只负责「图片 + 提示词 -> 视角摘要」。它不直接操作女仆、不写记忆，
 * 因而既可以在客户端直连视觉模型时使用，也可以在服务端代理模式下使用。</p>
 */
public final class VisionSummaryClient {
    private final VisionConfig config;
    private final HttpClient client;

    public VisionSummaryClient(VisionConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String summarize(String imageFormat, String imageBase64, String sceneHint) {
        if (!config.available()) {
            throw new IllegalStateException("视觉模型未启用或配置不完整");
        }
        String normalizedFormat = normalizeFormat(imageFormat);
        String body = buildBody(normalizedFormat, imageBase64, sceneHint);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl()))
                .timeout(Duration.ofMillis(config.timeoutMillis()))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        try {
            HttpResponse<String> response = client.sendAsync(
                            builder.build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                    )
                    .orTimeout(config.timeoutMillis(), TimeUnit.MILLISECONDS)
                    .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("视觉模型 HTTP " + response.statusCode() + ": " + clip(response.body(), 500));
            }
            String content = JsonText.extractFirstMessageContent(response.body()).trim();
            if (content.isBlank()) {
                throw new IllegalStateException("视觉模型没有返回摘要");
            }
            return content;
        } catch (RuntimeException e) {
            throw new IllegalStateException("视觉摘要请求失败: " + e.getMessage(), e);
        }
    }

    private String buildBody(String imageFormat, String imageBase64, String sceneHint) {
        String prompt = config.prompt();
        if (sceneHint != null && !sceneHint.isBlank()) {
            prompt += "\n\n结构化游戏状态参考：" + sceneHint;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"model\":\"").append(JsonText.escape(config.model())).append("\",");
        builder.append("\"temperature\":").append(config.temperature()).append(',');
        builder.append("\"max_tokens\":").append(config.maxTokens()).append(',');
        builder.append("\"messages\":[{\"role\":\"user\",\"content\":[");
        builder.append("{\"type\":\"text\",\"text\":\"").append(JsonText.escape(prompt)).append("\"},");
        builder.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/")
                .append(JsonText.escape(imageFormat))
                .append(";base64,")
                .append(JsonText.escape(imageBase64))
                .append("\"}}");
        builder.append("]}]}");
        return builder.toString();
    }

    private static String normalizeFormat(String imageFormat) {
        String lower = imageFormat == null ? "jpeg" : imageFormat.toLowerCase();
        return lower.equals("jpg") ? "jpeg" : lower;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
