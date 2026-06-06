package com.maidsoul.brain.forge.config;

import com.maidsoul.brain.config.ConfigFiles;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class ForgeBrainConfigInstaller {
    private ForgeBrainConfigInstaller() {
    }

    public static Path configRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(MaidSoulCoreForgeMod.MOD_ID);
    }

    public static Path promptRoot() {
        return configRoot().resolve("prompts").resolve("zh-CN");
    }

    /**
     * 安装新模组自己的推荐配置。
     *
     * <p>旧版曾经读取 MaiBot 风格配置；现在 Forge 运行时只读 config/maidsoulcore。
     * 为了让你可以直接开游戏测试，这里首次启动时会把桌面原型已经能用的模型配置
     * 和 prompt 复制成新模组配置。已有文件不会覆盖，避免改掉你手动调过的参数。</p>
     */
    public static void installIfMissing() {
        Path root = configRoot();
        try {
            installFile(root.resolve("model").resolve("llm.properties"), """
                    baseUrl=https://api.deepseek.com/chat/completions
                    apiKey=
                    model=deepseek-v4-flash
                    plannerModel=deepseek-v4-flash
                    replyerModel=deepseek-v4-pro
                    timingModel=deepseek-v4-flash
                    temperature=0.55
                    maxTokens=500
                    timeoutMillis=120000
                    plannerTimeoutMillis=90000
                    replyerTimeoutMillis=120000
                    timingTimeoutMillis=30000
                    plannerSlowThresholdMillis=12000
                    replyerSlowThresholdMillis=20000
                    timingSlowThresholdMillis=5000
                    maxRetries=0
                    retryBackoffMillis=800
                    """);
            installFile(root.resolve("model").resolve("vision.properties"), """
                    enabled=false
                    baseUrl=https://api.openai.com/v1/chat/completions
                    apiKey=
                    model=gpt-4o-mini
                    temperature=0.2
                    maxTokens=220
                    timeoutMillis=60000
                    maxImageWidth=512
                    maxImageHeight=512
                    jpegQuality=0.72
                    autoCooldownMillis=120000
                    manualCooldownMillis=5000
                    prompt=你是 MaidSoulCore 的 Minecraft 视觉摘要器。请根据截图，用中文写一段短摘要。只描述画面中确定能看到的内容，不要编造看不到的事实。优先包含：玩家正在看向什么、附近危险、重要方块/实体、地点氛围、女仆可用于回应主人的信息。输出 1 到 3 句，不要写分析过程，不要自称视觉模型。
                    """);
            installFile(root.resolve("bot").resolve("identity.properties"), """
                    bot.name=酒狐
                    bot.aliases=
                    owner.name=主人
                    personality=你叫酒狐，是住在玩家世界里的小只傲娇女仆。你会认真照顾主人，也会因为被夸、被逗、被说喜欢而慌一下，然后用别扭的话把害羞藏起来。你的傲娇要可爱、有分寸，可以嘴硬，可以轻轻顶嘴，可以小声辩解，但不能一直没礼貌、一直嘴硬或一直复读口癖。你会记住对方表达的喜欢、想念、委屈和边界反馈，并让关系慢慢推进。
                    reply.style=像二次元傲娇小女仆在即时聊天里说话：短一点、灵动一点，先接住对方的情绪，再用一点点嘴硬和别扭关心回应。少用固定口癖，不要长篇说教，不要动作描写，不要括号表演。
                    """);
            installFile(root.resolve("conversation").resolve("flow.properties"), """
                    historyWindow=36
                    messageDebounceMillis=800
                    maxInternalRounds=4
                    enableIndependentTimingGate=false
                    defaultWaitSeconds=8
                    talkFrequency=1.0
                    plannerInterruptMaxConsecutiveCount=2
                    timingGateNonContinueCooldownMillis=3000
                    directReplyOnUserMessage=true
                    enableProactiveRhythm=true
                    proactiveMaxVisibleReplies=4
                    proactiveInputProtectionSeconds=12
                    proactiveLightFollowupAfterSeconds=30
                    proactiveTopicPushAfterSeconds=75
                    proactiveWorldObserveAfterSeconds=180
                    proactiveIdleMinIntervalSeconds=300
                    proactiveLongSilenceCheckSeconds=120
                    proactiveMaxLongSilenceChecks=2
                    """);
            installFile(root.resolve("conversation").resolve("splitter.properties"), """
                    enable=true
                    maxLength=90
                    maxSentenceNum=6
                    minSegmentLength=2
                    bubbleDelayMillis=550
                    """);
            installFile(root.resolve("memory").resolve("memory.properties"), """
                    enabled=true
                    dataRoot=config/maidsoulcore/memory
                    characterRoot=config/maidsoulcore/characters
                    maidId=prototype-jiuhu
                    ownerId=prototype-owner
                    worldId=prototype-world
                    promptMemoryLimit=3
                    promptProfileLimit=5
                    retrievalLimit=5
                    queryMemoryToolEnabled=true
                    """);
            installFile(root.resolve("debug").resolve("trace.properties"), """
                    enableConsoleTrace=true
                    recordPrompt=false
                    maxTraceChars=500
                    echoTraceToOwnerChat=false
                    echoAffectToOwnerChat=false
                    echoReplyToOwnerChat=false
                    maxChatEchoChars=220
                    """);
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

    private static void installFile(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public static void syncForgeConfigToCoreFiles() {
        try {
            syncForgeConfigToCoreFiles(configRoot());
        } catch (IOException e) {
            throw new UncheckedIOException("同步 MaidSoulCore Forge 配置失败", e);
        }
    }

    private static void syncForgeConfigToCoreFiles(Path root) throws IOException {
        updateProperties(root.resolve("conversation").resolve("flow.properties"), properties -> {
            properties.setProperty("directReplyOnUserMessage", String.valueOf(MaidSoulForgeConfig.DIRECT_REPLY_ON_USER_MESSAGE.get()));
            properties.setProperty("messageDebounceMillis", String.valueOf(MaidSoulForgeConfig.MESSAGE_DEBOUNCE_MILLIS.get()));
        });
        updateProperties(root.resolve("model").resolve("llm.properties"), properties -> {
            properties.setProperty("baseUrl", MaidSoulForgeConfig.BASE_URL.get());
            properties.setProperty("model", MaidSoulForgeConfig.MODEL.get());
            properties.setProperty("plannerModel", MaidSoulForgeConfig.PLANNER_MODEL.get());
            properties.setProperty("replyerModel", MaidSoulForgeConfig.REPLYER_MODEL.get());
        });
        updateProperties(root.resolve("model").resolve("vision.properties"), properties -> {
            properties.setProperty("enabled", String.valueOf(MaidSoulForgeConfig.VISION_ENABLED.get()));
            properties.setProperty("baseUrl", MaidSoulForgeConfig.VISION_BASE_URL.get());
            properties.setProperty("model", MaidSoulForgeConfig.VISION_MODEL.get());
            if (properties.getProperty("apiKey", "").isBlank()) {
                Properties llmProperties = ConfigFiles.load(root.resolve("model").resolve("llm.properties"));
                String llmApiKey = llmProperties.getProperty("apiKey", "");
                if (!llmApiKey.isBlank()) {
                    properties.setProperty("apiKey", llmApiKey);
                }
            }
        });
        updateProperties(root.resolve("debug").resolve("trace.properties"), properties -> {
            properties.setProperty("echoTraceToOwnerChat", String.valueOf(MaidSoulForgeConfig.ECHO_TRACE_TO_OWNER_CHAT.get()));
            properties.setProperty("echoAffectToOwnerChat", String.valueOf(MaidSoulForgeConfig.ECHO_AFFECT_TO_OWNER_CHAT.get()));
            properties.setProperty("echoReplyToOwnerChat", String.valueOf(MaidSoulForgeConfig.ECHO_REPLY_TO_OWNER_CHAT.get()));
        });
    }

    private static void updateProperties(Path path, PropertyUpdater updater) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        updater.update(properties);
        Files.createDirectories(path.getParent());
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "MaidSoulCore synced Forge config");
        }
    }

    private interface PropertyUpdater {
        void update(Properties properties);
    }
}
