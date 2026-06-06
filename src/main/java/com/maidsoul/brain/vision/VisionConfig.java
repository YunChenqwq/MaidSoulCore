package com.maidsoul.brain.vision;

import com.maidsoul.brain.config.ConfigFiles;

import java.nio.file.Path;
import java.util.Properties;

/**
 * 视觉模型配置。
 *
 * <p>视觉能力是独立的 VLM 任务，不和普通聊天模型混在一起。默认模式是 client_direct：
 * 玩家客户端截图后直接请求视觉模型，只把文字摘要发给服务端。这样不会把图片流量压到 MC 服务器上。
 * server_proxy 仅作为备用模式，用于服务器统一持有 API Key 的场景。</p>
 */
public record VisionConfig(
        boolean enabled,
        String mode,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        long timeoutMillis,
        int maxImageWidth,
        int maxImageHeight,
        float jpegQuality,
        long autoCooldownMillis,
        long manualCooldownMillis,
        String prompt
) {
    public static VisionConfig load(Path root) {
        Properties properties = ConfigFiles.load(root.resolve("model").resolve("vision.properties"));
        String key = ConfigFiles.text(properties, "apiKey", "");
        String envKey = System.getenv("MAIDSOUL_VISION_API_KEY");
        if ((key == null || key.isBlank()) && envKey != null && !envKey.isBlank()) {
            key = envKey.trim();
        }
        return new VisionConfig(
                ConfigFiles.bool(properties, "enabled", false),
                ConfigFiles.text(properties, "mode", "client_direct"),
                ConfigFiles.text(properties, "baseUrl", "https://api.openai.com/v1/chat/completions"),
                key,
                ConfigFiles.text(properties, "model", "gpt-4o-mini"),
                ConfigFiles.decimal(properties, "temperature", 0.2D),
                ConfigFiles.integer(properties, "maxTokens", 220),
                ConfigFiles.number(properties, "timeoutMillis", 60000L),
                ConfigFiles.integer(properties, "maxImageWidth", 512),
                ConfigFiles.integer(properties, "maxImageHeight", 512),
                (float) Math.max(0.05D, Math.min(1.0D, ConfigFiles.decimal(properties, "jpegQuality", 0.72D))),
                ConfigFiles.number(properties, "autoCooldownMillis", 120000L),
                ConfigFiles.number(properties, "manualCooldownMillis", 5000L),
                ConfigFiles.text(properties, "prompt", defaultPrompt())
        );
    }

    public boolean available() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    public boolean clientDirectMode() {
        return !"server_proxy".equalsIgnoreCase(mode);
    }

    private static String defaultPrompt() {
        return """
                你是 MaidSoulCore 的 Minecraft 视觉摘要器。请根据截图，用中文写一段短摘要。
                只描述画面中确定能看到的内容，不要编造看不到的事实。
                优先包含：玩家正在看向什么、附近危险、重要方块/实体、地点氛围、女仆可用于回应主人的信息。
                输出 1 到 3 句，不要写分析过程，不要自称视觉模型。
                """.replace('\n', ' ').trim();
    }
}
