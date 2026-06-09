package com.maidsoul.brain;

import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.memory.v2.MemoryV2Store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 记忆维护循环验收。
 *
 * <p>输入全是结构化 tags，不靠自然语言关键词分类。这里验证两件事：
 * 结构相似的重要记忆能合并；显式 error_mark 能影响目标记忆，避免旧错误继续高权重出现。</p>
 */
public final class MemoryV2MaintenanceSmokeTest {
    private MemoryV2MaintenanceSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("out"), "memory-v2-maintenance-");
        MemoryConfig config = new MemoryConfig(
                true,
                root.toString(),
                root.resolve("characters").toString(),
                "maid-smoke",
                "owner-smoke",
                "world-smoke",
                8,
                8,
                8,
                true
        );
        MemoryV2Store store = new MemoryV2Store(config);
        store.ingestText(
                "sim-1",
                "profile",
                "world-smoke",
                "owner",
                "主人喜欢雨天散步，觉得雨声很安心。",
                List.of("owner-smoke", "owner"),
                List.of("user_profile", "preference"),
                "memoryLayer=user_profile;subject=owner-smoke;object=rain_walk",
                8
        );
        store.ingestText(
                "sim-2",
                "profile",
                "world-smoke",
                "owner",
                "主人很喜欢在雨天散步，因为雨声会让他安心。",
                List.of("owner-smoke", "owner"),
                List.of("user_profile", "preference"),
                "memoryLayer=user_profile;subject=owner-smoke;object=rain_walk",
                7
        );
        store.ingestText(
                "mark-1",
                "profile",
                "world-smoke",
                "planner",
                "主人喜欢雨天散步这条旧记忆是错误的，需要标记为失效。",
                List.of("owner-smoke", "planner"),
                List.of("user_profile", "error_mark"),
                "memoryLayer=user_profile;subject=owner-smoke;object=rain_walk",
                9
        );

        MemoryMaintenanceReport report = store.maintainCycle();
        String dump = store.debugDump("雨天散步", 20);
        if (report.deduplicated() <= 0) {
            throw new IllegalStateException("结构相似记忆没有合并: " + report.toHumanText() + "\n" + dump);
        }
        if (!dump.contains("error_affected") || !report.toHumanText().contains("errorAffected=")) {
            throw new IllegalStateException("error_mark 没有影响目标记忆: " + report.toHumanText() + "\n" + dump);
        }
        System.out.println("MEMORY_V2_MAINTENANCE_SMOKE_OK");
    }
}
