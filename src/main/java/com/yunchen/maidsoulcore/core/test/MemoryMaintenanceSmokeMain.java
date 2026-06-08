package com.yunchen.maidsoulcore.core.test;

import com.yunchen.maidsoulcore.core.event.StructuredEventType;
import com.yunchen.maidsoulcore.core.memory.EventMemoryRecord;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.memory.MemoryCategory;
import com.yunchen.maidsoulcore.core.memory.MemoryMaintenanceService;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class MemoryMaintenanceSmokeMain {
    private MemoryMaintenanceSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path root = Path.of("build", "tmp", "maidsoulcore-memory-maintenance-smoke");
        Path memoryFile = root.resolve("life.json");
        Files.createDirectories(root);
        Files.deleteIfExists(memoryFile);

        LifeMemoryStore store = new LifeMemoryStore(memoryFile);
        store.appendRecord(record(
                MemoryCategory.RELATION_EVENT.id(),
                StructuredEventType.AFFECTION.id(),
                "主人",
                "灵汐",
                "主人认真说喜欢灵汐，灵汐把它当作亲近事件记住。",
                "planner:event=affection confidence=0.93",
                0.93D,
                0.82D,
                0
        ));
        store.appendRecord(record(
                MemoryCategory.RELATION_EVENT.id(),
                StructuredEventType.AFFECTION.id(),
                "主人",
                "灵汐",
                "主人认真说喜欢灵汐，灵汐把它当作亲近事件记住。",
                "planner:event=affection confidence=0.91 duplicate",
                0.91D,
                0.80D,
                0
        ));
        store.appendRecord(record(
                MemoryCategory.PROMISE.id(),
                StructuredEventType.PROMISE.id(),
                "主人",
                "夜晚陪伴",
                "主人承诺晚上回来陪灵汐聊天。",
                "planner:event=promise object=夜晚陪伴",
                0.95D,
                0.94D,
                0
        ));
        store.appendRecord(record(
                MemoryCategory.MEMORY_ANCHOR.id(),
                StructuredEventType.MEMORY_ANCHOR.id(),
                "灵汐",
                "第一次绑定",
                "灵魂核心第一次绑定到灵汐身上。",
                "world:event=maid_registered",
                0.98D,
                0.98D,
                0
        ));
        store.appendRecord(record(
                MemoryCategory.OWNER_PROFILE.id(),
                StructuredEventType.MEMORY_ANCHOR.id(),
                "主人",
                "称呼偏好",
                "主人希望灵汐称呼自己为主人。",
                "planner:profile object=称呼偏好 confidence=0.88",
                0.88D,
                0.72D,
                1
        ));
        store.appendRecord(record(
                MemoryCategory.ERROR_MARK.id(),
                StructuredEventType.MEMORY_ANCHOR.id(),
                "主人",
                "称呼偏好",
                "planner 判定旧称呼偏好需要复核，暂时不要作为稳定事实使用。",
                "planner:category=error_mark object=称呼偏好 confidence=0.78",
                0.78D,
                0.62D,
                1
        ));
        store.appendRecord(record(
                MemoryCategory.WORLD_FACT.id(),
                StructuredEventType.WORLD_CHANGE.id(),
                "世界",
                "天气",
                "曾经下过一场雨。",
                "world:weather=rain",
                0.74D,
                0.35D,
                21
        ));

        MemoryMaintenanceService service = new MemoryMaintenanceService();
        MemoryMaintenanceService.MaintenanceReport maintenance = service.maintain(store);
        List<LifeMemoryStore.MemoryEpisode> episodes = store.allEpisodes().stream()
                .sorted(Comparator
                        .comparing((LifeMemoryStore.MemoryEpisode e) -> e.category == null ? "" : e.category)
                        .thenComparing(e -> e.subject == null ? "" : e.subject)
                        .thenComparing(e -> e.object == null ? "" : e.object)
                        .thenComparing(e -> e.summary == null ? "" : e.summary))
                .toList();

        StringBuilder report = new StringBuilder();
        append(report, "# Memory Maintenance Smoke Report");
        append(report, "");
        append(report, "## 维护报告");
        append(report, "");
        append(report, "- 扫描条目: " + maintenance.scanned());
        append(report, "- 精确重复: " + maintenance.exactDuplicates());
        append(report, "- 合并次数: " + maintenance.merged());
        append(report, "- 降权条目: " + maintenance.degraded());
        append(report, "- 固化条目: " + maintenance.pinned());
        append(report, "- 错误标记: " + maintenance.errorMarked());
        append(report, "");
        append(report, "## 维护后的记忆");
        append(report, "");
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            append(report, "- category=" + episode.category
                    + " event=" + episode.eventType
                    + " subject=" + episode.subject
                    + " object=" + episode.object
                    + " confidence=" + fmt(episode.confidence)
                    + " salience=" + fmt(episode.salience)
                    + " pinned=" + episode.pinned
                    + " errorMarked=" + episode.errorMarked
                    + " mergeCount=" + episode.mergeCount
                    + " summary=" + episode.summary);
        }
        append(report, "");
        append(report, "## 输出文件");
        append(report, "");
        append(report, "- memory=" + memoryFile.toAbsolutePath());

        Path output = Path.of("build", "reports", "maidsoulcore", "memory-maintenance-smoke.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static EventMemoryRecord record(
            String category,
            String eventType,
            String subject,
            String object,
            String summary,
            String evidence,
            double confidence,
            double importance,
            int ageDays
    ) {
        EventMemoryRecord record = new EventMemoryRecord();
        record.category = category;
        record.eventType = eventType;
        record.subject = subject;
        record.object = object;
        record.summary = summary;
        record.evidence = evidence;
        record.confidence = confidence;
        record.importance = importance;
        record.salience = importance * confidence;
        long ageMillis = Math.max(0L, ageDays) * 86_400_000L;
        record.createdAtEpochMillis = Instant.now().toEpochMilli() - ageMillis;
        record.updatedAtEpochMillis = record.createdAtEpochMillis;
        record.normalize();
        return record;
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
