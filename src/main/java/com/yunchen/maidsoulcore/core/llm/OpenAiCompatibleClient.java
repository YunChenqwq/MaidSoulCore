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
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class OpenAiCompatibleClient implements LlmClient {
    private static final Gson GSON = new Gson();
    private final DialogueModelConfig config;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiCompatibleClient(DialogueModelConfig config) {
        this.config = config;
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, long timeoutMillis) {
        return send(messages, timeoutMillis, false, null);
    }

    @Override
    public LlmResponse chatStream(List<LlmMessage> messages, long timeoutMillis, Consumer<String> deltaConsumer) {
        return send(messages, timeoutMillis, true, deltaConsumer);
    }

    private LlmResponse send(List<LlmMessage> messages, long timeoutMillis, boolean stream, Consumer<String> deltaConsumer) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("temperature", config.temperature);
        body.addProperty("max_tokens", config.maxTokens);
        if (stream) {
            body.addProperty("stream", true);
        }

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
            if (stream) {
                return sendStream(builder, timeoutMillis, deltaConsumer);
            }
            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM 请求失败: HTTP " + response.statusCode() + " " + response.body());
            }
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            JsonObject usage = json.has("usage") && json.get("usage").isJsonObject()
                    ? json.getAsJsonObject("usage")
                    : new JsonObject();
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

    private LlmResponse sendStream(HttpRequest.Builder builder, long timeoutMillis, Consumer<String> deltaConsumer)
            throws IOException, InterruptedException {
        HttpResponse<Stream<String>> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofLines()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM 请求失败: HTTP " + response.statusCode() + " "
                    + collectBody(response.body(), 800));
        }

        StringBuilder full = new StringBuilder();
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMillis);
        try (Stream<String> lines = response.body()) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("LLM 流式请求超时");
                }
                String delta = deltaFromSseLine(iterator.next());
                if (delta.isBlank()) {
                    continue;
                }
                full.append(delta);
                if (deltaConsumer != null) {
                    deltaConsumer.accept(delta);
                }
            }
        }
        return new LlmResponse(full.toString(), config.model, 0, 0);
    }

    private static String deltaFromSseLine(String line) {
        if (line == null) {
            return "";
        }
        String text = line.trim();
        if (text.isBlank() || text.startsWith(":")) {
            return "";
        }
        if (text.startsWith("data:")) {
            text = text.substring("data:".length()).trim();
        }
        if (text.isBlank() || "[DONE]".equals(text)) {
            return "";
        }
        try {
            JsonObject json = GSON.fromJson(text, JsonObject.class);
            if (json == null || !json.has("choices") || !json.get("choices").isJsonArray()) {
                return "";
            }
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
                return "";
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                JsonObject delta = choice.getAsJsonObject("delta");
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    return delta.get("content").getAsString();
                }
            }
            return "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String collectBody(Stream<String> lines, int maxChars) {
        if (lines == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (Stream<String> stream = lines) {
            stream.limit(40).forEach(line -> {
                if (builder.length() < maxChars) {
                    builder.append(line).append('\n');
                }
            });
        }
        String text = builder.toString();
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
