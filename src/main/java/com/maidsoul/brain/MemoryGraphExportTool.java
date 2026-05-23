package com.maidsoul.brain;

import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.memory.v2.MemoryV2Store;

import java.nio.file.Path;

/**
 * 记忆图谱 JSON 导出工具。
 *
 * <p>外部渲染器不需要启动 Swing GUI，也不需要理解 Java 内部对象；直接运行这个
 * main 方法即可得到稳定的 graph_snapshot.json。参数：
 * query 可选，limit 可选，output 可选。</p>
 */
public final class MemoryGraphExportTool {
    private MemoryGraphExportTool() {
    }

    public static void main(String[] args) {
        Path root = Path.of("").toAbsolutePath();
        String query = args.length >= 1 ? args[0] : "";
        int limit = args.length >= 2 ? parseLimit(args[1]) : 80;
        Path output = args.length >= 3 ? Path.of(args[2]) : null;

        BrainConfig config = BrainConfig.load(root.resolve("config"));
        MemoryV2Store store = new MemoryV2Store(config.memory());
        Path written = store.writeGraphJson(query, limit, output);
        System.out.println(written.toAbsolutePath().normalize());
    }

    private static int parseLimit(String raw) {
        try {
            return Math.max(1, Math.min(200, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 80;
        }
    }
}
