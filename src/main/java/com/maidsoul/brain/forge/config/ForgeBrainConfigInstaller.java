package com.maidsoul.brain.forge.config;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                    apiKey=sk-da04efbfac05424b97c4ac7256386bfa
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
                    directReplyOnUserMessage=false
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
}
