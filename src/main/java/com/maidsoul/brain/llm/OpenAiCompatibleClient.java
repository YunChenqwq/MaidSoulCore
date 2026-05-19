package com.maidsoul.brain.llm;

import com.maidsoul.brain.config.ModelConfig;
import com.maidsoul.brain.tool.ToolCall;
import com.maidsoul.brain.tool.ToolSpec;
import com.maidsoul.brain.util.JsonText;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class OpenAiCompatibleClient implements LlmClient {
    private final ModelConfig config;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiCompatibleClient(ModelConfig config) {
        this.config = config;
    }

    @Override
    public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
        return chat("chat", messages, timeoutMillis);
    }

    @Override
    public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis) {
        return chat(requestKind, messages, timeoutMillis, null);
    }

    @Override
    public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
        return send(requestKind, messages, List.of(), timeoutMillis, interruptFlag);
    }

    @Override
    public LlmResponse chatWithTools(List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis) {
        return chatWithTools("planner", messages, tools, timeoutMillis);
    }

    @Override
    public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis) {
        return chatWithTools(requestKind, messages, tools, timeoutMillis, null);
    }

    @Override
    public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer) {
        return chatStream(requestKind, messages, timeoutMillis, deltaConsumer, null);
    }

    @Override
    public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
        return send(requestKind, messages, tools == null ? List.of() : tools, timeoutMillis, interruptFlag);
    }

    @Override
    public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
        return sendStream(requestKind, messages, timeoutMillis, deltaConsumer, interruptFlag);
    }

    private LlmResponse send(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
        String model = modelFor(requestKind);
        String body = buildBody(model, messages, tools, false);
        int attempts = Math.max(1, config.maxRetries() + 1);
        LlmRequestException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long started = System.currentTimeMillis();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl()))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.apiKey());
            }
            try {
                var future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                HttpResponse<String> response = awaitInterruptibly(requestKind, attempt, started, future, interruptFlag);
                long elapsed = System.currentTimeMillis() - started;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new LlmRequestException(requestKind, "http_" + response.statusCode(), attempt, elapsed,
                            "HTTP " + response.statusCode() + ": " + clip(response.body(), 600), null);
                }
                String content = JsonText.extractFirstMessageContent(response.body());
                List<ToolCall> toolCalls = JsonText.extractToolCalls(response.body());
                return new LlmResponse(
                        content,
                        model,
                        JsonText.extractUsageInt(response.body(), "prompt_tokens"),
                        JsonText.extractUsageInt(response.body(), "completion_tokens"),
                        JsonText.extractUsageInt(response.body(), "total_tokens"),
                        promptChars(messages),
                        content.length(),
                        messages == null ? 0 : messages.size(),
                        toolCalls
                );
            } catch (LlmRequestException e) {
                last = e;
            } catch (CompletionException | CancellationException e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                long elapsed = System.currentTimeMillis() - started;
                String failureKind = classify(cause);
                String message = cause.getMessage();
                if (message == null || message.isBlank()) {
                    message = cause.getClass().getSimpleName();
                }
                last = new LlmRequestException(requestKind, failureKind, attempt, elapsed, message, cause);
            }
            if (attempt < attempts && shouldRetry(last)) {
                sleepQuietly(config.retryBackoffMillis());
                continue;
            }
            break;
        }
        throw last == null
                ? new LlmRequestException(requestKind, "unknown", 1, 0, "模型请求失败", null)
                : last;
    }

    private LlmResponse sendStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
        String model = modelFor(requestKind);
        String body = buildBody(model, messages, List.of(), true);
        int attempts = Math.max(1, config.maxRetries() + 1);
        LlmRequestException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long started = System.currentTimeMillis();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl()))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.apiKey());
            }
            StringBuilder full = new StringBuilder();
            try {
                var future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofLines())
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                HttpResponse<Stream<String>> response = awaitInterruptibly(requestKind, attempt, started, future, interruptFlag);
                long elapsed = System.currentTimeMillis() - started;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = collectBody(response.body(), 600);
                    throw new LlmRequestException(requestKind, "http_" + response.statusCode(), attempt, elapsed,
                            "HTTP " + response.statusCode() + ": " + errorBody, null);
                }
                try (Stream<String> lines = response.body()) {
                    var iterator = lines.iterator();
                    while (iterator.hasNext()) {
                        if (interruptFlag != null && interruptFlag.isInterrupted()) {
                            throw new LlmRequestException(requestKind, "aborted", attempt,
                                    System.currentTimeMillis() - started, "模型流式请求被新消息中断", null);
                        }
                        consumeStreamLine(iterator.next(), full, deltaConsumer);
                    }
                }
                return new LlmResponse(
                        full.toString(),
                        model,
                        0,
                        0,
                        0,
                        promptChars(messages),
                        full.length(),
                        messages == null ? 0 : messages.size(),
                        List.of()
                );
            } catch (LlmRequestException e) {
                last = e;
            } catch (CompletionException | CancellationException e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                long elapsed = System.currentTimeMillis() - started;
                String failureKind = classify(cause);
                String message = cause.getMessage();
                if (message == null || message.isBlank()) {
                    message = cause.getClass().getSimpleName();
                }
                last = new LlmRequestException(requestKind, failureKind, attempt, elapsed, message, cause);
            }
            if (attempt < attempts && shouldRetry(last)) {
                sleepQuietly(config.retryBackoffMillis());
                continue;
            }
            break;
        }
        throw last == null
                ? new LlmRequestException(requestKind, "unknown", 1, 0, "模型流式请求失败", null)
                : last;
    }

    private static boolean shouldRetry(LlmRequestException exception) {
        if (exception == null) {
            return false;
        }
        return exception.failureKind().startsWith("timeout")
                || exception.failureKind().startsWith("connect")
                || exception.failureKind().startsWith("http_5")
                || exception.failureKind().equals("io");
    }

    private static <T> T awaitInterruptibly(
            String requestKind,
            int attempt,
            long started,
            java.util.concurrent.CompletableFuture<T> future,
            InterruptFlag interruptFlag
    ) {
        try {
            while (true) {
                if (interruptFlag != null && interruptFlag.isInterrupted()) {
                    future.cancel(true);
                    throw new LlmRequestException(requestKind, "aborted", attempt,
                            System.currentTimeMillis() - started, "模型请求被新消息中断", null);
                }
                try {
                    return future.get(40, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    // 短轮询只为了检查中断标记，真正的 hard timeout 由 HttpRequest / orTimeout 控制。
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new LlmRequestException(requestKind, "interrupted", attempt,
                    System.currentTimeMillis() - started, "等待模型请求时线程被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof CompletionException completion && completion.getCause() != null) {
                cause = completion.getCause();
            }
            throw new CompletionException(cause);
        }
    }

    private static String classify(Throwable throwable) {
        if (throwable instanceof TimeoutException || throwable instanceof java.net.http.HttpTimeoutException) {
            return "timeout";
        }
        if (throwable instanceof java.net.ConnectException) {
            return "connect";
        }
        if (throwable instanceof java.io.IOException) {
            return "io";
        }
        if (throwable instanceof CancellationException) {
            return "cancelled";
        }
        return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildBody(String model, List<ChatPayload> messages, List<ToolSpec> tools, boolean stream) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"model\":\"").append(JsonText.escape(model)).append("\",");
        builder.append("\"temperature\":").append(config.temperature()).append(',');
        builder.append("\"max_tokens\":").append(config.maxTokens()).append(',');
        builder.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            ChatPayload message = messages.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"role\":\"").append(JsonText.escape(message.role())).append("\",")
                    .append("\"content\":\"").append(JsonText.escape(message.content())).append("\"")
                    .append('}');
        }
        builder.append(']');
        if (stream) {
            builder.append(",\"stream\":true");
        }
        if (!tools.isEmpty()) {
            builder.append(",\"tools\":[");
            for (int i = 0; i < tools.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                appendTool(builder, tools.get(i));
            }
            builder.append("],\"tool_choice\":\"auto\"");
        }
        builder.append('}');
        return builder.toString();
    }

    private static int promptChars(List<ChatPayload> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChatPayload message : messages) {
            if (message != null && message.content() != null) {
                count += message.content().length();
            }
        }
        return count;
    }

    private String modelFor(String requestKind) {
        String kind = requestKind == null ? "" : requestKind.toLowerCase();
        String selected;
        if (kind.contains("planner")) {
            selected = config.plannerModel();
        } else if (kind.contains("replyer")) {
            selected = config.replyerModel();
        } else if (kind.contains("timing")) {
            selected = config.timingModel();
        } else {
            selected = config.model();
        }
        return selected == null || selected.isBlank() ? config.model() : selected;
    }

    private static void consumeStreamLine(String line, StringBuilder full, Consumer<String> deltaConsumer) {
        if (line == null) {
            return;
        }
        String text = line.trim();
        if (text.isBlank() || text.startsWith(":")) {
            return;
        }
        if (text.startsWith("data:")) {
            text = text.substring("data:".length()).trim();
        }
        if ("[DONE]".equals(text)) {
            return;
        }
        String delta = JsonText.extractDeltaContent(text);
        if (delta.isBlank()) {
            return;
        }
        full.append(delta);
        if (deltaConsumer != null) {
            deltaConsumer.accept(delta);
        }
    }

    private static String collectBody(Stream<String> lines, int limit) {
        if (lines == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (Stream<String> stream = lines) {
            stream.limit(20).forEach(line -> {
                if (builder.length() < limit) {
                    builder.append(line).append('\n');
                }
            });
        }
        return clip(builder.toString(), limit);
    }

    private void appendTool(StringBuilder builder, ToolSpec tool) {
        builder.append('{')
                .append("\"type\":\"function\",")
                .append("\"function\":{")
                .append("\"name\":\"").append(JsonText.escape(tool.name())).append("\",")
                .append("\"description\":\"").append(JsonText.escape(tool.description())).append("\",")
                .append("\"parameters\":").append(toJson(tool.parametersSchema()))
                .append("}}");
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + JsonText.escape(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(JsonText.escape(String.valueOf(entry.getKey()))).append("\":")
                        .append(toJson(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(toJson(item));
            }
            return builder.append(']').toString();
        }
        return "\"" + JsonText.escape(String.valueOf(value)) + "\"";
    }

    private static String clip(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value == null ? "" : value;
        }
        return value.substring(0, limit) + "...";
    }
}
