package com.maidsoul.brain.reasoning;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmRequestException;
import com.maidsoul.brain.message.ChatMessage;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.reply.effect.ReplyEffectTracker;
import com.maidsoul.brain.runtime.ChatSession;
import com.maidsoul.brain.runtime.RuntimeTraceSink;
import com.maidsoul.brain.message.MessageRole;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 内部思考循环。
 *
 * <p>它只做一件事：把“缓存消息 -> 上下文 -> 节奏/规划 -> 回复”的阶段串起来。
 * 工具、视觉、长期记忆以后都应该作为上下文或工具结果进入这里，而不是另起一条发言链路。</p>
 */
public final class ReasoningEngine {
    private final BrainConfig config;
    private final ChatSession session;
    private final RuntimeTraceSink trace;
    private final ContextWindow contextWindow;
    private final TimingGate timingGate;
    private final PlannerAgent planner;
    private final ReplyComposer replyComposer;
    private final DialogueStateTracker dialogueStateTracker = new DialogueStateTracker();
    private final NoActionPolicy noActionPolicy = new NoActionPolicy();
    private final DirectReplyPolicy directReplyPolicy = new DirectReplyPolicy();
    private final MemoryRuntime memoryRuntime;
    private final ReplyEffectTracker replyEffectTracker;
    private final Consumer<String> streamDeltaConsumer;
    private final Runnable streamFlush;

    public ReasoningEngine(
            BrainConfig config,
            PromptCatalog prompts,
            LlmClient llm,
            ChatSession session,
            RuntimeTraceSink trace,
            MemoryRuntime memoryRuntime,
            ReplyEffectTracker replyEffectTracker,
            Consumer<String> streamDeltaConsumer,
            Runnable streamFlush
    ) {
        this.config = config;
        this.session = session;
        this.trace = trace;
        this.replyEffectTracker = replyEffectTracker == null ? new ReplyEffectTracker() : replyEffectTracker;
        this.streamDeltaConsumer = streamDeltaConsumer;
        this.streamFlush = streamFlush;
        this.memoryRuntime = memoryRuntime;
        this.contextWindow = new ContextWindow(config, memoryRuntime == null ? null : memoryRuntime::renderPromptBlock);
        this.timingGate = new TimingGate(config, prompts, llm);
        this.planner = new PlannerAgent(config, prompts, llm);
        this.replyComposer = new ReplyComposer(config, prompts, llm);
    }

    public Result runOneCycle(long cycleVersion, LongSupplier currentVersion, InterruptFlag interruptFlag) {
        List<ChatMessage> pending = session.collectPendingMessages();
        boolean proactiveEvent = isProactiveEvent(pending);
        if (!pending.isEmpty()) {
            session.ingest(pending);
        }
        DialogueState dialogueState = dialogueStateTracker.update(
                pending,
                session.contextWindow(Math.min(config.flow().historyWindow(), 16)),
                replyEffectTracker.latestSummary()
        );
        ChatMessage anchor = pending.isEmpty()
                ? session.latestUserMessage().orElse(null)
                : pending.get(pending.size() - 1);
        if (anchor == null) {
            return Result.noAction();
        }

        // 玩家刚发来的普通聊天是最高频场景。这里默认走单次回复器快路径：
        // 不先跑规划器，避免“planner 超时后再 replyer”的串行等待。
        // 以后接入真实工具、长期记忆和主动事件时，再按事件类型切回完整规划链路。
        if (!pending.isEmpty() && config.flow().directReplyOnUserMessage() && directReplyPolicy.canReplyDirectly(pending)) {
            if (session.hasForcedContinue()) {
                trace.trace("timing.skip", session.consumeForceReason());
            }
            String context = contextWindow.renderForReplyer(session.contextWindow(config.flow().historyWindow()), dialogueState);
            ChatMessage target = pending.get(pending.size() - 1);
            long replyStarted = System.currentTimeMillis();
            try {
                ReplyComposer.LlmReply composed = replyComposer.composeStreamingWithMeta(
                        context,
                        target,
                        "玩家刚发来新消息，直接自然回应并适当推进话题。",
                        "",
                        guardedDeltaConsumer(cycleVersion, currentVersion),
                        interruptFlag
                );
                flushStream(cycleVersion, currentVersion);
                trace.trace("llm.replyer.done", "direct=true model=" + composed.model()
                        + " elapsed=" + (System.currentTimeMillis() - replyStarted) + "ms "
                        + composed.metricsSummary());
                return Result.streamed(composed.content(), target, "玩家刚发来新消息，直接自然回应并适当推进话题。", "");
            } catch (LlmRequestException e) {
                if ("aborted".equals(e.failureKind())) {
                    trace.trace("llm.replyer.aborted", e.traceText());
                    return Result.noAction();
                }
                trace.trace("llm.replyer.error", e.traceText());
            return Result.reply(fallbackReply(target, e), target, "回复器请求失败后的兜底回复。", "");
            }
        }
        boolean memoryQueried = false;

        for (int round = 0; round < Math.max(1, config.flow().maxInternalRounds()); round++) {
            if (cycleVersion != currentVersion.getAsLong()) {
                return Result.noAction();
            }
            String plannerContext = contextWindow.renderForPlanner(session.contextWindow(config.flow().historyWindow()), dialogueState);
            String replyerContext = contextWindow.renderForReplyer(session.contextWindow(config.flow().historyWindow()), dialogueState);
            boolean forced = session.hasForcedContinue();

            if (config.flow().enableIndependentTimingGate() && !forced) {
                TimingDecision timing;
                long timingStarted = System.currentTimeMillis();
                try {
                    timing = timingGate.decide(plannerContext, interruptFlag);
                    traceModelDone("timing_gate", timingStarted, config.model().timingSlowThresholdMillis(), "");
                } catch (LlmRequestException e) {
                    if ("aborted".equals(e.failureKind())) {
                        trace.trace("llm.timing_gate.aborted", e.traceText());
                        return Result.noAction();
                    }
                    trace.trace("llm.timing_gate.error", e.traceText());
                    timing = TimingDecision.continueNow("节奏判断失败，交给主规划器处理。");
                }
                trace.trace("timing", timing.action() + " / " + timing.reason());
                if ("wait".equals(timing.action())) {
                    return Result.waiting(timing.waitSeconds() > 0 ? timing.waitSeconds() : config.flow().defaultWaitSeconds());
                }
                if ("no_action".equals(timing.action())) {
                    return Result.noAction();
                }
            } else if (forced) {
                trace.trace("timing.skip", session.consumeForceReason());
            }

            PlanDecision plan;
            long plannerStarted = System.currentTimeMillis();
            try {
                PlannerAgent.PlannerResult plannerResult = planner.planWithMeta(plannerContext, interruptFlag);
                plan = plannerResult.decision();
                traceModelDone("planner", plannerStarted, config.model().plannerSlowThresholdMillis(),
                        "model=" + plannerResult.model() + " " + plannerResult.metricsSummary());
            } catch (LlmRequestException e) {
                if ("aborted".equals(e.failureKind())) {
                    trace.trace("llm.planner.aborted", e.traceText());
                    return Result.noAction();
                }
                trace.trace("llm.planner.error", e.traceText());
                plan = PlanDecision.replyLatest("规划器请求失败，直接回应最新消息。");
            }
            trace.trace("planner", plan.action() + " target=" + plan.targetMessageId() + " / " + plan.reason());
            applyPlannerAffectEvent(plan);
            applyPlannerMemoryEvent(plan);
            if ("wait".equals(plan.action())) {
                return Result.waiting(plan.waitSeconds() > 0 ? plan.waitSeconds() : config.flow().defaultWaitSeconds());
            }
            if ("no_action".equals(plan.action())) {
                if (noActionPolicy.shouldOverrideForUserInput(pending, dialogueState)) {
                    trace.trace("planner.override", "真实用户输入仍需回应，覆盖 planner no_action。");
                    plan = PlanDecision.replyLatest("玩家刚发来可回应内容，不能把真实输入当成主动沉默事件。");
                } else {
                    return Result.noAction();
                }
            }
            if ("query_memory".equals(plan.action())) {
                if (memoryQueried) {
                    trace.trace("tool.query_memory.skip", "本轮已经查询过记忆，避免重复检索。");
                    plan = PlanDecision.replyLatest("本轮已经检索过记忆，直接依据现有上下文回复。");
                } else {
                    memoryQueried = true;
                    String memoryText = queryMemory(plan.targetMessageId(), plan.reason());
                    session.appendReference(memoryText);
                    trace.trace("tool.query_memory", memoryText);
                    // 当前原型还没有真实长期记忆后端。继续回到规划器只会多打一轮模型请求，
                    // 而且检索结果已经足够交给回复器参考，所以这里直接进入可见回复阶段。
                    plan = PlanDecision.replyLatest("已完成一次记忆检索，直接结合检索结果和最近聊天回复。", memoryText);
                }
            }
            if (!"reply".equals(plan.action())) {
                session.appendInternal("未知动作已忽略: " + plan.action());
                return Result.noAction();
            }

            ChatMessage target = resolveReplyTarget(plan, anchor, proactiveEvent);
            String reply;
            long replyStarted = System.currentTimeMillis();
            String effectiveReason = proactiveEvent
                    ? proactiveReplyReason(plan.reason())
                    : plan.reason();
            try {
                ReplyComposer.LlmReply composed = replyComposer.composeStreamingWithMeta(
                        replyerContext,
                        target,
                        effectiveReason,
                        plan.referenceInfo(),
                        guardedDeltaConsumer(cycleVersion, currentVersion),
                        interruptFlag
                );
                reply = composed.content();
                flushStream(cycleVersion, currentVersion);
                traceModelDone("replyer", replyStarted, config.model().replyerSlowThresholdMillis(),
                        "model=" + composed.model() + " " + composed.metricsSummary());
            } catch (LlmRequestException e) {
                if ("aborted".equals(e.failureKind())) {
                    trace.trace("llm.replyer.aborted", e.traceText());
                    return Result.noAction();
                }
                trace.trace("llm.replyer.error", e.traceText());
                reply = fallbackReply(target, e);
            }
            return Result.streamed(reply, target, effectiveReason, plan.referenceInfo());
        }
        return Result.noAction();
    }

    private void applyPlannerAffectEvent(PlanDecision plan) {
        if (memoryRuntime == null || plan == null || plan.affectEvent() == null) {
            return;
        }
        memoryRuntime.observeAffectEvent(plan.affectEvent());
        trace.trace("affect.event", plan.affectEvent().kind()
                + " intensity=" + plan.affectEvent().intensity()
                + " / " + memoryRuntime.affectSummary());
    }

    private void applyPlannerMemoryEvent(PlanDecision plan) {
        if (memoryRuntime == null || plan == null || plan.memoryEvent() == null) {
            return;
        }
        memoryRuntime.observeStructuredMemory(plan.memoryEvent());
        trace.trace("memory.event", plan.memoryEvent().type()
                + " layer=" + plan.memoryEvent().layer()
                + " tags=" + String.join(",", plan.memoryEvent().tags()));
    }

    private ChatMessage resolveReplyTarget(PlanDecision plan, ChatMessage anchor, boolean proactiveEvent) {
        if (proactiveEvent) {
            // 主动候选事件不是在回复一条新的用户消息。不能把 latestUserMessage 当 target，
            // 否则 replyer 会误以为用户刚刚又说了一遍历史内容。
            return null;
        }
        return session.findMessage(plan.targetMessageId())
                .or(() -> session.latestUserMessage())
                .orElse(anchor);
    }

    private static String proactiveReplyReason(String plannerReason) {
        String base = plannerReason == null ? "" : plannerReason.trim();
        String prefix = "这是运行时提交给 planner 的主动候选事件，不是在回复新的用户发言；不要复述或重新解释历史用户消息。";
        if (base.isBlank()) {
            return prefix;
        }
        return prefix + " " + base;
    }

    private static boolean isProactiveEvent(List<ChatMessage> pending) {
        for (ChatMessage message : pending) {
            if (message.role() == MessageRole.INTERNAL
                    && (message.content().startsWith("[主动候选事件]")
                    || message.content().startsWith("[现场观察]"))) {
                return true;
            }
        }
        return false;
    }

    private void traceModelDone(String stage, long startedAt, long slowThresholdMillis, String extra) {
        long elapsed = System.currentTimeMillis() - startedAt;
        String detail = (extra == null || extra.isBlank() ? "" : extra + " ")
                + "elapsed=" + elapsed + "ms slowThreshold=" + slowThresholdMillis + "ms";
        trace.trace("llm." + stage + ".done", detail);
        if (slowThresholdMillis > 0 && elapsed >= slowThresholdMillis) {
            // slow 只是观测信号，不能像 hard timeout 那样打断本轮。
            trace.trace("llm." + stage + ".slow", detail);
        }
    }

    private Consumer<String> guardedDeltaConsumer(long cycleVersion, LongSupplier currentVersion) {
        if (streamDeltaConsumer == null) {
            return null;
        }
        return delta -> {
            if (cycleVersion == currentVersion.getAsLong()) {
                streamDeltaConsumer.accept(delta);
            }
        };
    }

    private void flushStream(long cycleVersion, LongSupplier currentVersion) {
        if (streamFlush != null && cycleVersion == currentVersion.getAsLong()) {
            streamFlush.run();
        }
    }

    private String fallbackReply(ChatMessage target, LlmRequestException exception) {
        String content = target == null ? "" : target.content();
        // 兜底回复必须尽量接住当前这句话，不能只说“我在听”这种万能废话。
        // 否则模型下一轮会像“延迟补答”一样，继续回答上一轮没答上的问题。
        if (content.contains("在意")) {
            return "在意啊。但我不想把这句话说得像标准答案，你刚刚那样问，我会有点不好意思。";
        }
        if (content.contains("陪伴者") || content.contains("陪着")) {
            return "我懂你的意思。你不是想要一个只会答题的人，而是想要一个真的会接住你、也会有自己反应的人。";
        }
        if (content.contains("调") && (content.contains("聊天") || content.contains("大脑") || content.contains("核心"))) {
            return "调这个确实容易烦，尤其是明明知道哪里不对，却要一轮一轮试出来。";
        }
        if (content.contains("冒犯") || content.contains("打了你") || content.contains("打我")) {
            return "如果真被冒犯了，我不会马上当没事发生。可以继续聊，但我会先有点不高兴，也想听你怎么解释。";
        }
        if (content.contains("对不起") || content.contains("不该")) {
            return "我听到了。道歉我会收下，但心情不会一下子切回没事，我们慢慢来。";
        }
        if (content.contains("烦") || content.contains("生气") || content.contains("不满")) {
            return "我听见了。刚才这一下没接住，我先不狡辩，你慢慢说。";
        }
        if (content.contains("？") || content.contains("?")) {
            return "我在听。这个问题我不会糊弄过去，你再往下说一点。";
        }
        return "嗯，我在。刚才有点卡住了，但我没有把你的话丢掉。";
    }

    private String queryMemory(String query, String reason) {
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedQuery.isBlank()) {
            normalizedQuery = normalizedReason;
        }
        if (memoryRuntime == null) {
            return "[记忆检索] 记忆运行时未接入。";
        }
        return memoryRuntime.queryMemory(normalizedQuery, config.memory().retrievalLimit());
    }

    public enum ResultKind {
        REPLY,
        STREAMED,
        WAIT,
        NO_ACTION
    }

    public record Result(
            ResultKind kind,
            String replyText,
            int waitSeconds,
            ChatMessage targetMessage,
            String plannerReasoning,
            String referenceInfo
    ) {
        static Result reply(String text) {
            return reply(text, null, "", "");
        }

        static Result reply(String text, ChatMessage targetMessage, String plannerReasoning, String referenceInfo) {
            return new Result(ResultKind.REPLY, text, 0, targetMessage, plannerReasoning, referenceInfo);
        }

        static Result streamed(String text) {
            return new Result(ResultKind.STREAMED, text, 0, null, "", "");
        }

        static Result streamed(String text, ChatMessage targetMessage, String plannerReasoning, String referenceInfo) {
            return new Result(ResultKind.STREAMED, text, 0, targetMessage, plannerReasoning, referenceInfo);
        }

        static Result waiting(int seconds) {
            return new Result(ResultKind.WAIT, null, seconds, null, "", "");
        }

        static Result noAction() {
            return new Result(ResultKind.NO_ACTION, null, 0, null, "", "");
        }
    }
}
