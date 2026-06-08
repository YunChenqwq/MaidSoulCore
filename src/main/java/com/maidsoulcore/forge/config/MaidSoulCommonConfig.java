package com.maidsoulcore.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Forge Common 配置定义。
 * <p>
 * 这里集中管理 MaidSoulCore 的公共配置项，
 * 包括 MaiBot 对接、主动陪伴行为、视觉摘要和调试输出。
 */
public final class MaidSoulCommonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> MAIBOT_CONFIG_DIR;
    public static final ForgeConfigSpec.BooleanValue AUTO_SYNC_TLM_SITE;
    public static final ForgeConfigSpec.ConfigValue<String> TLM_SITE_ID;
    public static final ForgeConfigSpec.BooleanValue AUTO_APPLY_CHAT_PRESET;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROACTIVE_CHAT;
    public static final ForgeConfigSpec.BooleanValue IDLE_CHAT_ENABLED;
    public static final ForgeConfigSpec.IntValue IDLE_CHAT_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue PROACTIVE_SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue PROACTIVE_CHAT_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue VISION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue VISION_LLM_INTERPRET_ENABLED;
    public static final ForgeConfigSpec.IntValue VISION_CHAT_ACTIVE_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue VISION_CHAT_ACTIVE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue VISION_IDLE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue ENVIRONMENT_REPLY_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue TOPIC_DEDUP_ENABLED;
    public static final ForgeConfigSpec.IntValue TOPIC_DEDUP_WINDOW_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOCAL_COMMAND_FAST_PATH_ENABLED;
    public static final ForgeConfigSpec.IntValue CHAT_RUNTIME_DEBOUNCE_MILLIS;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_MEMORY_ENABLED;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MEMORY_MAX_LINES;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MEMORY_PROMPT_LINES;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MEMORY_MAX_OWNER_NOTES;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MEMORY_LINE_MAX_CHARS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MEMORY_NOTE_MAX_CHARS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_OWNER_NOTE_TRIGGERS;
    public static final ForgeConfigSpec.BooleanValue LIFE_MEMORY_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> LIFE_MEMORY_ROOT_DIR;
    public static final ForgeConfigSpec.BooleanValue LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED;
    public static final ForgeConfigSpec.IntValue LIFE_MEMORY_MAX_PROMPT_EPISODES;
    public static final ForgeConfigSpec.IntValue LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS;
    public static final ForgeConfigSpec.IntValue LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY;
    public static final ForgeConfigSpec.IntValue LIFE_MEMORY_RAW_LINES_PER_EPISODE;
    public static final ForgeConfigSpec.IntValue CONVERSATION_PACING_WAIT_MILLIS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_TURN_TIMEOUT_MILLIS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MODEL_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_MODEL_MAX_RETRY;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_LLM_TIMING_GATE_ENABLED;
    public static final ForgeConfigSpec.IntValue CONVERSATION_LLM_TIMING_GATE_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_LLM_TIMING_GATE_DEFAULT_WAIT_MILLIS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_WAIT_TRIGGERS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_NO_REPLY_TRIGGERS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_FINISH_TRIGGERS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_HOT_COMMAND_KEYWORDS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_REPLY_MAX_CHARS;
    public static final ForgeConfigSpec.ConfigValue<String> CONVERSATION_EMPTY_REPLY_FALLBACK;
    public static final ForgeConfigSpec.ConfigValue<String> CONVERSATION_REPEAT_REPLY_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_REPEAT_GUARD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_SPLITTER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_SPLITTER_PROTECT_KAOMOJI;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_MAX_SEGMENTS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_FORCE_BREAK_CHARS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_SHORT_TEXT_CHARS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPEECH_MIN_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPEECH_MAX_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_REFLECTION_ENABLED;
    public static final ForgeConfigSpec.IntValue CONVERSATION_REFLECTION_EVERY_LINES;
    public static final ForgeConfigSpec.IntValue CONVERSATION_REFLECTION_RECENT_LINES;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_STYLE_ROTATION_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_STYLE_VARIANTS;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_HEARTFLOW_REWRITE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED;
    public static final ForgeConfigSpec.IntValue CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_FOLLOWUP_ENABLED;
    public static final ForgeConfigSpec.IntValue CONVERSATION_FOLLOWUP_DELAY_MILLIS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_FOLLOWUP_TRIGGERS;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_INTERRUPT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_INTERRUPT_SPEECH_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONVERSATION_INTERRUPT_CANCEL_OWNER_TURN;
    public static final ForgeConfigSpec.IntValue CONVERSATION_INTERRUPT_ACTIVE_SECONDS;
    public static final ForgeConfigSpec.IntValue CONVERSATION_INTERRUPT_COOLDOWN_MILLIS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CONVERSATION_INTERRUPT_EVENT_TYPES;
    public static final ForgeConfigSpec.BooleanValue EMOTION_SYSTEM_ENABLED;
    public static final ForgeConfigSpec.BooleanValue EMOTION_TRACE_ECHO_ENABLED;
    public static final ForgeConfigSpec.IntValue EMOTION_BASELINE_MOOD;
    public static final ForgeConfigSpec.IntValue EMOTION_BASELINE_TRUST;
    public static final ForgeConfigSpec.IntValue EMOTION_RECOVERY_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue EMOTION_MOOD_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_TRUST_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_STRESS_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_PAIN_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_FEAR_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_ANGER_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_CONFUSION_RECOVERY_STEP;
    public static final ForgeConfigSpec.BooleanValue EMOTION_DAY_RECOVERY_ENABLED;
    public static final ForgeConfigSpec.IntValue EMOTION_DAY_RECOVERY_PERCENT;
    public static final ForgeConfigSpec.IntValue EMOTION_SLEEP_RECOVERY_MIN_TICKS;
    public static final ForgeConfigSpec.IntValue EMOTION_SLEEP_RECOVERY_PERCENT;
    public static final ForgeConfigSpec.IntValue EMOTION_DAY_GRUDGE_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_SLEEP_GRUDGE_RECOVERY_STEP;
    public static final ForgeConfigSpec.IntValue EMOTION_UNRESOLVED_SECONDS;
    public static final ForgeConfigSpec.IntValue EMOTION_OWNER_ATTACK_MOOD_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_OWNER_ATTACK_TRUST_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_HOSTILE_ATTACK_MOOD_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_POSITIVE_EVENT_MOOD_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_COMFORT_MOOD_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_COMFORT_TRUST_DELTA;
    public static final ForgeConfigSpec.IntValue EMOTION_SOOTHED_MOOD_THRESHOLD;
    public static final ForgeConfigSpec.IntValue EMOTION_SOOTHED_STRESS_THRESHOLD;
    public static final ForgeConfigSpec.IntValue EMOTION_SOOTHED_PAIN_THRESHOLD;
    public static final ForgeConfigSpec.IntValue PERSONA_SENSITIVITY;
    public static final ForgeConfigSpec.IntValue PERSONA_FORGIVENESS;
    public static final ForgeConfigSpec.IntValue PERSONA_ATTACHMENT;
    public static final ForgeConfigSpec.IntValue PERSONA_PRIDE;
    public static final ForgeConfigSpec.IntValue PERSONA_FEARFULNESS;
    public static final ForgeConfigSpec.IntValue PERSONA_ANGER_EXPRESSIVENESS;
    public static final ForgeConfigSpec.IntValue PERSONA_COMFORT_NEED;
    public static final ForgeConfigSpec.IntValue PERSONA_BOUNDARY_STRENGTH;
    public static final ForgeConfigSpec.ConfigValue<String> PERSONA_COPING_STYLE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_FOLLOW_ON_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_SIT_ON_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_SIT_OFF_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS;
    public static final ForgeConfigSpec.IntValue LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> LOCAL_COMMAND_ACK_TEMPLATE;
    public static final ForgeConfigSpec.BooleanValue CHAT_FOCUS_MODE_ENABLED;
    public static final ForgeConfigSpec.IntValue CHAT_FOCUS_HOLD_SECONDS;
    public static final ForgeConfigSpec.IntValue CHAT_FOCUS_VISION_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue FULL_SILENT_MODE_ENABLED;
    public static final ForgeConfigSpec.IntValue IDLE_TOPIC_RETRY_SECONDS;
    public static final ForgeConfigSpec.IntValue IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue IDLE_TOPIC_DORMANT_RECHECK_SECONDS;
    public static final ForgeConfigSpec.BooleanValue TOPIC_DEDUP_TRACE_ECHO_ENABLED;
    public static final ForgeConfigSpec.IntValue NORMAL_HOSTILE_ALERT_THRESHOLD;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HIGH_RISK_MOBS;
    public static final ForgeConfigSpec.BooleanValue TRACE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRACE_LOG_TO_FILE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_CHAT_ECHO_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRACE_CHAT_ECHO_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRACE_CHAT_VERBOSE_ECHO_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED;
    public static final ForgeConfigSpec.IntValue TRACE_BUFFER_SIZE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("integration");
        MAIBOT_CONFIG_DIR = builder
                .comment("MaiBot 配置目录，目录内需要包含 bot_config.toml 和 model_config.toml")
                .define("maibotConfigDir", "E:/wallpaper/MaidSoulCore/config");
        AUTO_SYNC_TLM_SITE = builder
                .comment("启动时自动根据 MaiBot 的 model_config.toml 创建或更新 TLM 兼容站点")
                .define("autoSyncTlmSite", true);
        TLM_SITE_ID = builder
                .comment("由 MaidSoulCore 管理的 TLM 站点 ID")
                .define("tlmSiteId", "maidsoulcore-openai");
        AUTO_APPLY_CHAT_PRESET = builder
                .comment("自动把 MaidSoulCore 的聊天预设和模型绑定写入女仆 AI 配置")
                .define("autoApplyChatPreset", true);
        builder.pop();

        builder.push("behavior");
        ENABLE_PROACTIVE_CHAT = builder
                .comment("启用 MaidSoulCore 的事件驱动主动陪伴回复")
                .define("enableProactiveChat", true);
        IDLE_CHAT_ENABLED = builder
                .comment("启用女仆在平静陪伴状态下的待机搭话")
                .define("idleChatEnabled", true);
        IDLE_CHAT_INTERVAL_SECONDS = builder
                .comment("两次待机搭话之间的最小间隔，单位为秒")
                .defineInRange("idleChatIntervalSeconds", 90, 10, 3600);
        PROACTIVE_SCAN_INTERVAL_TICKS = builder
                .comment("女仆扫描周围环境与敌对摘要的频率，单位为 tick")
                .defineInRange("proactiveScanIntervalTicks", 40, 10, 1200);
        PROACTIVE_CHAT_COOLDOWN_SECONDS = builder
                .comment("单个女仆主动说话的全局冷却时间，单位为秒")
                .defineInRange("proactiveChatCooldownSeconds", 30, 1, 600);
        VISION_ENABLED = builder
                .comment("启用主人视角摘要与视觉解释链路")
                .define("visionEnabled", true);
        VISION_LLM_INTERPRET_ENABLED = builder
                .comment("启用模型解释视角摘要；关闭时只做本地轻量摘要，性能更好")
                .define("visionLlmInterpretEnabled", false);
        VISION_CHAT_ACTIVE_WINDOW_SECONDS = builder
                .comment("主人最近说过话后，视觉采样进入活跃模式的持续时间，单位为秒")
                .defineInRange("visionChatActiveWindowSeconds", 30, 5, 600);
        VISION_CHAT_ACTIVE_INTERVAL_SECONDS = builder
                .comment("处于活跃聊天窗口时的视觉采样间隔，单位为秒")
                .defineInRange("visionChatActiveIntervalSeconds", 20, 2, 300);
        VISION_IDLE_INTERVAL_SECONDS = builder
                .comment("未处于活跃聊天窗口时的视觉采样间隔，单位为秒")
                .defineInRange("visionIdleIntervalSeconds", 90, 5, 600);
        ENVIRONMENT_REPLY_COOLDOWN_SECONDS = builder
                .comment("可爱动物、附近玩家等非关键环境回复的冷却时间，单位为秒")
                .defineInRange("environmentReplyCooldownSeconds", 20, 1, 600);
        TOPIC_DEDUP_ENABLED = builder
                .comment("启用短时主题去重，避免环境话题短时间重复")
                .define("topicDedupEnabled", true);
        TOPIC_DEDUP_WINDOW_SECONDS = builder
                .comment("重复主题的抑制窗口，单位为秒")
                .defineInRange("topicDedupWindowSeconds", 180, 10, 3600);
        LOCAL_COMMAND_FAST_PATH_ENABLED = builder
                .comment("鍚敤涓讳汉鏄庣‘鍛戒护鐨勬湰鍦板揩璺В鏋愬拰璁″垝鎻愪氦")
                .define("localCommandFastPathEnabled", true);
        CHAT_RUNTIME_DEBOUNCE_MILLIS = builder
                .comment("鑱婂ぉ杩愯鏃剁殑鍐呴儴闃熷垪鍘婚攼鍔ㄥ欢杩燂紝鍗曚綅涓烘绉?")
                .defineInRange("chatRuntimeDebounceMillis", 1200, 0, 5000);
        builder.push("conversation");
        CONVERSATION_MEMORY_ENABLED = builder
                .comment("启用会话本地干净历史和主人偏好线索")
                .define("memoryEnabled", true);
        CONVERSATION_MEMORY_MAX_LINES = builder
                .comment("每只女仆保留的本地干净对话行数")
                .defineInRange("memoryMaxLines", 18, 2, 200);
        CONVERSATION_MEMORY_PROMPT_LINES = builder
                .comment("普通聊天 prompt 中注入的本地干净对话行数")
                .defineInRange("memoryPromptLines", 8, 0, 50);
        CONVERSATION_MEMORY_MAX_OWNER_NOTES = builder
                .comment("每只女仆保留的主人偏好线索数量")
                .defineInRange("memoryMaxOwnerNotes", 8, 0, 100);
        CONVERSATION_MEMORY_LINE_MAX_CHARS = builder
                .comment("单条本地对话历史的最大字符数")
                .defineInRange("memoryLineMaxChars", 120, 20, 500);
        CONVERSATION_MEMORY_NOTE_MAX_CHARS = builder
                .comment("单条主人偏好线索的最大字符数")
                .defineInRange("memoryNoteMaxChars", 80, 20, 500);
        CONVERSATION_OWNER_NOTE_TRIGGERS = builder
                .comment("命中这些文本片段时，把主人消息记录为偏好线索")
                .defineListAllowEmpty(
                        java.util.List.of("ownerNoteTriggers"),
                        () -> java.util.List.of("我喜欢", "我想要", "我讨厌", "我不喜欢", "我是", "我叫"),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        LIFE_MEMORY_ENABLED = builder
                .comment("Enable per-maid portable life memory files under the configured root directory.")
                .define("lifeMemoryEnabled", true);
        LIFE_MEMORY_ROOT_DIR = builder
                .comment("Portable life memory root. Relative paths are resolved under the Forge config directory.")
                .define("lifeMemoryRootDir", "maidsoulcore/life_memory");
        LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED = builder
                .comment("Consolidate each maid's draft memory automatically when the Minecraft day changes.")
                .define("lifeMemoryDailyConsolidationEnabled", true);
        LIFE_MEMORY_MAX_PROMPT_EPISODES = builder
                .comment("Maximum consolidated life episodes injected into a chat prompt.")
                .defineInRange("lifeMemoryMaxPromptEpisodes", 3, 0, 12);
        LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS = builder
                .comment("Maximum durable life understandings injected into a chat prompt.")
                .defineInRange("lifeMemoryMaxPromptUnderstandings", 4, 0, 12);
        LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY = builder
                .comment("Approximate draft line budget per maid day before old unmerged draft is trimmed.")
                .defineInRange("lifeMemoryMaxDraftLinesPerDay", 160, 20, 2000);
        LIFE_MEMORY_RAW_LINES_PER_EPISODE = builder
                .comment("Maximum raw dialogue/event lines stored inside one consolidated episode.")
                .defineInRange("lifeMemoryRawLinesPerEpisode", 80, 5, 500);
        CONVERSATION_PACING_WAIT_MILLIS = builder
                .comment("本地节奏门判断主人可能还没说完时，等待多久再继续，单位毫秒")
                .defineInRange("pacingWaitMillis", 6000, 0, 30000);
        CONVERSATION_TURN_TIMEOUT_MILLIS = builder
                .comment("普通聊天单轮最大等待时间，超时会清理思考气泡并给出可见兜底，单位毫秒")
                .defineInRange("turnTimeoutMillis", 45000, 5000, 180000);
        CONVERSATION_MODEL_TIMEOUT_SECONDS = builder
                .comment("普通聊天模型 HTTP 单次请求超时，覆盖外部模型配置中过长的 timeout，单位秒")
                .defineInRange("modelTimeoutSeconds", 25, 5, 120);
        CONVERSATION_MODEL_MAX_RETRY = builder
                .comment("普通聊天模型最大尝试次数；游戏里建议 1，避免思考气泡挂几分钟")
                .defineInRange("modelMaxRetry", 1, 1, 5);
        CONVERSATION_LLM_TIMING_GATE_ENABLED = builder
                .comment("启用 LLM 话轮门控；主动事件、待机搭话、沉默续话先判断 continue/wait/no_action，再决定是否生成台词")
                .define("llmTimingGateEnabled", true);
        CONVERSATION_LLM_TIMING_GATE_TIMEOUT_SECONDS = builder
                .comment("LLM 话轮门控请求超时，单位秒；建议比主聊天短")
                .defineInRange("llmTimingGateTimeoutSeconds", 6, 3, 30);
        CONVERSATION_LLM_TIMING_GATE_DEFAULT_WAIT_MILLIS = builder
                .comment("LLM 话轮门控返回 wait 但未给等待时间时使用的默认等待毫秒数")
                .defineInRange("llmTimingGateDefaultWaitMillis", 30000, 5000, 300000);
        CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS = builder
                .comment("Timing Gate 做出 wait/no_action/finish 后，对软输入继续不回复的短冷却，单位毫秒")
                .defineInRange("timingNonContinueCooldownMillis", 1200, 0, 30000);
        CONVERSATION_WAIT_TRIGGERS = builder
                .comment("命中这些文本片段时，本轮等待后再处理")
                .defineListAllowEmpty(
                        java.util.List.of("waitTriggers"),
                        () -> java.util.List.of("等一下", "等下", "我想想", "让我想想", "等等我", "先等"),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_NO_REPLY_TRIGGERS = builder
                .comment("命中这些文本片段时，本轮不回复")
                .defineListAllowEmpty(
                        java.util.List.of("noReplyTriggers"),
                        () -> java.util.List.of("不用回", "别回", "先别说话", "闭嘴", "安静一下"),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_FINISH_TRIGGERS = builder
                .comment("命中这些文本片段时，结束当前聊天轮次")
                .defineListAllowEmpty(
                        java.util.List.of("finishTriggers"),
                        () -> java.util.List.of("结束对话", "先到这里", "不聊了", "待会再聊"),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_HOT_COMMAND_KEYWORDS = builder
                .comment("命中这些文本片段时，普通聊天升级为命令/动作链路")
                .defineListAllowEmpty(
                        java.util.List.of("hotCommandKeywords"),
                        () -> java.util.List.of(
                                "跟随", "跟着", "坐下", "起来", "站起",
                                "攻击", "打", "清理", "杀", "目标",
                                "切换任务", "切换日程", "回家", "回去", "返回家", "先", "然后", "再"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_REPLY_MAX_CHARS = builder
                .comment("普通聊天可见回复最大字符数，超过后尽量截到句末")
                .defineInRange("replyMaxChars", 180, 20, 1000);
        CONVERSATION_EMPTY_REPLY_FALLBACK = builder
                .comment("模型空回复时使用的兜底可见回复")
                .define("emptyReplyFallback", "我在呢，主人。");
        CONVERSATION_REPEAT_REPLY_FALLBACK = builder
                .comment("连续回复复读时使用的兜底可见回复")
                .define("repeatReplyFallback", "嗯嗯，我听着呢。主人继续说就好。");
        CONVERSATION_REPEAT_GUARD_ENABLED = builder
                .comment("启用连续回复复读抑制")
                .define("repeatGuardEnabled", true);
        CONVERSATION_SPLITTER_ENABLED = builder
                .comment("启用可见回复分句，把长回复拆成更像聊天的短句")
                .define("splitterEnabled", true);
        CONVERSATION_SPLITTER_PROTECT_KAOMOJI = builder
                .comment("分句前保护颜文字和括号表情，避免被错误拆开")
                .define("splitterProtectKaomoji", true);
        CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL = builder
                .comment("分句数量超过上限时是否整段返回原文；关闭后会保留前 N 段，避免长回复看起来完全没分句")
                .define("splitterOverflowReturnOriginal", false);
        CONVERSATION_SPLITTER_MAX_SEGMENTS = builder
                .comment("单次回复最多拆成多少条可见短句；默认 8 段，更接近自然聊天分句器的节奏")
                .defineInRange("splitterMaxSegments", 8, 1, 12);
        CONVERSATION_SPLITTER_FORCE_BREAK_CHARS = builder
                .comment("保留配置项：旧版硬切长度。新版默认不再按长度强切，仅作为兼容开关")
                .defineInRange("splitterForceBreakChars", 80, 8, 120);
        CONVERSATION_SPLITTER_SHORT_TEXT_CHARS = builder
                .comment("短文本阈值；低于该长度时更倾向合并，不拆太碎")
                .defineInRange("splitterShortTextChars", 12, 1, 80);
        CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS = builder
                .comment("中等文本阈值；超过该长度后更倾向拆成多句")
                .defineInRange("splitterMediumTextChars", 32, 2, 160);
        CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT = builder
                .comment("短文本分裂强度百分比，越低越容易合并")
                .defineInRange("splitterShortSplitPercent", 20, 0, 100);
        CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT = builder
                .comment("中等文本分裂强度百分比")
                .defineInRange("splitterMediumSplitPercent", 60, 0, 100);
        CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT = builder
                .comment("长文本分裂强度百分比")
                .defineInRange("splitterLongSplitPercent", 70, 0, 100);
        CONVERSATION_SPEECH_MIN_DELAY_TICKS = builder
                .comment("逐句输出时，两句之间的最小等待 tick")
                .defineInRange("speechMinDelayTicks", 8, 0, 200);
        CONVERSATION_SPEECH_MAX_DELAY_TICKS = builder
                .comment("逐句输出时，两句之间的最大等待 tick")
                .defineInRange("speechMaxDelayTicks", 36, 1, 400);
        CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK = builder
                .comment("短句每多少有效字符增加 1 tick 等待")
                .defineInRange("speechCharsPerDelayTick", 2, 1, 20);
        CONVERSATION_REFLECTION_ENABLED = builder
                .comment("启用轻量会话反思摘要")
                .define("reflectionEnabled", true);
        CONVERSATION_REFLECTION_EVERY_LINES = builder
                .comment("每新增多少条本地对话历史后更新一次轻量反思")
                .defineInRange("reflectionEveryLines", 8, 1, 100);
        CONVERSATION_REFLECTION_RECENT_LINES = builder
                .comment("生成轻量反思时读取的最近本地对话行数")
                .defineInRange("reflectionRecentLines", 8, 1, 50);
        CONVERSATION_STYLE_ROTATION_ENABLED = builder
                .comment("启用普通聊天表达风格轮换")
                .define("styleRotationEnabled", true);
        CONVERSATION_STYLE_VARIANTS = builder
                .comment("普通聊天表达风格候选列表")
                .defineListAllowEmpty(
                        java.util.List.of("styleVariants"),
                        () -> java.util.List.of(
                                "温柔贴近：像正在陪主人说话，短句，有一点关心。",
                                "轻松俏皮：可以稍微撒娇，但不要夸张，不要堆颜文字。",
                                "认真可靠：先把主人的话接住，再给简短回应。",
                                "安静陪伴：语气轻一点，像在旁边小声回应。",
                                "自然日常：像熟人聊天，不解释系统，不写报告。"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_HEARTFLOW_REWRITE_ENABLED = builder
                .comment("启用会话心流的语义复读检测与本地质量检查；不等于一定发起第二次模型请求")
                .define("heartflowRewriteEnabled", true);
        CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED = builder
                .comment("质量检查失败时允许再请求一次模型重写；游戏内默认关闭，避免主聊天耗时翻倍和 timeout")
                .define("heartflowSecondPassEnabled", false);
        CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT = builder
                .comment("会话心流认为两句回复过于相似的阈值，越低越严格，单位百分比")
                .defineInRange("heartflowSimilarityPercent", 78, 50, 98);
        CONVERSATION_FOLLOWUP_ENABLED = builder
                .comment("启用普通聊天后的同话题续话；主人沉默一会儿时，女仆可以自然补一句或推进话题")
                .define("followupEnabled", true);
        CONVERSATION_FOLLOWUP_DELAY_MILLIS = builder
                .comment("女仆主回复结束后，等待多久再考虑同话题续话")
                .defineInRange("followupDelayMillis", 22000, 1000, 60000);
        CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN = builder
                .comment("每轮主人输入后，最多允许几次主动续话")
                .defineInRange("followupMaxPerOwnerTurn", 1, 0, 3);
        CONVERSATION_FOLLOWUP_TRIGGERS = builder
                .comment("这些文本片段会让沉默后的续话更容易触发")
                .defineListAllowEmpty(
                        java.util.List.of("followupTriggers"),
                        () -> java.util.List.of(
                                "?", "？", "喜欢", "讨厌", "累", "疼", "生气", "难过",
                                "对不起", "抱歉", "陪", "记住", "晚安", "害怕", "没事"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        CONVERSATION_INTERRUPT_ENABLED = builder
                .comment("启用受击、死亡、失败等急迫事件对当前聊天主题的短时打断")
                .define("interruptEnabled", true);
        CONVERSATION_INTERRUPT_SPEECH_ENABLED = builder
                .comment("急迫事件到来时，先清掉旧的待播句子，再说新事件")
                .define("interruptSpeechEnabled", true);
        CONVERSATION_INTERRUPT_CANCEL_OWNER_TURN = builder
                .comment("急迫事件到来时，丢弃仍在生成中的旧主人聊天回复，避免继续旧话题")
                .define("interruptCancelOwnerTurn", true);
        CONVERSATION_INTERRUPT_ACTIVE_SECONDS = builder
                .comment("急迫事件会在普通聊天 prompt 中保留多久，单位为秒")
                .defineInRange("interruptActiveSeconds", 12, 1, 120);
        CONVERSATION_INTERRUPT_COOLDOWN_MILLIS = builder
                .comment("同一个急迫事件详情刷新打断状态的最小间隔，单位为毫秒")
                .defineInRange("interruptCooldownMillis", 1200, 0, 30000);
        CONVERSATION_INTERRUPT_EVENT_TYPES = builder
                .comment("允许打断当前话题的事件类型；后缀 * 或 .* 表示前缀匹配")
                .defineListAllowEmpty(
                        java.util.List.of("interruptEventTypes"),
                        () -> java.util.List.of(
                                "maid.attacked*",
                                "maid.death",
                                "world.hostile_summary.changed",
                                "maid.action.failed",
                                "maid.plan.failed",
                                "owner.command.*"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        EMOTION_SYSTEM_ENABLED = builder
                .comment("启用量化情绪系统：心情、信任、压力、疼痛和未解决话题")
                .define("emotionSystemEnabled", true);
        EMOTION_TRACE_ECHO_ENABLED = builder
                .comment("把情绪动作、理由和分值输出到调试聊天日志")
                .define("emotionTraceEchoEnabled", true);
        EMOTION_BASELINE_MOOD = builder
                .comment("心情基线，情绪恢复时会逐步靠近该值")
                .defineInRange("emotionBaselineMood", 70, 0, 100);
        EMOTION_BASELINE_TRUST = builder
                .comment("信任基线，情绪恢复时会逐步靠近该值")
                .defineInRange("emotionBaselineTrust", 70, 0, 100);
        EMOTION_RECOVERY_INTERVAL_TICKS = builder
                .comment("情绪自然恢复检查间隔，单位 tick")
                .defineInRange("emotionRecoveryIntervalTicks", 100, 20, 6000);
        EMOTION_MOOD_RECOVERY_STEP = builder
                .comment("短期自然缓和时心情向基线移动的步长；只代表几秒内缓一口气，不代表完全想开")
                .defineInRange("emotionMoodRecoveryStep", 1, 0, 20);
        EMOTION_TRUST_RECOVERY_STEP = builder
                .comment("短期自然缓和时信任向基线移动的步长；建议为 0，信任主要靠安抚、睡眠和跨天恢复")
                .defineInRange("emotionTrustRecoveryStep", 0, 0, 10);
        EMOTION_STRESS_RECOVERY_STEP = builder
                .comment("每次自然恢复时压力下降步长")
                .defineInRange("emotionStressRecoveryStep", 2, 0, 30);
        EMOTION_PAIN_RECOVERY_STEP = builder
                .comment("每次自然恢复时疼痛下降步长")
                .defineInRange("emotionPainRecoveryStep", 2, 0, 30);
        EMOTION_FEAR_RECOVERY_STEP = builder
                .comment("短期自然缓和时恐惧下降步长")
                .defineInRange("emotionFearRecoveryStep", 1, 0, 30);
        EMOTION_ANGER_RECOVERY_STEP = builder
                .comment("短期自然缓和时愤怒下降步长；建议较低，避免刚被冒犯就立刻消气")
                .defineInRange("emotionAngerRecoveryStep", 0, 0, 30);
        EMOTION_CONFUSION_RECOVERY_STEP = builder
                .comment("短期自然缓和时困惑下降步长")
                .defineInRange("emotionConfusionRecoveryStep", 1, 0, 30);
        EMOTION_DAY_RECOVERY_ENABLED = builder
                .comment("启用跨天阶段性恢复；新的一天会恢复一部分情绪，但不会完全清空关系痕迹")
                .define("emotionDayRecoveryEnabled", true);
        EMOTION_DAY_RECOVERY_PERCENT = builder
                .comment("跨天时向稳定状态恢复的百分比；100 才是完全恢复，建议 20~45")
                .defineInRange("emotionDayRecoveryPercent", 30, 0, 100);
        EMOTION_SLEEP_RECOVERY_MIN_TICKS = builder
                .comment("睡眠达到多少 tick 才触发睡醒恢复，1200 tick 约 60 秒")
                .defineInRange("emotionSleepRecoveryMinTicks", 1200, 20, 24000);
        EMOTION_SLEEP_RECOVERY_PERCENT = builder
                .comment("睡醒时向稳定状态恢复的百分比；通常高于跨天，但仍不完全恢复")
                .defineInRange("emotionSleepRecoveryPercent", 45, 0, 100);
        EMOTION_DAY_GRUDGE_RECOVERY_STEP = builder
                .comment("跨天时芥蒂/近期冒犯次数下降步长")
                .defineInRange("emotionDayGrudgeRecoveryStep", 6, 0, 100);
        EMOTION_SLEEP_GRUDGE_RECOVERY_STEP = builder
                .comment("睡醒时芥蒂/近期冒犯次数下降步长")
                .defineInRange("emotionSleepGrudgeRecoveryStep", 10, 0, 100);
        EMOTION_UNRESOLVED_SECONDS = builder
                .comment("受击等事件未被安抚时，保留为当前情绪话题的时间")
                .defineInRange("emotionUnresolvedSeconds", 45, 3, 600);
        EMOTION_OWNER_ATTACK_MOOD_DELTA = builder
                .comment("主人攻击女仆时心情下降量")
                .defineInRange("emotionOwnerAttackMoodDelta", 24, 0, 100);
        EMOTION_OWNER_ATTACK_TRUST_DELTA = builder
                .comment("主人攻击女仆时信任下降量")
                .defineInRange("emotionOwnerAttackTrustDelta", 12, 0, 100);
        EMOTION_HOSTILE_ATTACK_MOOD_DELTA = builder
                .comment("非主人伤害女仆时心情下降量")
                .defineInRange("emotionHostileAttackMoodDelta", 8, 0, 100);
        EMOTION_POSITIVE_EVENT_MOOD_DELTA = builder
                .comment("喂食、温和互动等正向事件带来的心情提升")
                .defineInRange("emotionPositiveEventMoodDelta", 4, 0, 100);
        EMOTION_COMFORT_MOOD_DELTA = builder
                .comment("主人道歉、安抚时心情恢复量")
                .defineInRange("emotionComfortMoodDelta", 16, 0, 100);
        EMOTION_COMFORT_TRUST_DELTA = builder
                .comment("主人道歉、安抚时信任恢复量")
                .defineInRange("emotionComfortTrustDelta", 7, 0, 100);
        EMOTION_SOOTHED_MOOD_THRESHOLD = builder
                .comment("心情达到该值且压力/疼痛足够低时，视为被哄好")
                .defineInRange("emotionSoothedMoodThreshold", 58, 0, 100);
        EMOTION_SOOTHED_STRESS_THRESHOLD = builder
                .comment("压力低于该值时可结束未解决情绪话题")
                .defineInRange("emotionSoothedStressThreshold", 25, 0, 100);
        EMOTION_SOOTHED_PAIN_THRESHOLD = builder
                .comment("疼痛低于该值时可结束未解决情绪话题")
                .defineInRange("emotionSoothedPainThreshold", 25, 0, 100);
        builder.push("persona");
        PERSONA_SENSITIVITY = builder
                .comment("角色卡敏感度：越高，受伤、冒犯、辱骂带来的心情和压力波动越强")
                .defineInRange("sensitivity", 62, 0, 100);
        PERSONA_FORGIVENESS = builder
                .comment("角色卡宽恕度：越高，道歉和安抚越容易修复未解决情绪")
                .defineInRange("forgiveness", 68, 0, 100);
        PERSONA_ATTACHMENT = builder
                .comment("角色卡依恋度：越高，熟悉主人造成伤害时越偏向困惑、委屈和需要确认")
                .defineInRange("attachment", 74, 0, 100);
        PERSONA_PRIDE = builder
                .comment("角色卡自尊：越高，被冒犯时越容易转化为愤怒和边界表达")
                .defineInRange("pride", 42, 0, 100);
        PERSONA_FEARFULNESS = builder
                .comment("角色卡怕生/恐惧倾向：越高，陌生危险越容易造成恐惧和安全感下降")
                .defineInRange("fearfulness", 46, 0, 100);
        PERSONA_ANGER_EXPRESSIVENESS = builder
                .comment("角色卡愤怒表达：越高，愤怒越会被直接说出来，而不是沉默或憋着")
                .defineInRange("angerExpressiveness", 34, 0, 100);
        PERSONA_COMFORT_NEED = builder
                .comment("角色卡安抚需求：越高，受伤后越需要道歉、照顾或安全确认才会转回普通话题")
                .defineInRange("comfortNeed", 72, 0, 100);
        PERSONA_BOUNDARY_STRENGTH = builder
                .comment("角色卡边界感：越高，重复冒犯时越会明确拒绝和拉开距离")
                .defineInRange("boundaryStrength", 56, 0, 100);
        PERSONA_COPING_STYLE = builder
                .comment("角色卡应对风格：soft/guarded/direct/quiet/playful/clingy/sulking")
                .define("copingStyle", "soft");
        builder.pop();
        LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS = builder
                .comment("命中这些文本片段时，本地命令快路径放弃解析，交给模型/计划链路")
                .defineListAllowEmpty(
                        java.util.List.of("localCommandComplexFallbackKeywords"),
                        () -> java.util.List.of(
                                "然后", "再", "之后", "接着", "并且", "顺便", "同时", "完成后", "先", "后",
                                "攻击", "打", "清理", "消灭", "杀", "干掉", "锁定", "目标", "兔子", "猪", "僵尸", "苦力怕",
                                "一群", "一堆", "全部", "所有", "这些", "那个", "那只", "那边"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        LOCAL_COMMAND_FOLLOW_ON_KEYWORDS = builder
                .comment("本地快路径：跟随主人触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandFollowOnKeywords"), () -> java.util.List.of("跟着我", "跟我走", "跟随我", "跟随"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS = builder
                .comment("本地快路径：停留原地/留家触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandFollowOffKeywords"), () -> java.util.List.of("待在这里", "留在这里", "别跟着我", "停在这"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_SIT_ON_KEYWORDS = builder
                .comment("本地快路径：坐下触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandSitOnKeywords"), () -> java.util.List.of("坐下", "坐着", "坐好"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_SIT_OFF_KEYWORDS = builder
                .comment("本地快路径：起身触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandSitOffKeywords"), () -> java.util.List.of("起来", "站起来", "起身", "站好"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS = builder
                .comment("本地快路径：白天日程触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandScheduleDayKeywords"), () -> java.util.List.of("白天工作", "白天模式", "日班"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS = builder
                .comment("本地快路径：夜间日程触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandScheduleNightKeywords"), () -> java.util.List.of("夜间工作", "夜晚工作", "夜班"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS = builder
                .comment("本地快路径：全天日程触发词")
                .defineListAllowEmpty(java.util.List.of("localCommandScheduleAllKeywords"), () -> java.util.List.of("全天工作", "全天模式", "全日工作"), entry -> entry instanceof String text && !text.isBlank());
        LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS = builder
                .comment("本地快路径即时动作步骤超时 tick 数")
                .defineInRange("localCommandImmediateStepTimeoutTicks", 40, 1, 1200);
        LOCAL_COMMAND_ACK_TEMPLATE = builder
                .comment("本地快路径提交计划后的确认话术模板，使用 %s 表示动作摘要")
                .define("localCommandAckTemplate", "好的主人，我这就%s喔~");
        builder.pop();
        CHAT_FOCUS_MODE_ENABLED = builder
                .comment("主人进行文字指令聊天时进入聊天专注态")
                .define("chatFocusModeEnabled", true);
        CHAT_FOCUS_HOLD_SECONDS = builder
                .comment("聊天专注态保持时长，单位为秒")
                .defineInRange("chatFocusHoldSeconds", 15, 3, 300);
        CHAT_FOCUS_VISION_INTERVAL_SECONDS = builder
                .comment("聊天专注态下视觉采样的兜底间隔，单位为秒")
                .defineInRange("chatFocusVisionIntervalSeconds", 25, 5, 600);
        FULL_SILENT_MODE_ENABLED = builder
                .comment("闭嘴模式：完全停止主动搭话，尽量降低 token 消耗")
                .define("fullSilentModeEnabled", false);
        IDLE_TOPIC_RETRY_SECONDS = builder
                .comment("主人未回应时，女仆再次提起同一话题的等待时间，单位为秒")
                .defineInRange("idleTopicRetrySeconds", 120, 5, 300);
        IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS = builder
                .comment("进入待机沉默后，视觉扫描频率降低到该间隔，单位为秒")
                .defineInRange("idleTopicDormantVisionIntervalSeconds", 60, 10, 600);
        IDLE_TOPIC_DORMANT_RECHECK_SECONDS = builder
                .comment("待机沉默后，再次试探性提起话题的重试间隔，单位为秒")
                .defineInRange("idleTopicDormantRecheckSeconds", 300, 60, 1800);
        NORMAL_HOSTILE_ALERT_THRESHOLD = builder
                .comment("普通敌对生物数量低于该阈值时，不主动发出环境警报")
                .defineInRange("normalHostileAlertThreshold", 8, 1, 64);
        HIGH_RISK_MOBS = builder
                .comment("可绕过普通阈值的高风险敌对生物实体 ID 列表")
                .defineListAllowEmpty(
                        java.util.List.of("highRiskMobs"),
                        () -> java.util.List.of(
                                "minecraft:creeper",
                                "minecraft:witch",
                                "minecraft:enderman",
                                "minecraft:ravager",
                                "minecraft:evoker",
                                "minecraft:vindicator",
                                "minecraft:warden",
                                "minecraft:wither",
                                "minecraft:ender_dragon"
                        ),
                        entry -> entry instanceof String text && !text.isBlank()
                );
        builder.pop();

        builder.push("debug");
        TRACE_ENABLED = builder
                .comment("启用 MaidSoulCore 的内存 trace 收集")
                .define("traceEnabled", true);
        TRACE_LOG_TO_FILE_ENABLED = builder
                .comment("把关键 MaidSoulCore trace 写入 Forge latest.log，方便排查 timeout 和主链路卡住")
                .define("traceLogToFileEnabled", true);
        DEBUG_CHAT_ECHO_ENABLED = builder
                .comment("总调试开关，开启后关键运行日志和 trace 会回显到游戏聊天栏")
                .define("debugChatEchoEnabled", false);
        TRACE_CHAT_ECHO_ENABLED = builder
                .comment("把关键 MaidSoulCore trace 行回显到主人的聊天栏")
                .define("traceChatEchoEnabled", false);
        TRACE_CHAT_VERBOSE_ECHO_ENABLED = builder
                .comment("把高频详细 trace 也回显到聊天栏；调试性能问题时不建议开启")
                .define("traceChatVerboseEchoEnabled", false);
        DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED = builder
                .comment("把完整 prompt/messages/模型原始输出分块回显到聊天栏；极度刷屏，只用于现场排查")
                .define("debugFullPromptChatEchoEnabled", false);
        TOPIC_DEDUP_TRACE_ECHO_ENABLED = builder
                .comment("把主题去重命中的抑制信息回显到主人的聊天栏")
                .define("topicDedupTraceEchoEnabled", false);
        TRACE_BUFFER_SIZE = builder
                .comment("每个女仆维护的 trace 环形缓冲区大小")
                .defineInRange("traceBufferSize", 256, 32, 8192);
        builder.pop();

        SPEC = builder.build();
    }

    private MaidSoulCommonConfig() {
    }
}
