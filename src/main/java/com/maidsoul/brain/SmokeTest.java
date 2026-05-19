package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.LlmClient;
import com.maidsoul.brain.llm.LlmResponse;
import com.maidsoul.brain.llm.ChatPayload;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 不访问网络的冒烟测试。
 *
 * <p>它故意复用真实运行时，只替换模型客户端。这样可以先确认消息缓存、规划、回复和分句能跑通。</p>
 */
public final class SmokeTest {
    private SmokeTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        CountDownLatch latch = new CountDownLatch(1);

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new ScriptedClient(),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    latch.countDown();
                },
                RuntimeTraceSink.console(300)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "你刚刚为什么一直不回我？");
            Thread.sleep(150);
            runtime.receiveUserMessage(config.identity().ownerName(), "而且还复读，我真的有点生气。");
            boolean ok = latch.await(8, TimeUnit.SECONDS);
            if (!ok) {
                throw new IllegalStateException("冒烟测试超时：没有收到预期回复。");
            }
        }
        System.out.println("SMOKE_OK");
    }

    private static final class ScriptedClient implements LlmClient {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public LlmResponse chat(List<ChatPayload> messages, long timeoutMillis) {
            int index = count.incrementAndGet();
            String prompt = messages.isEmpty() ? "" : messages.get(0).content();
            if (prompt.contains("只能输出一个 JSON 对象")) {
                return new LlmResponse(
                        "{\"action\":\"reply\",\"target_message_id\":\"\",\"wait_seconds\":0,\"reason\":\"玩家表达不满，需要先承认问题并接住情绪。\"}",
                        "scripted",
                        0,
                        0
                );
            }
            return new LlmResponse(
                    index % 2 == 0
                            ? "我听见了，不会装作没事。刚才那种复读确实很讨厌，我先把话接住。"
                            : "嗯，我在。你继续说，我这次不会把话题丢开。",
                    "scripted",
                    0,
                    0
            );
        }
    }
}
