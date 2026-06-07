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
    public static final String DEFAULT_MODEL = "Qwen/Qwen3-VL-32B-Instruct";

    public static VisionConfig load(Path root) {
        Properties properties = ConfigFiles.load(root.resolve("model").resolve("vision.properties"));
        String key = ConfigFiles.text(properties, "apiKey", "");
        String envKey = System.getenv("MAIDSOUL_VISION_API_KEY");
        if ((key == null || key.isBlank()) && envKey != null && !envKey.isBlank()) {
            key = envKey.trim();
        }
        return new VisionConfig(
                ConfigFiles.bool(properties, "enabled", true),
                ConfigFiles.text(properties, "mode", "client_direct"),
                ConfigFiles.text(properties, "baseUrl", "https://api.siliconflow.cn/v1/chat/completions"),
                key,
                ConfigFiles.text(properties, "model", DEFAULT_MODEL),
                ConfigFiles.decimal(properties, "temperature", 0.2D),
                ConfigFiles.integer(properties, "maxTokens", 220),
                ConfigFiles.number(properties, "timeoutMillis", 60000L),
                ConfigFiles.integer(properties, "maxImageWidth", 512),
                ConfigFiles.integer(properties, "maxImageHeight", 512),
                (float) Math.max(0.05D, Math.min(1.0D, ConfigFiles.decimal(properties, "jpegQuality", 0.72D))),
                ConfigFiles.number(properties, "autoCooldownMillis", 45000L),
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
                结构化游戏状态比截图猜测更可靠：如果状态说明 owner_looking_at_request_maid=true，画面里主人正看着的女仆就是当前说话的女仆/你自己的身体，不要说成陌生女仆或另一只女仆。
                不要把空气、天空、十字准星没有实际命中的位置描述成一个方块；如果结构化焦点是 none，就说没有明确焦点。
                优先包含：玩家正在看向什么、附近危险、重要方块/实体、地点氛围、女仆可用于回应主人的信息。
                输出 1 到 3 句，不要写分析过程，不要自称视觉模型。
                """.replace('\n', ' ').trim();
    }
}
