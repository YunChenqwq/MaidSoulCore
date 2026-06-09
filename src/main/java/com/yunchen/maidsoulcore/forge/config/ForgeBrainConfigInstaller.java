package com.yunchen.maidsoulcore.forge.config;

import com.maidsoul.brain.config.ConfigFiles;
import com.maidsoul.brain.vision.VisionConfig;
import com.yunchen.maidsoulcore.MaidSoulCoreMod;
import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class ForgeBrainConfigInstaller {
    private ForgeBrainConfigInstaller() {
    }

    public static Path configRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(MaidSoulCoreMod.MOD_ID);
    }

    public static Path promptRoot() {
        return configRoot().resolve("prompts").resolve("zh-CN");
    }

    /**
     * 安装 MaidSoulCore 的统一配置与默认资源。
     *
     * <p>当前真实配置源只有 config/maidsoulcore/dialogue-config.json。
     * 旧的 model/*.properties、conversation/*.properties、debug/*.properties
     * 只在这里读取一次用于迁移，不再作为运行时配置源。</p>
     */
    public static void installIfMissing() {
        Path root = configRoot();
        try {
            Files.createDirectories(root);
            DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(root.resolve("dialogue-config.json"));
            migrateLegacyProperties(root, config);
            DialogueConfigLoader.save(root.resolve("dialogue-config.json"), config);

            installResourceTree("maidsoulcore/prompts/zh-CN", promptRoot(), List.of(
                    ".meta.toml",
                    "default_expressor.prompt",
                    "emoji_content_analysis.prompt",
                    "emoji_content_filtration.prompt",
                    "emoji_replace.prompt",
                    "expression_evaluation.prompt",
                    "expression_select.prompt",
                    "image_description.prompt",
                    "jargon_compare_inference.prompt",
                    "jargon_explainer_summarize.prompt",
                    "jargon_inference_content_only.prompt",
                    "jargon_inference_with_context.prompt",
                    "learn_style.prompt",
                    "maisaka_chat.prompt",
                    "maisaka_chat_merged_timing.prompt",
                    "maisaka_replyer.prompt",
                    "maisaka_timing_gate.prompt",
                    "replyer_user.prompt"
            ));
            installResourceTree("maidsoulcore/characters/prototype-jiuhu", root.resolve("characters").resolve("prototype-jiuhu"), List.of(
                    "character.properties",
                    "traits.properties",
                    "relationship.json",
                    "affect_state.json",
                    "memories/core_memories.jsonl"
            ));
            syncForgeConfigToCoreFiles(root);
        } catch (IOException e) {
            throw new UncheckedIOException("安装 MaidSoulCore Forge 配置失败", e);
        }
    }

    private static void installResourceTree(String resourceRoot, Path targetRoot, List<String> relativeFiles) throws IOException {
        ClassLoader loader = ForgeBrainConfigInstaller.class.getClassLoader();
        for (String relativeFile : relativeFiles) {
            Path target = targetRoot.resolve(relativeFile);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream input = loader.getResourceAsStream(resourceRoot + "/" + relativeFile)) {
                if (input == null) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
            }
        }
    }

    public static void syncForgeConfigToCoreFiles() {
        try {
            syncForgeConfigToCoreFiles(configRoot());
        } catch (IOException e) {
            throw new UncheckedIOException("同步 MaidSoulCore Forge 配置失败", e);
        }
    }

    private static void syncForgeConfigToCoreFiles(Path root) throws IOException {
        Path configPath = root.resolve("dialogue-config.json");
        DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(configPath);
        migrateLegacyProperties(root, config);

        config.messageDebounceMillis = MaidSoulForgeConfig.MESSAGE_DEBOUNCE_MILLIS.get();
        config.model.baseUrl = MaidSoulForgeConfig.BASE_URL.get();
        config.model.model = MaidSoulForgeConfig.MODEL.get();
        config.model.plannerModel = MaidSoulForgeConfig.PLANNER_MODEL.get();
        config.model.replyerModel = MaidSoulForgeConfig.REPLYER_MODEL.get();
        if (config.model.timingModel == null || config.model.timingModel.isBlank()) {
            config.model.timingModel = config.model.model;
        }

        config.vision.enabled = MaidSoulForgeConfig.VISION_ENABLED.get();
        setIfNotBlank(value -> config.vision.baseUrl = value, MaidSoulForgeConfig.VISION_BASE_URL.get());
        String forgeVisionModel = MaidSoulForgeConfig.VISION_MODEL.get();
        config.vision.model = forgeVisionModel == null || forgeVisionModel.isBlank()
                ? VisionConfig.DEFAULT_MODEL
                : forgeVisionModel;

        config.debug.echoTraceToOwnerChat = MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.get();
        config.debug.echoAffectToOwnerChat = MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.get();
        config.debug.echoReplyToOwnerChat = MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.get();

        DialogueConfigLoader.save(configPath, config);
    }

    private static void migrateLegacyProperties(Path root, DialogueCoreConfig config) {
        DialogueConfigLoader.normalize(config);
        Properties llm = loadIfExists(root.resolve("model").resolve("llm.properties"));
        if (!llm.isEmpty()) {
            copyIfPresent(llm, "baseUrl", value -> config.model.baseUrl = value);
            copyIfPresent(llm, "model", value -> config.model.model = value);
            copyIfPresent(llm, "plannerModel", value -> config.model.plannerModel = value);
            copyIfPresent(llm, "replyerModel", value -> config.model.replyerModel = value);
            copyIfPresent(llm, "timingModel", value -> config.model.timingModel = value);
            copyIfPresent(llm, "temperature", value -> config.model.temperature = parseDouble(value, config.model.temperature));
            copyIfPresent(llm, "maxTokens", value -> config.model.maxTokens = parseInt(value, config.model.maxTokens));
            copySecretIfBlank(llm, "apiKey", config.model.apiKey, value -> config.model.apiKey = value);
        }

        Properties vision = loadIfExists(root.resolve("model").resolve("vision.properties"));
        if (!vision.isEmpty()) {
            copyIfPresent(vision, "enabled", value -> config.vision.enabled = Boolean.parseBoolean(value));
            copyIfPresent(vision, "mode", value -> config.vision.mode = value);
            copyIfPresent(vision, "baseUrl", value -> config.vision.baseUrl = value);
            copyIfPresent(vision, "model", value -> config.vision.model = value);
            copyIfPresent(vision, "temperature", value -> config.vision.temperature = parseDouble(value, config.vision.temperature));
            copyIfPresent(vision, "maxTokens", value -> config.vision.maxTokens = parseInt(value, config.vision.maxTokens));
            copyIfPresent(vision, "timeoutMillis", value -> config.vision.timeoutMillis = parseLong(value, config.vision.timeoutMillis));
            copyIfPresent(vision, "maxImageWidth", value -> config.vision.maxImageWidth = parseInt(value, config.vision.maxImageWidth));
            copyIfPresent(vision, "maxImageHeight", value -> config.vision.maxImageHeight = parseInt(value, config.vision.maxImageHeight));
            copyIfPresent(vision, "jpegQuality", value -> config.vision.jpegQuality = (float) parseDouble(value, config.vision.jpegQuality));
            copyIfPresent(vision, "autoCooldownMillis", value -> config.vision.autoCooldownMillis = parseLong(value, config.vision.autoCooldownMillis));
            copyIfPresent(vision, "manualCooldownMillis", value -> config.vision.manualCooldownMillis = parseLong(value, config.vision.manualCooldownMillis));
            copyIfPresent(vision, "prompt", value -> config.vision.prompt = value);
            copySecretIfBlank(vision, "apiKey", config.vision.apiKey, value -> config.vision.apiKey = value);
        }

        Properties flow = loadIfExists(root.resolve("conversation").resolve("flow.properties"));
        copyIfPresent(flow, "historyWindow", value -> config.historyWindow = parseInt(value, config.historyWindow));
        copyIfPresent(flow, "messageDebounceMillis", value -> config.messageDebounceMillis = parseLong(value, config.messageDebounceMillis));
        copyIfPresent(flow, "maxInternalRounds", value -> config.maxInternalRounds = parseInt(value, config.maxInternalRounds));
        copyIfPresent(flow, "defaultWaitSeconds", value -> config.defaultWaitSeconds = parseInt(value, config.defaultWaitSeconds));
        copyIfPresent(flow, "enableIndependentTimingGate", value -> config.enableIndependentTimingGate = Boolean.parseBoolean(value));

        Properties debug = loadIfExists(root.resolve("debug").resolve("trace.properties"));
        copyIfPresent(debug, "echoTraceToOwnerChat", value -> config.debug.echoTraceToOwnerChat = Boolean.parseBoolean(value));
        copyIfPresent(debug, "echoAffectToOwnerChat", value -> config.debug.echoAffectToOwnerChat = Boolean.parseBoolean(value));
        copyIfPresent(debug, "echoReplyToOwnerChat", value -> config.debug.echoReplyToOwnerChat = Boolean.parseBoolean(value));
        copyIfPresent(debug, "recordPrompt", value -> config.debug.echoPromptToOwnerChat = Boolean.parseBoolean(value));
        copyIfPresent(debug, "maxTraceChars", value -> config.debug.maxChatEchoChars = parseInt(value, config.debug.maxChatEchoChars));
    }

    private static Properties loadIfExists(Path path) {
        return Files.exists(path) ? ConfigFiles.load(path) : new Properties();
    }

    private static void copyIfPresent(Properties properties, String key, ValueWriter writer) {
        String value = properties.getProperty(key);
        if (value != null && !value.isBlank()) {
            writer.write(value.trim());
        }
    }

    private static void copySecretIfBlank(Properties properties, String key, String currentValue, ValueWriter writer) {
        if (currentValue != null && !currentValue.isBlank()) {
            return;
        }
        copyIfPresent(properties, key, writer);
    }

    private static void setIfNotBlank(ValueWriter writer, String value) {
        if (value != null && !value.isBlank()) {
            writer.write(value.trim());
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private interface ValueWriter {
        void write(String value);
    }
}
