package com.yunchen.maidsoulcore.core.test;

import com.yunchen.maidsoulcore.core.event.StructuredEventType;
import com.yunchen.maidsoulcore.core.memory.EventMemoryRecord;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.memory.MemoryCategory;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MemoryArchitectureSmokeMain {
    private MemoryArchitectureSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path root = Path.of("build", "tmp", "maidsoulcore-memory-architecture-smoke");
        Path life = root.resolve("life.json");
        Files.createDirectories(root);
        for (String name : List.of(
                "life.json", "raw_events.json", "facts.json", "relations.json", "promises.json",
                "anchors.json", "repair_records.json", "owner_profile.json", "maid_self.json",
                "world_facts.json", "memory_graph.json"
        )) {
            Files.deleteIfExists(root.resolve(name));
        }

        LifeMemoryStore store = new LifeMemoryStore(life);
        store.appendRecord(record(MemoryCategory.MEMORY_ANCHOR, StructuredEventType.MEMORY_ANCHOR,
                "主人", "灵汐", "主人第一次绑定灵魂核心时选择了灵汐。", "主人明确要求记住第一次绑定。", 0.96D, 0.95D));
        store.appendRecord(record(MemoryCategory.PROMISE, StructuredEventType.PROMISE,
                "主人", "新世界同行", "主人承诺去新的世界也会带上灵汐。", "主人明确说会带上灵汐。", 0.98D, 0.94D));
        store.appendRecord(record(MemoryCategory.OWNER_PROFILE, StructuredEventType.MEMORY_ANCHOR,
                "主人", "回复偏好", "主人喜欢灵汐温柔一点，也可以稍微粘人一点。", "主人明确表达偏好。", 0.91D, 0.86D));
        store.appendRecord(record(MemoryCategory.REPAIR_RECORD, StructuredEventType.APOLOGY,
                "主人", "灵汐", "主人为说话太重向灵汐道歉。", "主人明确道歉。", 0.92D, 0.82D));
        EventMemoryRecord wrong = record(MemoryCategory.ERROR_MARK, StructuredEventType.MEMORY_ANCHOR,
                "主人", "错误事实", "错误事实不应进入正常检索。", "测试错误标记。", 0.90D, 0.80D);
        wrong.errorMarked = true;
        wrong.contradicted = true;
        store.appendRecord(wrong);

        String memoryText = store.searchText("第一次绑定灵魂核心 新世界 温柔粘人", 8);
        StringBuilder report = new StringBuilder();
        append(report, "# Memory Architecture Smoke Report");
        append(report, "");
        append(report, "## Projection Files");
        for (String name : List.of(
                "raw_events.json", "facts.json", "relations.json", "promises.json",
                "anchors.json", "repair_records.json", "owner_profile.json", "memory_graph.json"
        )) {
            Path file = root.resolve(name);
            append(report, "- " + name + " exists=" + Files.exists(file) + " size=" + (Files.exists(file) ? Files.size(file) : 0));
        }
        append(report, "");
        append(report, "## Search Text");
        append(report, codeBlock(memoryText));

        Path output = Path.of("build", "reports", "maidsoulcore", "memory-architecture-smoke.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static EventMemoryRecord record(
            MemoryCategory category,
            StructuredEventType type,
            String subject,
            String object,
            String summary,
            String evidence,
            double confidence,
            double importance
    ) {
        EventMemoryRecord record = new EventMemoryRecord();
        record.category = category.id();
        record.eventType = type.id();
        record.subject = subject;
        record.object = object;
        record.summary = summary;
        record.evidence = evidence;
        record.confidence = confidence;
        record.importance = importance;
        record.salience = confidence * importance;
        record.pinned = category == MemoryCategory.MEMORY_ANCHOR || category == MemoryCategory.PROMISE;
        record.normalize();
        return record;
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String codeBlock(String text) {
        return "```text\n" + text + "\n```";
    }
}
