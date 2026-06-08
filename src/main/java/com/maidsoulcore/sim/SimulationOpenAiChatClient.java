package com.maidsoulcore.sim;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容聊天客户端。
 * <p>
 * 当前只实现 openai 兼容接口，足够覆盖你现有 MaiBot 配置里的 SiliconFlow/DeepSeek/BaiLian。
 */
public final class SimulationOpenAiChatClient {
    private static final Gson GSON = new Gson();

    private final SimulationMaiBotRuntimeConfig runtimeConfig;
    private final HttpClient httpClient;

    public SimulationOpenAiChatClient(SimulationMaiBotRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 调用文本模型并返回原始文本。
     */
    public String completeText(SimulationLlmTaskConfig taskConfig, String systemPrompt, String userPrompt) {
        SimulationResolvedModel resolvedModel = runtimeConfig.resolveTask(taskConfig);
        return callOnce(resolvedModel, List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        ));
    }

    /**
     * 调用文本模型并传入完整消息列表。
     * <p>
     * 这个重载主要给 Forge 正式版使用，
     * 使 MaidSoulCore 能直接复用 TLM 已整理好的 system + history + user 上下文。
     */
    public String completeText(SimulationLlmTaskConfig taskConfig, List<ChatMessage> messages) {
        SimulationResolvedModel resolvedModel = runtimeConfig.resolveTask(taskConfig);
        return callOnce(resolvedModel, messages, -1, -1);
    }

    public String completeText(SimulationLlmTaskConfig taskConfig, List<ChatMessage> messages, int timeoutSeconds, int maxRetry) {
        SimulationResolvedModel resolvedModel = runtimeConfig.resolveTask(taskConfig);
        return callOnce(resolvedModel, messages, timeoutSeconds, maxRetry);
    }

    /**
     * 调用模型并尝试解析 JSON。
     */
    public JsonObject completeJson(SimulationLlmTaskConfig taskConfig, String systemPrompt, String userPrompt) {
        String text = completeText(taskConfig, systemPrompt, userPrompt);
        try {
            return JsonParser.parseString(extractJson(text)).getAsJsonObject();
        } catch (Exception exception) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("raw_text", text);
            fallback.addProperty("parse_error", exception.getMessage());
            return fallback;
        }
    }

    /**
     * 调用模型并尝试把完整消息列表的结果解析为 JSON。
     */
    public JsonObject completeJson(SimulationLlmTaskConfig taskConfig, List<ChatMessage> messages) {
        String text = completeText(taskConfig, messages);
        try {
            return JsonParser.parseString(extractJson(text)).getAsJsonObject();
        } catch (Exception exception) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("raw_text", text);
            fallback.addProperty("parse_error", exception.getMessage());
            return fallback;
        }
    }

    /**
     * 单次 HTTP 调用，失败自动重试。
     */
    private String callOnce(SimulationResolvedModel resolvedModel, List<ChatMessage> messages) {
        return callOnce(resolvedModel, messages, -1, -1);
    }

    private String callOnce(SimulationResolvedModel resolvedModel, List<ChatMessage> messages, int timeoutSeconds, int maxRetry) {
        SimulationLlmProviderConfig provider = resolvedModel.providerConfig();
        if (!"openai".equalsIgnoreCase(provider.clientType())) {
            throw new IllegalStateException("Unsupported client_type for text game: " + provider.clientType());
        }
        if (provider.apiKey().isBlank() || provider.apiKey().contains("your-")) {
            throw new IllegalStateException("Provider api_key is empty or placeholder: " + provider.name());
        }

        RuntimeException lastError = null;
        int attempts = maxRetry > 0 ? maxRetry : provider.maxRetry();
        int requestTimeout = timeoutSeconds > 0 ? timeoutSeconds : provider.timeoutSeconds();
        for (int attempt = 1; attempt <= Math.max(1, attempts); attempt++) {
            try {
                JsonObject requestBody = buildRequestBody(resolvedModel, messages);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(normalizeBaseUrl(provider.baseUrl()) + "/chat/completions"))
                        .timeout(Duration.ofSeconds(Math.max(5, requestTimeout)))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + provider.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                }
                return parseAssistantContent(response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("LLM request interrupted", exception);
            } catch (IOException exception) {
                lastError = new IllegalStateException("LLM request failed: " + exception.getMessage(), exception);
                if (attempt < Math.max(1, attempts)) {
                    sleepSilently(provider.retryIntervalSeconds());
                }
            } catch (RuntimeException exception) {
                lastError = exception;
                if (attempt < Math.max(1, attempts)) {
                    sleepSilently(provider.retryIntervalSeconds());
                }
            }
        }
        throw lastError == null ? new IllegalStateException("Unknown LLM call failure") : lastError;
    }

    private JsonObject buildRequestBody(SimulationResolvedModel resolvedModel, List<ChatMessage> messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", resolvedModel.modelConfig().modelIdentifier());
        body.addProperty("temperature", resolvedModel.taskConfig().temperature());
        body.addProperty("max_tokens", resolvedModel.taskConfig().maxTokens());

        JsonArray messageArray = new JsonArray();
        for (ChatMessage chatMessage : messages) {
            messageArray.add(message(chatMessage.role(), chatMessage.content()));
        }
        body.add("messages", messageArray);

        for (Map.Entry<String, Object> entry : resolvedModel.modelConfig().extraParams().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean bool) {
                body.addProperty(entry.getKey(), bool);
            } else if (value instanceof Number number) {
                body.addProperty(entry.getKey(), number);
            } else if (value != null) {
                body.addProperty(entry.getKey(), String.valueOf(value));
            }
        }
        return body;
    }

    private JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private String parseAssistantContent(String bodyText) {
        JsonObject root = JsonParser.parseString(bodyText).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in response: " + bodyText);
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) {
            throw new IllegalStateException("No message in response: " + bodyText);
        }
        JsonElement content = message.get("content");
        if (content == null || content.isJsonNull()) {
            throw new IllegalStateException("Empty content in response: " + bodyText);
        }
        return content.getAsString().trim();
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void sleepSilently(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 通用聊天消息结构。
     */
    public record ChatMessage(String role, String content) {
    }
}
