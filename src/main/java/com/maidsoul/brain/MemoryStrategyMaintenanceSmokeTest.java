package com.maidsoul.brain;

import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.memory.MemoryType;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.memory.v2.MemoryV2Store;
import com.maidsoul.brain.memory.v2.MemoryWritePlan;
import com.maidsoul.brain.memory.v2.MemoryWriteStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 记忆写入策略与维护循环烟测。
 *
 * <p>这个测试不依赖模型，只验证“分类写入”和“维护动作”本身：
 * 用户画像、关系事件、角色自我记忆、情绪债务、去重、修正标记、GUI 调试 dump。</p>
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
        MemoryWritePlan profile = strategy.plan("user", "我不喜欢机械模板式关心，希望你自然一点。", MemoryType.PREFERENCE, 4, List.of("preference"));
        require(profile.shouldStore() && "user_profile".equals(profile.layer()) && profile.tags().contains("boundary"), "profile/boundary plan");

        MemoryWritePlan relation = strategy.plan("user", "我想和你在一起，以后我们就是恋人。", MemoryType.RELATION, 4, List.of("relation"));
        require(relation.shouldStore() && "relationship_event".equals(relation.layer()), "relationship plan");

        MemoryWritePlan repair = strategy.plan("user", "刚才我骂你了，对不起，我们和好吧。", MemoryType.EMOTION, 4, List.of("emotion"));
        require(repair.shouldStore() && "repair_debt".equals(repair.layer()), "repair debt plan");

        MemoryWritePlan self = strategy.plan("assistant", "我会记住你的边界。", MemoryType.PROMISE, 4, List.of("promise"));
        require(self.shouldStore() && self.tags().contains("self_memory"), "self memory plan");

        MemoryV2Store store = new MemoryV2Store(config);
        store.ingestText("chat:dedupe:1", profile.sourceType(), "prototype-world", "user",
                "测试去重：用户喜欢直接但自然的回应。", List.of("prototype-owner", "user"), profile.tags(), profile.metadataSuffix(), profile.salience());
        store.ingestText("chat:dedupe:2", profile.sourceType(), "prototype-world", "user",
                "测试去重：用户喜欢直接但自然的回应。", List.of("prototype-owner", "user"), profile.tags(), profile.metadataSuffix(), profile.salience() - 1);
        store.ingestText("chat:correction:1", "chat", "prototype-world", "user",
                "不对，刚才那条记错了，其实我是在测试记忆维护。", List.of("prototype-owner", "user"), List.of("correction"), "source=smoke", 7);

        MemoryMaintenanceReport report = store.maintainCycle();
        require(report.scanned() >= 3, "maintenance scanned");
        require(report.deduplicated() >= 1, "maintenance deduplicated");
        require(report.correctionMarked() >= 1, "maintenance correction marked");
        require(store.debugDump("自然回应", 8).contains("A-Memorix v2"), "debug dump");

        MemoryRuntime runtime = new MemoryRuntime(config);
        runtime.observeUserMessage("请记住，我不喜欢机械模板式关心，希望自然一点。");
        require(runtime.debugMemoryV2("模板式关心", 5).contains("A-Memorix v2"), "runtime debug");
        require(runtime.maintainV2().scanned() >= 1, "runtime maintain");

        System.out.println("MEMORY_STRATEGY_MAINTENANCE_SMOKE_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
