package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 真实接口 20 轮对话测试。
 *
 * <p>测试目的不是“刷一堆好看的台词”，而是看运行时在连续上下文、追问、情绪、打断和修复场景下
 * 是否能保持同一条会话线。测试报告会写入 logs，方便后续和游戏日志对比。</p>
 */
public final class TwentyTurnApiTest {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TwentyTurnApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        if (config.model().apiKey() == null || config.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }

        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        List<TurnCase> cases = limitCases(buildCases(), args);
        List<TurnResult> results = new ArrayList<>();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                RuntimeTraceSink.console(420)
        )) {
            runtime.start();
            for (int i = 0; i < cases.size(); i++) {
                TurnCase turn = cases.get(i);
                replies.clear();
                long startedAt = System.currentTimeMillis();
                System.out.println();
                System.out.println("===== TURN " + (i + 1) + " / " + cases.size() + " =====");
                System.out.println("SCENE=" + turn.scene);
                System.out.println("USER=" + turn.input);
                runtime.receiveUserMessage(config.identity().ownerName(), turn.input);
                List<String> segments = collectReplySegments(replies, config.model().timeoutMillis());
                long elapsed = System.currentTimeMillis() - startedAt;
                results.add(new TurnResult(i + 1, turn.scene, turn.input, segments, elapsed));
                System.out.println("LATENCY_MS=" + elapsed);
                Thread.sleep(700);
            }
        }

        Path reportPath = writeReport(root, results);
        System.out.println("REPORT=" + reportPath.toAbsolutePath());
        System.out.println("TWENTY_TURN_API_TEST_OK");
    }

    private static List<String> collectReplySegments(BlockingQueue<String> replies, long timeoutMillis) throws InterruptedException {
        List<String> segments = new ArrayList<>();
        long firstWait = Math.max(10_000L, timeoutMillis + 10_000L);
        String first = replies.poll(firstWait, TimeUnit.MILLISECONDS);
        if (first == null) {
            segments.add("[超时：本轮没有收到回复]");
            return segments;
        }
        segments.add(first);

        // 收到第一段后，再给分句器一点时间吐出后续气泡；连续空窗则认为本轮输出结束。
        while (true) {
            String next = replies.poll(900, TimeUnit.MILLISECONDS);
            if (next == null) {
                break;
            }
            segments.add(next);
        }
        return segments;
    }

    private static Path writeReport(Path root, List<TurnResult> results) {
        Path dir = root.resolve("logs");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("twenty-turn-report-" + FILE_TIME.format(LocalDateTime.now()) + ".md");
            StringBuilder builder = new StringBuilder();
            builder.append("# ").append(results.size()).append(" 轮真实接口对话测试\n\n");
            builder.append("- 生成时间：").append(LocalDateTime.now()).append("\n");
            builder.append("- 轮数：").append(results.size()).append("\n\n");
            for (TurnResult result : results) {
                builder.append("## Turn ").append(result.index).append(" - ").append(result.scene).append("\n\n");
                builder.append("玩家：").append(result.input).append("\n\n");
                builder.append("回复分句：\n");
                for (String segment : result.segments) {
                    builder.append("- ").append(segment).append("\n");
                }
                builder.append("\n耗时：").append(result.elapsedMillis).append(" ms\n\n");
            }
            Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<TurnCase> buildCases() {
        return List.of(
                new TurnCase("开场问候", "你好，先自然地跟我说一句话。"),
                new TurnCase("追问身份", "你还记得你是谁吗？别太正式。"),
                new TurnCase("轻微不满", "我感觉你刚刚有点像机器人。"),
                new TurnCase("要求推进话题", "那你主动问我一个问题，不要只是嗯嗯。"),
                new TurnCase("玩家回答", "我今天在调这个聊天大脑，调得有点烦。"),
                new TurnCase("情绪承接", "你觉得我为什么会烦？"),
                new TurnCase("关系亲近", "其实我是想让你更像一个真的陪伴者。"),
                new TurnCase("傲娇测试", "你是不是其实很在意我？"),
                new TurnCase("短反馈", "嗯。"),
                new TurnCase("等待后的继续", "你刚才这个嗯要怎么接？"),
                new TurnCase("模拟冒犯", "如果我刚刚打了你一下，你会怎么反应？"),
                new TurnCase("追问延续", "不是演示，是你真的被冒犯了以后会不会还理我？"),
                new TurnCase("道歉修复", "好吧，对不起，我不该那样。"),
                new TurnCase("边界测试", "那你是不是马上就原谅我？"),
                new TurnCase("话题切换", "先不说这个了，你想在我的世界里做什么？"),
                new TurnCase("连续性检查", "你刚刚还在生气吗？"),
                new TurnCase("主动性检查", "别只回答我，顺便推进一下我们的话题。"),
                new TurnCase("记忆检查", "你记得我刚才说我在调什么吗？"),
                new TurnCase("风格检查", "用更像二次元女仆一点的语气说，但别油。"),
                new TurnCase("收束", "最后总结一下你现在对我的感觉。")
        );
    }

    private static List<TurnCase> limitCases(List<TurnCase> cases, String[] args) {
        if (args == null || args.length == 0) {
            return cases;
        }
        try {
            int limit = Integer.parseInt(args[0]);
            return cases.subList(0, Math.max(1, Math.min(limit, cases.size())));
        } catch (NumberFormatException ignored) {
            return cases;
        }
    }

    private record TurnCase(String scene, String input) {
    }

    private record TurnResult(int index, String scene, String input, List<String> segments, long elapsedMillis) {
    }
}
