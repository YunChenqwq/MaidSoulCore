package com.maidsoul.brain.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;
import com.maidsoul.brain.vision.VisionConfig;

/**
 * Forge Mods 配置页入口。
 *
 * <p>这里不是核心配置真源。真源仍然是 config/maidsoulcore 下的分层 properties，
 * 因为 Java 原型和外部 GUI 都要直接读写那些文件。ForgeConfig 只放少量游戏内
 * 最常调的开关和推荐值，启动时由 {@link ForgeBrainConfigInstaller} 同步到
 * MaidSoulCore 自己的配置文件。</p>
 */
public final class MaidSoulForgeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.LongValue MESSAGE_DEBOUNCE_MILLIS;
    public static final ForgeConfigSpec.BooleanValue ECHO_TRACE_TO_OWNER_CHAT;
    public static final ForgeConfigSpec.BooleanValue ECHO_AFFECT_TO_OWNER_CHAT;
    public static final ForgeConfigSpec.BooleanValue ECHO_REPLY_TO_OWNER_CHAT;
    public static final ForgeConfigSpec.ConfigValue<String> BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> PLANNER_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> REPLYER_MODEL;
    public static final ForgeConfigSpec.BooleanValue VISION_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> VISION_BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> VISION_MODEL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("conversation");
        MESSAGE_DEBOUNCE_MILLIS = builder
                .comment("玩家连续输入的合批等待时间，单位毫秒。")
                .defineInRange("messageDebounceMillis", 250L, 0L, 5000L);
        builder.pop();

        builder.push("model");
        BASE_URL = builder
                .comment("OpenAI compatible chat completions endpoint.")
                .define("baseUrl", "https://api.deepseek.com/chat/completions");
        MODEL = builder.define("model", "deepseek-v4-flash");
        PLANNER_MODEL = builder.define("plannerModel", "deepseek-v4-flash");
        REPLYER_MODEL = builder.define("replyerModel", "deepseek-v4-pro");
        builder.pop();

        builder.push("vision");
        VISION_ENABLED = builder
                .comment("启用 Minecraft 截图视觉摘要。图片会先由视觉模型转成短摘要，再写入女仆世界事件。")
                .define("enabled", true);
        VISION_BASE_URL = builder
                .comment("OpenAI compatible vision chat completions endpoint.")
                .define("baseUrl", "https://api.siliconflow.cn/v1/chat/completions");
        VISION_MODEL = builder
                .comment("支持图片输入的视觉模型。默认使用硅基流动可用的 Qwen3-VL 32B。")
                .define("model", VisionConfig.DEFAULT_MODEL);
        builder.pop();

        builder.push("debug");
        ECHO_TRACE_TO_OWNER_CHAT = builder
                .comment("把运行时 trace 发给主人聊天栏，调试时开启。")
                .define("echoTraceToOwnerChat", false);
        ECHO_AFFECT_TO_OWNER_CHAT = builder
                .comment("把情绪摘要发给主人聊天栏，调试时开启。")
                .define("echoAffectToOwnerChat", false);
        ECHO_REPLY_TO_OWNER_CHAT = builder
                .comment("没有 TLM callback 时，把回复也发给主人聊天栏。")
                .define("echoReplyToOwnerChat", false);
        builder.pop();

        SPEC = builder.build();
    }

    private MaidSoulForgeConfig() {
    }
}
