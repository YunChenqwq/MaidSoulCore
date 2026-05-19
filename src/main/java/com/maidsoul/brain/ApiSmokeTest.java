package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 真实接口冒烟测试。
 *
 * <p>只发一轮消息，验证模型配置、请求格式、运行时链路和分句输出是否能闭环。
 * API Key 可以写在配置里，也可以通过 MAIDSOUL_API_KEY 环境变量传入。</p>
 */
public final class ApiSmokeTest {
    private ApiSmokeTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        if (config.model().apiKey() == null || config.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }

        CountDownLatch latch = new CountDownLatch(1);
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    latch.countDown();
                },
                RuntimeTraceSink.console(500)
        )) {
            runtime.start();
            runtime.receiveUserMessage(config.identity().ownerName(), "你好，先用一句自然的话回应我，别解释系统。");
            boolean ok = latch.await(Math.max(10, config.model().timeoutMillis() / 1000 + 10), TimeUnit.SECONDS);
            if (!ok) {
                throw new IllegalStateException("真实接口冒烟测试超时。");
            }
        }
        System.out.println("API_SMOKE_OK");
    }
}

