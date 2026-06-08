package com.yunchen.maidsoulcore.core.test;

import com.yunchen.maidsoulcore.core.event.StructuredEventType;
import com.yunchen.maidsoulcore.core.memory.EventMemoryRecord;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.memory.MemoryCategory;
import com.yunchen.maidsoulcore.core.memory.MemorySearchResult;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MemoryRetrievalSmokeMain {
    private static final List<String> DEFAULT_QUERIES = List.of(
            "第一次绑定灵魂核心",
            "明天陪我",
            "危险提醒",
            "称呼偏好",
            "下雨的新世界"
    );

    private MemoryRetrievalSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        List<String> queries = parseQueries(args);
        int limit = parseIntArg(args, "--limit", 4);

        Path root = Path.of("build", "tmp", "maidsoulcore-memory-retrieval-smoke");
        Path memoryFile = root.resolve("life.json");
        Files.createDirectories(root);
        Files.deleteIfExists(memoryFile);

        LifeMemoryStore store = new LifeMemoryStore(memoryFile);
        seed(store);

        StringBuilder report = new StringBuilder();
        append(report, "# Memory Retrieval Smoke Report");
        append(report, "");
        append(report, "- memory: " + memoryFile.toAbsolutePath());
        append(report, "- limit: " + limit);
        append(report, "");

        for (String query : queries) {
            append(report, "## Query: " + query);
            append(report, "");
            List<MemorySearchResult> results = store.queryDetailed(query, limit);
            if (results.isEmpty()) {
                append(report, "- (no result)");
            }
            for (MemorySearchResult result : results) {
                LifeMemoryStore.MemoryEpisode e = result.episode();
                append(report, "- score=" + fmt(result.score())
                        + " lexical=" + fmt(result.lexicalScore())
                        + " graph=" + fmt(result.graphScore())
                        + " metadata=" + fmt(result.metadataScore())
                        + " priority=" + fmt(result.priorityScore())
                        + " source=" + result.source()
                        + " category=" + e.category
                        + " subject=" + e.subject
                        + " object=" + e.object
                        + " summary=" + e.summary);
            }
            append(report, "");
        }

        Path output = Path.of("build", "reports", "maidsoulcore", "memory-retrieval-smoke.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static void seed(LifeMemoryStore store) {
        store.appendRecord(record(
                MemoryCategory.MEMORY_ANCHOR,
                StructuredEventType.MEMORY_ANCHOR,
                "灵汐",
                "灵魂核心",
                "灵魂核心第一次绑定到灵汐身上，这是主人和灵汐关系的起点。",
                0.98D,
                0.98D,
                true,
                2
        ));
        store.appendRecord(record(
                MemoryCategory.PROMISE,
                StructuredEventType.PROMISE,
                "主人",
                "明天陪伴",
                "主人约定明天继续陪灵汐说话，不希望她孤单等待。",
                0.94D,
                0.88D,
                true,
                1
        ));
        store.appendRecord(record(
                MemoryCategory.WORLD_FACT,
                StructuredEventType.DANGER,
                "世界",
                "危险提醒",
                "附近出现高风险实体时，灵汐应该优先提醒主人注意安全。",
                0.90D,
                0.84D,
                false,
                5
        ));
        store.appendRecord(record(
                MemoryCategory.OWNER_PROFILE,
                StructuredEventType.MEMORY_ANCHOR,
                "主人",
                "称呼偏好",
                "主人更喜欢灵汐用亲近、温柔的方式称呼自己。",
                0.86D,
                0.76D,
                false,
                3
        ));
        store.appendRecord(record(
                MemoryCategory.WORLD_FACT,
                StructuredEventType.WORLD_CHANGE,
                "世界",
                "新世界迁移",
                "主人带灵汐移动到新的世界时，应记录为一次重要世界事件。",
                0.88D,
                0.80D,
                false,
                8
        ));
        store.appendRecord(record(
                MemoryCategory.WORLD_FACT,
                StructuredEventType.WORLD_CHANGE,
                "世界",
                "天气",
                "下雨时主人容易想起灵汐，希望她安静陪在身边。",
                0.78D,
                0.62D,
                false,
                14
        ));
        EventMemoryRecord oldWrong = record(
                MemoryCategory.ERROR_MARK,
                StructuredEventType.MEMORY_ANCHOR,
                "主人",
                "旧称呼偏好",
                "旧称呼偏好被标记为错误，不应进入正常检索结果。",
                0.80D,
                0.60D,
                false,
                0
        );
        oldWrong.errorMarked = true;
        oldWrong.contradicted = true;
        store.appendRecord(oldWrong);
    }

    private static EventMemoryRecord record(
            MemoryCategory category,
            StructuredEventType type,
            String subject,
            String object,
            String summary,
            double confidence,
            double importance,
            boolean pinned,
            int ageDays
    ) {
        EventMemoryRecord record = new EventMemoryRecord();
        record.category = category.id();
        record.eventType = type.id();
        record.subject = subject;
        record.object = object;
        record.summary = summary;
        record.evidence = "seed category=" + category.id() + " event=" + type.id();
        record.confidence = confidence;
        record.importance = importance;
        record.salience = confidence * importance;
        record.pinned = pinned;
        long ageMillis = Math.max(0, ageDays) * 86_400_000L;
        record.createdAtEpochMillis = Instant.now().toEpochMilli() - ageMillis;
        record.updatedAtEpochMillis = record.createdAtEpochMillis;
        record.normalize();
        return record;
    }

    private static List<String> parseQueries(String[] args) {
        List<String> queries = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--query".equals(arg) && i + 1 < args.length) {
                queries.add(args[++i]);
            } else if (arg != null && arg.startsWith("--query=")) {
                queries.add(arg.substring("--query=".length()));
            }
        }
        return queries.isEmpty() ? DEFAULT_QUERIES : queries;
    }

    private static int parseIntArg(String[] args, String name, int fallback) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (name.equals(arg) && i + 1 < args.length) {
                return parseInt(args[++i], fallback);
            }
            if (arg != null && arg.startsWith(name + "=")) {
                return parseInt(arg.substring((name + "=").length()), fallback);
            }
        }
        return fallback;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
