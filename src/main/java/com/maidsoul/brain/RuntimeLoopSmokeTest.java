package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmResponse;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;
import com.maidsoul.brain.tool.ToolCall;
import com.maidsoul.brain.tool.ToolSpec;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 运行时结构验收测试。
 *
 * <p>不访问真实网络，只验证单内部循环、连续输入合批和 interrupt flag 是否生效。
 * 这类测试比“看几句回复好不好听”更重要，因为它能防止旧版并发请求问题回潮。</p>
 */
public final class RuntimeLoopSmokeTest {
    private RuntimeLoopSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        testMessageBatching();
        testInterruptDuringRunning();
        testRepairComplaintDoesNotNoAction();
        testSemanticUserInputOverridesNoAction();
        testShortSemanticInputOverridesNoAction();
        System.out.println("RUNTIME_LOOP_SMOKE_OK");
    }

    private static void testMessageBatching() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withFlow(BrainConfig.load(root.resolve("config")), 250, 2);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        ScriptedClient client = new ScriptedClient(false);

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                client,
                replies::offer,
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "第一句");
            Thread.sleep(60);
            runtime.receiveUserMessage(config.identity().ownerName(), "第二句");
            Thread.sleep(60);
            runtime.receiveUserMessage(config.identity().ownerName(), "第三句");

            String reply = replies.poll(5, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("连续输入合批测试超时。");
            }
            if (client.plannerCalls.get() != 1) {
                throw new IllegalStateException("连续输入不应触发多次 planner，实际=" + client.plannerCalls.get());
            }
            String prompt = client.lastPlannerPrompt;
            if (!prompt.contains("第一句") || !prompt.contains("第二句") || !prompt.contains("第三句")) {
                throw new IllegalStateException("planner 没有看到完整合批消息: " + prompt);
            }
        }
    }

    private static void testInterruptDuringRunning() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withFlow(BrainConfig.load(root.resolve("config")), 30, 2);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        ScriptedClient client = new ScriptedClient(true);

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                client,
                replies::offer,
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "旧问题");
            if (!client.plannerStarted.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("planner 没有启动，无法测试 interrupt。");
            }
            runtime.receiveUserMessage(config.identity().ownerName(), "新问题，要以这个为准");

            String reply = replies.poll(6, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("interrupt 后没有收到新回复。");
            }
            if (client.abortedPlannerCalls.get() < 1) {
                throw new IllegalStateException("运行中输入新消息没有中断旧 planner。");
            }
            if (!client.lastPlannerPrompt.contains("新问题，要以这个为准")) {
                throw new IllegalStateException("中断后新一轮没有使用最新消息。");
            }
        }
    }

    private static void testRepairComplaintDoesNotNoAction() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withFlow(BrainConfig.load(root.resolve("config")), 30, 2);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        ScriptedClient client = new ScriptedClient(false);
        client.replyText = "那你想让我怎么办嘛，我又不知道。";

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                client,
                replies::offer,
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "你一点都不可爱");
            runtime.receiveUserMessage(config.identity().ownerName(), "我生气了");
            runtime.receiveUserMessage(config.identity().ownerName(), "呵呵");

            String reply = replies.poll(5, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("修复场景不应 no_action 或沉默。");
            }
            if (reply.isBlank()) {
                throw new IllegalStateException("纯流式修复场景至少要有可见回应，不能沉默。");
            }
        }
    }

    private static void testSemanticUserInputOverridesNoAction() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withFlow(BrainConfig.load(root.resolve("config")), 30, 2);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        ScriptedClient client = new ScriptedClient(false);
        client.forcePlannerNoAction = true;
        client.replyText = "知道了，没有根据的东西我不会乱编。";

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                client,
                replies::offer,
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "没有根据就别乱编，知道吗？");

            String reply = replies.poll(5, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("有语义的用户反馈不能被 planner no_action 吞掉。");
            }
        }
    }

    private static void testShortSemanticInputOverridesNoAction() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withFlow(BrainConfig.load(root.resolve("config")), 30, 2);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        ScriptedClient client = new ScriptedClient(false);
        client.forcePlannerNoAction = true;
        client.replyText = "嗯，我在听。";

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                client,
                replies::offer,
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "嗯");

            String reply = replies.poll(5, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("真实短反馈不能被 planner no_action 吞掉。");
            }
        }
    }

    private static BrainConfig withFlow(BrainConfig base, long debounceMillis, int interruptLimit) {
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                debounceMillis,
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                old.defaultWaitSeconds(),
                old.talkFrequency(),
                interruptLimit,
                old.timingGateNonContinueCooldownMillis(),
                old.directReplyOnUserMessage(),
                false,
                old.proactiveInputProtectionSeconds(),
                old.proactiveLightFollowupAfterSeconds(),
                old.proactiveTopicPushAfterSeconds(),
                old.proactiveWorldObserveAfterSeconds(),
                old.proactiveIdleMinIntervalSeconds()
        );
        return new BrainConfig(base.identity(), base.model(), flow, base.splitter(), base.memory(), base.debug());
    }

    private static final class ScriptedClient implements LlmClient {
        private final boolean slowFirstPlanner;
        private final AtomicInteger plannerCalls = new AtomicInteger();
        private final AtomicInteger abortedPlannerCalls = new AtomicInteger();
        private final CountDownLatch plannerStarted = new CountDownLatch(1);
        private volatile String lastPlannerPrompt = "";
        private volatile String replyText = "收到，我按最新这轮来接。";
        private volatile boolean forcePlannerNoAction;

        private ScriptedClient(boolean slowFirstPlanner) {
            this.slowFirstPlanner = slowFirstPlanner;
        }

        @Override
        public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
            return new LlmResponse(replyText, "scripted", 0, 0);
        }

        @Override
        public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
            return new LlmResponse(replyText, "scripted-" + requestKind, 0, 0);
        }

        @Override
        public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
            int call = plannerCalls.incrementAndGet();
            lastPlannerPrompt = messages.stream().map(ChatPayload::content).reduce("", (a, b) -> a + "\n" + b);
            plannerStarted.countDown();
            if (slowFirstPlanner && call == 1) {
                long end = System.currentTimeMillis() + 3000;
                while (System.currentTimeMillis() < end) {
                    if (interruptFlag != null && interruptFlag.isInterrupted()) {
                        abortedPlannerCalls.incrementAndGet();
                        throw new com.maidsoul.brain.llm.LlmRequestException(
                                requestKind,
                                "aborted",
                                1,
                                0,
                                "scripted abort",
                                null
                        );
                    }
                    sleep(20);
                }
            }
            if (forcePlannerNoAction) {
                return new LlmResponse(
                        "no_action",
                        "scripted-planner",
                        0,
                        0,
                        List.of(new ToolCall("call-no-action", "no_action", Map.of("reason", "脚本强制不说"), ""))
                );
            }
            return new LlmResponse(
                    "reply",
                    "scripted-planner",
                    0,
                    0,
                    List.of(new ToolCall("call-reply", "reply", Map.of("reason", "按最新合批消息自然回应。"), ""))
            );
        }

        @Override
        public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
            String reply = replyText;
            if (deltaConsumer != null) {
                deltaConsumer.accept(reply);
            }
            return new LlmResponse(reply, "scripted-replyer", 0, 0);
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
