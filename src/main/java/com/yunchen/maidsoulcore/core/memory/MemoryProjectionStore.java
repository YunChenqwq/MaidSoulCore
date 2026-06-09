package com.yunchen.maidsoulcore.core.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地记忆投影库。
 *
 * <p>这里参考 metadata-first / graph-first 的思路：life.json 仍然保留为兼容总表，
 * 但写入时同步生成不同用途的本地 JSON 视图。向量不是主数据源，因此这里不依赖
 * embedding；后续如果加向量，只需要把这些投影作为可回填的 metadata。</p>
 */
public final class MemoryProjectionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root;

    public MemoryProjectionStore(Path lifePath) {
        Path parent = lifePath == null ? null : lifePath.getParent();
        this.root = parent == null ? Path.of("memory") : parent;
    }

    public synchronized void rebuild(List<LifeMemoryStore.MemoryEpisode> episodes) {
        List<ProjectionRecord> rawEvents = new ArrayList<>();
        List<ProjectionRecord> facts = new ArrayList<>();
        List<ProjectionRecord> promises = new ArrayList<>();
        List<ProjectionRecord> anchors = new ArrayList<>();
        List<ProjectionRecord> repairs = new ArrayList<>();
        List<ProjectionRecord> ownerProfile = new ArrayList<>();
        List<ProjectionRecord> maidSelf = new ArrayList<>();
        List<ProjectionRecord> worldFacts = new ArrayList<>();
        List<RelationRecord> relations = new ArrayList<>();

        if (episodes != null) {
            for (LifeMemoryStore.MemoryEpisode episode : episodes) {
                if (episode == null || episode.summary == null || episode.summary.isBlank()) {
                    continue;
                }
                ProjectionRecord record = ProjectionRecord.from(episode);
                rawEvents.add(record);
                relations.add(RelationRecord.from(episode));
                String category = normalizeCategory(episode.category);
                switch (category) {
                    case "promise" -> {
                        promises.add(record);
                        facts.add(record);
                    }
                    case "memory_anchor" -> {
                        anchors.add(record);
                        facts.add(record);
                    }
                    case "repair_record" -> repairs.add(record);
                    case "owner_profile" -> {
                        ownerProfile.add(record);
                        facts.add(record);
                    }
                    case "maid_self" -> maidSelf.add(record);
                    case "world_fact" -> {
                        worldFacts.add(record);
                        facts.add(record);
                    }
                    default -> {
                        if (episode.pinned || episode.importance >= 88) {
                            facts.add(record);
                        }
                    }
                }
            }
        }

        writeJson("raw_events.json", rawEvents);
        writeJson("facts.json", facts);
        writeJson("relations.json", relations);
        writeJson("promises.json", promises);
        writeJson("anchors.json", anchors);
        writeJson("repair_records.json", repairs);
        writeJson("owner_profile.json", ownerProfile);
        writeJson("maid_self.json", maidSelf);
        writeJson("world_facts.json", worldFacts);
        writeJson("memory_graph.json", graphPayload(relations));
    }

    private Map<String, Object> graphPayload(List<RelationRecord> relations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, GraphNode> nodeWeights = new LinkedHashMap<>();
        List<RelationRecord> graphEdges = new ArrayList<>();
        for (RelationRecord relation : relations) {
            addNode(nodeWeights, relation.subject, "person", relation.category);
            addNode(nodeWeights, relation.object, "entity", relation.category);
            graphEdges.add(relation);

            String topic = topicNodeId(relation);
            if (!topic.isBlank()
                    && !topic.equals(relation.subject)
                    && !topic.equals(relation.object)) {
                addNode(nodeWeights, topic, "memory_topic", relation.category);
                graphEdges.add(RelationRecord.topicEdge(relation, relation.subject, topic, "mentions"));
                if (!relation.object.isBlank()) {
                    graphEdges.add(RelationRecord.topicEdge(relation, topic, relation.object, "about"));
                }
            }
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (GraphNode entry : nodeWeights.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", entry.id);
            node.put("kind", entry.kind);
            node.put("category", entry.category);
            node.put("weight", entry.weight);
            nodes.add(node);
        }
        payload.put("nodes", nodes);
        payload.put("edges", graphEdges);
        return payload;
    }

    private static void addNode(Map<String, GraphNode> nodes, String id, String kind, String category) {
        if (id == null || id.isBlank()) {
            return;
        }
        GraphNode node = nodes.get(id);
        if (node == null) {
            node = new GraphNode();
            node.id = id;
            node.kind = kind;
            node.category = category == null || category.isBlank() ? "unknown" : category;
            nodes.put(id, node);
        }
        node.weight++;
        if ("unknown".equals(node.category) && category != null && !category.isBlank()) {
            node.category = category;
        }
    }

    private static String topicNodeId(RelationRecord relation) {
        if (relation == null || relation.summary == null || relation.summary.isBlank()) {
            return "";
        }
        String category = relation.category == null || relation.category.isBlank() ? "memory" : relation.category;
        return categoryLabel(category) + "：" + compact(relation.summary, 18);
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "promise" -> "承诺";
            case "memory_anchor" -> "锚点";
            case "owner_profile" -> "画像";
            case "repair_record" -> "修复";
            case "world_fact" -> "世界";
            case "maid_self" -> "自我";
            case "relation_event" -> "关系";
            default -> "记忆";
        };
    }

    private static String compact(String text, int maxCodePoints) {
        String normalized = text == null ? "" : text
                .replaceAll("，关系阶段=.*$", "")
                .replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return "";
        }
        int[] cps = normalized.codePoints().toArray();
        if (cps.length <= maxCodePoints) {
            return normalized;
        }
        return new String(cps, 0, Math.max(1, maxCodePoints)) + "…";
    }

    private void writeJson(String fileName, Object payload) {
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(fileName), GSON.toJson(payload), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save memory projection: " + root.resolve(fileName), e);
        }
    }

    private static String normalizeCategory(String category) {
        return category == null || category.isBlank() ? MemoryCategory.RELATION_EVENT.id() : category;
    }

    public static final class ProjectionRecord {
        public String time;
        public String category;
        public String eventType;
        public String subject;
        public String object;
        public String summary;
        public String evidence;
        public int importance;
        public double confidence;
        public double salience;
        public boolean pinned;
        public boolean contradicted;
        public boolean errorMarked;

        static ProjectionRecord from(LifeMemoryStore.MemoryEpisode episode) {
            ProjectionRecord record = new ProjectionRecord();
            record.time = safe(episode.time);
            record.category = safe(episode.category);
            record.eventType = safe(episode.eventType);
            record.subject = safe(episode.subject);
            record.object = safe(episode.object);
            record.summary = safe(episode.summary);
            record.evidence = safe(episode.evidence);
            record.importance = episode.importance;
            record.confidence = episode.confidence;
            record.salience = episode.salience;
            record.pinned = episode.pinned;
            record.contradicted = episode.contradicted;
            record.errorMarked = episode.errorMarked;
            return record;
        }
    }

    public static final class RelationRecord {
        public String subject;
        public String predicate;
        public String object;
        public String category;
        public String eventType;
        public String summary;
        public String evidence;
        public double confidence;
        public int importance;
        public boolean active;

        static RelationRecord from(LifeMemoryStore.MemoryEpisode episode) {
            RelationRecord record = new RelationRecord();
            record.subject = safe(episode.subject);
            record.predicate = safe(episode.relationPredicate == null || episode.relationPredicate.isBlank()
                    ? episode.eventType
                    : episode.relationPredicate);
            record.object = safe(episode.object);
            record.category = safe(episode.category);
            record.eventType = safe(episode.eventType);
            record.summary = safe(episode.summary);
            record.evidence = safe(episode.evidence);
            record.confidence = episode.confidence;
            record.importance = episode.importance;
            record.active = !episode.errorMarked && !episode.contradicted;
            return record;
        }

        static RelationRecord topicEdge(RelationRecord source, String subject, String object, String predicate) {
            RelationRecord record = new RelationRecord();
            record.subject = safe(subject);
            record.predicate = safe(predicate);
            record.object = safe(object);
            record.category = safe(source.category);
            record.eventType = safe(source.eventType);
            record.summary = safe(source.summary);
            record.evidence = safe(source.evidence);
            record.confidence = source.confidence;
            record.importance = source.importance;
            record.active = source.active;
            return record;
        }
    }

    private static final class GraphNode {
        String id;
        String kind;
        String category;
        int weight;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
