package com.yunchen.maidsoulcore.core.runtime;

import com.yunchen.maidsoulcore.core.affect.AffectEngine;
import com.yunchen.maidsoulcore.core.affect.AffectiveLongingEngine;
import com.yunchen.maidsoulcore.core.affect.AffectiveResult;
import com.yunchen.maidsoulcore.core.affect.AffectiveEvent;
import com.yunchen.maidsoulcore.core.affect.AffectProfile;
import com.yunchen.maidsoulcore.core.affect.AffectProfileStore;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.context.ContextBuilder;
import com.yunchen.maidsoulcore.core.context.ContextPack;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.memory.MemoryMaintenanceService;
import com.yunchen.maidsoulcore.core.memory.MemoryWritebackService;
import com.yunchen.maidsoulcore.core.message.RuntimeMessage;
import com.yunchen.maidsoulcore.core.event.StructuredEvent;
import com.yunchen.maidsoulcore.core.event.StructuredEventPostProcessor;
import com.yunchen.maidsoulcore.core.event.StructuredEventType;
import com.yunchen.maidsoulcore.core.prompt.PromptCatalog;
import com.yunchen.maidsoulcore.core.prompt.PromptRenderer;
import com.yunchen.maidsoulcore.core.reasoning.PlanDecision;
import com.yunchen.maidsoulcore.core.reasoning.PlanDecisionValidator;
import com.yunchen.maidsoulcore.core.reasoning.PlannerRunner;
import com.yunchen.maidsoulcore.core.reasoning.TimingDecision;
import com.yunchen.maidsoulcore.core.reasoning.TimingGateRunner;
import com.yunchen.maidsoulcore.core.reply.ReplyGenerator;
import com.yunchen.maidsoulcore.core.text.SentenceSplitter;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MaidSoulRuntime implements AutoCloseable {
    private static final double PLANNER_AFFECT_WRITE_THRESHOLD = 0.65D;
    private final DialogueCoreConfig config;
    private final PromptCatalog prompts;
    private final TimingGateRunner timingGate;
    private final PlannerRunner planner;
    private final ReplyGenerator replyer;
    private final LifeMemoryStore memory;
    private final AffectProfileStore affectStore;
    private final AffectProfile affectProfile;
    private final AffectiveLongingEngine affectiveLonging = new AffectiveLongingEngine();
    private final AffectEngine affectEngine = affectiveLonging.affectEngine();
    private final MemoryWritebackService memoryWriteback = new MemoryWritebackService();
    private final MemoryMaintenanceService memoryMaintenance = new MemoryMaintenanceService();
    private final PlanDecisionValidator planValidator = new PlanDecisionValidator();
    private final StructuredEventPostProcessor eventPostProcessor = new StructuredEventPostProcessor();
    private final MessageBuffer buffer = new MessageBuffer();
    private final ContextBuilder contextBuilder = new ContextBuilder();
    private final SentenceSplitter splitter = new SentenceSplitter();
    private final Consumer<String> output;
    private final RuntimeTraceSink trace;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile RuntimeState state = RuntimeState.STOP;
    private volatile boolean messageTurnScheduled;
    private volatile boolean closed;
    private volatile long version;

    public MaidSoulRuntime(
            DialogueCoreConfig config,
            PromptCatalog prompts,
            TimingGateRunner timingGate,
            PlannerRunner planner,
            ReplyGenerator replyer,
            LifeMemoryStore memory,
            AffectProfileStore affectStore,
            Consumer<String> output,
            RuntimeTraceSink trace
    ) {
        this.config = config;
        this.prompts = prompts;
        this.timingGate = timingGate;
        this.planner = planner;
        this.replyer = replyer;
        this.memory = memory;
        this.affectStore = affectStore;
        this.affectProfile = affectStore.load();
        this.output = output;
        this.trace = trace == null ? RuntimeTraceSink.noop() : trace;
    }

    public void receiveOwnerMessage(String ownerName, String content) {
        if (closed || content == null || content.isBlank()) {
            return;
        }
        buffer.append(RuntimeMessage.user(ownerName, content));
        affectEngine.observeOwnerMessage(affectProfile, content);
        saveAffect();
        version++;
        if (state == RuntimeState.RUNNING) {
            trace.trace("runtime.interrupt", "收到新消息，当前规划完成后将丢弃旧结果并重新处理。");
        }
        scheduleMessageTurn();
    }

    public void receiveWorldEvent(String eventType, String detail) {
        if (closed) {
            return;
        }
        buffer.append(RuntimeMessage.system(eventType, eventType + " | " + (detail == null ? "" : detail)));
        affectEngine.observeWorldEvent(affectProfile, eventType);
        saveAffect();
        version++;
        scheduleMessageTurn();
    }

    public void syncFavorability(int favorability) {
        affectEngine.syncFavorability(affectProfile, favorability);
        saveAffect();
    }

    private synchronized void scheduleMessageTurn() {
        if (closed || state == RuntimeState.WAIT || messageTurnScheduled || !buffer.hasPendingMessages()) {
            return;
        }
        messageTurnScheduled = true;
        scheduler.schedule(() -> worker.execute(this::runLoopSafely), config.messageDebounceMillis, TimeUnit.MILLISECONDS);
    }

    private void runLoopSafely() {
        try {
            runLoop();
        } catch (Exception e) {
            trace.trace("runtime.error", e.getMessage());
        } finally {
            messageTurnScheduled = false;
            if (!closed && buffer.hasPendingMessages() && state != RuntimeState.WAIT) {
                scheduleMessageTurn();
            }
        }
    }

    private void runLoop() {
        if (closed) {
            return;
        }
        List<RuntimeMessage> pending = buffer.collectPendingMessages();
        if (pending.isEmpty()) {
            return;
        }
        state = RuntimeState.RUNNING;
        long cycleVersion = version;
        trace.trace("runtime.cycle", "开始处理新消息，pending=" + pending.size() + "，version=" + cycleVersion);
        String memoryQueryOverride = "";
        boolean semanticAffectApplied = false;
        int memoryQueryCount = 0;
        for (int round = 0; round < Math.max(1, config.maxInternalRounds); round++) {
            ContextPack context = buildContext(memoryQueryOverride);
            memoryQueryOverride = "";
            if (config.enableIndependentTimingGate) {
                TimingDecision timing = timingGate.decide(context, identity());
                trace.trace("timing", timing.action + " / " + timing.reason);
                if ("wait".equalsIgnoreCase(timing.action)) {
                    enterWait(timing.wait_seconds > 0 ? timing.wait_seconds : config.defaultWaitSeconds);
                    return;
                }
                if ("no_action".equalsIgnoreCase(timing.action)) {
                    state = RuntimeState.STOP;
                    return;
                }
            }

            PlanDecision plan = validatePlan(planner.plan(context, identity()), context);
            if (cycleVersion != version) {
                trace.trace("planner.discard", "规划期间收到更新消息，丢弃旧规划和旧情绪事件。");
                state = RuntimeState.STOP;
                return;
            }
            String action = plan.action == null ? "reply" : plan.action.toLowerCase();
            if (!semanticAffectApplied && applyPlannerAffect(plan)) {
                semanticAffectApplied = true;
                context = buildContext(plan.memory_query);
            }
            buffer.append(RuntimeMessage.thought("action=" + action
                    + " affect=" + blankToDefault(plan.affect_event, "none")
                    + " confidence=" + String.format(java.util.Locale.ROOT, "%.2f", plan.affect_confidence)
                    + " reason=" + plan.reason));
            tracePlanner(plan, action);
            if ("query_memory".equals(action)) {
                memoryQueryCount++;
                if (memoryQueryCount >= Math.max(1, config.maxInternalRounds - 1)) {
                    plan.action = "reply";
                    plan.target_message_id = blankToDefault(plan.target_message_id, context.latestMessageId());
                    plan.reference_info = (blankToDefault(plan.reference_info, "")
                            + "\n已完成可用记忆查询，本轮不要继续查询记忆；请直接回应最新消息，并保持与 planner event 一致。").trim();
                    trace.trace("planner.validate", "query_memory_budget_exhausted_forced_reply");
                    sendReply(context, plan, cycleVersion);
                    state = RuntimeState.STOP;
                    return;
                }
                memoryQueryOverride = plan.memory_query == null || plan.memory_query.isBlank()
                        ? latestContextText()
                        : plan.memory_query;
                trace.trace("memory.query", memoryQueryOverride);
                continue;
            }
            if ("reply".equals(action)) {
                if (StructuredEventType.MEMORY_ANCHOR.id().equals(plan.affect_event)
                        && (plan.memory_query == null || plan.memory_query.isBlank())) {
                    context = buildContext(latestContextText());
                    trace.trace("memory.query", "auto_memory_anchor_query=" + latestContextText());
                }
                sendReply(context, plan, cycleVersion);
                state = RuntimeState.STOP;
                return;
            }
            if ("wait".equals(action)) {
                enterWait(plan.wait_seconds > 0 ? plan.wait_seconds : config.defaultWaitSeconds);
                return;
            }
            if ("no_action".equals(action)) {
                trace.trace("planner.no_action", blankToDefault(plan.reason, "规划器选择本轮不动作。"));
                state = RuntimeState.STOP;
                return;
            }
            if ("finish".equals(action)) {
                trace.trace("planner.finish", blankToDefault(plan.reason, "规划器结束本轮。"));
                state = RuntimeState.STOP;
                return;
            }
            state = RuntimeState.STOP;
            return;
        }
        trace.trace("planner.finish", "达到最大内部轮次，结束本轮。");
        state = RuntimeState.STOP;
    }

    private PlanDecision validatePlan(PlanDecision rawPlan, ContextPack context) {
        PlanDecisionValidator.ValidationResult result = planValidator.validate(rawPlan, context);
        PlanDecision plan = result.decision();
        plan.validation_note = result.reason();
        if (result.changed()) {
            trace.trace("planner.validate", result.reason());
        }
        return plan;
    }

    private void tracePlanner(PlanDecision plan, String action) {
        trace.trace("planner.raw", blankToDefault(plan.raw_response, "{}"));
        if (plan.tool_name != null && !plan.tool_name.isBlank()) {
            trace.trace("planner.tool", plan.tool_name + " " + blankToDefault(plan.tool_arguments, "{}"));
        }
        trace.trace("planner", action
                    + " / affect=" + blankToDefault(plan.affect_event, "none")
                    + "@" + String.format(java.util.Locale.ROOT, "%.2f", plan.affect_confidence)
                    + " / target=" + plan.target_message_id
                    + " / validation=" + blankToDefault(plan.validation_note, "ok")
                    + " / " + plan.reason);
    }

    private ContextPack buildContext(String memoryQuery) {
        String query = memoryQuery == null || memoryQuery.isBlank() ? latestContextText() : memoryQuery;
        AffectiveResult affective = affectiveLonging.tick(affectProfile, query, memory.querySummaries(query, 8), 0.35D);
        saveAffect();
        String memoryText = memory.searchText(query, 4);
        String affectText = affectProfile.brief() + "\n" + affective.toPromptBlock();
        return contextBuilder.build(buffer, config.historyWindow, affectText, memoryText);
    }

    /**
     * 消费 planner 给出的语义情绪事件。
     *
     * <p>这里是新情绪链路的关键边界：运行时不解析“对不起/喜欢/骂人”等文本，
     * 只相信 planner 输出的结构化 affect_event。置信度不够时只记录 trace，不更新情绪。</p>
     */
    private boolean applyPlannerAffect(PlanDecision plan) {
        if (plan == null) {
            return false;
        }
        StructuredEvent event = structuredEventFromPlan(plan);
        if (!hasWritableAffectEvent(event)) {
            return false;
        }
        if (event.confidence < PLANNER_AFFECT_WRITE_THRESHOLD) {
            trace.trace("affect.skip", "planner 置信度不足，未写入情绪事件："
                    + blankToDefault(event.type, "none")
                    + " confidence=" + String.format(java.util.Locale.ROOT, "%.2f", event.confidence));
            return false;
        }

        RuntimeMessage target = buffer.findById(plan.target_message_id);
        if (target == null) {
            target = buffer.latestUserMessage();
        }
        String ownerText = target == null ? latestContextText() : target.content();
        if (event.sourceText == null || event.sourceText.isBlank()) {
            event.sourceText = ownerText;
        }

        affectEngine.apply(affectProfile, event);
        AffectiveResult affective = affectiveLonging.tick(
                affectProfile,
                ownerText,
                memory.querySummaries(ownerText, 8),
                0.35D
        );
        saveAffect();
        memoryWriteback.write(memory, memoryWriteback.propose(
                config.ownerName,
                config.botName,
                ownerText,
                event,
                affective
        ));
        MemoryMaintenanceService.MaintenanceReport maintenance = memoryMaintenance.maintain(memory);
        trace.trace("structured.event", event.brief());
        trace.trace("memory.maintenance", "scanned=" + maintenance.scanned()
                + " exactMerged=" + maintenance.merged()
                + " structuralMerged=" + maintenance.structuralMerged()
                + " degraded=" + maintenance.degraded()
                + " pinned=" + maintenance.pinned()
                + " errorAffected=" + maintenance.errorAffected());
        trace.trace("affect.event", event.type
                + " confidence=" + String.format(java.util.Locale.ROOT, "%.2f", event.confidence)
                + " evidence=" + blankToDefault(event.evidence, "无"));
        return true;
    }

    /**
     * 过滤掉无语义或已经由 receiveOwnerMessage 处理过的通用事件。
     *
     * <p>OWNER_MESSAGE/LONG_MESSAGE 是“有消息来了”的低层信号，已经在收到玩家消息时处理；
     * planner 这里只负责更高层的关系语义，避免同一条消息重复增益。</p>
     */
    private StructuredEvent structuredEventFromPlan(PlanDecision plan) {
        RuntimeMessage target = buffer.findById(plan.target_message_id);
        if (target == null) {
            target = buffer.latestUserMessage();
        }
        String sourceText = target == null ? latestContextText() : target.content();
        return eventPostProcessor.complete(
                plan.event,
                plan.affect_event,
                plan.affect_confidence,
                plan.affect_evidence,
                config.ownerName,
                config.botName,
                sourceText
        );
    }

    private static boolean hasWritableAffectEvent(StructuredEvent event) {
        if (event == null || event.type == null || event.type.isBlank()) {
            return false;
        }
        StructuredEventType type = event.typeEnum();
        return event.shouldUpdateAffect
                && type != StructuredEventType.NEUTRAL_WORLD
                && type != StructuredEventType.OWNER_MESSAGE
                && type != StructuredEventType.LONG_MESSAGE;
    }

    private String latestContextText() {
        RuntimeMessage latest = buffer.latestUserMessage();
        return latest == null ? "" : latest.content();
    }

    private void sendReply(ContextPack context, PlanDecision plan, long cycleVersion) {
        RuntimeMessage target = buffer.findById(plan.target_message_id);
        if (target == null) {
            target = buffer.latestUserMessage();
        }
        String raw = replyer.generate(context, target, identity(), plan.reference_info);
        if (cycleVersion != version) {
            trace.trace("reply.discard", "回复生成期间收到更新消息，丢弃旧回复。");
            return;
        }
        for (String segment : splitter.split(raw)) {
            buffer.append(RuntimeMessage.assistant(config.botName, segment));
            output.accept(segment);
        }
    }

    private void enterWait(int seconds) {
        state = RuntimeState.WAIT;
        trace.trace("runtime.wait", "进入等待 " + seconds + " 秒。");
        scheduler.schedule(() -> {
            if (!closed && state == RuntimeState.WAIT) {
                state = RuntimeState.STOP;
                scheduleMessageTurn();
            }
        }, Math.max(1, seconds), TimeUnit.SECONDS);
    }

    private String identity() {
        return PromptRenderer.render(prompts.load("identity"), Map.of(
                "bot_name", config.botName,
                "owner_name", config.ownerName
        ));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public AffectProfile affectProfile() {
        return affectProfile;
    }

    private void saveAffect() {
        affectStore.save(affectProfile);
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        scheduler.shutdownNow();
    }
}
