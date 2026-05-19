package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * 命令行测试入口。
 *
 * <p>这个入口只验证“聊天大脑”本身：输入玩家文本，观察运行时如何缓存、规划、生成、分句和输出。
 * 后续接 Forge 时，游戏事件只需要替换这里的输入输出适配层。</p>
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        RuntimeTraceSink trace = config.debug().enableConsoleTrace()
                ? RuntimeTraceSink.console(config.debug().maxTraceChars())
                : RuntimeTraceSink.noop();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> System.out.println(config.identity().botName() + " > " + segment),
                trace
        )) {
            runtime.start();
            System.out.println("聊天大脑已启动。输入文字开始测试，输入 /exit 退出。");
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print(config.identity().ownerName() + " > ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine();
                if ("/exit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                runtime.receiveUserMessage(config.identity().ownerName(), line);
            }
        }
    }
}

