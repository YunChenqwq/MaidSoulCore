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
 * 主动节奏真实接口测试。
 *
 * <p>测试时把主动窗口临时缩短，便于快速观察：玩家说完 -> 女仆回复 -> 玩家沉默 ->
 * 运行时投递主动事件 -> 规划器决定是否继续说。</p>
 */
public final class ProactiveRhythmApiTest {
    private ProactiveRhythmApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig base = BrainConfig.load(root.resolve("config"));
        if (base.model().apiKey() == null || base.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }
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
            String naturalInput = args.length == 0
                    ? "我今天调了一下午东西，脑子有点木了。"
                    : String.join(" ", args);
            System.out.println("USER=" + naturalInput);
            runtime.receiveUserMessage(config.identity().ownerName(), naturalInput);

            String first = replies.poll(Math.max(10_000L, config.model().timeoutMillis() + 10_000L), TimeUnit.MILLISECONDS);
            if (first == null) {
                throw new IllegalStateException("普通聊天阶段超时。");
            }
            while (replies.poll(1200, TimeUnit.MILLISECONDS) != null) {
                // 等第一轮分句结束。
            }

            System.out.println("USER=[沉默，不告诉模型这是测试]");
            String proactive = replies.poll(90, TimeUnit.SECONDS);
            if (proactive == null) {
                System.out.println("BOT_SEGMENT=[90 秒内没有可见主动回复，可能规划器选择了 wait/no_action]");
            }
        }

        System.out.println("PROACTIVE_RHYTHM_API_TEST_DONE");
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
