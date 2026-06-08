package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.conversation.ConversationJournalService;
import com.maidsoulcore.forge.conversation.ConversationInterruptService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主动陪伴服务。
 * <p>
 * 这个服务负责把 Forge/TLM 事件翻译成“女仆像真人一样主动说话”的行为链：
 * 1. 监听显式事件与环境变化；
 * 2. 给模型发起主动回复请求；
 * 3. 先显示等待态；
 * 4. 再把整段回复按句子拆开，逐句落到气泡和聊天栏。
 * <p>
 * 这样做之后，聊天体验不再是“瞬间整段弹出”，而更接近真实陪伴中的短句节奏。
 */
public final class MaidSoulCompanionService {
    /**
     * 主动回复统一走单线程执行器，保证同一只女仆的对外说话顺序稳定。
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "maidsoulcore-proactive-chat");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * 记录最近一次主动发言时间，用于全局冷却。
     */
    private static final ConcurrentMap<UUID, Long> LAST_PROACTIVE_REPLY_MILLIS = new ConcurrentHashMap<>();
    /**
     * 记录主人最近一次主动聊天时间，用于切换视觉高活跃窗口。
     */
    private static final ConcurrentMap<UUID, Long> LAST_OWNER_CHAT_MILLIS = new ConcurrentHashMap<>();
    /**
     * 记录最近一次“有活动”的时间，用于空闲搭话判定。
     */
    private static final ConcurrentMap<UUID, Long> LAST_ACTIVITY_MILLIS = new ConcurrentHashMap<>();
    /**
     * 睡眠状态边沿检测缓存。
     */
    private static final ConcurrentMap<UUID, Boolean> LAST_SLEEPING_STATE = new ConcurrentHashMap<>();
    /**
     * 敌对生物摘要签名缓存，避免同样的环境反复提醒。
     */
    private static final ConcurrentMap<UUID, Integer> LAST_HOSTILE_SIGNATURE = new ConcurrentHashMap<>();
    /**
     * 环境类回复的单独冷却，防止周围玩家、动物、风景提醒过密。
     */
    private static final ConcurrentMap<UUID, Long> LAST_ENVIRONMENT_REPLY_MILLIS = new ConcurrentHashMap<>();
    /**
     * 空闲搭话状态机。
     * <p>
     * 用于实现：
     * 1. 首次搭话；
     * 2. 主人未回应时的二次提醒；
     * 3. 再次未回应时的轻微闷气；
     * 4. 三次后进入待机沉默；
     * 5. 沉默一段时间后再做低频试探。
     */
    private static final ConcurrentMap<UUID, IdleTopicState> IDLE_TOPIC_STATES = new ConcurrentHashMap<>();
    /**
     * 显式高频事件去重缓存。
     * <p>
     * 这层不替代 topic dedup，而是专门处理“同一个强事件被底层频繁抖动触发”的问题，
     * 例如同一个怪连续多次打到女仆时，不希望聊天栏每一下都重复播报。
     */
    private static final ConcurrentMap<UUID, ExplicitEventEchoState> LAST_EXPLICIT_EVENT_ECHO = new ConcurrentHashMap<>();
    /**
     * 当前正在请求模型的女仆集合，防止并发重复请求。
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<UUID, Long> LAST_PRESET_CHECK_MILLIS = new ConcurrentHashMap<>();
    private MaidSoulCompanionService() {
    }

    /**
     * 每个 tick 驱动一次轻量主动逻辑。
     * <p>
     * 当前会做四件事：
     * 1. 保证聊天预设已注入；
     * 2. 推进逐句输出队列；
     * 3. 检测睡眠切换与敌对生物摘要；
     * 4. 在平静陪伴状态下周期性发起空闲搭话。
     */
    public static void onMaidTick(EntityMaid maid) {
        ensureChatPresetOccasionally(maid);
        MaidSoulSpeechService.flushPendingSpeech(maid);
        MaidSoulVisionService.onMaidTick(maid);
        if (!MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get()) {
            return;
        }
        if (maid.tickCount % MaidSoulCommonConfig.PROACTIVE_SCAN_INTERVAL_TICKS.get() != 0) {
            return;
        }
        detectHostileSummary(maid);
        detectIdleCompanionChat(maid);
    }

    private static void ensureChatPresetOccasionally(EntityMaid maid) {
        long now = System.currentTimeMillis();
        Long previous = LAST_PRESET_CHECK_MILLIS.get(maid.getUUID());
        if (previous != null && now - previous < 10_000L) {
            return;
        }
        LAST_PRESET_CHECK_MILLIS.put(maid.getUUID(), now);
        MaidSoulSiteService.ensureChatPreset(maid);
    }

    /**
     * 处理显式事件，例如受击、进食、交互。
     */
    public static void onExplicitEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        markActivity(maid.getUUID());
        if (!eventType.startsWith("maid.idle.")) {
            resetIdleTopicState(maid.getUUID(), System.currentTimeMillis());
        }
        if (!MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get()) {
            return;
        }
        if (shouldSuppressRepeatedExplicitEvent(maid, eventType, detail)) {
            observeSuppressedExplicitEvent(maid, eventType, detail, priority, "explicit_repeat_suppressed");
            return;
        }
        if (shouldSuppressTopic(maid, eventType, detail)) {
            return;
        }
        if (!allowProactiveEvent(maid, eventType)) {
            return;
        }
        triggerProactiveEvent(maid, eventType, detail, priority);
    }

    /**
     * ?? tick ???????????
     */
    public static void onObservedStateChange(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (!eventType.startsWith("maid.idle.")) {
            resetIdleTopicState(maid.getUUID(), System.currentTimeMillis());
        }
        if (!MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get()) {
            return;
        }
        if (!shouldSpeakForObservedState(eventType)) {
            return;
        }
        if (shouldSuppressTopic(maid, eventType, detail)) {
            return;
        }
        if (!allowProactiveEvent(maid, eventType)) {
            return;
        }
        triggerProactiveEvent(maid, eventType, detail, priority);
    }

    /**
     * ??????????????????????
     * ???????????????????
     */
    public static boolean allowProactiveEvent(EntityMaid maid, String eventType) {
        if (isChatFocusActive(maid) && !isChatFocusBypassEvent(eventType)) {
            traceTopicEcho(maid, "chat_focus_skip", eventType);
            return false;
        }
        if (MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid) && isCasualCareEvent(eventType)) {
            traceTopicEcho(maid, "owner_offense_guard", eventType);
            return false;
        }
        return MaidSoulCommonConfig.ENABLE_PROACTIVE_CHAT.get() && shouldReplyForEvent(maid.getUUID(), eventType);
    }

    /**
     * 对外暴露一个统一的主动发言入口，
     * 让视觉、环境、行为等不同服务都走同一条输出链。
     */
    public static void triggerProactiveEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        // 主动事件入口只接收“世界/行为发生了什么”。
        // 是否真的说出口必须经过：认知帧 -> 情绪更新 -> 打断判断 -> MindLoop。
        // 普通参考信息不要绕过这里直接 queueSpeech，否则就会和主聊天互相抢话。
        // 事件 reference 的落盘由统一 runtime 在真正消费 proactive job 时完成，
        // 这样可以避免“被检测到”和“被思考到”各写一遍，导致模型误以为事件重复发生。
        if (MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid) && isCasualCareEvent(eventType)) {
            traceTopicEcho(maid, "owner_offense_guard", eventType);
            MaidSoulStateRegistry.record(maid, "maidsoul.affect.gate", "blocked casual event while owner offense is unresolved: " + eventType, priority);
            return;
        }
        MaidSoulCognitionService.CognitiveFrame cognitiveFrame = MaidSoulCognitionService.observePerception(maid, "proactive_event", eventType, detail, priority);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.cognition", MaidSoulCognitionService.debugSummary(maid));
        MaidSoulEmotionService.observeEvent(maid, eventType, detail, priority);
        boolean interrupted = ConversationInterruptService.record(maid, eventType, detail, priority);
        if (interrupted) {
            MaidSoulChatLoopRuntimeService.interruptForEvent(maid, eventType, detail, priority);
            if (MaidSoulCommonConfig.CONVERSATION_INTERRUPT_SPEECH_ENABLED.get()) {
                MaidSoulSpeechService.interruptSpeech(maid, eventType);
            }
        }
        if (MaidSoulRuntimeRouterService.supportsEventLinePool(eventType)) {
            MaidSoulEventLinePoolService.ensureWarmupAsync(maid, eventType, detail);
        }
        if (shouldSuppressTopic(maid, eventType, detail)) {
            MaidSoulMindLoopService.MindDecision suppressed = MaidSoulMindLoopService.MindDecision.silence("topic_dedup");
            MaidSoulUnderstandingService.observe(maid, eventType, detail, priority, cognitiveFrame, suppressed);
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.mindloop", suppressed.action() + " | " + suppressed.reason());
            return;
        }
        MaidSoulMindLoopService.MindDecision decision = MaidSoulMindLoopService.decideProactiveReaction(
                maid,
                eventType,
                detail,
                priority,
                cognitiveFrame
        );
        MaidSoulUnderstandingService.observe(maid, eventType, detail, priority, cognitiveFrame, decision);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.mindloop", decision.action() + " | " + decision.reason());
        if (decision.action() == MaidSoulMindLoopService.MindAction.SILENCE) {
            return;
        }
        MaidSoulTopicCooldownService.markPending(maid, eventType, detail);
        generateProactiveReplyAsync(maid, eventType, detail, priority);
    }

    private static void observeSuppressedExplicitEvent(EntityMaid maid,
                                                       String eventType,
                                                       String detail,
                                                       EventPriority priority,
                                                       String reason) {
        MaidSoulCognitionService.CognitiveFrame cognitiveFrame = MaidSoulCognitionService.observePerception(
                maid,
                "suppressed_explicit_event",
                eventType,
                detail,
                priority
        );
        MaidSoulEmotionService.observeEvent(maid, eventType, detail, priority);
        MaidSoulMindLoopService.MindDecision suppressed = MaidSoulMindLoopService.MindDecision.silence(reason);
        MaidSoulUnderstandingService.observe(maid, eventType, detail, priority, cognitiveFrame, suppressed);
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.cognition", MaidSoulCognitionService.debugSummary(maid));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.mindloop", suppressed.action() + " | " + suppressed.reason());
    }

    /**
     * ???????????????????
     */
    public static void markOwnerChat(EntityMaid maid) {
        long now = System.currentTimeMillis();
        LAST_OWNER_CHAT_MILLIS.put(maid.getUUID(), now);
        LAST_ACTIVITY_MILLIS.put(maid.getUUID(), now);
        resetIdleTopicState(maid.getUUID(), now);
    }

    /**
     * 对外暴露最近一次主人聊天时间。
     */
    public static long getLastOwnerChatMillis(UUID maidId) {
        return LAST_OWNER_CHAT_MILLIS.getOrDefault(maidId, 0L);
    }

    /**
     * 判断当前是不是聊天活跃窗口。
     */
    public static boolean isRecentOwnerChatActive(EntityMaid maid) {
        long lastOwnerChat = getLastOwnerChatMillis(maid.getUUID());
        if (lastOwnerChat <= 0L) {
            return false;
        }
        long activeWindowMillis = MaidSoulCommonConfig.VISION_CHAT_ACTIVE_WINDOW_SECONDS.get() * 1000L;
        return System.currentTimeMillis() - lastOwnerChat <= activeWindowMillis;
    }

    /**
     * 为视觉层返回当前推荐的采样间隔。
     */
    public static int visionIntervalSeconds(EntityMaid maid) {
        if (MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.get()) {
            return MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get();
        }
        IdleTopicState idleTopicState = IDLE_TOPIC_STATES.get(maid.getUUID());
        if (idleTopicState != null && idleTopicState.stage() == IdleTopicStage.DORMANT) {
            long dormantMillis = System.currentTimeMillis() - idleTopicState.lastPromptMillis();
            long recheckMillis = MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get() * 1000L;
            if (dormantMillis >= recheckMillis) {
                return MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get();
            }
            return MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_VISION_INTERVAL_SECONDS.get();
        }
        if (isChatFocusActive(maid)) {
            return MaidSoulCommonConfig.CHAT_FOCUS_VISION_INTERVAL_SECONDS.get();
        }
        return isRecentOwnerChatActive(maid)
                ? MaidSoulCommonConfig.VISION_CHAT_ACTIVE_INTERVAL_SECONDS.get()
                : MaidSoulCommonConfig.VISION_IDLE_INTERVAL_SECONDS.get();
    }

    /**
     * 判断当前是否应进入聊天专注态。
     * <p>
     * 进入条件分两层：
     * 1. 主人最近刚发过消息，说明正在持续对话；
     * 2. 运行时队列里仍有主人聊天任务，说明这轮命令还没处理完。
     * 只要任一条件成立，就尽量暂停普通视觉和普通环境反馈。
     */
    public static boolean isChatFocusActive(EntityMaid maid) {
        if (!MaidSoulCommonConfig.CHAT_FOCUS_MODE_ENABLED.get()) {
            return false;
        }
        long lastOwnerChat = getLastOwnerChatMillis(maid.getUUID());
        long holdMillis = MaidSoulCommonConfig.CHAT_FOCUS_HOLD_SECONDS.get() * 1000L;
        boolean recentChatWindow = lastOwnerChat > 0L && System.currentTimeMillis() - lastOwnerChat <= holdMillis;
        return recentChatWindow
                || MaidSoulChatLoopRuntimeService.hasPendingOwnerInput(maid);
    }

    /**
     * 检测睡眠状态切换，并在切换时生成轻量回复。
     */
    private static void detectSleepTransition(EntityMaid maid) {
        boolean current = maid.isSleeping();
        Boolean previous = LAST_SLEEPING_STATE.put(maid.getUUID(), current);
        if (previous == null || previous == current) {
            return;
        }
        String eventType = current ? "maid.sleep.enter" : "maid.sleep.exit";
        String detail = current ? "maid started sleeping" : "maid woke up";
        if (allowProactiveEvent(maid, eventType)) {
            triggerProactiveEvent(maid, eventType, detail, EventPriority.P1);
        }
    }

    /**
     * 检测敌对生物摘要变化。
     * <p>
     * 规则保持之前的设计：
     * - 普通怪低于阈值不提醒；
     * - 高风险怪可以绕过阈值；
     * - 主人近距离有危险时允许更敏感提醒。
     */
    private static void detectHostileSummary(EntityMaid maid) {
        HostileSummary summary = HostileSummary.scan(maid);
        if (!summary.shouldAlert()) {
            LAST_HOSTILE_SIGNATURE.remove(maid.getUUID());
            return;
        }
        int signature = summary.signature();
        Integer previous = LAST_HOSTILE_SIGNATURE.put(maid.getUUID(), signature);
        if (previous != null && previous == signature) {
            return;
        }
        if (!allowProactiveEvent(maid, "world.hostile_summary.changed")) {
            return;
        }
        MaidSoulStateRegistry.record(maid, "world.hostile_summary.changed", summary.summaryText(), EventPriority.P1);
        triggerProactiveEvent(maid, "world.hostile_summary.changed", summary.summaryText(), EventPriority.P1);
    }

    /**
     * 在环境平稳、主人就在身边、且女仆已经安静陪伴一段时间后，发起轻量闲聊。
     * <p>
     * 这部分是“像真人”的关键补足：没有事件时也会有自然搭话，而不是永远被动等待。
     */
    private static void detectIdleCompanionChat(EntityMaid maid) {
        if (!MaidSoulCommonConfig.IDLE_CHAT_ENABLED.get()) {
            return;
        }
        if (MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.get()) {
            return;
        }
        if (maid.isSleeping() || maid.getOwner() == null || maid.distanceTo(maid.getOwner()) > 8.0f) {
            return;
        }
        if (maid.getTarget() != null || maid.getLastHurtByMob() != null) {
            return;
        }
        if (MaidSoulSpeechService.hasPendingSpeech(maid) || IN_FLIGHT.contains(maid.getUUID())) {
            return;
        }
        if (MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid)) {
            traceTopicEcho(maid, "idle_guard_owner_offense", "unresolved owner offense; skip casual idle care");
            return;
        }
        long now = System.currentTimeMillis();
        Long lastActivity = LAST_ACTIVITY_MILLIS.get(maid.getUUID());
        IdleTopicState state = IDLE_TOPIC_STATES.getOrDefault(maid.getUUID(), IdleTopicState.initial());
        if (hasOwnerRespondedAfterPrompt(maid.getUUID(), state)) {
            resetIdleTopicState(maid.getUUID(), now);
            state = IdleTopicState.initial();
        }
        if (state.stage() == IdleTopicStage.DORMANT) {
            long recheckMillis = MaidSoulCommonConfig.IDLE_TOPIC_DORMANT_RECHECK_SECONDS.get() * 1000L;
            if (now - state.lastPromptMillis() < recheckMillis) {
                return;
            }
            if (lastActivity != null && now - lastActivity < recheckMillis) {
                return;
            }
            String detail = "owner has been quiet for a long while; softly check whether a low-pressure companion topic is welcome; do not assume dazing or ignoring";
            if (!allowProactiveEvent(maid, "maid.idle.reconnect")) {
                return;
            }
            recordIdleTopicState(maid.getUUID(), new IdleTopicState(IdleTopicStage.FIRST, now, 1));
            MaidSoulStateRegistry.record(maid, "maid.idle.reconnect", detail, EventPriority.P2);
            triggerProactiveEvent(maid, "maid.idle.reconnect", detail, EventPriority.P2);
            return;
        }

        long requiredGapMillis = requiredIdleTopicGapMillis(state);
        if (lastActivity != null && now - lastActivity < requiredGapMillis) {
            return;
        }

        IdleTopicTrigger trigger = nextIdleTopicTrigger(state);
        if (!allowProactiveEvent(maid, trigger.eventType())) {
            return;
        }
        recordIdleTopicState(maid.getUUID(), new IdleTopicState(trigger.nextStage(), now, trigger.nextAttempt()));
        MaidSoulStateRegistry.record(maid, trigger.eventType(), trigger.detail(), EventPriority.P2);
        triggerProactiveEvent(maid, trigger.eventType(), trigger.detail(), EventPriority.P2);
    }

    /**
     * 异步调用真实模型生成主动回复。
     * <p>
     * 流程是：
     * 1. 先创建等待气泡；
     * 2. 在后台线程请求模型；
     * 3. 回到主线程后，把整段回复拆成短句并排进逐句输出队列。
     */
    private static void generateProactiveReplyAsync(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        UUID maidId = maid.getUUID();
        if (!IN_FLIGHT.add(maidId)) {
            return;
        }
        markActivity(maidId);
        MaidSoulStateRegistry.record(maid, "maidsoul.proactive.request", eventType + " | " + detail, priority);
        try {
            MaidSoulChatLoopRuntimeService.handleProactiveEvent(maid, eventType, detail, priority);
            long now = System.currentTimeMillis();
            LAST_PROACTIVE_REPLY_MILLIS.put(maidId, now);
            rememberExplicitEventEcho(maid, eventType, detail, now);
            if (isEnvironmentEvent(eventType)) {
                LAST_ENVIRONMENT_REPLY_MILLIS.put(maidId, now);
            }
        } finally {
            IN_FLIGHT.remove(maidId);
        }
    }

    /**
     * ????????
     */
    private static boolean shouldReplyForEvent(UUID maidId, String eventType) {
        long now = System.currentTimeMillis();
        if (isPriorityWorldEvent(eventType) || isConversationFollowupEvent(eventType)) {
            return true;
        }
        if (isEnvironmentEvent(eventType)) {
            long environmentCooldownMillis = MaidSoulCommonConfig.ENVIRONMENT_REPLY_COOLDOWN_SECONDS.get() * 1000L;
            Long lastEnvironmentReply = LAST_ENVIRONMENT_REPLY_MILLIS.get(maidId);
            if (lastEnvironmentReply != null && now - lastEnvironmentReply < environmentCooldownMillis) {
                return false;
            }
        }
        long cooldownMillis = MaidSoulCommonConfig.PROACTIVE_CHAT_COOLDOWN_SECONDS.get() * 1000L;
        Long previous = LAST_PROACTIVE_REPLY_MILLIS.get(maidId);
        if (previous != null && now - previous < cooldownMillis) {
            return isCriticalEvent(eventType);
        }
        return !"maid.death".equals(eventType);
    }

    /**
     * ?????????????????
     */
    private static boolean shouldSuppressTopic(EntityMaid maid, String eventType, String detail) {
        boolean suppress = MaidSoulTopicCooldownService.shouldSuppress(maid, eventType, detail);
        if (suppress) {
            traceTopicEcho(maid, "suppressed", eventType + " => " + MaidSoulTopicCooldownService.classifyTopicKey(eventType, detail));
        }
        return suppress;
    }

    /**
     * 抑制同一类显式强事件的高频重复回声。
     * <p>
     * 目前重点处理：
     * - `maid.attacked`
     * - `maid.attacked.by_owner`
     * <p>
     * 规则：
     * 1. 同一攻击来源在短窗口内连续触发，只说一次；
     * 2. 但如果女仆血量已经偏低，允许再次提醒；
     * 3. 如果攻击来源变了，也允许重新提醒；
     * 4. 这层只抑制“明显重复的同类抖动”，不影响真正的重要状态变化。
     */
    private static boolean shouldSuppressRepeatedExplicitEvent(EntityMaid maid, String eventType, String detail) {
        if (!isRepeatSensitiveExplicitEvent(eventType)) {
            return false;
        }
        ExplicitEventEchoState previous = LAST_EXPLICIT_EVENT_ECHO.get(maid.getUUID());
        if (previous == null) {
            return false;
        }
        String currentKey = explicitEventKey(eventType, detail);
        long now = System.currentTimeMillis();
        long windowMillis = repeatedExplicitEventWindowMillis(eventType);
        if (!previous.eventKey().equals(currentKey)) {
            return false;
        }
        if (now - previous.lastEchoMillis() >= windowMillis) {
            return false;
        }
        if (isLowHealthException(maid, previous)) {
            return false;
        }
        traceTopicEcho(maid, "explicit_suppressed", eventType + " => " + currentKey);
        return true;
    }

    /**
     * 记录最近一次已播报的显式强事件。
     */
    private static void rememberExplicitEventEcho(EntityMaid maid, String eventType, String detail, long now) {
        if (!isRepeatSensitiveExplicitEvent(eventType)) {
            return;
        }
        LAST_EXPLICIT_EVENT_ECHO.put(
                maid.getUUID(),
                new ExplicitEventEchoState(
                        explicitEventKey(eventType, detail),
                        now,
                        maid.getHealth()
                )
        );
    }

    /**
     * 判断是否属于需要做高频抑制的显式事件。
     */
    private static boolean isRepeatSensitiveExplicitEvent(String eventType) {
        return "maid.attacked".equals(eventType) || "maid.attacked.by_owner".equals(eventType);
    }

    /**
     * 生成显式事件去重键。
     * <p>
     * 对受击事件，只按“事件类型 + 攻击来源”去重，
     * 不把每次伤害数值纳进去，否则同一个怪连续攻击会被视为不同事件。
     */
    private static String explicitEventKey(String eventType, String detail) {
        if (detail == null || detail.isBlank()) {
            return eventType;
        }
        int amountIndex = detail.indexOf(" amount=");
        String normalizedDetail = amountIndex >= 0 ? detail.substring(0, amountIndex).trim() : detail.trim();
        return eventType + "|" + normalizedDetail;
    }

    /**
     * 返回显式高频事件的抑制窗口。
     */
    private static long repeatedExplicitEventWindowMillis(String eventType) {
        if ("maid.attacked.by_owner".equals(eventType)) {
            return 6_000L;
        }
        return 4_000L;
    }

    /**
     * 当血量进入危险段时，允许再次提醒主人。
     * <p>
     * 只要满足以下任一条件，就不抑制：
     * - 当前血量低于 35%；
     * - 当前血量低于 8 点且比上次播报又继续下降。
     */
    private static boolean isLowHealthException(EntityMaid maid, ExplicitEventEchoState previous) {
        float currentHealth = maid.getHealth();
        float maxHealth = Math.max(1.0f, maid.getMaxHealth());
        if (currentHealth / maxHealth <= 0.35f) {
            return true;
        }
        return currentHealth <= 8.0f && currentHealth < previous.healthWhenEchoed() - 0.5f;
    }

    /**
     * ?????????? trace ?????????????
     */
    private static void traceTopicEcho(EntityMaid maid, String traceType, String detail) {
        if (!MaidSoulCommonConfig.TOPIC_DEDUP_TRACE_ECHO_ENABLED.get()) {
            return;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.topic." + traceType, detail);
    }

    /**
     * ?????????
     */
    private static void markActivity(UUID maidId) {
        LAST_ACTIVITY_MILLIS.put(maidId, System.currentTimeMillis());
    }

    /**
     * 根据当前空闲搭话阶段，返回下一次所需的等待时长。
     */
    private static long requiredIdleTopicGapMillis(IdleTopicState state) {
        return MaidSoulCommonConfig.IDLE_TOPIC_RETRY_SECONDS.get() * 1000L;
    }

    /**
     * 主人是否在女仆上一次主动搭话后给出了回应。
     */
    private static boolean hasOwnerRespondedAfterPrompt(UUID maidId, IdleTopicState state) {
        if (state.stage() == IdleTopicStage.READY || state.lastPromptMillis() <= 0L) {
            return false;
        }
        return getLastOwnerChatMillis(maidId) > state.lastPromptMillis();
    }

    /**
     * 写入新的空闲搭话状态。
     */
    private static void recordIdleTopicState(UUID maidId, IdleTopicState state) {
        IDLE_TOPIC_STATES.put(maidId, state);
    }

    /**
     * 收到主人回应后，恢复到初始空闲陪伴状态。
     */
    private static void resetIdleTopicState(UUID maidId, long now) {
        IDLE_TOPIC_STATES.put(maidId, new IdleTopicState(IdleTopicStage.READY, now, 0));
    }

    /**
     * 计算当前应发送的空闲搭话事件。
     */
    private static IdleTopicTrigger nextIdleTopicTrigger(IdleTopicState state) {
        return switch (state.stage()) {
            case READY -> new IdleTopicTrigger(
                    "maid.idle.companion",
                    "owner nearby and environment calm for a while; consider whether to open a light low-pressure companion topic",
                    IdleTopicStage.FIRST,
                    1
            );
            case FIRST -> new IdleTopicTrigger(
                    "maid.idle.followup",
                    "owner has been quiet after the previous companion topic; consider waiting or adding one gentle continuation without pressure",
                    IdleTopicStage.SECOND,
                    2
            );
            case SECOND -> new IdleTopicTrigger(
                    "maid.idle.sulking",
                    "owner remains quiet; do not accuse or ask why the owner is not responding; usually wait or stay quietly nearby",
                    IdleTopicStage.DORMANT,
                    3
            );
            case DORMANT -> new IdleTopicTrigger(
                    "maid.idle.reconnect",
                    "after a long silence, consider one soft reconnecting line only if it would not disturb the owner",
                    IdleTopicStage.FIRST,
                    1
            );
        };
    }

    /**
     * 只让高价值状态边缘变化触发主动开口。
     */
    private static boolean shouldSpeakForObservedState(String eventType) {
        return switch (eventType) {
            case "maid.sleep.enter",
                    "maid.sleep.exit",
                    "maid.action.task",
                    "maid.action.follow",
                    "maid.action.schedule",
                    "maid.action.sit" -> true;
            default -> false;
        };
    }

    /**
     * 环境类事件走更保守的节流。
     */
    private static boolean isEnvironmentEvent(String eventType) {
        return eventType.startsWith("owner.view.")
                || eventType.startsWith("world.")
                || "maid.idle.companion".equals(eventType);
    }

    private static boolean isCasualCareEvent(String eventType) {
        return eventType.startsWith("maid.idle.")
                || eventType.startsWith("owner.view.")
                || "world.weather.changed".equals(eventType)
                || "world.time_phase.changed".equals(eventType)
                || "maid.interact".equals(eventType)
                || "maid.ate".equals(eventType)
                || "conversation.followup".equals(eventType);
    }

    /**
     * 关键事件允许压过通用冷却，避免该反馈的时候不反馈。
     */
    private static boolean isCriticalEvent(String eventType) {
        return switch (eventType) {
            case "maid.attacked",
                    "maid.attacked.by_owner",
                    "world.weather.changed",
                    "world.time_phase.changed",
                    "maid.action.task",
                    "maid.action.executed",
                    "maid.action.follow",
                    "maid.action.sit",
                    "maid.action.schedule" -> true;
            default -> eventType.contains("failed")
                    || eventType.contains("missing")
                    || eventType.contains("not_allowed")
                    || eventType.contains("target_missing")
                    || eventType.startsWith("owner.command");
        };
    }

    /**
     * 聊天专注态下允许继续放行的少数关键事件。
     * <p>
     * 这类事件要么涉及战斗/受击安全，要么属于任务失败，
     * 不能因为主人正在打字就完全吞掉。
     */
    private static boolean isChatFocusBypassEvent(String eventType) {
        return isConversationFollowupEvent(eventType)
                || eventType.startsWith("maid.attacked")
                || eventType.contains("hostile")
                || eventType.contains("task.failed")
                || eventType.contains("target_missing")
                || eventType.contains("not_allowed")
                || eventType.contains("missing")
                || eventType.contains("death");
    }

    /**
     * 这些世界事件即使在普通冷却内也允许发声。
     */
    private static boolean isPriorityWorldEvent(String eventType) {
        return switch (eventType) {
            case "world.weather.changed",
                    "world.time_phase.changed",
                    "maid.sleep.enter",
                    "maid.sleep.exit",
                    "maid.attacked",
                    "maid.attacked.by_owner" -> true;
            default -> false;
        };
    }

    private static boolean isConversationFollowupEvent(String eventType) {
        return "conversation.followup".equals(eventType) || "maid.chat.followup".equals(eventType);
    }

    /**
     * ???????? trace ???
     */
    private static String abbreviate(String text) {
        return text.length() <= 96 ? text : text.substring(0, 96) + "...";
    }

    /**
     * 附近敌对摘要。
     */
    private record HostileSummary(
            int normalCount,
            int highRiskCount,
            List<String> sampleNames,
            boolean ownerInDanger
    ) {
        static HostileSummary scan(EntityMaid maid) {
            LivingEntity owner = maid.getOwner();
            double radius = 16.0d;
            Set<String> highRiskWhitelist = Set.copyOf(MaidSoulCommonConfig.HIGH_RISK_MOBS.get().stream().map(String::valueOf).toList());
            List<Entity> nearby = maid.level().getEntities(
                    maid,
                    maid.getBoundingBox().inflate(radius),
                    entity -> entity instanceof Monster && entity.isAlive()
            );

            int normal = 0;
            int highRisk = 0;
            ArrayList<String> names = new ArrayList<>();
            boolean ownerDanger = false;
            for (Entity entity : nearby) {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                String entityId = key == null ? "unknown" : key.toString();
                boolean isHighRisk = highRiskWhitelist.contains(entityId);
                if (isHighRisk) {
                    highRisk++;
                } else {
                    normal++;
                }
                if (names.size() < 4) {
                    names.add(entity.getName().getString() + "(" + String.format(Locale.ROOT, "%.1f", maid.distanceTo(entity)) + ")");
                }
                if (owner != null && entity.distanceTo(owner) <= 8.0f) {
                    ownerDanger = true;
                }
            }
            return new HostileSummary(normal, highRisk, List.copyOf(names), ownerDanger);
        }

        boolean shouldAlert() {
            return highRiskCount >= 1
                    || normalCount >= MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.get()
                    || ownerInDanger && normalCount >= Math.max(1, MaidSoulCommonConfig.NORMAL_HOSTILE_ALERT_THRESHOLD.get() / 2);
        }

        String summaryText() {
            return "normal_hostile=" + normalCount
                    + ", high_risk=" + highRiskCount
                    + ", owner_danger=" + ownerInDanger
                    + ", sample=" + sampleNames;
        }

        int signature() {
            return java.util.Objects.hash(normalCount, highRiskCount, ownerInDanger, sampleNames);
        }
    }

    /**
     * 最近一次显式强事件的播报状态。
     */
    private record ExplicitEventEchoState(
            String eventKey,
            long lastEchoMillis,
            float healthWhenEchoed
    ) {
    }

    /**
     * 空闲搭话阶段。
     */
    private enum IdleTopicStage {
        READY,
        FIRST,
        SECOND,
        DORMANT
    }

    /**
     * 空闲搭话运行时状态。
     */
    private record IdleTopicState(
            IdleTopicStage stage,
            long lastPromptMillis,
            int attemptCount
    ) {
        private static IdleTopicState initial() {
            return new IdleTopicState(IdleTopicStage.READY, 0L, 0);
        }
    }

    /**
     * 下一次空闲搭话触发信息。
     */
    private record IdleTopicTrigger(
            String eventType,
            String detail,
            IdleTopicStage nextStage,
            int nextAttempt
    ) {
    }

}
