package com.maidsoul.brain;

import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.memory.MemoryType;
import com.maidsoul.brain.memory.StructuredMemoryEvent;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.memory.v2.MemoryV2Store;
import com.maidsoul.brain.memory.v2.MemoryWritePlan;
import com.maidsoul.brain.memory.v2.MemoryWriteStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 结构化记忆写入与维护循环烟测。
 *
 * <p>测试重点是：语义标签来自显式 type/tags，而不是从中文文本里硬猜。</p>
 */
public final class MemoryStrategyMaintenanceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("").toAbsolutePath().resolve("out"), "memory-maintenance-smoke-");
        MemoryConfig config = new MemoryConfig(
                true,
                root.resolve("memory").toString(),
                root.resolve("characters").toString(),
                "prototype-jiuhu",
                "prototype-owner",
                "prototype-world",
                5,
                5,
                5,
                true
        );

        MemoryWriteStrategy strategy = new MemoryWriteStrategy();
        MemoryWritePlan profile = strategy.plan("user", "plain text", MemoryType.DIALOGUE, 4, List.of("user_profile", "boundary"));
        require(profile.shouldStore() && "user_profile".equals(profile.layer()) && profile.tags().contains("boundary"), "profile/boundary plan");

        MemoryWritePlan relation = strategy.plan("user", "plain text", MemoryType.DIALOGUE, 4, List.of("relationship_event"));
        require(relation.shouldStore() && "relationship_event".equals(relation.layer()), "relationship plan");

        MemoryWritePlan repair = strategy.plan("user", "plain text", MemoryType.DIALOGUE, 4, List.of("repair_debt"));
        require(repair.shouldStore() && "repair_debt".equals(repair.layer()), "repair debt plan");

        MemoryWritePlan self = strategy.plan("assistant", "plain text", MemoryType.DIALOGUE, 4, List.of("self_memory"));
        require(self.shouldStore() && "self_memory".equals(self.layer()), "self memory plan");

        MemoryWritePlan notCorrection = strategy.plan("user", "不是讨厌，只是有点突然。", MemoryType.DIALOGUE, 4, List.of("raw_dialogue"));
        require(!notCorrection.tags().contains("correction") && !notCorrection.tags().contains("error_mark"), "natural text should not become correction");

        MemoryV2Store store = new MemoryV2Store(config);
        store.ingestText("chat:dedupe:1", profile.sourceType(), "prototype-world", "user",
                "same structured memory", List.of("prototype-owner", "user"), profile.tags(), profile.metadataSuffix(), profile.salience());
        store.ingestText("chat:dedupe:2", profile.sourceType(), "prototype-world", "user",
                "same structured memory", List.of("prototype-owner", "user"), profile.tags(), profile.metadataSuffix(), profile.salience() - 1);
        store.ingestText("chat:correction:1", "structured", "prototype-world", "user",
                "explicit correction event", List.of("prototype-owner", "user"), List.of("correction"), "source=smoke", 7);

        MemoryMaintenanceReport report = store.maintainCycle();
        require(report.scanned() >= 3, "maintenance scanned");
        require(report.deduplicated() >= 1, "maintenance deduplicated");
        require(report.correctionMarked() >= 1, "maintenance correction marked");
        require(store.debugDump("structured", 8).contains("A-Memorix v2"), "debug dump");

        MemoryRuntime runtime = new MemoryRuntime(config);
        runtime.observeStructuredMemory(new StructuredMemoryEvent(
                MemoryType.PREFERENCE,
                "user_profile",
                "user",
                "User prefers direct but gentle tone.",
                4,
                List.of("preference", "boundary"),
                "smoke"
        ));
        runtime.observeUserMessage("不是讨厌，只是有点突然。");
        String dump = runtime.debugMemoryV2("direct gentle", 8);
        require(dump.contains("A-Memorix v2"), "runtime debug");
        require(runtime.maintainV2().scanned() >= 1, "runtime maintain");

        System.out.println("MEMORY_STRATEGY_MAINTENANCE_SMOKE_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
