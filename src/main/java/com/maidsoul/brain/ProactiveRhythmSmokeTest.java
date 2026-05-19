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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 主动节奏离线测试。
 *
 * <p>这里把主动窗口临时压缩到几秒，用脚本模型验证运行时是否会在“女仆说完后”投递主动事件，
 * 并确认主动事件仍然经过规划器，而不是绕过主链路直接输出固定话术。</p>
 */
public final class ProactiveRhythmSmokeTest {
    private ProactiveRhythmSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig base = BrainConfig.load(root.resolve("config"));
        BrainConfig config = withFastProactiveRhythm(base);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new ScriptedClient(),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                RuntimeTraceSink.console(600)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "我在调主动聊天节奏，先自然接我一句。");

            String first = replies.poll(5, TimeUnit.SECONDS);
            if (first == null) {
                throw new IllegalStateException("没有收到普通聊天回复。");
            }
            while (replies.poll(1600, TimeUnit.MILLISECONDS) != null) {
                // 等本轮分句吐完，再开始观察主动节奏。
            }

            String proactive = replies.poll(8, TimeUnit.SECONDS);
            if (proactive == null) {
                throw new IllegalStateException("没有收到主动节奏回复。");
            }
            if (!proactive.contains("刚才")) {
                throw new IllegalStateException("主动回复没有承接刚才的话题: " + proactive);
            }
        }

        System.out.println("PROACTIVE_RHYTHM_SMOKE_OK");
    }

    private static BrainConfig withFastProactiveRhythm(BrainConfig base) {
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                20,
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                2,
                old.talkFrequency(),
                old.plannerInterruptMaxConsecutiveCount(),
                old.timingGateNonContinueCooldownMillis(),
                old.directReplyOnUserMessage(),
                true,
                old.proactiveMaxVisibleReplies(),
                1,
                2,
                4,
                6,
                8
        );
        return new BrainConfig(base.identity(), base.model(), flow, base.splitter(), base.memory(), base.debug());
    }

    private static final class ScriptedClient implements LlmClient {
        @Override
        public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
            return replyerResponse(messages, "scripted");
        }

        @Override
        public LlmResponse chat(String requestKind, List<ChatPayload> messages, long timeoutMillis, InterruptFlag interruptFlag) {
            return replyerResponse(messages, "scripted-" + requestKind);
        }

        @Override
        public LlmResponse chatWithTools(String requestKind, List<ChatPayload> messages, List<ToolSpec> tools, long timeoutMillis) {
            String visible = messages.stream().map(ChatPayload::content).reduce("", (a, b) -> a + "\n" + b);
            String reason = visible.contains("阶段=")
                    ? "玩家沉默后进入轻续话期，刚才话题还没收束，可以轻轻补一句。"
                    : "玩家有新消息，需要自然回应。";
            return new LlmResponse(
                    "分析：" + reason,
                    "scripted-planner",
                    0,
                    0,
                    List.of(new ToolCall("call-1", "reply", Map.of("reason", reason), ""))
            );
        }

        @Override
        public LlmResponse chatStream(String requestKind, List<ChatPayload> messages, long timeoutMillis, Consumer<String> deltaConsumer) {
            LlmResponse response = replyerResponse(messages, "scripted-replyer");
            if (deltaConsumer != null) {
                deltaConsumer.accept(response.content());
            }
            return response;
        }

        private LlmResponse replyerResponse(List<ChatPayload> messages, String model) {
            String visible = messages.stream().map(ChatPayload::content).reduce("", (a, b) -> a + "\n" + b);
            String reply = visible.contains("轻续话期") || visible.contains("玩家沉默")
                    ? "刚才你说在调主动聊天节奏，我想了一下：我可以先等你，不急着抢话。"
                    : "好，我先接住这句。你是在看我会不会像真人一样等你回话，对吧？";
            return new LlmResponse(reply, model, 0, 0);
        }
    }
}
