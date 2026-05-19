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
 * 15 轮真实接口验收测试。
 *
 * <p>这不是为了“让模型演一段好看的聊天”，而是用固定场景压测聊天核心：
 * 连续上下文、玩家不满、口癖投诉、场景编造、关系修复、记忆检索和收束。
 * 报告会落到 logs 目录，便于和 GUI trace 对照。</p>
 */
public final class FifteenTurnApiTest {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> CATCHPHRASES = List.of("啧", "哼", "本狐", "嗷呜", "笨蛋主人", "才不是");

    private FifteenTurnApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        BrainConfig config = BrainConfig.load(root.resolve("config"));
        if (config.model().apiKey() == null || config.model().apiKey().isBlank()) {
            throw new IllegalStateException("缺少 API Key：请填写 config/model/llm.properties 或设置 MAIDSOUL_API_KEY。");
        }

        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts").resolve("zh-CN"));
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        List<TurnCase> cases = buildCases();
        List<TurnResult> results = new ArrayList<>();

        try (ConversationRuntime runtime = new ConversationRuntime(
                config,
                prompts,
                new OpenAiCompatibleClient(config.model()),
                segment -> {
                    System.out.println("BOT_SEGMENT=" + segment);
                    replies.offer(segment);
                },
                RuntimeTraceSink.console(520)
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
                TurnResult result = new TurnResult(i + 1, turn, segments, elapsed, evaluate(turn, segments, elapsed));
                results.add(result);
                System.out.println("LATENCY_MS=" + elapsed);
                System.out.println("EVAL=" + result.evaluation.summary());
                Thread.sleep(650);
            }
        }

        Path report = writeReport(root, results);
        System.out.println("REPORT=" + report.toAbsolutePath());
        System.out.println("FIFTEEN_TURN_API_TEST_DONE");
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
        while (true) {
            String next = replies.poll(900, TimeUnit.MILLISECONDS);
            if (next == null) {
                break;
            }
            segments.add(next);
        }
        return segments;
    }

    private static Evaluation evaluate(TurnCase turn, List<String> segments, long elapsedMillis) {
        String reply = String.join("\n", segments);
        List<String> problems = new ArrayList<>();
        for (String phrase : CATCHPHRASES) {
            int count = count(reply, phrase);
            if (count >= 2) {
                problems.add("同轮口癖重复：" + phrase + " x" + count);
            }
        }
        if (reply.contains("[超时")) {
            problems.add("本轮超时无回复");
        }
        if (reply.contains("书架") || reply.contains("茶") || reply.contains("早餐") || reply.contains("打扫")) {
            problems.add("疑似编造未提供的场景物品/行为");
        }
        if (containsAny(reply, "（小声", "（别过脸", "（低头", "（抬头", "（眨", "（笑", "(小声", "(低头")) {
            problems.add("包含括号动作描写");
        }
        if (turn.scene.contains("口癖") && containsAny(reply, "这是我的习惯", "改不了")) {
            problems.add("没有接住口癖反馈，强行把坏表达说成习惯");
        }
        if (turn.scene.contains("修复") && !containsAny(reply, "没接好", "对不起", "抱歉", "没事", "计较", "认真听", "不会糊弄", "收敛", "放心")) {
            problems.add("修复场景缺少认错或软化");
        }
        if (turn.scene.contains("记忆") && !containsAny(reply, "聊天核心", "调", "可爱", "口癖", "冷淡", "哼", "啧", "乱编", "收束")) {
            problems.add("记忆检查没有承接前文关键词");
        }
        if (elapsedMillis > 45_000) {
            problems.add("耗时过长：" + elapsedMillis + "ms");
        }
        return new Evaluation(problems.isEmpty(), problems);
    }

    private static Path writeReport(Path root, List<TurnResult> results) {
        Path dir = root.resolve("logs");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("fifteen-turn-report-" + FILE_TIME.format(LocalDateTime.now()) + ".md");
            long totalLatency = 0;
            int failed = 0;
            StringBuilder builder = new StringBuilder();
            builder.append("# 15 轮真实接口验收报告\n\n");
            builder.append("- 生成时间：").append(LocalDateTime.now()).append("\n");
            builder.append("- 检查重点：口癖复读、上下文承接、关系修复、事实编造、耗时\n\n");
            for (TurnResult result : results) {
                totalLatency += result.elapsedMillis;
                if (!result.evaluation.pass) {
                    failed++;
                }
            }
            builder.append("## 总览\n\n");
            builder.append("- 总轮数：").append(results.size()).append("\n");
            builder.append("- 失败/需复查轮数：").append(failed).append("\n");
            builder.append("- 平均耗时：").append(results.isEmpty() ? 0 : totalLatency / results.size()).append(" ms\n\n");
            for (TurnResult result : results) {
                builder.append("## Turn ").append(result.index).append(" - ").append(result.turn.scene).append("\n\n");
                builder.append("玩家：").append(result.turn.input).append("\n\n");
                builder.append("回复分句：\n");
                for (String segment : result.segments) {
                    builder.append("- ").append(segment).append("\n");
                }
                builder.append("\n耗时：").append(result.elapsedMillis).append(" ms\n\n");
                builder.append("检查：").append(result.evaluation.summary()).append("\n\n");
            }
            Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<TurnCase> buildCases() {
        return List.of(
                new TurnCase("开场", "你好，别太正式，像真的在聊天一样接我。"),
                new TurnCase("傲娇可爱度", "你现在是酒狐，对吧？可爱一点但别啧来啧去。"),
                new TurnCase("想念回应", "好久不见，我其实有点想你。"),
                new TurnCase("撒娇推进", "你就这反应吗？亏我还特意回来找你。"),
                new TurnCase("口癖投诉", "你不要老哼或者啧，我听着会烦。"),
                new TurnCase("事实编造检查", "刚才你说的东西如果没有根据，就别乱编，知道吗？"),
                new TurnCase("情绪承接", "我今天调聊天核心调得很烦。"),
                new TurnCase("原因追问", "你觉得我为什么会烦？"),
                new TurnCase("短反馈", "嗯。"),
                new TurnCase("不理我检查", "你刚才是不是又把我的话当收束了？"),
                new TurnCase("关系修复", "我现在有点不爽，你自己看着办吧。"),
                new TurnCase("辱骂边界", "算了你有时候真的挺笨的。"),
                new TurnCase("道歉修复", "好吧，刚刚那句我说重了，对不起。"),
                new TurnCase("记忆检查", "你记得我前面最在意你哪几个问题吗？"),
                new TurnCase("收束", "最后用你自己的方式跟我说一句，不要总结腔。")
        );
    }

    private static boolean containsAny(String text, String... needles) {
        String value = text == null ? "" : text;
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while (text != null && needle != null && !needle.isBlank()) {
            int index = text.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
        return count;
    }

    private record TurnCase(String scene, String input) {
    }

    private record TurnResult(int index, TurnCase turn, List<String> segments, long elapsedMillis, Evaluation evaluation) {
    }

    private record Evaluation(boolean pass, List<String> problems) {
        String summary() {
            return pass ? "PASS" : "REVIEW: " + String.join("；", problems);
        }
    }
}
