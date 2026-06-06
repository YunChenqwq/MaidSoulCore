package com.maidsoul.brain.vision;

import com.maidsoul.brain.config.ConfigFiles;

import java.nio.file.Path;
import java.util.Properties;

/**
 * 视觉模型配置。
 *
 * <p>这里采用独立的 VLM 配置，而不是复用普通回复模型配置。原因和上游参考系统一致：
 * 文本回复模型不一定具备视觉能力，盲目把图片传给普通模型会导致请求失败或者浪费 token。
 * 因此 MaidSoulCore 把「看图生成视角摘要」拆成单独任务，摘要完成后再作为世界事件进入记忆和会话链路。</p>
 */
public record VisionConfig(
        boolean enabled,
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

    private static String defaultPrompt() {
        return """
                你是 MaidSoulCore 的 Minecraft 视觉摘要器。请根据截图，用中文写一段短摘要。
                只描述画面中确定能看到的内容，不要编造看不到的事实。
                优先包含：玩家正在看向什么、附近危险、重要方块/实体、地点氛围、女仆可用于回应主人的信息。
                输出 1 到 3 句，不要写分析过程，不要自称视觉模型。
                """.replace('\n', ' ').trim();
    }
}
