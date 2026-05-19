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
import com.maidsoul.brain.runtime.RuntimeTraceSink;
import com.maidsoul.brain.tool.ToolCall;
import com.maidsoul.brain.tool.ToolSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 情绪和主动节奏综合验收。
 *
 * <p>这不是为了替代真人手测，而是把用户最在意的结构问题钉住：
 * planner 负责语义情绪事件，运行时只提交沉默候选；沉默期间最多推进四次，最后进入安静。</p>
 */
public final class RhythmAffectScenarioSmokeTest {
    private RhythmAffectScenarioSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        testHighInterestSilence(root, "我今天不开心", "OWNER_DISTRESS");
        testHighInterestSilence(root, "我有个问题，等会跟你说", "OWNER_QUESTION");
        testHighInterestSilence(root, "你知道吗 今天我好开心啊", "OWNER_AFFECTION");
        System.out.println("RHYTHM_AFFECT_SCENARIO_SMOKE_OK");
    }

    private static void testHighInterestSilence(Path root, String userText, String expectedEvent) throws Exception {
        BrainConfig config = withIsolatedFastConfig(root, BrainConfig.load(root.resolve("config")));
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        List<String> traces = new ArrayList<>();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new ScenarioClient(expectedEvent),
                replies::offer,
                traceCollector(traces)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), userText);

            String first = replies.poll(3, TimeUnit.SECONDS);
            if (first == null) {
                throw new IllegalStateException("高兴趣输入没有初始回应: " + userText);
            }
            drain(replies);

            List<String> proactiveReplies = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && proactiveReplies.size() < 4) {
                String reply = replies.poll(1500, TimeUnit.MILLISECONDS);
                if (reply != null) {
                    proactiveReplies.add(reply);
                    drain(replies);
                }
            }
            if (proactiveReplies.size() != 4) {
                throw new IllegalStateException("沉默后应最多推进四轮，实际=" + proactiveReplies.size()
                        + " input=" + userText + " replies=" + proactiveReplies + "\n" + String.join("\n", traces));
            }
            Thread.sleep(1800);
            if (replies.poll(200, TimeUnit.MILLISECONDS) != null) {
                throw new IllegalStateException("四轮收束后不应继续主动刷屏: " + userText);
            }
        }

        String traceText = String.join("\n", traces);
        if (!traceText.contains("affect.event | " + expectedEvent)) {
            throw new IllegalStateException("planner 情绪事件没有进入情绪层: " + expectedEvent + "\n" + traceText);
        }
        if (!traceText.contains("stage=final_notice")) {
            throw new IllegalStateException("第四轮没有进入 final_notice 收束阶段。\n" + traceText);
        }
        if (!traceText.contains("proactive.stop")) {
            throw new IllegalStateException("四轮后没有进入主动沉默状态。\n" + traceText);
        }
    }

    private static RuntimeTraceSink traceCollector(List<String> traces) {
        return (stage, detail) -> traces.add(stage + " | " + detail);
    }

    private static void drain(BlockingQueue<String> replies) throws InterruptedException {
        while (replies.poll(150, TimeUnit.MILLISECONDS) != null) {
            // 等本轮流式分句吐完。
        }
    }

    private static BrainConfig withIsolatedFastConfig(Path root, BrainConfig base) throws Exception {
        Path memoryRoot = Files.createTempDirectory(root.resolve("out"), "rhythm-affect-memory-");
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                10,
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                1,
                old.talkFrequency(),
                old.plannerInterruptMaxConsecutiveCount(),
                old.timingGateNonContinueCooldownMillis(),
                false,
                true,
                4,
                1,
                1,
                1,
                1,
                1,
                1,
                2
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

    private static final class ScenarioClient implements LlmClient {
        private final String eventKind;
        private int proactiveReplyCount;

        private ScenarioClient(String eventKind) {
            this.eventKind = eventKind;
        }

        @Override
        public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
            return replyer(messages, "scripted");
        }

        @Override
        public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
            return replyer(messages, "scripted-" + requestKind);
        }

        @Override
        public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis, InterruptFlag interruptFlag) {
            String prompt = messages.stream().map(ChatPayload::content).reduce("", (a, b) -> a + "\n" + b);
            if (prompt.contains("[主动候选事件]")) {
                boolean finalNotice = prompt.contains("阶段=final_notice");
                String reason = finalNotice ? "最后短短收束，然后安静下来。" : "玩家沉默，轻轻追问一次。";
                return tool("reply", Map.of("reason", reason));
            }
            return tool("reply", Map.of(
                    "reason", "接住用户抛出的高兴趣话题。",
                    "affect_event_kind", eventKind,
                    "affect_event_intensity", 75,
                    "affect_event_note", "用户抛出需要被接住的高兴趣内容。"
            ));
        }

        @Override
        public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer, InterruptFlag interruptFlag) {
            LlmResponse response = replyer(messages, "scripted-replyer");
            if (deltaConsumer != null) {
                deltaConsumer.accept(response.content());
            }
            return response;
        }

        private LlmResponse replyer(List<ChatPayload> messages, String model) {
            String prompt = messages.stream().map(ChatPayload::content).reduce("", (a, b) -> a + "\n" + b);
            String text;
            if (prompt.contains("最后短短收束")) {
                text = "好，我先不追着问了，等你想说的时候我还在。";
            } else if (prompt.contains("[主动候选事件]") || prompt.contains("玩家沉默")) {
                proactiveReplyCount++;
                text = "我还记着刚才那件事，你愿意的话可以慢慢告诉我 " + proactiveReplyCount;
            } else {
                text = "我听到了，这件事我会认真放在心上。";
            }
            return new LlmResponse(text, model, 0, 0);
        }

        private static LlmResponse tool(String name, Map<String, Object> args) {
            return new LlmResponse(name, "scripted-planner", 0, 0,
                    List.of(new ToolCall("call-" + name, name, args, "")));
        }
    }
}
