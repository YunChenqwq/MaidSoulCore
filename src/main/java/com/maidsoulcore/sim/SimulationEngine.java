package com.maidsoulcore.sim;

import com.maidsoulcore.blackboard.BlackboardStore;
import com.maidsoulcore.blackboard.BlackboardView;
import com.maidsoulcore.decision.DecisionGate;
import com.maidsoulcore.decision.DecisionResult;
import com.maidsoulcore.decision.DecisionRoute;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.EventStage;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.planner.ActionPlan;
import com.maidsoulcore.planner.PlannedAction;
import com.maidsoulcore.runtime.RuntimeConfig;
import com.maidsoulcore.tool.ToolCall;
import com.maidsoulcore.trace.RingBufferTraceSink;
import com.maidsoulcore.trace.TraceEvent;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulationEngine {
    private final RuntimeConfig runtimeConfig;
    private final SimulationEnvironment environment;
    private final BlackboardStore blackboardStore;
    private final DecisionGate decisionGate;
    private final SimulationBlackboardUpdater blackboardUpdater;
    private final SimulationMaiBotRuntimeConfig runtimeModelConfig;
    private final SimulationOpenAiChatClient chatClient;
    private final SimulationPromptFactory promptFactory;
    private final SimulationPlannerClient plannerClient;
    private final SimulationToolUseClient toolUseClient;
    private final SimulationToolExecutor toolExecutor;
    private final SimulationReplyGenerator replyGenerator;
    private final SimulationVisionClient visionClient;
    private final SimulationConversationMemory conversationMemory;
    private final RingBufferTraceSink traceSink;
    private final AtomicLong traceSequence;

    public SimulationEngine(RuntimeConfig runtimeConfig, SimulationEnvironment environment) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
        this.environment = Objects.requireNonNull(environment);
        this.blackboardStore = new BlackboardStore();
        this.decisionGate = new DecisionGate();
        this.blackboardUpdater = new SimulationBlackboardUpdater();
        this.runtimeModelConfig = SimulationMaiBotConfigLoader.load();
        this.chatClient = new SimulationOpenAiChatClient(runtimeModelConfig);
        this.promptFactory = new SimulationPromptFactory(runtimeModelConfig);
        this.plannerClient = new SimulationPlannerClient(runtimeModelConfig, chatClient, promptFactory);
        this.toolUseClient = new SimulationToolUseClient(runtimeModelConfig, chatClient, promptFactory);
        this.toolExecutor = new SimulationToolExecutor(environment);
        this.replyGenerator = new SimulationReplyGenerator(runtimeModelConfig, chatClient, promptFactory);
        this.visionClient = new SimulationVisionClient(runtimeModelConfig, chatClient, promptFactory);
        this.conversationMemory = new SimulationConversationMemory(18);
        this.traceSink = new RingBufferTraceSink(runtimeConfig.traceBufferSize());
        this.traceSequence = new AtomicLong();
    }

    public SimulationEnvironment environment() { return environment; }
    public SimulationToolExecutor toolExecutor() { return toolExecutor; }
    public SimulationMaiBotRuntimeConfig runtimeModelConfig() { return runtimeModelConfig; }
    public List<TraceEvent> traceSnapshot() { return traceSink.snapshot(); }
    public BlackboardView currentBlackboard() { return blackboardStore.snapshot(environment.maidId(), environment.ownerId()); }

    public SimulationTurnResult handle(MaidEvent event) {
        String linkId = UUID.randomUUID().toString();
        trace(event, linkId, EventStage.INGEST, "accepted");

        applyLocalEventRules(event, linkId);
        blackboardUpdater.update(blackboardStore, environment, event);
        BlackboardView blackboard = blackboardStore.snapshot(event.maidId(), event.ownerId());

        if ("vision.capture".equals(event.type())) {
            String rawSummary = String.valueOf(event.payload().getOrDefault("summary", environment.lastVisionSummary()));
            BlackboardView visionBlackboard = blackboard;
            String interpreted = safeExternalCall(() -> visionClient.interpret(rawSummary, visionBlackboard), rawSummary);
            environment.setLastVisionSummary(interpreted);
            environment.markCapture();
            blackboardUpdater.update(blackboardStore, environment, event);
            blackboard = blackboardStore.snapshot(event.maidId(), event.ownerId());
            trace(event, linkId, EventStage.NORMALIZE, "vlm=" + interpreted);
        } else {
            trace(event, linkId, EventStage.NORMALIZE, "blackboard.version=" + blackboard.version());
        }

        DecisionResult heuristicDecision = decisionGate.route(event, blackboard);
        trace(event, linkId, EventStage.GATE, "heuristic=" + heuristicDecision.route() + " " + heuristicDecision.reason());

        BlackboardView plannerBlackboard = blackboard;
        SimulationPlannerResult plannerResult = safeExternalCall(
                () -> plannerClient.plan(event, plannerBlackboard, conversationMemory.snapshot(), heuristicDecision.route()),
                fallbackPlannerResult(event, heuristicDecision.route())
        );
        trace(event, linkId, EventStage.PLAN, plannerResult.rawText());

        BlackboardView toolBlackboard = blackboard;
        List<ToolCall> toolCalls = safeExternalCall(
                () -> toolUseClient.decide(event, toolBlackboard, plannerResult, toolExecutor.toolRegistry().all()),
                List.of()
        );

        ActionPlan actionPlan = new ActionPlan(
                UUID.randomUUID().toString(),
                plannerResult.planSummary(),
                toolCalls.stream().map(call -> new PlannedAction(call.toolName(), call.arguments())).toList()
        );

        List<String> executionLogs = executeTools(toolCalls, event, linkId);
        blackboardUpdater.update(blackboardStore, environment, event);
        BlackboardView afterExecution = blackboardStore.snapshot(event.maidId(), event.ownerId());

        String reply = "";
        boolean allowReply = plannerResult.shouldReply() && environment.canSpeakForEvent(event.type());
        if (allowReply) {
            reply = safeExternalCall(
                    () -> replyGenerator.generate(event, afterExecution, plannerResult, executionLogs, conversationMemory.snapshot()),
                    fallbackReply(event)
            );
            reply = reply == null ? "" : reply.trim();
            if (!reply.isBlank()) {
                environment.setLastReply(reply);
                environment.markSpoke(event.type());
                blackboardStore.put("memory.last_reply", reply);
                trace(event, linkId, EventStage.REPLY, reply);
            }
        } else {
            trace(event, linkId, EventStage.REPLY, plannerResult.shouldReply() ? "reply suppressed by cooldown" : "no reply");
        }

        rememberConversation(event, reply, plannerResult, executionLogs);
        return new SimulationTurnResult(
                event,
                blackboardStore.snapshot(event.maidId(), event.ownerId()),
                new DecisionResult(plannerResult.route(), plannerResult.intent()),
                actionPlan,
                executionLogs,
                reply
        );
    }

    private void rememberConversation(MaidEvent event, String reply, SimulationPlannerResult plannerResult, List<String> toolOutputs) {
        if ("owner.talk".equals(event.type())) {
            conversationMemory.add("user: " + event.payload().getOrDefault("text", ""));
        } else {
            conversationMemory.add("event: " + event.type() + " " + event.payload());
        }
        conversationMemory.add("plan: route=" + plannerResult.route() + " intent=" + plannerResult.intent());
        if (!toolOutputs.isEmpty()) {
            conversationMemory.add("tools: " + toolOutputs);
        }
        if (!reply.isBlank()) {
            conversationMemory.add("maid: " + reply);
        }
    }

    private List<String> executeTools(List<ToolCall> toolCalls, MaidEvent event, String linkId) {
        if (toolCalls.isEmpty()) {
            return List.of();
        }
        List<String> outputs = toolExecutor.executePlan(toolCalls);
        for (String output : outputs) {
            trace(event, linkId, EventStage.EXECUTE, output);
        }
        return outputs;
    }

    public MaidEvent newEvent(String type, EventPriority priority, Map<String, Object> payload) {
        return new MaidEvent(UUID.randomUUID(), environment.maidId(), environment.ownerId(), type, priority, Instant.now(), payload);
    }

    private void applyLocalEventRules(MaidEvent event, String linkId) {
        switch (event.type()) {
            case "owner.talk" -> {
                environment.startChatSession();
                String text = String.valueOf(event.payload().getOrDefault("text", ""));
                if (containsExplicitStay(text)) {
                    environment.setExplicitStayPolicy("text command: " + text);
                    trace(event, linkId, EventStage.GATE, "follow policy => EXPLICIT_STAY");
                } else if (containsExplicitFollow(text)) {
                    environment.setDefaultFollowPolicy("text command: " + text);
                    trace(event, linkId, EventStage.GATE, "follow policy => DEFAULT_FOLLOW");
                }
                environment.tryGainFavorability("chat", 1, "owner_talk");
            }
            case "owner.feed" -> {
                String item = String.valueOf(event.payload().getOrDefault("item", "food"));
                environment.setMaidHunger(environment.maidHunger() - 0.30D);
                environment.recoverEnergy(0.10D);
                environment.tryGainFavorability("care", 2, "owner_feed");
                environment.setLastActionSummary("owner_feed:" + item);
                environment.startChatSession();
            }
            case "owner.interact" -> {
                environment.tryGainFavorability("action", 1, "owner_interact");
                environment.setLastActionSummary("owner_interact");
                environment.startChatSession();
            }
            case "maid.attacked" -> environment.spendEnergy(0.04D);
            case "maid.sleep.enter" -> {
                environment.setMaidSleeping(true);
                environment.setMaidSitting(false);
                environment.setLastActionSummary("sleep_enter");
            }
            case "maid.sleep.exit" -> {
                environment.setMaidSleeping(false);
                environment.recoverEnergy(0.08D);
                environment.setLastActionSummary("sleep_exit");
            }
            case "owner.command.return_home" -> environment.setExplicitStayPolicy("explicit owner command");
            case "owner.command.follow" -> environment.setDefaultFollowPolicy("explicit owner command");
            default -> {
            }
        }
    }

    private boolean containsExplicitStay(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("stay home") || normalized.contains("stay here")
                || normalized.contains("wait here") || normalized.contains("do not follow")
                || normalized.contains("待在这里") || normalized.contains("别跟")
                || normalized.contains("不要跟") || normalized.contains("留在家");
    }

    private boolean containsExplicitFollow(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("follow me") || normalized.contains("come here")
                || normalized.contains("stay with me") || normalized.contains("resume follow")
                || normalized.contains("跟着我") || normalized.contains("跟随我")
                || normalized.contains("跟我走") || normalized.contains("过来");
    }

    private SimulationPlannerResult fallbackPlannerResult(MaidEvent event, DecisionRoute route) {
        boolean shouldReply = event.priority() != EventPriority.P2 || "owner.talk".equals(event.type());
        return new SimulationPlannerResult(
                route,
                shouldReply,
                "calm",
                "fallback planner",
                "planner call failed, fallback logic enabled",
                "",
                "respond to current event",
                "fallback planner"
        );
    }

    private String fallbackReply(MaidEvent event) {
        if ("owner.talk".equals(event.type())) {
            return "I am listening, master.";
        }
        if ("maid.sleep.enter".equals(event.type())) {
            return "Good night, master. I will rest for a while.";
        }
        if ("maid.sleep.exit".equals(event.type())) {
            return "Good morning. I am awake now.";
        }
        return "Received. I will keep watching.";
    }

    private void trace(MaidEvent event, String linkId, EventStage stage, String reason) {
        traceSink.accept(new TraceEvent(
                traceSequence.incrementAndGet(),
                Instant.now(),
                event.maidId(),
                linkId,
                event.type(),
                event.priority(),
                stage,
                stage.name(),
                reason
        ));
    }

    private <T> T safeExternalCall(CheckedSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception exception) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
