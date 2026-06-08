package com.yunchen.maidsoulcore.core.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yunchen.maidsoulcore.core.config.DialogueModelConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class OpenAiCompatibleClient implements LlmClient {
    private static final Gson GSON = new Gson();
    private final DialogueModelConfig config;
    private final HttpClient client = HttpClient.newHttpClient();

    public OpenAiCompatibleClient(DialogueModelConfig config) {
        this.config = config;
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, long timeoutMillis) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("temperature", config.temperature);
        body.addProperty("max_tokens", config.maxTokens);
        JsonArray array = new JsonArray();
        for (LlmMessage message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role());
            item.addProperty("content", message.content());
            array.add(item);
        }
        body.add("messages", array);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), java.nio.charset.StandardCharsets.UTF_8));
        if (config.apiKey != null && !config.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey);
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM 请求失败: HTTP " + response.statusCode() + " " + response.body());
            }
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            JsonObject usage = json.has("usage") && json.get("usage").isJsonObject() ? json.getAsJsonObject("usage") : new JsonObject();
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
            String model = json.has("model") ? json.get("model").getAsString() : config.model;
            return new LlmResponse(content, model, promptTokens, completionTokens);
        } catch (IOException e) {
            throw new IllegalStateException("LLM 网络请求异常", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 请求被中断", e);
        }
    }
}
