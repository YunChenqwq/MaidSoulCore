package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;

import java.util.List;
import java.util.Locale;

/**
 * 记忆图谱快照。
 *
 * <p>它不是新的存储表，而是把当前 A-Memorix 风格的 paragraphs、entities、
 * relations、episodes 聚合成一张可观察的图。这样 GUI 和调试报告看到的是
 * “谁、什么标签、哪段证据、哪条关系”之间的连接，而不是一堆孤立 JSONL。</p>
 */
public record MemoryGraphSnapshot(
        List<Node> nodes,
        List<Edge> edges,
        int paragraphCount,
        int relationCount,
        int entityCount,
        int episodeCount,
        String query,
        String generatedAt
) {
    public MemoryGraphSnapshot {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        query = query == null ? "" : query.trim();
        generatedAt = generatedAt == null ? "" : generatedAt.trim();
    }

    public String toHumanText(int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        StringBuilder builder = new StringBuilder();
        builder.append("Memory Graph Snapshot\n")
                .append("query=").append(query.isBlank() ? "(none)" : query).append('\n')
                .append("generatedAt=").append(generatedAt).append('\n')
                .append("sourceCounts: paragraphs=").append(paragraphCount)
                .append(", relations=").append(relationCount)
                .append(", entities=").append(entityCount)
                .append(", episodes=").append(episodeCount).append("\n\n");

        builder.append("---- Nodes ----\n");
        nodes.stream().limit(safeLimit).forEach(node -> builder.append("- [")
                .append(node.kind()).append("] ")
                .append(node.label())
                .append(" id=").append(node.id())
                .append(" weight=").append(String.format(Locale.ROOT, "%.2f", node.weight()))
                .append(node.detail().isBlank() ? "" : " / " + node.detail())
                .append('\n'));

        builder.append("\n---- Edges ----\n");
        edges.stream().limit(safeLimit).forEach(edge -> builder.append("- ")
                .append(edge.from()).append(" -[")
                .append(edge.label()).append("]-> ")
                .append(edge.to())
                .append(" weight=").append(String.format(Locale.ROOT, "%.2f", edge.weight()))
                .append(edge.evidenceId().isBlank() ? "" : " evidence=" + edge.evidenceId())
                .append('\n'));
        return builder.toString().trim();
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
                .append("\"query\":\"").append(JsonText.escape(query)).append("\",")
                .append("\"generatedAt\":\"").append(JsonText.escape(generatedAt)).append("\",")
                .append("\"paragraphCount\":").append(paragraphCount).append(',')
                .append("\"relationCount\":").append(relationCount).append(',')
                .append("\"entityCount\":").append(entityCount).append(',')
                .append("\"episodeCount\":").append(episodeCount).append(',')
                .append("\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(nodes.get(i).toJson());
        }
        builder.append("],\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(edges.get(i).toJson());
        }
        builder.append("]}");
        return builder.toString();
    }

    public record Node(
            String id,
            String kind,
            String label,
            String detail,
            double weight
    ) {
        public Node {
            id = clean(id);
            kind = clean(kind);
            label = clean(label);
            detail = clean(detail);
            weight = Math.max(0.0, weight);
        }

        String toJson() {
            return "{"
                    + "\"id\":\"" + JsonText.escape(id) + "\","
                    + "\"kind\":\"" + JsonText.escape(kind) + "\","
                    + "\"label\":\"" + JsonText.escape(label) + "\","
                    + "\"detail\":\"" + JsonText.escape(detail) + "\","
                    + "\"weight\":" + weight
                    + "}";
        }
    }

    public record Edge(
            String from,
            String to,
            String label,
            String evidenceId,
            double weight
    ) {
        public Edge {
            from = clean(from);
            to = clean(to);
            label = clean(label);
            evidenceId = clean(evidenceId);
            weight = Math.max(0.0, weight);
        }

        String toJson() {
            return "{"
                    + "\"from\":\"" + JsonText.escape(from) + "\","
                    + "\"to\":\"" + JsonText.escape(to) + "\","
                    + "\"label\":\"" + JsonText.escape(label) + "\","
                    + "\"evidenceId\":\"" + JsonText.escape(evidenceId) + "\","
                    + "\"weight\":" + weight
                    + "}";
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
