package com.maidsoul.brain;

import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.MemoryRuntime;
import com.maidsoul.brain.memory.MemoryType;
import com.maidsoul.brain.memory.StructuredMemoryEvent;
import com.maidsoul.brain.memory.v2.MemoryGraphSnapshot;
import com.maidsoul.brain.memory.v2.MemoryV2Store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 记忆图谱 JSON 烟测。
 *
 * <p>图谱层面向外部渲染器，所以这里重点验证稳定 JSON 契约：
 * nodes/edges/counts 必须存在，结构化 tags、paragraph 证据和关系边必须能被导出。</p>
 */
public final class MemoryGraphSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("").toAbsolutePath().resolve("out"), "memory-graph-smoke-");
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
        store.ingestText(
                "graph:preference:1",
                "structured",
                "prototype-world",
                "user",
                "User prefers direct but gentle tone.",
                List.of("prototype-owner", "user"),
                List.of("user_profile", "preference", "boundary"),
                "source=graph-smoke",
                8
        );
        store.ingestText(
                "graph:relationship:1",
                "structured",
                "prototype-world",
                "user",
                "User and maid confirmed a repaired trust moment.",
                List.of("prototype-owner", "user", "prototype-jiuhu"),
                List.of("relationship_event", "repair_debt"),
                "source=graph-smoke",
                8
        );

        MemoryGraphSnapshot snapshot = store.graphSnapshot("boundary", 20);
        require(snapshot.paragraphCount() >= 2, "paragraph count");
        require(!snapshot.nodes().isEmpty(), "graph nodes");
        require(!snapshot.edges().isEmpty(), "graph edges");
        require(snapshot.nodes().stream().anyMatch(node -> "tag".equals(node.kind()) && "boundary".equals(node.label())), "boundary tag node");
        require(snapshot.nodes().stream().anyMatch(node -> "paragraph".equals(node.kind())), "paragraph node");
        require(snapshot.edges().stream().anyMatch(edge -> "tagged_as".equals(edge.label())), "tag edge");

        String json = store.exportGraphJson("boundary", 20);
        require(json.contains("\"nodes\""), "json nodes");
        require(json.contains("\"edges\""), "json edges");
        require(json.contains("\"paragraphCount\""), "json counts");
        require(json.contains("boundary"), "json tag content");
        require(store.debugGraph("boundary", 20).contains("Memory Graph Snapshot"), "human graph");
        Path exported = store.writeGraphJson("boundary", 20, root.resolve("graph_snapshot.json"));
        require(Files.exists(exported), "graph json file");
        require(Files.readString(exported).contains("\"edges\""), "graph json file content");

        MemoryRuntime runtime = new MemoryRuntime(config);
        runtime.observeStructuredMemory(new StructuredMemoryEvent(
                MemoryType.PREFERENCE,
                "user_profile",
                "user",
                "Runtime graph export keeps structured memory visible.",
                4,
                List.of("preference", "boundary"),
                "graph-smoke"
        ));
        require(runtime.exportMemoryGraphJson("structured", 20).contains("\"nodes\""), "runtime graph json");
        require(runtime.debugMemoryGraph("structured", 20).contains("Memory Graph Snapshot"), "runtime graph text");
        Path runtimeExported = runtime.writeMemoryGraphJson("structured", 20, root.resolve("runtime_graph_snapshot.json"));
        require(Files.exists(runtimeExported), "runtime graph json file");

        System.out.println("MEMORY_GRAPH_SMOKE_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
