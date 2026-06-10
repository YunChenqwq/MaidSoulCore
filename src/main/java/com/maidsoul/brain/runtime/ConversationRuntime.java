package com.maidsoul.brain.runtime;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.reply.effect.ReplyEffectTracker;
import com.maidsoul.brain.reasoning.EnvironmentObservationTool;
import com.maidsoul.brain.reasoning.ReasoningEngine;
import com.maidsoul.brain.reasoning.ReplySanitizer;
import com.maidsoul.brain.reasoning.ViewObservationTool;
import com.maidsoul.brain.text.SentenceSplitter;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 统一聊天运行时。
 *
 * <p>所有外部输入先进入这里，再由内部循环统一消费。这样可以避免“主动聊天、玩家聊天、事件反应”
 * 各自开口，导致角色像多个系统拼在一起。</p>
 */
public final class ConversationRuntime implements AutoCloseable {
    private final BrainConfig config;
    private final ChatSession session = new ChatSession();
    private final RuntimeTraceSink trace;
    private final ReasoningEngine reasoningEngine;
    private final MemoryRuntime memoryRuntime;
    private final ReplyEffectTracker replyEffectTracker = new ReplyEffectTracker();
    private final SentenceSplitter splitter;
    private final StreamingSegmentEmitter streamEmitter = new StreamingSegmentEmitter();
    private final MessageTurnScheduler messageTurnScheduler = new MessageTurnScheduler();
    private final ProactiveScheduler proactiveScheduler;
    private final Consumer<String> output;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<TurnKind> internalTurnQueue = new LinkedBlockingQueue<>();
    private final AtomicLong version = new AtomicLong();
    private final ArrayDeque<String> recentAssistantReplyFingerprints = new ArrayDeque<>();

    private volatile RuntimeState state = RuntimeState.STOP;
    private volatile boolean running;
    private volatile boolean messageTurnScheduled;
    private volatile boolean messageDebounceRequired;
    private volatile ScheduledFuture<?> messageFuture;
    private volatile ScheduledFuture<?> waitFuture;
    private volatile ScheduledFuture<?> proactiveFuture;
    private volatile CompletableFuture<?> internalLoopTask;
    private volatile InterruptFlag currentInterruptFlag;
    private volatile long activeCycleVersion = -1L;
    private volatile long lastMessageReceivedAtMillis;
    private volatile long oldestPendingMessageReceivedAtMillis;
    private volatile long lastAssistantFinishedAtMillis;
    private volatile int proactiveStageIndex;
    private volatile long proactiveGeneration;
    private volatile boolean proactiveAwaitingCycle;
    private volatile int proactiveAwaitingStage = -1;
    private volatile int proactiveVisibleRepliesSinceLastUser;
    private volatile int proactiveFiredCandidatesSinceLastUser;
    private volatile int proactiveSilentDecisionsSinceLastUser;
    private volatile int proactiveLongSilenceChecksSinceLastUser;
    private volatile boolean messageArrivedDuringRun;
    private volatile boolean plannerInterruptRequested;
    private volatile int plannerInterruptConsecutiveCount;
    private volatile long replyLatencyMeasurementStartedAtMillis;
    private final ArrayDeque<ReplyLatencySample> recentReplyLatencies = new ArrayDeque<>();

    public ConversationRuntime(
            BrainConfig config,
            PromptCatalog prompts,
            LlmClient llm,
            Consumer<String> output,
            RuntimeTraceSink trace
    ) {
        this(config, prompts, llm, output, trace, ViewObservationTool.NONE);
    }

    public ConversationRuntime(
            BrainConfig config,
            PromptCatalog prompts,
            LlmClient llm,
            Consumer<String> output,
            RuntimeTraceSink trace,
            ViewObservationTool viewObservationTool
    ) {
        this(config, prompts, llm, output, trace, viewObservationTool, EnvironmentObservationTool.NONE);
    }

    public ConversationRuntime(
            BrainConfig config,
            PromptCatalog prompts,
            LlmClient llm,
            Consumer<String> output,
            RuntimeTraceSink trace,
            ViewObservationTool viewObservationTool,
            EnvironmentObservationTool environmentObservationTool
    ) {
        this.config = config;
        this.output = output;
        this.trace = trace == null ? RuntimeTraceSink.noop() : trace;
        this.splitter = new SentenceSplitter(config.splitter());
        this.proactiveScheduler = new ProactiveScheduler(config.flow());
        this.memoryRuntime = new MemoryRuntime(config.memory());
        this.reasoningEngine = new ReasoningEngine(
                config,
                prompts,
                llm,
                session,
                this.trace,
                memoryRuntime,
                replyEffectTracker,
                streamEmitter::acceptDelta,
                streamEmitter::flush,
                viewObservationTool,
                environmentObservationTool
        );
    }

    public void start() {
        running = true;
        ensureInternalLoopRunning();
        scheduleMessageTurn();
    }

    public MemoryMaintenanceReport maintainV2() {
        return memoryRuntime.maintainV2();
    }

    public String debugMemoryV2(String query, int limit) {
        return memoryRuntime.debugMemoryV2(query, limit);
    }

    public String affectSummary() {
        return memoryRuntime.affectSummary();
    }

    public void receiveUserMessage(String speaker, String content) {
        if (!running || content == null || content.isBlank()) {
            return;
        }
        proactiveGeneration++;
        cancelProactiveTimer();
        proactiveAwaitingStage = -1;
        proactiveVisibleRepliesSinceLastUser = 0;
        proactiveFiredCandidatesSinceLastUser = 0;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveLongSilenceChecksSinceLastUser = 0;
        proactiveStageIndex = ProactiveScheduler.STAGE_LIGHT_FOLLOWUP;
        long newVersion = version.incrementAndGet();
        long now = System.currentTimeMillis();
        lastMessageReceivedAtMillis = now;
        boolean hadPendingBeforeAppend = session.hasPendingMessages();
        if (!hadPendingBeforeAppend) {
            oldestPendingMessageReceivedAtMillis = now;
        }
        ChatMessage userMessage = ChatMessage.user(speaker, content.trim());
        session.appendIncoming(userMessage);
        replyEffectTracker.observeUserMessage(userMessage);
        memoryRuntime.observeUserMessage(content.trim());
        trace.trace("input.user", "version=" + newVersion + " text=" + content.trim());
        trace.trace("affect", memoryRuntime.affectSummary());

        if (messageTurnScheduled) {
            messageDebounceRequired = true;
            if (state == RuntimeState.STOP) {
                cancelMessageTimerLocked();
                messageTurnScheduled = false;
            }
        }

        // 运行中收到新消息时，只标记“需要等输入静默后再处理下一轮”，并请求当前模型链路中断。
        // 不在这里直接启动新 CompletableFuture，否则就会回到旧版本的并发请求燃烧问题。
        if (state == RuntimeState.RUNNING) {
            messageDebounceRequired = true;
            messageArrivedDuringRun = true;
            InterruptFlag flag = currentInterruptFlag;
            if (flag != null) {
                if (!plannerInterruptRequested && plannerInterruptConsecutiveCount < config.flow().plannerInterruptMaxConsecutiveCount()) {
                    plannerInterruptRequested = true;
                    plannerInterruptConsecutiveCount++;
                    flag.requestInterrupt();
                    trace.trace("runtime.interrupt", "新消息到达，已请求中断当前模型链路。连续打断="
                            + plannerInterruptConsecutiveCount + "/" + config.flow().plannerInterruptMaxConsecutiveCount());
                } else {
                    trace.trace("runtime.interrupt.skip", "当前轮次已请求过中断或达到连续打断上限，等待自然收束。");
                }
            }
            return;
        }
        if (state == RuntimeState.WAIT) {
            // 私聊原型里，真实用户消息优先级高于 wait；否则用户已经开口还等 timeout，会像“不理人”。
            leaveWaitState();
            trace.trace("runtime.wait.interrupt", "WAIT 状态收到真实用户消息，提前结束等待并重新判断。");
            ensureInternalLoopRunning();
            scheduleMessageTurn();
            return;
        }
        ensureInternalLoopRunning();
        scheduleMessageTurn();
    }

    /**
     * 接收来自 Minecraft 世界的结构化事件。
     *
     * <p>这类输入不是玩家直接发言，而是“女仆被主人互动”“主人视角附近有怪物”
     * 之类的环境事实。它们会进入记忆与情绪系统，也会作为 reference 消息交给
     * planner 判断是否需要主动回应。</p>
     */
    public void receiveWorldEvent(String eventType, String detail) {
        if (!running) {
            return;
        }
        String safeType = eventType == null || eventType.isBlank() ? "world.event" : eventType.trim();
        String safeDetail = detail == null ? "" : detail.trim();
        memoryRuntime.observeWorldEvent(safeType, safeDetail);
        boolean runningTurn = state == RuntimeState.RUNNING;
        long newVersion = runningTurn ? version.get() : version.incrementAndGet();
        session.appendIncoming(ChatMessage.reference("[世界事件] " + safeType + (safeDetail.isBlank() ? "" : " | " + safeDetail)));
        trace.trace("input.world", "version=" + newVersion + " type=" + safeType + " detail=" + safeDetail);
        trace.trace("affect", memoryRuntime.affectSummary());
        ensureInternalLoopRunning();
        scheduleMessageTurn();
    }

    private synchronized void scheduleMessageTurn() {
        if (!running || state == RuntimeState.WAIT || messageTurnScheduled || !session.hasPendingMessages()) {
            return;
        }
        int pendingCount = session.pendingCount();
        long idleMillis = Math.max(0L, System.currentTimeMillis() - lastMessageReceivedAtMillis);
        MessageTurnScheduler.Decision decision = messageTurnScheduler.decide(
                pendingCount,
                config.flow().talkFrequency(),
                session.hasForcedContinue(),
                idleMillis,
                averageReplyLatencyMillis()
        );
        if (!decision.triggerNow() && decision.delayMillis() < 0) {
            trace.trace("runtime.message.defer", "pending=" + pendingCount + "，频率阈值未满足，等待更多消息。");
            return;
        }
        messageTurnScheduled = true;
        long delay = decision.triggerNow()
                ? Math.max(0, config.flow().messageDebounceMillis())
                : decision.delayMillis();
        messageFuture = scheduler.schedule(() -> {
            synchronized (ConversationRuntime.this) {
                messageTurnScheduled = false;
                messageFuture = null;
                if (!running || state == RuntimeState.WAIT || state == RuntimeState.RUNNING || !session.hasPendingMessages()) {
                    return;
                }
                if (replyLatencyMeasurementStartedAtMillis <= 0) {
                    replyLatencyMeasurementStartedAtMillis = oldestPendingMessageReceivedAtMillis > 0
                            ? oldestPendingMessageReceivedAtMillis
                            : lastMessageReceivedAtMillis;
                }
                internalTurnQueue.offer(TurnKind.MESSAGE);
            }
        }, delay, TimeUnit.MILLISECONDS);
        trace.trace("runtime.message.schedule", "pending=" + pendingCount
                + " delay=" + delay + "ms"
                + (decision.reason().isBlank() ? "" : " / " + decision.reason()));
    }

    private synchronized void ensureInternalLoopRunning() {
        if (!running) {
            return;
        }
        if (internalLoopTask == null || internalLoopTask.isDone()) {
            internalLoopTask = CompletableFuture.runAsync(this::internalLoopSafely, worker);
        }
    }

    private void internalLoopSafely() {
        while (running) {
            try {
                TurnKind kind = internalTurnQueue.take();
                if (!running) {
                    return;
                }
                runOneTurnSafely(kind);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runOneTurnSafely(TurnKind kind) {
        if (kind == TurnKind.MESSAGE) {
            waitForMessageQuietPeriod();
        }
        if (!running || state == RuntimeState.WAIT || !session.hasPendingMessages()) {
            return;
        }
        long cycleVersion = version.get();
        ReasoningEngine.Result result = null;
        boolean proactiveCycle = proactiveAwaitingCycle;
        InterruptFlag interruptFlag = new InterruptFlag();
        currentInterruptFlag = interruptFlag;
        plannerInterruptRequested = false;
        messageArrivedDuringRun = false;
        try {
            state = RuntimeState.RUNNING;
            streamEmitter.reset();
            trace.trace("runtime.cycle", "开始处理，pending=" + session.pendingCount() + " version=" + cycleVersion);
            activeCycleVersion = cycleVersion;
            result = reasoningEngine.runOneCycle(cycleVersion, version::get, interruptFlag);
            if (cycleVersion != version.get()) {
                trace.trace("runtime.discard", "轮次已过期，丢弃输出。");
                return;
            }
            if (result.kind() == ReasoningEngine.ResultKind.WAIT) {
                enterWaitState(result.waitSeconds());
                return;
            }
            if (result.kind() == ReasoningEngine.ResultKind.REPLY && result.replyText() != null) {
                List<String> segments = emitReply(result.replyText());
                replyEffectTracker.recordReply(
                        result.targetMessage(),
                        result.replyText(),
                        segments,
                        result.plannerReasoning(),
                        result.referenceInfo()
                );
            }
            boolean duplicateProactiveSuppressed = false;
            if (result.kind() == ReasoningEngine.ResultKind.STREAMED && result.replyText() != null) {
                List<String> segments = streamEmitter.emittedSegments();
                if (proactiveCycle && segments.isEmpty() && streamEmitter.suppressedDuplicateCount() > 0) {
                    duplicateProactiveSuppressed = true;
                    trace.trace("proactive.dedupe", "主动回复与最近主动发言过于相似，已压制输出。");
                } else if (segments.isEmpty()) {
                    segments = splitter.split(result.replyText());
                }
                if (!duplicateProactiveSuppressed) {
                    replyEffectTracker.recordReply(
                            result.targetMessage(),
                            result.replyText(),
                            segments,
                            result.plannerReasoning(),
                            result.referenceInfo()
                    );
                }
            }
            if ((result.kind() == ReasoningEngine.ResultKind.REPLY || result.kind() == ReasoningEngine.ResultKind.STREAMED)
                    && !duplicateProactiveSuppressed) {
                recordReplyLatency();
                if (proactiveCycle) {
                    markProactiveAssistantFinished();
                } else {
                    markAssistantFinished();
                }
            } else if (duplicateProactiveSuppressed) {
                clearReplyLatencyMeasurement();
                markProactiveDuplicateSuppressed();
            }
        } catch (Exception e) {
            if (running) {
                trace.trace("runtime.error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            currentInterruptFlag = null;
            if (!messageArrivedDuringRun) {
                plannerInterruptConsecutiveCount = 0;
            }
            // 单内部循环保证同一时刻只有这一处会结束 RUNNING。
            // 即使本轮已经因为新消息而过期，也必须释放 RUNNING；否则下一轮 pending 消息会因为
            // scheduleMessageTurn() 看到 RUNNING 而永远排不进去，表现成“打断后不回复/像是不理人”。
            if (state == RuntimeState.RUNNING) {
                state = RuntimeState.STOP;
            }
            messageTurnScheduled = false;
            if (running && state != RuntimeState.WAIT && session.hasPendingMessages()) {
                scheduleMessageTurn();
            }
            if (result != null
                    && (result.kind() == ReasoningEngine.ResultKind.NO_ACTION
                    || result.kind() == ReasoningEngine.ResultKind.WAIT)) {
                clearReplyLatencyMeasurement();
            }
            if (running && proactiveAwaitingCycle && cycleVersion == version.get() && result != null
                    && result.kind() == ReasoningEngine.ResultKind.WAIT) {
                proactiveSilentDecisionsSinceLastUser++;
                proactiveAwaitingCycle = false;
                proactiveAwaitingStage = -1;
                trace.trace("proactive.wait", "planner 对主动候选选择 wait，运行时尊重等待，不覆盖为回复。");
            }
            if (running && state != RuntimeState.WAIT && proactiveAwaitingCycle && cycleVersion == version.get() && result != null) {
                proactiveAwaitingCycle = false;
                proactiveAwaitingStage = -1;
                if (result.kind() == ReasoningEngine.ResultKind.NO_ACTION) {
                    proactiveSilentDecisionsSinceLastUser++;
                    // 本轮已经明确选择沉默，下一阶段要从“这次决定沉默”之后重新计时，
                    // 不能继续用上一句发言结束时间，否则模型一慢就会立刻连触发下一阶段。
                    lastAssistantFinishedAtMillis = System.currentTimeMillis();
                    int activeCuriosity = memoryRuntime.activeCuriosity();
                    if (proactiveScheduler.shouldScheduleAfterSilentDecision(
                            activeCuriosity,
                            proactiveSilentDecisionsSinceLastUser,
                            proactiveFiredCandidatesSinceLastUser)) {
                        scheduleNextProactiveCheckLocked(proactiveGeneration);
                    } else if (scheduleLongSilenceCheckLocked(proactiveGeneration)) {
                        trace.trace("proactive.long_silence.schedule",
                                "短期主动已停止，改为长沉默复检。主动好奇=" + activeCuriosity);
                    } else {
                        trace.trace("proactive.stop", "planner 已连续选择沉默，主动好奇="
                                + activeCuriosity + "，停止主动候选，等待玩家下一条消息。");
                    }
                }
            }
            if (running && !proactiveCycle && state != RuntimeState.WAIT && kind == TurnKind.MESSAGE
                    && cycleVersion == version.get() && result != null
                    && result.kind() == ReasoningEngine.ResultKind.NO_ACTION) {
                markConversationAnchorAfterUserNoAction();
            }
            if (running && !proactiveCycle && state != RuntimeState.WAIT && kind == TurnKind.TIMEOUT
                    && cycleVersion == version.get() && result != null
                    && result.kind() == ReasoningEngine.ResultKind.NO_ACTION) {
                markConversationAnchorAfterTimeoutNoAction();
            }
        }
    }

    private List<String> emitReply(String rawText) {
        List<String> segments = splitter.split(rawText);
        for (String segment : segments) {
            if (shouldSuppressDuplicateProactiveSegment(segment)) {
                trace.trace("proactive.dedupe", "跳过重复主动分句：" + clipTrace(segment));
                continue;
            }
            session.appendAssistant(ChatMessage.assistant(config.identity().botName(), segment));
            memoryRuntime.observeAssistantMessage(segment);
            output.accept(segment);
            rememberAssistantSegment(segment);
            sleepQuietly(config.splitter().bubbleDelayMillis());
        }
        return segments;
    }

    /**
     * 可见流式分句发送器。
     *
     * <p>这里恢复用户想要的“replyer 纯流式体感”：模型 delta 到达后先进入短缓冲，
     * 一旦形成完整句子或超过长度阈值就立刻发给前端。它不再等待整段回复全部生成完成。
     * 注意：已经发出的分句无法撤回，所以 interrupt 只能阻止后续 delta，不能撤销已发送内容。</p>
     */
    private final class StreamingSegmentEmitter {
        private final StringBuilder buffer = new StringBuilder();
        private final ReplySanitizer sanitizer = new ReplySanitizer();
        private final java.util.ArrayList<String> emittedSegments = new java.util.ArrayList<>();
        private int suppressedDuplicateCount;

        synchronized void reset() {
            buffer.setLength(0);
            emittedSegments.clear();
            suppressedDuplicateCount = 0;
        }

        synchronized void acceptDelta(String delta) {
            if (delta == null || delta.isBlank()) {
                return;
            }
            for (int i = 0; i < delta.length(); i++) {
                char ch = delta.charAt(i);
                buffer.append(ch);
                if (shouldEmit(ch, buffer.length())) {
                    emitBuffered(false);
                }
            }
        }

        synchronized void flush() {
            emitBuffered(true);
        }

        synchronized List<String> emittedSegments() {
            return List.copyOf(emittedSegments);
        }

        synchronized int suppressedDuplicateCount() {
            return suppressedDuplicateCount;
        }

        private boolean shouldEmit(char ch, int length) {
            if (length >= Math.max(10, config.splitter().maxLength())) {
                return true;
            }
            return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '\n';
        }

        private void emitBuffered(boolean force) {
            String raw = buffer.toString().trim();
            if (raw.isBlank()) {
                buffer.setLength(0);
                return;
            }
            if (!force && raw.length() < Math.max(1, config.splitter().minSegmentLength())) {
                return;
            }
            buffer.setLength(0);
            String cleaned = sanitizer.clean(raw);
            if (cleaned.isBlank()) {
                return;
            }
            if (shouldSuppressDuplicateProactiveSegment(cleaned)) {
                suppressedDuplicateCount++;
                trace.trace("proactive.dedupe", "跳过重复主动分句：" + clipTrace(cleaned));
                return;
            }
            emittedSegments.add(cleaned);
            session.appendAssistant(ChatMessage.assistant(config.identity().botName(), cleaned));
            memoryRuntime.observeAssistantMessage(cleaned);
            output.accept(cleaned);
            rememberAssistantSegment(cleaned);
            sleepQuietly(config.splitter().bubbleDelayMillis());
        }
    }

    private synchronized void markAssistantFinished() {
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveStageIndex = ProactiveScheduler.STAGE_LIGHT_FOLLOWUP;
        proactiveGeneration++;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveLongSilenceChecksSinceLastUser = 0;
        scheduleNextProactiveCheckLocked(proactiveGeneration);
    }

    private synchronized void markProactiveAssistantFinished() {
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveAwaitingCycle = false;
        proactiveVisibleRepliesSinceLastUser++;
        proactiveSilentDecisionsSinceLastUser = 0;
        if (proactiveAwaitingStage != ProactiveScheduler.STAGE_LONG_SILENCE_CHECK) {
            proactiveLongSilenceChecksSinceLastUser = 0;
        }
        proactiveAwaitingStage = -1;
        if (proactiveVisibleRepliesSinceLastUser >= proactiveScheduler.maxVisibleReplies()) {
            trace.trace("proactive.stop", "本轮玩家沉默期间已达到主动上限，进入沉默状态等待玩家回应。");
            return;
        }
        proactiveStageIndex = proactiveVisibleRepliesSinceLastUser >= proactiveScheduler.maxVisibleReplies() - 1
                ? ProactiveScheduler.STAGE_FINAL_NOTICE
                : Math.max(proactiveStageIndex, ProactiveScheduler.STAGE_TOPIC_PUSH);
        scheduleNextProactiveCheckLocked(proactiveGeneration);
    }

    private synchronized void markProactiveDuplicateSuppressed() {
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveAwaitingCycle = false;
        proactiveAwaitingStage = -1;
        proactiveSilentDecisionsSinceLastUser++;
        int activeCuriosity = memoryRuntime.activeCuriosity();
        if (proactiveScheduler.shouldScheduleAfterSilentDecision(
                activeCuriosity,
                proactiveSilentDecisionsSinceLastUser,
                proactiveFiredCandidatesSinceLastUser)) {
            scheduleNextProactiveCheckLocked(proactiveGeneration);
        } else if (scheduleLongSilenceCheckLocked(proactiveGeneration)) {
            trace.trace("proactive.long_silence.schedule",
                    "重复主动回复被压制后，改为长沉默复检。主动好奇=" + activeCuriosity);
        } else {
            trace.trace("proactive.stop", "重复主动回复被压制，停止主动候选，等待玩家下一条消息。");
        }
    }

    private synchronized void markConversationAnchorAfterUserNoAction() {
        // 玩家真实发言被 planner 判断为暂不回应时，也要留下一个节奏锚点。
        // 否则之后 30s/75s 的长沉默不会再提交给 planner，看起来就像系统彻底暂停。
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveStageIndex = ProactiveScheduler.STAGE_TOPIC_PUSH;
        proactiveGeneration++;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveLongSilenceChecksSinceLastUser = 0;
        scheduleNextProactiveCheckLocked(proactiveGeneration);
    }

    private synchronized void markConversationAnchorAfterTimeoutNoAction() {
        // wait 到期后 planner 选择 no_action，不代表节奏系统彻底关机。
        // 如果主动好奇仍然足够，继续排下一次候选；真正是否发言仍交给 planner。
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveGeneration++;
        proactiveSilentDecisionsSinceLastUser++;
        int activeCuriosity = memoryRuntime.activeCuriosity();
        if (proactiveScheduler.shouldScheduleAfterSilentDecision(
                activeCuriosity,
                proactiveSilentDecisionsSinceLastUser,
                proactiveFiredCandidatesSinceLastUser)) {
            scheduleNextProactiveCheckLocked(proactiveGeneration);
        } else if (scheduleLongSilenceCheckLocked(proactiveGeneration)) {
            trace.trace("proactive.long_silence.schedule",
                    "wait 到期后短期主动停止，改为长沉默复检。主动好奇=" + activeCuriosity);
        } else {
            trace.trace("proactive.stop", "wait 到期后 planner 选择沉默，主动好奇="
                    + activeCuriosity + "，停止主动候选，等待玩家下一条消息。");
        }
    }

    private void enterWaitState(int seconds) {
        int waitSeconds = seconds > 0 ? seconds : config.flow().defaultWaitSeconds();
        state = RuntimeState.WAIT;
        cancelProactiveTimer();
        trace.trace("runtime.wait", "等待 " + waitSeconds + " 秒后重新判断。");
        waitFuture = scheduler.schedule(() -> {
            if (!running || state != RuntimeState.WAIT) {
                return;
            }
            leaveWaitState();
            long newVersion = version.incrementAndGet();
            session.appendIncoming(ChatMessage.reference("[节奏事件] 刚才的 wait 已到期，需要重新判断是否继续说话、继续等待或停止。"));
            trace.trace("runtime.wait.timeout", "version=" + newVersion);
            internalTurnQueue.offer(TurnKind.TIMEOUT);
        }, waitSeconds, TimeUnit.SECONDS);
    }

    private void leaveWaitState() {
        ScheduledFuture<?> future = waitFuture;
        if (future != null) {
            future.cancel(false);
            waitFuture = null;
        }
        state = RuntimeState.STOP;
    }

    private void cancelMessageTimerLocked() {
        ScheduledFuture<?> future = messageFuture;
        if (future != null) {
            future.cancel(false);
            messageFuture = null;
        }
    }

    private synchronized void scheduleNextProactiveCheckLocked(long generation) {
        cancelProactiveTimerLocked();
        if (!running || !config.flow().enableProactiveRhythm() || lastAssistantFinishedAtMillis <= 0) {
            return;
        }
        if (proactiveVisibleRepliesSinceLastUser >= proactiveScheduler.maxVisibleReplies()) {
            trace.trace("proactive.skip", "玩家未回应前已经达到主动上限，不再继续排主动检查。");
            return;
        }

        int activeCuriosity = memoryRuntime.activeCuriosity();
        int nextSeconds = proactiveScheduler.nextDelaySeconds(
                proactiveStageIndex,
                activeCuriosity,
                proactiveSilentDecisionsSinceLastUser,
                proactiveFiredCandidatesSinceLastUser
        );
        if (nextSeconds <= 0) {
            trace.trace("proactive.skip", "主动好奇=" + activeCuriosity + "，当前阶段不再排主动候选。");
            return;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - lastAssistantFinishedAtMillis);
        long delayMillis = Math.max(0L, nextSeconds * 1000L - elapsedMillis);
        proactiveFuture = scheduler.schedule(() -> fireProactiveCheck(generation), delayMillis, TimeUnit.MILLISECONDS);
        trace.trace("proactive.schedule", "stage=" + proactiveScheduler.stageName(proactiveStageIndex)
                + " activeCuriosity=" + activeCuriosity
                + " after=" + nextSeconds + "s delay=" + delayMillis + "ms");
    }

    private synchronized boolean scheduleLongSilenceCheckLocked(long generation) {
        if (!running || !config.flow().enableProactiveRhythm() || lastAssistantFinishedAtMillis <= 0) {
            return false;
        }
        if (!proactiveScheduler.shouldScheduleLongSilenceCheck(proactiveLongSilenceChecksSinceLastUser)) {
            return false;
        }
        proactiveStageIndex = ProactiveScheduler.STAGE_LONG_SILENCE_CHECK;
        int nextSeconds = proactiveScheduler.longSilenceCheckSeconds();
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - lastAssistantFinishedAtMillis);
        long delayMillis = Math.max(0L, nextSeconds * 1000L - elapsedMillis);
        cancelProactiveTimerLocked();
        proactiveFuture = scheduler.schedule(() -> fireProactiveCheck(generation), delayMillis, TimeUnit.MILLISECONDS);
        trace.trace("proactive.schedule", "stage=" + proactiveScheduler.stageName(proactiveStageIndex)
                + " activeCuriosity=" + memoryRuntime.activeCuriosity()
                + " longSilenceCheck=" + (proactiveLongSilenceChecksSinceLastUser + 1)
                + "/" + proactiveScheduler.maxLongSilenceChecks()
                + " after=" + nextSeconds + "s delay=" + delayMillis + "ms");
        return true;
    }

    private void fireProactiveCheck(long generation) {
        synchronized (this) {
            if (!running || generation != proactiveGeneration || state == RuntimeState.WAIT || session.hasPendingMessages()) {
                return;
            }
            if (state == RuntimeState.RUNNING) {
                scheduleProactiveRetryLocked(generation);
                return;
            }
            long silentSeconds = Math.max(0L, (System.currentTimeMillis() - lastAssistantFinishedAtMillis) / 1000L);
            int activeCuriosity = memoryRuntime.activeCuriosity();
            String event = proactiveScheduler.buildEvent(
                    silentSeconds,
                    proactiveStageIndex,
                    activeCuriosity,
                    proactiveFiredCandidatesSinceLastUser,
                    proactiveLongSilenceChecksSinceLastUser,
                    memoryRuntime.proactiveAffectHint()
            );
            long newVersion = version.incrementAndGet();
            session.appendIncoming(ChatMessage.reference(event));
            trace.trace("proactive.event", "version=" + newVersion
                    + " stage=" + proactiveScheduler.stageName(proactiveStageIndex)
                    + " activeCuriosity=" + activeCuriosity
                    + " silentSeconds=" + silentSeconds);
            if (proactiveStageIndex == ProactiveScheduler.STAGE_LONG_SILENCE_CHECK) {
                proactiveLongSilenceChecksSinceLastUser++;
            }
            proactiveAwaitingStage = proactiveStageIndex;
            proactiveStageIndex = proactiveScheduler.nextStageAfterFire(proactiveStageIndex);
            proactiveFiredCandidatesSinceLastUser++;
            proactiveAwaitingCycle = true;
            internalTurnQueue.offer(TurnKind.PROACTIVE);
        }
    }

    private void scheduleProactiveRetryLocked(long generation) {
        cancelProactiveTimerLocked();
        if (!running || generation != proactiveGeneration) {
            return;
        }
        proactiveFuture = scheduler.schedule(() -> fireProactiveCheck(generation), 5000, TimeUnit.MILLISECONDS);
        trace.trace("proactive.retry", "模型仍在处理，5 秒后重新检查。stage=" + proactiveScheduler.stageName(proactiveStageIndex));
    }

    private void cancelProactiveTimer() {
        synchronized (this) {
            cancelProactiveTimerLocked();
        }
    }

    private void cancelProactiveTimerLocked() {
        ScheduledFuture<?> future = proactiveFuture;
        if (future != null) {
            future.cancel(false);
            proactiveFuture = null;
        }
    }

    private synchronized boolean shouldSuppressDuplicateProactiveSegment(String text) {
        if (!proactiveAwaitingCycle) {
            return false;
        }
        String fingerprint = proactiveFingerprint(text);
        if (fingerprint.length() < 6) {
            return false;
        }
        for (String previous : recentAssistantReplyFingerprints) {
            if (proactiveSimilarity(fingerprint, previous) >= 0.86) {
                return true;
            }
        }
        return false;
    }

    private synchronized void rememberAssistantSegment(String text) {
        String fingerprint = proactiveFingerprint(text);
        if (fingerprint.length() < 6) {
            return;
        }
        recentAssistantReplyFingerprints.addLast(fingerprint);
        while (recentAssistantReplyFingerprints.size() > 8) {
            recentAssistantReplyFingerprints.removeFirst();
        }
    }

    private static double proactiveSimilarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.contains(right) || right.contains(left)) {
            int min = Math.min(left.length(), right.length());
            int max = Math.max(left.length(), right.length());
            return max == 0 ? 0.0 : (double) min / (double) max;
        }
        java.util.Set<String> leftBigrams = bigrams(left);
        java.util.Set<String> rightBigrams = bigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String item : leftBigrams) {
            if (rightBigrams.contains(item)) {
                intersection++;
            }
        }
        int union = leftBigrams.size() + rightBigrams.size() - intersection;
        return union <= 0 ? 0.0 : (double) intersection / (double) union;
    }

    private static java.util.Set<String> bigrams(String text) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (text.length() < 2) {
            if (!text.isBlank()) {
                out.add(text);
            }
            return out;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            out.add(text.substring(i, i + 2));
        }
        return out;
    }

    private static String proactiveFingerprint(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String clipTrace(String text) {
        String clean = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 60 ? clean : clean.substring(0, 60) + "...";
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForMessageQuietPeriod() {
        if (!messageDebounceRequired || config.flow().messageDebounceMillis() <= 0) {
            messageDebounceRequired = false;
            return;
        }
        while (running) {
            long elapsed = System.currentTimeMillis() - lastMessageReceivedAtMillis;
            long remaining = config.flow().messageDebounceMillis() - elapsed;
            if (remaining <= 0) {
                break;
            }
            sleepQuietly(Math.min(remaining, 100));
        }
        messageDebounceRequired = false;
        oldestPendingMessageReceivedAtMillis = 0;
    }

    private synchronized void recordReplyLatency() {
        if (replyLatencyMeasurementStartedAtMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long duration = Math.max(0L, now - replyLatencyMeasurementStartedAtMillis);
        replyLatencyMeasurementStartedAtMillis = 0;
        oldestPendingMessageReceivedAtMillis = 0;
        recentReplyLatencies.addLast(new ReplyLatencySample(now, duration));
        pruneReplyLatencies(now);
        trace.trace("runtime.reply_latency", "duration=" + duration + "ms samples=" + recentReplyLatencies.size());
    }

    private synchronized void clearReplyLatencyMeasurement() {
        replyLatencyMeasurementStartedAtMillis = 0;
        oldestPendingMessageReceivedAtMillis = 0;
    }

    private synchronized Long averageReplyLatencyMillis() {
        long now = System.currentTimeMillis();
        pruneReplyLatencies(now);
        if (recentReplyLatencies.isEmpty()) {
            return null;
        }
        long total = 0L;
        for (ReplyLatencySample sample : recentReplyLatencies) {
            total += sample.durationMillis();
        }
        return Math.max(1L, total / recentReplyLatencies.size());
    }

    private void pruneReplyLatencies(long now) {
        long expireBefore = now - TimeUnit.MINUTES.toMillis(10);
        while (!recentReplyLatencies.isEmpty() && recentReplyLatencies.peekFirst().recordedAtMillis() < expireBefore) {
            recentReplyLatencies.removeFirst();
        }
    }

    @Override
    public void close() {
        running = false;
        InterruptFlag flag = currentInterruptFlag;
        if (flag != null) {
            flag.requestInterrupt();
        }
        CompletableFuture<?> loop = internalLoopTask;
        if (loop != null) {
            loop.cancel(true);
        }
        ScheduledFuture<?> future = waitFuture;
        if (future != null) {
            future.cancel(false);
        }
        ScheduledFuture<?> message = messageFuture;
        if (message != null) {
            message.cancel(false);
        }
        ScheduledFuture<?> proactive = proactiveFuture;
        if (proactive != null) {
            proactive.cancel(false);
        }
        worker.shutdownNow();
        scheduler.shutdownNow();
    }

    private enum TurnKind {
        MESSAGE,
        TIMEOUT,
        PROACTIVE
    }

    private record ReplyLatencySample(long recordedAtMillis, long durationMillis) {
    }
}
