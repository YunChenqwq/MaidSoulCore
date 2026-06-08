package com.maidsoulcore.forge.config;

import com.maidsoulcore.forge.service.MaidSoulSiteService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MaidSoulConfigScreen extends Screen {
    private final Screen parent;
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, String> fieldLabels = new HashMap<>();
    private final Map<String, ToggleButton> toggles = new HashMap<>();
    private Page currentPage = Page.INTEGRATION;

    public MaidSoulConfigScreen(Screen parent) {
        super(Component.literal("MaidSoulCore 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.fields.clear();
        this.fieldLabels.clear();
        this.toggles.clear();

        int left = this.width / 2 - 210;
        Page[] pages = Page.values();
        int pageWidth = Math.max(30, Math.min(48, 420 / pages.length));
        for (int index = 0; index < pages.length; index++) {
            Page page = pages[index];
            addRenderableWidget(Button.builder(Component.literal(page.title), button -> switchPage(page))
                    .bounds(left + index * pageWidth, 20, pageWidth - 2, 20)
                    .build());
        }

        int top = 54;
        switch (currentPage) {
            case INTEGRATION -> buildIntegration(left, top);
            case BEHAVIOR -> buildBehavior(left, top);
            case CONVERSATION -> buildConversation(left, top);
            case FOLLOWUP -> buildFollowup(left, top);
            case SPLIT -> buildSplit(left, top);
            case INTERRUPT -> buildInterrupt(left, top);
            case EMOTION -> buildEmotion(left, top);
            case RECOVERY -> buildRecovery(left, top);
            case PERSONA -> buildPersona(left, top);
            case MEMORY -> buildMemory(left, top);
            case LIFE_MEMORY -> buildLifeMemory(left, top);
            case COMMANDS -> buildCommands(left, top);
            case DEBUG -> buildDebug(left, top);
        }

        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), button -> {
            saveValues();
            this.minecraft.setScreen(parent);
        }).bounds(left, this.height - 34, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("同步站点并关闭"), button -> {
            saveValues();
            MaidSoulSiteService.synchronizeTlmSiteFromMaiBot();
            this.minecraft.setScreen(parent);
        }).bounds(left + 110, this.height - 34, 130, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), button ->
                this.minecraft.setScreen(parent)).bounds(left + 250, this.height - 34, 80, 20).build());
    }

    private void buildIntegration(int left, int top) {
        field("configDir", left, top, 420, MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get());
        field("siteId", left, top + 30, 420, MaidSoulCommonConfig.TLM_SITE_ID.get());
        toggle("autoSync", left, top + 64, 150, "自动同步站点", MaidSoulCommonConfig.AUTO_SYNC_TLM_SITE.get());
        toggle("autoPreset", left + 170, top + 64, 150, "自动应用预设", MaidSoulCommonConfig.AUTO_APPLY_CHAT_PRESET.get());
    }

    private void buildBehavior(int left, int top) {
        toggle("proactive", left, top, 110, "主动陪伴", MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get());
        toggle("idle", left + 120, top, 110, "待机搭话", MaidSoulCommonConfig.IDLE_CHAT_ENABLED.get());
        toggle("vision", left + 240, top, 110, "视觉摘要", MaidSoulCommonConfig.VISION_ENABLED.get());
        toggle("visionLlm", left + 360, top, 60, "VLM", MaidSoulCommonConfig.VISION_LLM_INTERPRET_ENABLED.get());
        toggle("topicDedup", left, top + 28, 110, "主题去重", MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.get());
        toggle("localFast", left + 120, top + 28, 130, "本地命令快路", MaidSoulCommonConfig.LOCAL_COMMAND_FAST_PATH_ENABLED.get());
        toggle("chatFocus", left + 260, top + 28, 120, "聊天专注态", MaidSoulCommonConfig.CHAT_FOCUS_MODE_ENABLED.get());
        toggle("silent", left, top + 56, 110, "闭嘴模式", MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.get());
        number("scanTicks", left, top + 96, MaidSoulCommonConfig.PROACTIVE_SCAN_INTERVAL_TICKS.get());
        number("cooldown", left + 110, top + 96, MaidSoulCommonConfig.PROACTIVE_CHAT_COOLDOWN_SECONDS.get());
        number("idleInterval", left + 220, top + 96, MaidSoulCommonConfig.IDLE_CHAT_INTERVAL_SECONDS.get());
        number("activeWindow", left, top + 136, MaidSoulCommonConfig.VISION_CHAT_ACTIVE_WINDOW_SECONDS.get());
        number("activeVision", left + 110, top + 136, MaidSoulCommonConfig.VISION_CHAT_ACTIVE_INTERVAL_SECONDS.get());
        number("idleVision", left + 220, top + 136, MaidSoulCommonConfig.VISION_IDLE_INTERVAL_SECONDS.get());
        number("environmentCooldown", left, top + 176, MaidSoulCommonConfig.ENVIRONMENT_REPLY_COOLDOWN_SECONDS.get());
        number("topicWindow", left + 110, top + 176, MaidSoulCommonConfig.TOPIC_DEDUP_WINDOW_SECONDS.get());
        number("hostileThreshold", left + 220, top + 176, MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.get());
        number("debounce", left, top + 216, MaidSoulCommonConfig.CHAT_RUNTIME_DEBOUNCE_MILLIS.get());
        number("focusHold", left + 110, top + 216, MaidSoulCommonConfig.CHAT_FOCUS_HOLD_SECONDS.get());
        number("focusVision", left + 220, top + 216, MaidSoulCommonConfig.CHAT_FOCUS_VISION_INTERVAL_SECONDS.get());
        number("idleRetry", left, top + 256, MaidSoulCommonConfig.IDLE_TOPIC_RETRY_SECONDS.get());
        number("dormantVision", left + 110, top + 256, MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS.get());
        number("dormantRecheck", left + 220, top + 256, MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get());
        field("highRiskMobs", left, top + 296, 420, join(MaidSoulCommonConfig.HIGH_RISK_MOBS.get()));
    }

    private void buildConversation(int left, int top) {
        number("waitMillis", left, top, MaidSoulCommonConfig.CONVERSATION_PACING_WAIT_MILLIS.get());
        number("turnTimeout", left + 110, top, MaidSoulCommonConfig.CONVERSATION_TURN_TIMEOUT_MILLIS.get());
        number("modelTimeout", left + 220, top, MaidSoulCommonConfig.CONVERSATION_MODEL_TIMEOUT_SECONDS.get());
        number("modelRetry", left + 330, top, MaidSoulCommonConfig.CONVERSATION_MODEL_MAX_RETRY.get());
        number("timingCooldown", left, top + 40, MaidSoulCommonConfig.CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS.get());
        number("replyMax", left + 110, top + 40, MaidSoulCommonConfig.CONVERSATION_REPLY_MAX_CHARS.get());
        toggle("repeatGuard", left + 220, top + 40, 120, "复读抑制", MaidSoulCommonConfig.CONVERSATION_REPEAT_GUARD_ENABLED.get());
        toggle("llmTiming", left + 340, top + 40, 90, "LLM门控", MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_ENABLED.get());
        field("emptyFallback", left, top + 80, 420, MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get());
        field("repeatFallback", left, top + 120, 420, MaidSoulCommonConfig.CONVERSATION_REPEAT_REPLY_FALLBACK.get());
        field("waitTriggers", left, top + 160, 420, join(MaidSoulCommonConfig.CONVERSATION_WAIT_TRIGGERS.get()));
        field("noReplyTriggers", left, top + 200, 420, join(MaidSoulCommonConfig.CONVERSATION_NO_REPLY_TRIGGERS.get()));
        field("finishTriggers", left, top + 240, 420, join(MaidSoulCommonConfig.CONVERSATION_FINISH_TRIGGERS.get()));
    }

    private void buildFollowup(int left, int top) {
        toggle("followupEnabled", left, top, 130, "启用续话", MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_ENABLED.get());
        labeledNumber("followupDelay", "沉默等待ms", left, top + 42, MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_DELAY_MILLIS.get());
        labeledNumber("followupMax", "每轮最多次数", left + 120, top + 42, MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN.get());
        labeledField("followupTriggers", "续话触发词，逗号分隔", left, top + 94, 420, join(MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_TRIGGERS.get()));
    }

    private void buildSplit(int left, int top) {
        toggle("splitter", left, top, 110, "启用分句", MaidSoulCommonConfig.CONVERSATION_SPLITTER_ENABLED.get());
        toggle("kaomoji", left + 120, top, 130, "保护颜文字", MaidSoulCommonConfig.CONVERSATION_SPLITTER_PROTECT_KAOMOJI.get());
        toggle("overflowOriginal", left + 260, top, 150, "超限返回原文", MaidSoulCommonConfig.CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL.get());
        number("splitMax", left, top + 42, MaidSoulCommonConfig.CONVERSATION_SPLITTER_MAX_SEGMENTS.get());
        number("forceBreak", left + 110, top + 42, MaidSoulCommonConfig.CONVERSATION_SPLITTER_FORCE_BREAK_CHARS.get());
        number("shortChars", left + 220, top + 42, MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_TEXT_CHARS.get());
        number("mediumChars", left + 330, top + 42, MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS.get());
        number("shortPercent", left, top + 84, MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT.get());
        number("mediumPercent", left + 110, top + 84, MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT.get());
        number("longPercent", left + 220, top + 84, MaidSoulCommonConfig.CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT.get());
        number("speechMin", left, top + 126, MaidSoulCommonConfig.CONVERSATION_SPEECH_MIN_DELAY_TICKS.get());
        number("speechMax", left + 110, top + 126, MaidSoulCommonConfig.CONVERSATION_SPEECH_MAX_DELAY_TICKS.get());
        number("speechCharsPerTick", left + 220, top + 126, MaidSoulCommonConfig.CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK.get());
    }

    private void buildInterrupt(int left, int top) {
        toggle("interrupt", left, top, 110, "打断主题", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ENABLED.get());
        toggle("interruptSpeech", left + 120, top, 120, "打断旧话", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_SPEECH_ENABLED.get());
        toggle("interruptOwner", left + 250, top, 130, "丢弃旧轮", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_CANCEL_OWNER_TURN.get());
        number("interruptSeconds", left, top + 42, MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ACTIVE_SECONDS.get());
        number("interruptCooldown", left + 110, top + 42, MaidSoulCommonConfig.CONVERSATION_INTERRUPT_COOLDOWN_MILLIS.get());
        field("interruptTypes", left, top + 84, 420, join(MaidSoulCommonConfig.CONVERSATION_INTERRUPT_EVENT_TYPES.get()));
    }

    private void buildEmotion(int left, int top) {
        toggle("emotionEnabled", left, top, 120, "启用情绪", MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get());
        toggle("emotionTrace", left + 130, top, 140, "情绪日志", MaidSoulCommonConfig.EMOTION_TRACE_ECHO_ENABLED.get());
        number("emotionMoodBase", left, top + 42, MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.get());
        number("emotionTrustBase", left + 110, top + 42, MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get());
        number("emotionUnresolved", left + 220, top + 42, MaidSoulCommonConfig.EMOTION_UNRESOLVED_SECONDS.get());
        number("emotionOwnerHitMood", left, top + 84, MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_MOOD_DELTA.get());
        number("emotionOwnerHitTrust", left + 110, top + 84, MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_TRUST_DELTA.get());
        number("emotionHostileHitMood", left + 220, top + 84, MaidSoulCommonConfig.EMOTION_HOSTILE_ATTACK_MOOD_DELTA.get());
        number("emotionPositiveMood", left, top + 126, MaidSoulCommonConfig.EMOTION_POSITIVE_EVENT_MOOD_DELTA.get());
        number("emotionComfortMood", left + 110, top + 126, MaidSoulCommonConfig.EMOTION_COMFORT_MOOD_DELTA.get());
        number("emotionComfortTrust", left + 220, top + 126, MaidSoulCommonConfig.EMOTION_COMFORT_TRUST_DELTA.get());
        number("emotionSoothedMood", left, top + 168, MaidSoulCommonConfig.EMOTION_SOOTHED_MOOD_THRESHOLD.get());
        number("emotionSoothedStress", left + 110, top + 168, MaidSoulCommonConfig.EMOTION_SOOTHED_STRESS_THRESHOLD.get());
        number("emotionSoothedPain", left + 220, top + 168, MaidSoulCommonConfig.EMOTION_SOOTHED_PAIN_THRESHOLD.get());
    }

    private void buildRecovery(int left, int top) {
        toggle("emotionDayRecovery", left, top, 140, "跨天恢复", MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_ENABLED.get());
        labeledNumber("emotionRecoverTicks", "短期间隔tick", left, top + 42, MaidSoulCommonConfig.EMOTION_RECOVERY_INTERVAL_TICKS.get());
        labeledNumber("emotionMoodStep", "心情步长", left + 110, top + 42, MaidSoulCommonConfig.EMOTION_MOOD_RECOVERY_STEP.get());
        labeledNumber("emotionTrustStep", "信任步长", left + 220, top + 42, MaidSoulCommonConfig.EMOTION_TRUST_RECOVERY_STEP.get());
        labeledNumber("emotionStressStep", "压力步长", left + 330, top + 42, MaidSoulCommonConfig.EMOTION_STRESS_RECOVERY_STEP.get());
        labeledNumber("emotionPainStep", "疼痛步长", left, top + 94, MaidSoulCommonConfig.EMOTION_PAIN_RECOVERY_STEP.get());
        labeledNumber("emotionFearStep", "恐惧步长", left + 110, top + 94, MaidSoulCommonConfig.EMOTION_FEAR_RECOVERY_STEP.get());
        labeledNumber("emotionAngerStep", "愤怒步长", left + 220, top + 94, MaidSoulCommonConfig.EMOTION_ANGER_RECOVERY_STEP.get());
        labeledNumber("emotionConfusionStep", "困惑步长", left + 330, top + 94, MaidSoulCommonConfig.EMOTION_CONFUSION_RECOVERY_STEP.get());
        labeledNumber("emotionDayPercent", "跨天百分比", left, top + 146, MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_PERCENT.get());
        labeledNumber("emotionSleepTicks", "睡眠tick", left + 110, top + 146, MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_MIN_TICKS.get());
        labeledNumber("emotionSleepPercent", "睡醒百分比", left + 220, top + 146, MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_PERCENT.get());
        labeledNumber("emotionDayGrudge", "跨天芥蒂", left, top + 198, MaidSoulCommonConfig.EMOTION_DAY_GRUDGE_RECOVERY_STEP.get());
        labeledNumber("emotionSleepGrudge", "睡醒芥蒂", left + 110, top + 198, MaidSoulCommonConfig.EMOTION_SLEEP_GRUDGE_RECOVERY_STEP.get());
    }

    private void buildPersona(int left, int top) {
        labeledNumber("personaSensitivity", "敏感", left, top, MaidSoulCommonConfig.PERSONA_SENSITIVITY.get());
        labeledNumber("personaForgiveness", "宽恕", left + 110, top, MaidSoulCommonConfig.PERSONA_FORGIVENESS.get());
        labeledNumber("personaAttachment", "依恋", left + 220, top, MaidSoulCommonConfig.PERSONA_ATTACHMENT.get());
        labeledNumber("personaPride", "自尊", left + 330, top, MaidSoulCommonConfig.PERSONA_PRIDE.get());
        labeledNumber("personaFearfulness", "怕生", left, top + 42, MaidSoulCommonConfig.PERSONA_FEARFULNESS.get());
        labeledNumber("personaAnger", "愤怒表达", left + 110, top + 42, MaidSoulCommonConfig.PERSONA_ANGER_EXPRESSIVENESS.get());
        labeledNumber("personaComfortNeed", "安抚需求", left + 220, top + 42, MaidSoulCommonConfig.PERSONA_COMFORT_NEED.get());
        labeledNumber("personaBoundary", "边界感", left + 330, top + 42, MaidSoulCommonConfig.PERSONA_BOUNDARY_STRENGTH.get());
        labeledField("personaCopingStyle", "应对风格 soft/guarded/direct/quiet/playful/clingy/sulking", left, top + 88, 420, MaidSoulCommonConfig.PERSONA_COPING_STYLE.get());
    }

    private void buildMemory(int left, int top) {
        toggle("memory", left, top, 110, "本地记忆", MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get());
        toggle("reflection", left + 120, top, 110, "轻量反思", MaidSoulCommonConfig.CONVERSATION_REFLECTION_ENABLED.get());
        toggle("styleRotation", left + 240, top, 110, "风格轮换", MaidSoulCommonConfig.CONVERSATION_STYLE_ROTATION_ENABLED.get());
        toggle("heartRewrite", left + 350, top, 90, "心流检查", MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_REWRITE_ENABLED.get());
        toggle("heartSecondPass", left + 220, top + 126, 120, "二次模型", MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED.get());
        number("memoryMax", left, top + 42, MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_LINES.get());
        number("memoryPrompt", left + 110, top + 42, MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get());
        number("ownerNotes", left + 220, top + 42, MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_OWNER_NOTES.get());
        labeledNumber("heartSimilarity", "相似阈值%", left + 330, top + 42, MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT.get());
        number("lineChars", left, top + 84, MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get());
        number("noteChars", left + 110, top + 84, MaidSoulCommonConfig.CONVERSATION_MEMORY_NOTE_MAX_CHARS.get());
        number("reflectEvery", left + 220, top + 84, MaidSoulCommonConfig.CONVERSATION_REFLECTION_EVERY_LINES.get());
        number("reflectRecent", left, top + 126, MaidSoulCommonConfig.CONVERSATION_REFLECTION_RECENT_LINES.get());
        field("noteTriggers", left, top + 168, 420, join(MaidSoulCommonConfig.CONVERSATION_OWNER_NOTE_TRIGGERS.get()));
        field("styleVariants", left, top + 208, 420, join(MaidSoulCommonConfig.CONVERSATION_STYLE_VARIANTS.get()));
    }

    private void buildLifeMemory(int left, int top) {
        toggle("lifeMemory", left, top, 120, "Life Memory", MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.get());
        toggle("lifeDaily", left + 135, top, 150, "Daily Merge", MaidSoulCommonConfig.LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED.get());
        labeledField("lifeRoot", "Portable memory root", left, top + 42, 420, MaidSoulCommonConfig.LIFE_MEMORY_ROOT_DIR.get());
        labeledNumber("lifeEpisodes", "Prompt episodes", left, top + 94, MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_EPISODES.get());
        labeledNumber("lifeUnderstandings", "Prompt understandings", left + 120, top + 94, MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS.get());
        labeledNumber("lifeDraftLines", "Draft lines/day", left + 240, top + 94, MaidSoulCommonConfig.LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY.get());
        labeledNumber("lifeRawLines", "Raw lines/episode", left, top + 146, MaidSoulCommonConfig.LIFE_MEMORY_RAW_LINES_PER_EPISODE.get());
    }

    private void buildCommands(int left, int top) {
        field("hotCommands", left, top, 420, join(MaidSoulCommonConfig.CONVERSATION_HOT_COMMAND_KEYWORDS.get()));
        field("complexFallback", left, top + 40, 420, join(MaidSoulCommonConfig.LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS.get()));
        number("stepTimeout", left, top + 80, MaidSoulCommonConfig.LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS.get());
        field("ackTemplate", left + 110, top + 80, 310, MaidSoulCommonConfig.LOCAL_COMMAND_ACK_TEMPLATE.get());
        field("followOn", left, top + 120, 200, join(MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_ON_KEYWORDS.get()));
        field("followOff", left + 220, top + 120, 200, join(MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS.get()));
        field("sitOn", left, top + 160, 200, join(MaidSoulCommonConfig.LOCAL_COMMAND_SIT_ON_KEYWORDS.get()));
        field("sitOff", left + 220, top + 160, 200, join(MaidSoulCommonConfig.LOCAL_COMMAND_SIT_OFF_KEYWORDS.get()));
        field("scheduleDay", left, top + 200, 130, join(MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS.get()));
        field("scheduleNight", left + 145, top + 200, 130, join(MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS.get()));
        field("scheduleAll", left + 290, top + 200, 130, join(MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS.get()));
    }

    private void buildDebug(int left, int top) {
        toggle("debugEcho", left, top, 150, "调试聊天回显", MaidSoulCommonConfig.DEBUG_CHAT_ECHO_ENABLED.get());
        toggle("traceEcho", left, top + 30, 150, "Trace 回显", MaidSoulCommonConfig.TRACE_CHAT_ECHO_ENABLED.get());
        toggle("verboseTrace", left + 170, top + 30, 150, "详细 Trace", MaidSoulCommonConfig.TRACE_CHAT_VERBOSE_ECHO_ENABLED.get());
        toggle("topicTrace", left, top + 60, 150, "去重回显", MaidSoulCommonConfig.TOPIC_DEDUP_TRACE_ECHO_ENABLED.get());
        toggle("fullPromptEcho", left + 170, top + 60, 150, "完整链路回显", MaidSoulCommonConfig.DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED.get());
    }

    private void switchPage(Page page) {
        this.currentPage = page;
        init();
    }

    private EditBox field(String key, int x, int y, int width, String initialValue) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setValue(initialValue == null ? "" : initialValue);
        box.setMaxLength(4096);
        addRenderableWidget(box);
        fields.put(key, box);
        return box;
    }

    private EditBox number(String key, int x, int y, int value) {
        return field(key, x, y, 96, String.valueOf(value));
    }

    private EditBox labeledField(String key, String label, int x, int y, int width, String initialValue) {
        fieldLabels.put(key, label);
        return field(key, x, y, width, initialValue);
    }

    private EditBox labeledNumber(String key, String label, int x, int y, int value) {
        fieldLabels.put(key, label);
        return number(key, x, y, value);
    }

    private ToggleButton toggle(String key, int x, int y, int width, String label, boolean value) {
        ToggleButton button = new ToggleButton(x, y, width, 20, label, value);
        addRenderableWidget(button);
        toggles.put(key, button);
        return button;
    }

    private void saveValues() {
        MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.set(text("configDir", MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get()));
        MaidSoulCommonConfig.TLM_SITE_ID.set(text("siteId", MaidSoulCommonConfig.TLM_SITE_ID.get()));
        MaidSoulCommonConfig.AUTO_SYNC_TLM_SITE.set(bool("autoSync", MaidSoulCommonConfig.AUTO_SYNC_TLM_SITE.get()));
        MaidSoulCommonConfig.AUTO_APPLY_CHAT_PRESET.set(bool("autoPreset", MaidSoulCommonConfig.AUTO_APPLY_CHAT_PRESET.get()));
        MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.set(bool("proactive", MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get()));
        MaidSoulCommonConfig.IDLE_CHAT_ENABLED.set(bool("idle", MaidSoulCommonConfig.IDLE_CHAT_ENABLED.get()));
        MaidSoulCommonConfig.VISION_ENABLED.set(bool("vision", MaidSoulCommonConfig.VISION_ENABLED.get()));
        MaidSoulCommonConfig.VISION_LLM_INTERPRET_ENABLED.set(bool("visionLlm", MaidSoulCommonConfig.VISION_LLM_INTERPRET_ENABLED.get()));
        MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.set(bool("topicDedup", MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_FAST_PATH_ENABLED.set(bool("localFast", MaidSoulCommonConfig.LOCAL_COMMAND_FAST_PATH_ENABLED.get()));
        MaidSoulCommonConfig.CHAT_FOCUS_MODE_ENABLED.set(bool("chatFocus", MaidSoulCommonConfig.CHAT_FOCUS_MODE_ENABLED.get()));
        MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.set(bool("silent", MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.get()));
        MaidSoulCommonConfig.PROACTIVE_SCAN_INTERVAL_TICKS.set(number("scanTicks", MaidSoulCommonConfig.PROACTIVE_SCAN_INTERVAL_TICKS.get()));
        MaidSoulCommonConfig.PROACTIVE_CHAT_COOLDOWN_SECONDS.set(number("cooldown", MaidSoulCommonConfig.PROACTIVE_CHAT_COOLDOWN_SECONDS.get()));
        MaidSoulCommonConfig.IDLE_CHAT_INTERVAL_SECONDS.set(number("idleInterval", MaidSoulCommonConfig.IDLE_CHAT_INTERVAL_SECONDS.get()));
        MaidSoulCommonConfig.VISION_CHAT_ACTIVE_WINDOW_SECONDS.set(number("activeWindow", MaidSoulCommonConfig.VISION_CHAT_ACTIVE_WINDOW_SECONDS.get()));
        MaidSoulCommonConfig.VISION_CHAT_ACTIVE_INTERVAL_SECONDS.set(number("activeVision", MaidSoulCommonConfig.VISION_CHAT_ACTIVE_INTERVAL_SECONDS.get()));
        MaidSoulCommonConfig.VISION_IDLE_INTERVAL_SECONDS.set(number("idleVision", MaidSoulCommonConfig.VISION_IDLE_INTERVAL_SECONDS.get()));
        MaidSoulCommonConfig.ENVIRONMENT_REPLY_COOLDOWN_SECONDS.set(number("environmentCooldown", MaidSoulCommonConfig.ENVIRONMENT_REPLY_COOLDOWN_SECONDS.get()));
        MaidSoulCommonConfig.TOPIC_DEDUP_WINDOW_SECONDS.set(number("topicWindow", MaidSoulCommonConfig.TOPIC_DEDUP_WINDOW_SECONDS.get()));
        MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.set(number("hostileThreshold", MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.get()));
        MaidSoulCommonConfig.CHAT_RUNTIME_DEBOUNCE_MILLIS.set(number("debounce", MaidSoulCommonConfig.CHAT_RUNTIME_DEBOUNCE_MILLIS.get()));
        MaidSoulCommonConfig.CHAT_FOCUS_HOLD_SECONDS.set(number("focusHold", MaidSoulCommonConfig.CHAT_FOCUS_HOLD_SECONDS.get()));
        MaidSoulCommonConfig.CHAT_FOCUS_VISION_INTERVAL_SECONDS.set(number("focusVision", MaidSoulCommonConfig.CHAT_FOCUS_VISION_INTERVAL_SECONDS.get()));
        MaidSoulCommonConfig.IDLE_TOPIC_RETRY_SECONDS.set(number("idleRetry", MaidSoulCommonConfig.IDLE_TOPIC_RETRY_SECONDS.get()));
        MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS.set(number("dormantVision", MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS.get()));
        MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.set(number("dormantRecheck", MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get()));
        MaidSoulCommonConfig.HIGH_RISK_MOBS.set(csv("highRiskMobs", MaidSoulCommonConfig.HIGH_RISK_MOBS.get()));
        MaidSoulCommonConfig.CONVERSATION_PACING_WAIT_MILLIS.set(number("waitMillis", MaidSoulCommonConfig.CONVERSATION_PACING_WAIT_MILLIS.get()));
        MaidSoulCommonConfig.CONVERSATION_TURN_TIMEOUT_MILLIS.set(number("turnTimeout", MaidSoulCommonConfig.CONVERSATION_TURN_TIMEOUT_MILLIS.get()));
        MaidSoulCommonConfig.CONVERSATION_MODEL_TIMEOUT_SECONDS.set(number("modelTimeout", MaidSoulCommonConfig.CONVERSATION_MODEL_TIMEOUT_SECONDS.get()));
        MaidSoulCommonConfig.CONVERSATION_MODEL_MAX_RETRY.set(number("modelRetry", MaidSoulCommonConfig.CONVERSATION_MODEL_MAX_RETRY.get()));
        MaidSoulCommonConfig.CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS.set(number("timingCooldown", MaidSoulCommonConfig.CONVERSATION_TIMING_NON_CONTINUE_COOLDOWN_MILLIS.get()));
        MaidSoulCommonConfig.CONVERSATION_REPLY_MAX_CHARS.set(number("replyMax", MaidSoulCommonConfig.CONVERSATION_REPLY_MAX_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_REPEAT_GUARD_ENABLED.set(bool("repeatGuard", MaidSoulCommonConfig.CONVERSATION_REPEAT_GUARD_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_ENABLED.set(bool("llmTiming", MaidSoulCommonConfig.CONVERSATION_LLM_TIMING_GATE_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.set(text("emptyFallback", MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get()));
        MaidSoulCommonConfig.CONVERSATION_REPEAT_REPLY_FALLBACK.set(text("repeatFallback", MaidSoulCommonConfig.CONVERSATION_REPEAT_REPLY_FALLBACK.get()));
        MaidSoulCommonConfig.CONVERSATION_WAIT_TRIGGERS.set(csv("waitTriggers", MaidSoulCommonConfig.CONVERSATION_WAIT_TRIGGERS.get()));
        MaidSoulCommonConfig.CONVERSATION_NO_REPLY_TRIGGERS.set(csv("noReplyTriggers", MaidSoulCommonConfig.CONVERSATION_NO_REPLY_TRIGGERS.get()));
        MaidSoulCommonConfig.CONVERSATION_FINISH_TRIGGERS.set(csv("finishTriggers", MaidSoulCommonConfig.CONVERSATION_FINISH_TRIGGERS.get()));
        MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_ENABLED.set(bool("followupEnabled", MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_DELAY_MILLIS.set(number("followupDelay", MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_DELAY_MILLIS.get()));
        MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN.set(number("followupMax", MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN.get()));
        MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_TRIGGERS.set(csv("followupTriggers", MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_TRIGGERS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_ENABLED.set(bool("splitter", MaidSoulCommonConfig.CONVERSATION_SPLITTER_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_PROTECT_KAOMOJI.set(bool("kaomoji", MaidSoulCommonConfig.CONVERSATION_SPLITTER_PROTECT_KAOMOJI.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL.set(bool("overflowOriginal", MaidSoulCommonConfig.CONVERSATION_SPLITTER_OVERFLOW_RETURN_ORIGINAL.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_MAX_SEGMENTS.set(number("splitMax", MaidSoulCommonConfig.CONVERSATION_SPLITTER_MAX_SEGMENTS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_FORCE_BREAK_CHARS.set(number("forceBreak", MaidSoulCommonConfig.CONVERSATION_SPLITTER_FORCE_BREAK_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_TEXT_CHARS.set(number("shortChars", MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_TEXT_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS.set(number("mediumChars", MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_TEXT_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT.set(number("shortPercent", MaidSoulCommonConfig.CONVERSATION_SPLITTER_SHORT_SPLIT_PERCENT.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT.set(number("mediumPercent", MaidSoulCommonConfig.CONVERSATION_SPLITTER_MEDIUM_SPLIT_PERCENT.get()));
        MaidSoulCommonConfig.CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT.set(number("longPercent", MaidSoulCommonConfig.CONVERSATION_SPLITTER_LONG_SPLIT_PERCENT.get()));
        MaidSoulCommonConfig.CONVERSATION_SPEECH_MIN_DELAY_TICKS.set(number("speechMin", MaidSoulCommonConfig.CONVERSATION_SPEECH_MIN_DELAY_TICKS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPEECH_MAX_DELAY_TICKS.set(number("speechMax", MaidSoulCommonConfig.CONVERSATION_SPEECH_MAX_DELAY_TICKS.get()));
        MaidSoulCommonConfig.CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK.set(number("speechCharsPerTick", MaidSoulCommonConfig.CONVERSATION_SPEECH_CHARS_PER_DELAY_TICK.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ENABLED.set(bool("interrupt", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_SPEECH_ENABLED.set(bool("interruptSpeech", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_SPEECH_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_CANCEL_OWNER_TURN.set(bool("interruptOwner", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_CANCEL_OWNER_TURN.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ACTIVE_SECONDS.set(number("interruptSeconds", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ACTIVE_SECONDS.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_COOLDOWN_MILLIS.set(number("interruptCooldown", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_COOLDOWN_MILLIS.get()));
        MaidSoulCommonConfig.CONVERSATION_INTERRUPT_EVENT_TYPES.set(csv("interruptTypes", MaidSoulCommonConfig.CONVERSATION_INTERRUPT_EVENT_TYPES.get()));
        MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.set(bool("emotionEnabled", MaidSoulCommonConfig.EMOTION_SYSTEM_ENABLED.get()));
        MaidSoulCommonConfig.EMOTION_TRACE_ECHO_ENABLED.set(bool("emotionTrace", MaidSoulCommonConfig.EMOTION_TRACE_ECHO_ENABLED.get()));
        MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.set(number("emotionMoodBase", MaidSoulCommonConfig.EMOTION_BASELINE_MOOD.get()));
        MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.set(number("emotionTrustBase", MaidSoulCommonConfig.EMOTION_BASELINE_TRUST.get()));
        MaidSoulCommonConfig.EMOTION_RECOVERY_INTERVAL_TICKS.set(number("emotionRecoverTicks", MaidSoulCommonConfig.EMOTION_RECOVERY_INTERVAL_TICKS.get()));
        MaidSoulCommonConfig.EMOTION_MOOD_RECOVERY_STEP.set(number("emotionMoodStep", MaidSoulCommonConfig.EMOTION_MOOD_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_TRUST_RECOVERY_STEP.set(number("emotionTrustStep", MaidSoulCommonConfig.EMOTION_TRUST_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_STRESS_RECOVERY_STEP.set(number("emotionStressStep", MaidSoulCommonConfig.EMOTION_STRESS_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_PAIN_RECOVERY_STEP.set(number("emotionPainStep", MaidSoulCommonConfig.EMOTION_PAIN_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_FEAR_RECOVERY_STEP.set(number("emotionFearStep", MaidSoulCommonConfig.EMOTION_FEAR_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_ANGER_RECOVERY_STEP.set(number("emotionAngerStep", MaidSoulCommonConfig.EMOTION_ANGER_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_CONFUSION_RECOVERY_STEP.set(number("emotionConfusionStep", MaidSoulCommonConfig.EMOTION_CONFUSION_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_ENABLED.set(bool("emotionDayRecovery", MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_ENABLED.get()));
        MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_PERCENT.set(number("emotionDayPercent", MaidSoulCommonConfig.EMOTION_DAY_RECOVERY_PERCENT.get()));
        MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_MIN_TICKS.set(number("emotionSleepTicks", MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_MIN_TICKS.get()));
        MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_PERCENT.set(number("emotionSleepPercent", MaidSoulCommonConfig.EMOTION_SLEEP_RECOVERY_PERCENT.get()));
        MaidSoulCommonConfig.EMOTION_DAY_GRUDGE_RECOVERY_STEP.set(number("emotionDayGrudge", MaidSoulCommonConfig.EMOTION_DAY_GRUDGE_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_SLEEP_GRUDGE_RECOVERY_STEP.set(number("emotionSleepGrudge", MaidSoulCommonConfig.EMOTION_SLEEP_GRUDGE_RECOVERY_STEP.get()));
        MaidSoulCommonConfig.EMOTION_UNRESOLVED_SECONDS.set(number("emotionUnresolved", MaidSoulCommonConfig.EMOTION_UNRESOLVED_SECONDS.get()));
        MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_MOOD_DELTA.set(number("emotionOwnerHitMood", MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_MOOD_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_TRUST_DELTA.set(number("emotionOwnerHitTrust", MaidSoulCommonConfig.EMOTION_OWNER_ATTACK_TRUST_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_HOSTILE_ATTACK_MOOD_DELTA.set(number("emotionHostileHitMood", MaidSoulCommonConfig.EMOTION_HOSTILE_ATTACK_MOOD_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_POSITIVE_EVENT_MOOD_DELTA.set(number("emotionPositiveMood", MaidSoulCommonConfig.EMOTION_POSITIVE_EVENT_MOOD_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_COMFORT_MOOD_DELTA.set(number("emotionComfortMood", MaidSoulCommonConfig.EMOTION_COMFORT_MOOD_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_COMFORT_TRUST_DELTA.set(number("emotionComfortTrust", MaidSoulCommonConfig.EMOTION_COMFORT_TRUST_DELTA.get()));
        MaidSoulCommonConfig.EMOTION_SOOTHED_MOOD_THRESHOLD.set(number("emotionSoothedMood", MaidSoulCommonConfig.EMOTION_SOOTHED_MOOD_THRESHOLD.get()));
        MaidSoulCommonConfig.EMOTION_SOOTHED_STRESS_THRESHOLD.set(number("emotionSoothedStress", MaidSoulCommonConfig.EMOTION_SOOTHED_STRESS_THRESHOLD.get()));
        MaidSoulCommonConfig.EMOTION_SOOTHED_PAIN_THRESHOLD.set(number("emotionSoothedPain", MaidSoulCommonConfig.EMOTION_SOOTHED_PAIN_THRESHOLD.get()));
        MaidSoulCommonConfig.PERSONA_SENSITIVITY.set(number("personaSensitivity", MaidSoulCommonConfig.PERSONA_SENSITIVITY.get()));
        MaidSoulCommonConfig.PERSONA_FORGIVENESS.set(number("personaForgiveness", MaidSoulCommonConfig.PERSONA_FORGIVENESS.get()));
        MaidSoulCommonConfig.PERSONA_ATTACHMENT.set(number("personaAttachment", MaidSoulCommonConfig.PERSONA_ATTACHMENT.get()));
        MaidSoulCommonConfig.PERSONA_PRIDE.set(number("personaPride", MaidSoulCommonConfig.PERSONA_PRIDE.get()));
        MaidSoulCommonConfig.PERSONA_FEARFULNESS.set(number("personaFearfulness", MaidSoulCommonConfig.PERSONA_FEARFULNESS.get()));
        MaidSoulCommonConfig.PERSONA_ANGER_EXPRESSIVENESS.set(number("personaAnger", MaidSoulCommonConfig.PERSONA_ANGER_EXPRESSIVENESS.get()));
        MaidSoulCommonConfig.PERSONA_COMFORT_NEED.set(number("personaComfortNeed", MaidSoulCommonConfig.PERSONA_COMFORT_NEED.get()));
        MaidSoulCommonConfig.PERSONA_BOUNDARY_STRENGTH.set(number("personaBoundary", MaidSoulCommonConfig.PERSONA_BOUNDARY_STRENGTH.get()));
        MaidSoulCommonConfig.PERSONA_COPING_STYLE.set(text("personaCopingStyle", MaidSoulCommonConfig.PERSONA_COPING_STYLE.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.set(bool("memory", MaidSoulCommonConfig.CONVERSATION_MEMORY_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_REFLECTION_ENABLED.set(bool("reflection", MaidSoulCommonConfig.CONVERSATION_REFLECTION_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_STYLE_ROTATION_ENABLED.set(bool("styleRotation", MaidSoulCommonConfig.CONVERSATION_STYLE_ROTATION_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_REWRITE_ENABLED.set(bool("heartRewrite", MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_REWRITE_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED.set(bool("heartSecondPass", MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SECOND_PASS_ENABLED.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_LINES.set(number("memoryMax", MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_LINES.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.set(number("memoryPrompt", MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_OWNER_NOTES.set(number("ownerNotes", MaidSoulCommonConfig.CONVERSATION_MEMORY_MAX_OWNER_NOTES.get()));
        MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT.set(number("heartSimilarity", MaidSoulCommonConfig.CONVERSATION_HEARTFLOW_SIMILARITY_PERCENT.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.set(number("lineChars", MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_MEMORY_NOTE_MAX_CHARS.set(number("noteChars", MaidSoulCommonConfig.CONVERSATION_MEMORY_NOTE_MAX_CHARS.get()));
        MaidSoulCommonConfig.CONVERSATION_REFLECTION_EVERY_LINES.set(number("reflectEvery", MaidSoulCommonConfig.CONVERSATION_REFLECTION_EVERY_LINES.get()));
        MaidSoulCommonConfig.CONVERSATION_REFLECTION_RECENT_LINES.set(number("reflectRecent", MaidSoulCommonConfig.CONVERSATION_REFLECTION_RECENT_LINES.get()));
        MaidSoulCommonConfig.CONVERSATION_OWNER_NOTE_TRIGGERS.set(csv("noteTriggers", MaidSoulCommonConfig.CONVERSATION_OWNER_NOTE_TRIGGERS.get()));
        MaidSoulCommonConfig.CONVERSATION_STYLE_VARIANTS.set(csv("styleVariants", MaidSoulCommonConfig.CONVERSATION_STYLE_VARIANTS.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.set(bool("lifeMemory", MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED.set(bool("lifeDaily", MaidSoulCommonConfig.LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_ROOT_DIR.set(text("lifeRoot", MaidSoulCommonConfig.LIFE_MEMORY_ROOT_DIR.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_EPISODES.set(number("lifeEpisodes", MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_EPISODES.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS.set(number("lifeUnderstandings", MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY.set(number("lifeDraftLines", MaidSoulCommonConfig.LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY.get()));
        MaidSoulCommonConfig.LIFE_MEMORY_RAW_LINES_PER_EPISODE.set(number("lifeRawLines", MaidSoulCommonConfig.LIFE_MEMORY_RAW_LINES_PER_EPISODE.get()));
        MaidSoulCommonConfig.CONVERSATION_HOT_COMMAND_KEYWORDS.set(csv("hotCommands", MaidSoulCommonConfig.CONVERSATION_HOT_COMMAND_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS.set(csv("complexFallback", MaidSoulCommonConfig.LOCAL_COMMAND_COMPLEX_FALLBACK_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS.set(number("stepTimeout", MaidSoulCommonConfig.LOCAL_COMMAND_IMMEDIATE_STEP_TIMEOUT_TICKS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_ACK_TEMPLATE.set(text("ackTemplate", MaidSoulCommonConfig.LOCAL_COMMAND_ACK_TEMPLATE.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_ON_KEYWORDS.set(csv("followOn", MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_ON_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS.set(csv("followOff", MaidSoulCommonConfig.LOCAL_COMMAND_FOLLOW_OFF_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_SIT_ON_KEYWORDS.set(csv("sitOn", MaidSoulCommonConfig.LOCAL_COMMAND_SIT_ON_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_SIT_OFF_KEYWORDS.set(csv("sitOff", MaidSoulCommonConfig.LOCAL_COMMAND_SIT_OFF_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS.set(csv("scheduleDay", MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_DAY_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS.set(csv("scheduleNight", MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_NIGHT_KEYWORDS.get()));
        MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS.set(csv("scheduleAll", MaidSoulCommonConfig.LOCAL_COMMAND_SCHEDULE_ALL_KEYWORDS.get()));
        MaidSoulCommonConfig.DEBUG_CHAT_ECHO_ENABLED.set(bool("debugEcho", MaidSoulCommonConfig.DEBUG_CHAT_ECHO_ENABLED.get()));
        MaidSoulCommonConfig.TRACE_CHAT_ECHO_ENABLED.set(bool("traceEcho", MaidSoulCommonConfig.TRACE_CHAT_ECHO_ENABLED.get()));
        MaidSoulCommonConfig.TRACE_CHAT_VERBOSE_ECHO_ENABLED.set(bool("verboseTrace", MaidSoulCommonConfig.TRACE_CHAT_VERBOSE_ECHO_ENABLED.get()));
        MaidSoulCommonConfig.TOPIC_DEDUP_TRACE_ECHO_ENABLED.set(bool("topicTrace", MaidSoulCommonConfig.TOPIC_DEDUP_TRACE_ECHO_ENABLED.get()));
        MaidSoulCommonConfig.DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED.set(bool("fullPromptEcho", MaidSoulCommonConfig.DEBUG_FULL_PROMPT_CHAT_ECHO_ENABLED.get()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - 210;
        graphics.drawCenteredString(this.font, "MaidSoulCore 设置", this.width / 2, 6, 0xFFFFFF);
        graphics.drawString(this.font, "当前页: " + currentPage.title, left, 42, 0xC8C8C8);
        for (Map.Entry<String, String> entry : fieldLabels.entrySet()) {
            EditBox box = fields.get(entry.getKey());
            if (box != null) {
                graphics.drawString(this.font, entry.getValue(), box.getX(), box.getY() - 10, 0xA8A8A8);
            }
        }
        String help = switch (currentPage) {
            case SPLIT -> "分句页控制回复拆成几条、太长时怎么处理、逐句间隔多快。短文本分裂强度越低，越不容易被切碎。";
            case INTERRUPT -> "打断页控制受击、死亡、失败等即时事件是否压过旧话题，以及是否清空旧的待播句子。";
            case EMOTION -> "情绪页控制心情、信任、压力、疼痛、受击扣分、安抚恢复和未解决话题保留时间。";
            case RECOVERY -> "恢复页控制短期缓和、睡醒恢复和跨天恢复。信任与芥蒂默认不会几秒内自动修好。";
            case PERSONA -> "角色卡页控制性格如何调制情绪：敏感、宽恕、依恋、自尊、恐惧、愤怒表达、安抚需求和边界感。";
            case CONVERSATION -> "聊天页控制普通聊天节奏、空回复兜底、复读保护和等待/不回复/结束触发词。";
            default -> "";
        };
        if (!help.isBlank()) {
            MultiLineTextWidget widget = new MultiLineTextWidget(Component.literal(help), this.font);
            widget.setMaxWidth(420);
            widget.setPosition(left, this.height - 74);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private String text(String key, String fallback) {
        EditBox box = fields.get(key);
        if (box == null) {
            return fallback;
        }
        String value = StringUtils.trimToEmpty(box.getValue());
        return value.isEmpty() ? fallback : value;
    }

    private int number(String key, int fallback) {
        try {
            return Integer.parseInt(text(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean bool(String key, boolean fallback) {
        ToggleButton button = toggles.get(key);
        return button == null ? fallback : button.value();
    }

    private List<String> csv(String key, List<? extends String> fallback) {
        String value = text(key, join(fallback));
        ArrayList<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? fallback.stream().map(String::valueOf).toList() : result;
    }

    private static String join(List<? extends String> values) {
        return String.join(", ", values.stream().map(String::valueOf).toList());
    }

    private enum Page {
        INTEGRATION("集成"),
        BEHAVIOR("行为"),
        CONVERSATION("聊天"),
        FOLLOWUP("续话"),
        SPLIT("分句"),
        INTERRUPT("打断"),
        EMOTION("情绪"),
        RECOVERY("恢复"),
        PERSONA("角色"),
        MEMORY("记忆"),
        LIFE_MEMORY("生命"),
        COMMANDS("命令"),
        DEBUG("调试");

        private final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private static final class ToggleButton extends Button {
        private final String label;
        private boolean value;

        private ToggleButton(int x, int y, int width, int height, String label, boolean initialValue) {
            super(x, y, width, height, Component.empty(), button -> {
            }, DEFAULT_NARRATION);
            this.label = label;
            this.value = initialValue;
            refreshText();
        }

        @Override
        public void onPress() {
            this.value = !this.value;
            refreshText();
        }

        private void refreshText() {
            setMessage(Component.literal(label + ": " + (value ? "开" : "关")));
        }

        private boolean value() {
            return value;
        }
    }
}
