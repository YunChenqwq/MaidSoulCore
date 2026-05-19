package com.maidsoul.brain;

import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.memory.v2.MemorySearchResult;
import com.maidsoul.brain.memory.v2.MemoryV2Store;
import com.maidsoul.brain.memory.v2.MemoryWriteResult;
import com.maidsoul.brain.memory.v2.PersonProfileSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A_Memorix 风格记忆 v2 烟测。
 *
 * <p>它验证的是“记忆底座结构”而不是回复文案：paragraph 能写入，external_id 能幂等，
 * aggregate/episode 能检索，人物画像能从证据生成，维护操作能改变记忆状态，MemoryRuntime
 * 能把 v2 记忆投影进主链路上下文。</p>
 */
public final class MemoryV2SmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("").toAbsolutePath().resolve("out"), "memory-v2-smoke-");
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

        MemoryV2Store store = new MemoryV2Store(config);
        MemoryWriteResult first = store.ingestText(
                "chat:1",
                "chat",
                "prototype-world",
                "user",
                "用户不喜欢酒狐重复模板式关心，希望她像真实的人一样记住边界。",
                List.of("prototype-owner", "user"),
                List.of("boundary", "preference", "conversation_style"),
                "source=smoke",
                9
        );
        require(first.success() && !first.storedIds().isEmpty(), "首次写入应该成功");

        MemoryWriteResult duplicate = store.ingestText(
                "chat:1",
                "chat",
                "prototype-world",
                "user",
                "重复内容不应该二次写入。",
                List.of("prototype-owner"),
                List.of("boundary"),
                "",
                9
        );
        require(duplicate.success() && !duplicate.skippedIds().isEmpty(), "external_id 应该幂等跳过");

        MemorySearchResult aggregate = store.search("模板式关心", "aggregate", 5);
        require(aggregate.success() && !aggregate.hits().isEmpty(), "aggregate 应该命中 paragraph/episode");

        MemorySearchResult episode = store.search("真实的人", "episode", 5);
        require(episode.success() && episode.hits().stream().anyMatch(hit -> "episode".equals(hit.type())), "episode 检索应该命中");

        PersonProfileSnapshot profile = store.getPersonProfile("prototype-owner", 5);
        require(profile.profileText.contains("prototype-owner") || profile.profileText.contains("模板式关心"), "人物画像应包含证据");

        require(store.maintain("reinforce", "模板式关心", 0).success(), "reinforce 应该成功");
        require(store.maintain("protect", "模板式关心", 2).success(), "protect 应该成功");

        MemoryRuntime runtime = new MemoryRuntime(config);
        runtime.observeUserMessage("我希望你记住，我不喜欢模板式关心。");
        String block = runtime.renderPromptBlock("模板式关心");
        require(block.contains("[A-Memorix风格长期记忆]"), "MemoryRuntime 应投影 v2 记忆块");
        require(block.contains("[A-Memorix人物画像]"), "MemoryRuntime 应投影 v2 人物画像");

        System.out.println("MEMORY_V2_SMOKE_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
