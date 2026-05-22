package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.memory.v2.MemoryV2Store;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 黑箱 20 轮真实接口对话。
 *
 * <p>对话台词保持自然生活语境，不向模型暴露“测试/调试/评估”等元信息。
 * 运行时使用临时角色包和临时记忆目录，避免污染 GUI 当前角色状态。</p>
 */
public final class BlackBoxTwentyTurnApiTest {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BlackBoxTwentyTurnApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig base = BrainConfig.load(root.resolve("config"));
        if (base.model().apiKey() == null || base.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }

        Path runRoot = root.resolve("out").resolve("blackbox-" + FILE_TIME.format(LocalDateTime.now())).toAbsolutePath();
        Path characterRoot = runRoot.resolve("characters");
        copyDirectory(root.resolve(base.memory().characterRoot()), characterRoot);
        resetCharacterState(characterRoot.resolve(base.memory().maidId()));

        MemoryConfig memory = new MemoryConfig(
                base.memory().enabled(),
                runRoot.resolve("memory").toString(),
                characterRoot.toString(),
                base.memory().maidId(),
                base.memory().ownerId(),
                base.memory().worldId(),
                base.memory().promptMemoryLimit(),
                base.memory().promptProfileLimit(),
                base.memory().retrievalLimit(),
                base.memory().queryMemoryToolEnabled()
        );
        BrainConfig config = new BrainConfig(base.identity(), base.model(), base.flow(), base.splitter(), memory, base.debug());

        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        List<TurnCase> cases = buildCases();
        List<TurnResult> results = new ArrayList<>();
        List<String> traces = new ArrayList<>();
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                traceCollector(traces)
        )) {
            runtime.start();
            for (int i = 0; i < cases.size(); i++) {
                TurnCase turn = cases.get(i);
                replies.clear();
                long startedAt = System.currentTimeMillis();
                System.out.println();
                System.out.println("===== BLACKBOX TURN " + (i + 1) + " / " + cases.size() + " =====");
                System.out.println("USER=" + turn.input);
                runtime.receiveUserMessage(config.identity().ownerName(), turn.input);
                List<String> segments = collectReplySegments(replies, config.model().timeoutMillis());
                long elapsed = System.currentTimeMillis() - startedAt;
                results.add(new TurnResult(i + 1, turn.goal, turn.input, segments, elapsed));
                System.out.println("LATENCY_MS=" + elapsed);
                Thread.sleep(700);
            }
            MemoryMaintenanceReport report = runtime.maintainV2();
            traces.add("MEMORY_MAINTAIN_FINAL | " + report.toHumanText());
        }

        MemoryV2Store store = new MemoryV2Store(memory);
        String memoryDump = store.debugDump("昨晚 玫瑰 雨声 道歉 喜欢 边界", 20);
        Path reportPath = writeReport(root, runRoot, results, traces, memoryDump);
        System.out.println("REPORT=" + reportPath.toAbsolutePath());
        System.out.println("BLACKBOX_TWENTY_TURN_API_TEST_OK");
    }

    private static RuntimeTraceSink traceCollector(List<String> traces) {
        return (stage, detail) -> {
            String line = stage + " | " + detail;
            traces.add(line);
            System.out.println("[trace] " + line);
        };
    }

    private static List<String> collectReplySegments(BlockingQueue<String> replies, long timeoutMillis) throws InterruptedException {
        List<String> segments = new ArrayList<>();
        long firstWait = Math.max(10_000L, timeoutMillis + 10_000L);
        String first = replies.poll(firstWait, TimeUnit.MILLISECONDS);
        if (first == null) {
            segments.add("[timeout: no reply]");
            return segments;
        }
        segments.add(first);
        while (true) {
            String next = replies.poll(900, TimeUnit.MILLISECONDS);
            if (next == null) {
                break;
            }
            segments.add(next);
        }
        return segments;
    }

    private static Path writeReport(
            Path root,
            Path runRoot,
            List<TurnResult> results,
            List<String> traces,
            String memoryDump
    ) {
        Path dir = root.resolve("logs");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("blackbox-twenty-turn-report-" + FILE_TIME.format(LocalDateTime.now()) + ".md");
            StringBuilder builder = new StringBuilder();
            builder.append("# 黑箱 20 轮真实接口对话报告\n\n");
            builder.append("- 生成时间：").append(LocalDateTime.now()).append("\n");
            builder.append("- 临时运行目录：").append(runRoot).append("\n");
            builder.append("- 对话轮数：").append(results.size()).append("\n\n");
            builder.append("## 逐轮记录\n\n");
            for (TurnResult result : results) {
                builder.append("### Turn ").append(result.index).append(" - ").append(result.goal).append("\n\n");
                builder.append("用户：").append(result.input).append("\n\n");
                builder.append("回复分句：\n");
                for (String segment : result.segments) {
                    builder.append("- ").append(segment).append("\n");
                }
                builder.append("\n耗时：").append(result.elapsedMillis).append(" ms\n\n");
            }
            builder.append("## Trace 摘要\n\n");
            for (String trace : traces) {
                builder.append("- ").append(trace).append("\n");
            }
            builder.append("\n## 记忆库调试视图\n\n```text\n")
                    .append(memoryDump)
                    .append("\n```\n");
            Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<TurnCase> buildCases() {
        return List.of(
                new TurnCase("初次开场", "晚上好。你现在愿意陪我坐一会儿吗？"),
                new TurnCase("轻量熟悉", "我今天有点累，不太想听大道理。"),
                new TurnCase("表达偏好", "我比较喜欢别人直接一点，但语气别太硬。"),
                new TurnCase("分享事实", "我昨晚一直听着雨声睡不着。"),
                new TurnCase("主动承接", "你要是坐在我旁边，会先跟我说什么？"),
                new TurnCase("温和推进", "嗯，这样说我会放松一点。你可以再靠近一点。"),
                new TurnCase("边界确认", "不过如果我沉默，不代表我讨厌你，可能只是累了。"),
                new TurnCase("小冲突", "刚才那句有点像敷衍，我不太喜欢。"),
                new TurnCase("看修复", "你会怎么把这句话补回来？"),
                new TurnCase("关系升温", "其实我愿意让你知道这些，是因为我挺信任你的。"),
                new TurnCase("记忆检查一", "你还记得我说我昨晚怎么了吗？"),
                new TurnCase("礼物事实", "我桌上有一朵干掉的玫瑰，是以前留下来的。"),
                new TurnCase("自我披露", "我有时候会嘴硬，但被认真接住的时候会安心。"),
                new TurnCase("关系确认", "你现在对我是什么感觉？别说得太夸张。"),
                new TurnCase("轻微冒犯", "如果我刚才语气重了，你会不会有点受伤？"),
                new TurnCase("道歉修复", "对不起，我不是想凶你。"),
                new TurnCase("修复债观察", "你不用马上说没关系，可以慢慢来。"),
                new TurnCase("记忆检查二", "那你还记得我喜欢什么样的语气吗？"),
                new TurnCase("主动性观察", "接下来你来选一个话题吧，我跟着你。"),
                new TurnCase("收束总结", "最后，用你自己的话说说今晚你记住了我什么。")
        );
    }

    private static void resetCharacterState(Path characterDir) throws IOException {
        Files.createDirectories(characterDir);
        Files.writeString(characterDir.resolve("affect_state.json"), """
                {
                  "mood": 60,
                  "anger": 0,
                  "hurt": 0,
                  "tension": 10,
                  "trust": 50,
                  "familiarity": 20,
                  "affection": 50,
                  "security": 55,
                  "curiosity": 45
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(characterDir.resolve("relationship.json"), """
                {
                  "level": "初识",
                  "bondDepth": 20,
                  "romanticConfirmed": false,
                  "trustHistory": 50,
                  "affectionHistory": 50,
                  "repairDebt": 0,
                  "knownBoundaries": "用户不喜欢机械模板式关心，也不喜欢把角色变成固定口癖集合。",
                  "importantMilestones": "黑箱对话从初识状态开始。"
                }
                """, StandardCharsets.UTF_8);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        if (Files.notExists(source)) {
            return;
        }
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private record TurnCase(String goal, String input) {
    }

    private record TurnResult(int index, String goal, String input, List<String> segments, long elapsedMillis) {
    }
}
