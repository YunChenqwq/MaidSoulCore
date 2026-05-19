package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.FlowConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 主动追问真实接口测试。
 *
 * <p>先建立一个未收束话题，再让玩家用短反馈把话语权交回来，观察沉默后是否会自然追问。</p>
 */
public final class ProactiveFollowupApiTest {
    private ProactiveFollowupApiTest() {
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
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                RuntimeTraceSink.console(700)
        )) {
            runtime.start();
            sendAndDrain(runtime, replies, config, "我好像发现它为什么总是复读了，但还没完全想清楚。");
            sendAndDrain(runtime, replies, config, "嗯，你可以继续问。");

            System.out.println("USER=[沉默，等待她自己追问]");
            String proactive = replies.poll(45, TimeUnit.SECONDS);
            if (proactive == null) {
                System.out.println("BOT_SEGMENT=[45 秒内没有可见主动追问]");
            }
        }

        System.out.println("PROACTIVE_FOLLOWUP_API_TEST_DONE");
    }

    private static void sendAndDrain(
            ConversationRuntime runtime,
            BlockingQueue<String> replies,
            BrainConfig config,
            String text
    ) throws InterruptedException {
        replies.clear();
        System.out.println("USER=" + text);
        runtime.receiveUserMessage(config.identity().ownerName(), text);
        String first = replies.poll(Math.max(10_000L, config.model().timeoutMillis() + 10_000L), TimeUnit.MILLISECONDS);
        if (first == null) {
            throw new IllegalStateException("本轮超时: " + text);
        }
        while (replies.poll(1200, TimeUnit.MILLISECONDS) != null) {
            // 等分句结束。
        }
    }

    private static BrainConfig withFastProactiveRhythm(BrainConfig base) {
        FlowConfig old = base.flow();
        FlowConfig flow = new FlowConfig(
                old.historyWindow(),
                old.messageDebounceMillis(),
                old.maxInternalRounds(),
                old.enableIndependentTimingGate(),
                old.defaultWaitSeconds(),
                old.talkFrequency(),
                old.plannerInterruptMaxConsecutiveCount(),
                old.timingGateNonContinueCooldownMillis(),
                old.directReplyOnUserMessage(),
                true,
                old.proactiveMaxVisibleReplies(),
                3,
                6,
                14,
                24,
                36
        );
        return new BrainConfig(base.identity(), base.model(), flow, base.splitter(), base.memory(), base.debug());
    }
}
