package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 5 轮真实接口实时测试。
 *
 * <p>用于快速观察工具式规划、上下文连续、回复分句和输出清洗是否正常。</p>
 */
public final class FiveTurnApiTest {
    private FiveTurnApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        if (config.model().apiKey() == null || config.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }

        List<String> inputs = List.of(
                "你好，像平常聊天一样回我一句。",
                "你刚刚说得有点普通，能不能更像真的在陪我？",
                "我今天一直在调聊天核心，挺烦的。",
                "那你觉得我为什么会烦？",
                "最后主动推进一下话题，不要只回答。"
        );

        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        AtomicLong currentTurnStartedAt = new AtomicLong();
        AtomicLong firstSegmentAt = new AtomicLong();
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    long now = System.currentTimeMillis();
                    if (firstSegmentAt.compareAndSet(0L, now)) {
                        System.out.println("FIRST_SEGMENT_LATENCY_MS=" + (now - currentTurnStartedAt.get()));
                    }
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            for (int i = 0; i < inputs.size(); i++) {
                replies.clear();
                String input = inputs.get(i);
                long started = System.currentTimeMillis();
                currentTurnStartedAt.set(started);
                firstSegmentAt.set(0L);
                System.out.println();
                System.out.println("===== TURN " + (i + 1) + " =====");
                System.out.println("USER=" + input);
                runtime.receiveUserMessage(config.identity().ownerName(), input);
                String first = replies.poll(Math.max(10_000L, config.model().timeoutMillis() + 10_000L), TimeUnit.MILLISECONDS);
                if (first == null) {
                    System.out.println("BOT_SEGMENT=[超时：本轮没有收到回复]");
                } else {
                    while (replies.poll(900, TimeUnit.MILLISECONDS) != null) {
                        // 后续分句已由 output 回调实时打印，这里只等待本轮结束。
                    }
                }
                System.out.println("COLLECT_IDLE_LATENCY_MS=" + (System.currentTimeMillis() - started));
                Thread.sleep(700);
            }
        }
        System.out.println("FIVE_TURN_API_TEST_DONE");
    }
}
