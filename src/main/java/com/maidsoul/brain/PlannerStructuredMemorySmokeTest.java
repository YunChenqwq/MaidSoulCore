package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.llm.InterruptFlag;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmResponse;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.tool.ToolCall;
import com.maidsoul.brain.tool.ToolSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * planner 结构化记忆链路烟测。
 *
 * <p>这条测试故意让用户输入里出现“不是、讨厌”这类容易误判的自然语言，
 * 同时让 planner 通过工具参数显式提交一条 user_profile/boundary 记忆。
 * 预期结果是：记忆层只采纳 planner 的结构化 memory_event，不从原句里硬猜
 * correction/error_mark。</p>
 */
public final class PlannerStructuredMemorySmokeTest {
    private PlannerStructuredMemorySmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = withIsolatedConfig(root, BrainConfig.load(root.resolve("config")));
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new StructuredMemoryClient(),
                replies::offer,
                (stage, detail) -> {
                }
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "不是讨厌，只是我希望你说话直接但温柔。");

            String reply = replies.poll(3, TimeUnit.SECONDS);
            if (reply == null) {
                throw new IllegalStateException("planner structured memory smoke did not reply");
            }

            String beforeMaintain = runtime.debugMemoryV2("direct gentle boundary", 10);
            if (!beforeMaintain.contains("User prefers direct but gentle tone.")) {
                throw new IllegalStateException("structured planner memory was not stored:\n" + beforeMaintain);
            }
            if (!beforeMaintain.contains("user_profile") || !beforeMaintain.contains("boundary")) {
                throw new IllegalStateException("structured planner memory lost tags/layer:\n" + beforeMaintain);
            }

            runtime.maintainV2();
            String afterMaintain = runtime.debugMemoryV2("不是讨厌", 20);
            if (afterMaintain.contains("tags=error_mark")) {
                throw new IllegalStateException("raw natural text was incorrectly marked as error_mark:\n" + afterMaintain);
            }
        }

        System.out.println("PLANNER_STRUCTURED_MEMORY_SMOKE_OK");
    }

    private static BrainConfig withIsolatedConfig(Path root, BrainConfig base) throws Exception {
        Path memoryRoot = Files.createTempDirectory(root.resolve("out"), "planner-structured-memory-");
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                20,
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                1,
                old.talkFrequency(),
                old.plannerInterruptMaxConsecutiveCount(),
                old.timingGateNonContinueCooldownMillis(),
                false,
                false,
                old.proactiveMaxVisibleReplies(),
                old.proactiveInputProtectionSeconds(),
                old.proactiveLightFollowupAfterSeconds(),
                old.proactiveTopicPushAfterSeconds(),
                old.proactiveWorldObserveAfterSeconds(),
                old.proactiveIdleMinIntervalSeconds(),
                old.proactiveLongSilenceCheckSeconds(),
                old.proactiveMaxLongSilenceChecks()
        );
        MemoryConfig memory = new MemoryConfig(
                true,
                memoryRoot.toString(),
                memoryRoot.resolve("characters").toString(),
                base.memory().maidId(),
                base.memory().ownerId(),
                base.memory().worldId(),
                base.memory().promptMemoryLimit(),
                base.memory().promptProfileLimit(),
                base.memory().retrievalLimit(),
                base.memory().queryMemoryToolEnabled()
        );
        return new BrainConfig(base.identity(), base.model(), flow, base.splitter(), memory, base.debug());
    }

    private static final class StructuredMemoryClient implements LlmClient {
        @Override
        public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
            return new LlmResponse("收到，我会记住这个边界。", "scripted", 0, 0);
        }

        @Override
        public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
            return new LlmResponse("收到，我会记住这个边界。", "scripted-" + requestKind, 0, 0);
        }

        @Override
        public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("reason", "接住用户明确表达的说话边界。");
            args.put("affect_event_kind", "");
            args.put("affect_event_intensity", 0);
            args.put("memory_event_type", "PREFERENCE");
            args.put("memory_event_layer", "user_profile");
            args.put("memory_event_content", "User prefers direct but gentle tone.");
            args.put("memory_event_tags", "preference,boundary");
            args.put("memory_event_importance", 4);
            return new LlmResponse("reply", "scripted-planner", 0, 0,
                    List.of(new ToolCall("call-reply", "reply", args, "")));
        }

        @Override
        public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
            String reply = "收到，我会记住这个边界。";
            if (deltaConsumer != null) {
                deltaConsumer.accept(reply);
            }
            return new LlmResponse(reply, "scripted-replyer", 0, 0);
        }
    }
}
