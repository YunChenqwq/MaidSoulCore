package com.maidsoul.brain.runtime;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.reply.effect.ReplyEffectTracker;
import com.maidsoul.brain.reasoning.ReasoningEngine;
import com.maidsoul.brain.reasoning.ReplySanitizer;
import com.maidsoul.brain.text.SentenceSplitter;

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
    private final Consumer<String> output;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<TurnKind> internalTurnQueue = new LinkedBlockingQueue<>();
    private final AtomicLong version = new AtomicLong();

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
    private volatile int proactiveVisibleRepliesSinceLastUser;
    private volatile int proactiveSilentDecisionsSinceLastUser;
    private volatile boolean proactiveEmotionPushUsedSinceLastUser;
    private volatile boolean messageArrivedDuringRun;
    private volatile boolean plannerInterruptRequested;
    private volatile int plannerInterruptConsecutiveCount;

    public ConversationRuntime(
            BrainConfig config,
            PromptCatalog prompts,
            LlmClient llm,
            Consumer<String> output,
            RuntimeTraceSink trace
    ) {
        this.config = config;
        this.output = output;
        this.trace = trace == null ? RuntimeTraceSink.noop() : trace;
        this.splitter = new SentenceSplitter(config.splitter());
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
                streamEmitter::flush
        );
    }

    public void start() {
        running = true;
        ensureInternalLoopRunning();
        scheduleMessageTurn();
    }

    public void receiveUserMessage(String speaker, String content) {
        if (!running || content == null || content.isBlank()) {
            return;
        }
        proactiveGeneration++;
        cancelProactiveTimer();
        proactiveVisibleRepliesSinceLastUser = 0;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveEmotionPushUsedSinceLastUser = false;
        proactiveStageIndex = 0;
        long newVersion = version.incrementAndGet();
        long now = System.currentTimeMillis();
        lastMessageReceivedAtMillis = now;
        if (oldestPendingMessageReceivedAtMillis <= 0) {
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
            // 对齐参考实现：wait 期间新消息先进入 cache，不默认提前打断 wait。
            trace.trace("runtime.wait.cache", "WAIT 状态收到新消息，先缓存，等待 timeout 后统一判断。");
            return;
        }
        ensureInternalLoopRunning();
        scheduleMessageTurn();
    }

    private synchronized void scheduleMessageTurn() {
        if (!running || state == RuntimeState.WAIT || messageTurnScheduled || !session.hasPendingMessages()) {
            return;
        }
        messageTurnScheduled = true;
        long delay = Math.max(0, config.flow().messageDebounceMillis());
        messageFuture = scheduler.schedule(() -> {
            synchronized (ConversationRuntime.this) {
                messageTurnScheduled = false;
                messageFuture = null;
                if (!running || state == RuntimeState.WAIT || state == RuntimeState.RUNNING || !session.hasPendingMessages()) {
                    return;
                }
                internalTurnQueue.offer(TurnKind.MESSAGE);
            }
        }, delay, TimeUnit.MILLISECONDS);
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
            if (result.kind() == ReasoningEngine.ResultKind.STREAMED && result.replyText() != null) {
                List<String> segments = streamEmitter.emittedSegments();
                if (segments.isEmpty()) {
                    segments = splitter.split(result.replyText());
                }
                replyEffectTracker.recordReply(
                        result.targetMessage(),
                        result.replyText(),
                        segments,
                        result.plannerReasoning(),
                        result.referenceInfo()
                );
            }
            if (result.kind() == ReasoningEngine.ResultKind.REPLY || result.kind() == ReasoningEngine.ResultKind.STREAMED) {
                if (proactiveCycle) {
                    markProactiveAssistantFinished();
                } else {
                    markAssistantFinished();
                }
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
            if (running && proactiveAwaitingCycle && cycleVersion == version.get() && result != null
                    && result.kind() == ReasoningEngine.ResultKind.WAIT) {
                proactiveSilentDecisionsSinceLastUser++;
                if (proactiveSilentDecisionsSinceLastUser >= 2) {
                    scheduleEmotionPushOrStopLocked(cycleVersion, "连续主动判断都在等待/不说");
                }
            }
            if (running && state != RuntimeState.WAIT && proactiveAwaitingCycle && cycleVersion == version.get() && result != null) {
                proactiveAwaitingCycle = false;
                if (result.kind() == ReasoningEngine.ResultKind.NO_ACTION) {
                    proactiveSilentDecisionsSinceLastUser++;
                    // 本轮已经明确选择沉默，下一阶段要从“这次决定沉默”之后重新计时，
                    // 不能继续用上一句发言结束时间，否则模型一慢就会立刻连触发下一阶段。
                    lastAssistantFinishedAtMillis = System.currentTimeMillis();
                    if (proactiveSilentDecisionsSinceLastUser >= 2) {
                        scheduleEmotionPushOrStopLocked(cycleVersion, "连续主动判断都选择不说");
                    } else {
                        scheduleNextProactiveCheckLocked(proactiveGeneration);
                    }
                }
            }
        }
    }

    private List<String> emitReply(String rawText) {
        List<String> segments = splitter.split(rawText);
        for (String segment : segments) {
            session.appendAssistant(ChatMessage.assistant(config.identity().botName(), segment));
            memoryRuntime.observeAssistantMessage(segment);
            output.accept(segment);
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

        synchronized void reset() {
            buffer.setLength(0);
            emittedSegments.clear();
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
            emittedSegments.add(cleaned);
            session.appendAssistant(ChatMessage.assistant(config.identity().botName(), cleaned));
            memoryRuntime.observeAssistantMessage(cleaned);
            output.accept(cleaned);
            sleepQuietly(config.splitter().bubbleDelayMillis());
        }
    }

    private synchronized void markAssistantFinished() {
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveStageIndex = 0;
        proactiveGeneration++;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveEmotionPushUsedSinceLastUser = false;
        scheduleNextProactiveCheckLocked(proactiveGeneration);
    }

    private synchronized void markProactiveAssistantFinished() {
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        proactiveAwaitingCycle = false;
        proactiveVisibleRepliesSinceLastUser++;
        proactiveSilentDecisionsSinceLastUser = 0;
        proactiveEmotionPushUsedSinceLastUser = false;
        if (proactiveVisibleRepliesSinceLastUser >= 1) {
            trace.trace("proactive.stop", "本轮玩家消息后已经主动说过一次，停止继续主动，等待玩家回应。");
            return;
        }
        proactiveStageIndex = Math.max(proactiveStageIndex, 1);
        scheduleNextProactiveCheckLocked(proactiveGeneration);
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

    private synchronized void scheduleNextProactiveCheckLocked(long generation) {
        cancelProactiveTimerLocked();
        if (!running || !config.flow().enableProactiveRhythm() || lastAssistantFinishedAtMillis <= 0) {
            return;
        }
        if (proactiveVisibleRepliesSinceLastUser >= 1) {
            trace.trace("proactive.skip", "玩家未回应前已经主动说过一次，不再继续排主动检查。");
            return;
        }

        int nextSeconds = nextProactiveSeconds();
        if (nextSeconds <= 0) {
            return;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - lastAssistantFinishedAtMillis);
        long delayMillis = Math.max(0L, nextSeconds * 1000L - elapsedMillis);
        proactiveFuture = scheduler.schedule(() -> fireProactiveCheck(generation), delayMillis, TimeUnit.MILLISECONDS);
        trace.trace("proactive.schedule", "stage=" + proactiveStageName(proactiveStageIndex)
                + " after=" + nextSeconds + "s delay=" + delayMillis + "ms");
    }

    private synchronized void scheduleEmotionPushOrStopLocked(long cycleVersion, String reason) {
        proactiveAwaitingCycle = false;
        cancelProactiveTimerLocked();
        if (!running || cycleVersion != version.get()) {
            return;
        }
        if (proactiveEmotionPushUsedSinceLastUser) {
            trace.trace("proactive.stop", reason + "，情绪推进也已尝试过，停止主动检查，等待玩家下一条消息。");
            return;
        }
        proactiveEmotionPushUsedSinceLastUser = true;
        proactiveStageIndex = 4;
        lastAssistantFinishedAtMillis = System.currentTimeMillis();
        scheduleNextProactiveCheckLocked(proactiveGeneration);
        trace.trace("proactive.emotion_push.schedule", reason + "，改为排一次情绪推进检查。");
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
            String event = buildProactiveEvent(silentSeconds, proactiveStageIndex);
            long newVersion = version.incrementAndGet();
            session.appendIncoming(ChatMessage.reference(event));
            trace.trace("proactive.event", "version=" + newVersion + " stage=" + proactiveStageName(proactiveStageIndex)
                    + " silentSeconds=" + silentSeconds);
            proactiveStageIndex = Math.min(proactiveStageIndex + 1, 3);
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
        trace.trace("proactive.retry", "模型仍在处理，5 秒后重新检查。stage=" + proactiveStageName(proactiveStageIndex));
    }

    private int nextProactiveSeconds() {
        return switch (proactiveStageIndex) {
            case 0 -> Math.max(config.flow().proactiveInputProtectionSeconds(), config.flow().proactiveLightFollowupAfterSeconds());
            case 1 -> Math.max(config.flow().proactiveLightFollowupAfterSeconds() + 1, config.flow().proactiveTopicPushAfterSeconds());
            case 2 -> Math.max(config.flow().proactiveTopicPushAfterSeconds() + 1, config.flow().proactiveWorldObserveAfterSeconds());
            case 4 -> Math.max(45, config.flow().proactiveTopicPushAfterSeconds());
            default -> Math.max(config.flow().proactiveWorldObserveAfterSeconds() + 1, config.flow().proactiveIdleMinIntervalSeconds());
        };
    }

    private String buildProactiveEvent(long silentSeconds, int stage) {
        String stageName = proactiveStageName(stage);
        String rule = switch (stage) {
            case 0 -> "这是轻续话期。只有上一轮明确问了问题、话题明显没收束，或玩家用短反馈把话语权交回来，才考虑轻轻补一句；否则 wait 或 no_action。";
            case 1 -> "这是主动推进期。如果上一轮只是安抚或陪伴、没有问出具体问题，而对方仍然沉默，优先补一个低压力小问题帮助对方开口；问题要短，不要审问。可以问“是事情本身烦，还是现在脑子太乱？”这类二选一的小问题。不要突然换成无关日常，也不要为了这类即时情绪去查长期记忆。";
            case 2 -> "这是环境/记忆主动期。可以结合当前世界、视角摘要、情绪残留和关系记忆开新话题；没有依据时不要编造已经做了什么。";
            case 4 -> "这是情绪推进期。前面主动判断已经多次选择沉默，但用户长期没有回来。现在要从上一轮的情绪余韵或关系状态轻轻推进一次：可以给一个低压力台阶、轻微撒娇、关心或把刚才的情绪接住。不要问“还在吗”，不要审问，不要突然换无关日常；除非上一轮是明确冲突或边界拒绝，否则倾向 reply。";
            default -> "这是低频陪伴期。除非有明确情绪、环境或关系理由，否则优先 no_action，避免刷屏。";
        };
        return "[现场观察] 上一轮发言结束后，对方已经有一小会儿没有继续说话，约 " + silentSeconds + " 秒。"
                + "阶段=" + stageName + "。"
                + rule
                + "当前情绪主动参考：" + memoryRuntime.proactiveAffectHint()
                + "这只是内部观察，不是可见话题；如果要说话，只能像自然聊天一样承接当前关系和话题。";
    }

    private static String proactiveStageName(int stage) {
        return switch (stage) {
            case 0 -> "light_followup";
            case 1 -> "topic_push";
            case 2 -> "world_observe";
            case 4 -> "emotion_push";
            default -> "idle";
        };
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
}
