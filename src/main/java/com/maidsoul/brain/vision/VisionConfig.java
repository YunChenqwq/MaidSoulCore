package com.maidsoul.brain.vision;

import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.config.DialogueVisionConfig;

import java.nio.file.Path;

/**
 * 视觉模型配置。
 *
 * <p>运行时统一从 config/maidsoulcore/dialogue-config.json 读取。旧的
 * model/vision.properties 只在 Forge 配置安装器里做迁移，不再作为真实配置源。</p>
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
        DialogueCoreConfig core = DialogueConfigLoader.loadOrCreate(root.resolve("dialogue-config.json"));
        DialogueVisionConfig vision = core.vision;
        String key = vision.apiKey;
        String envKey = System.getenv("MAIDSOUL_VISION_API_KEY");
        if ((key == null || key.isBlank()) && envKey != null && !envKey.isBlank()) {
            key = envKey.trim();
        }
        return new VisionConfig(
                vision.enabled,
                defaultText(vision.mode, "client_direct"),
                defaultText(vision.baseUrl, "https://api.siliconflow.cn/v1/chat/completions"),
                key,
                defaultText(vision.model, DEFAULT_MODEL),
                vision.temperature,
                vision.maxTokens <= 0 ? 220 : vision.maxTokens,
                vision.timeoutMillis <= 0 ? 60000L : vision.timeoutMillis,
                vision.maxImageWidth <= 0 ? 512 : vision.maxImageWidth,
                vision.maxImageHeight <= 0 ? 512 : vision.maxImageHeight,
                (float) Math.max(0.05D, Math.min(1.0D, vision.jpegQuality)),
                vision.autoCooldownMillis <= 0 ? 45000L : vision.autoCooldownMillis,
                vision.manualCooldownMillis <= 0 ? 5000L : vision.manualCooldownMillis,
                defaultText(vision.prompt, defaultPrompt())
        );
    }

    public boolean available() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    public boolean clientDirectMode() {
        return !"server_proxy".equalsIgnoreCase(mode);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String defaultPrompt() {
        return "你是 MaidSoulCore 的 Minecraft 视觉摘要器。请根据截图和结构化游戏状态，用中文写 1 到 3 句短摘要。"
                + "只描述确定能看到或结构化状态明确给出的事实，不要编造。"
                + "若 owner_looking_at_request_maid=true，画面里主人正看着的女仆就是当前说话的女仆自己。"
                + "不要把空气、天空、准星没有实际命中的位置描述成方块。"
                + "优先包含：主人正在看向什么、附近危险、重要实体、地点氛围、女仆可用于回应主人的信息。"
                + "不要写分析过程，不要自称视觉模型。";
    }
}
