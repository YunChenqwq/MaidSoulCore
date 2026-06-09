package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.config.MemoryConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A_Memorix 风格长期记忆核心。
 *
 * <p>这不是简单替换旧 LifeMemory 的“摘要列表”，而是把 A_Memorix 的关键抽象在 Java
 * 原型里复刻出来：paragraph、entity、relation、episode、person profile、external_id
 * 幂等、软删除/保护和聚合检索。第一版使用 JSONL 持久化，避免引入数据库和 embedding
 * 依赖；后续可以把本类替换成 SQLite/向量实现而不改上层 MemoryRuntime。</p>
 */
public final class MemoryV2Store {
    private final Path root;

    public MemoryV2Store(MemoryConfig config) {
        Path configured = Path.of(config.dataRoot());
        Path dataRoot = configured.isAbsolute() ? configured : Path.of("").toAbsolutePath().resolve(configured).normalize();
        Path maidRoot = dataRoot.resolve("maids").resolve(config.maidId());
        this.root = (config.worldId() == null || config.worldId().isBlank() || "*".equals(config.worldId())
                ? maidRoot
                : maidRoot.resolve(config.worldId()))
                .resolve("a_memorix");
    }

    public synchronized MemoryWriteResult ingestText(
            String externalId,
            String sourceType,
            String chatId,
            String role,
            String text,
            List<String> participants,
            List<String> tags,
            String metadata,
            int salience
    ) {
        String clean = MemoryHash.normalize(text);
        if (clean.isBlank()) {
            return MemoryWriteResult.skipped("", "empty_text");
        }
        String id = externalId == null || externalId.isBlank()
                ? "auto:" + MemoryHash.of(sourceType + "\n" + chatId + "\n" + role + "\n" + clean)
                : externalId.trim();
        if (externalRefExists(id)) {
            return MemoryWriteResult.skipped(id, "external_id_exists");
        }

        MemoryParagraph paragraph = MemoryParagraph.create(
                id,
                sourceType,
                chatId,
                role,
                clean,
                join(participants),
                join(tags),
                metadata,
                salience
        );
        if (metadata != null && metadata.contains("protect=true")) {
            paragraph.protectedUntil = MemoryParagraph.now() + 7 * 24 * 3600.0;
        }
        if (metadata != null && metadata.contains("permanent=true")) {
            paragraph.permanent = true;
        }
        appendLine(paragraphPath(), paragraph.toJsonLine());
        appendLine(externalRefsPath(), id + "\t" + paragraph.hash);

        upsertEntities(paragraph);
        upsertRelations(paragraph);
        upsertEpisode(MemoryEpisode.fromParagraph(paragraph));
        return MemoryWriteResult.stored(paragraph.hash);
    }

    public synchronized MemoryWriteResult ingestSummary(
            String externalId,
            String chatId,
            String text,
            List<String> participants,
            List<String> tags,
            String metadata
    ) {
        return ingestText(
                externalId,
                "summary",
                chatId,
                "summary",
                text,
                participants,
                tags,
                metadata,
                6
        );
    }

    public synchronized MemorySearchResult search(String query, String mode, int limit) {
        String effectiveMode = mode == null || mode.isBlank() ? "search" : mode.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(20, limit));
        return switch (effectiveMode) {
            case "search" -> new MemorySearchResult(true, "", "search", searchParagraphs(query, safeLimit));
            case "episode" -> new MemorySearchResult(true, "", "episode", searchEpisodes(query, safeLimit));
            case "time" -> new MemorySearchResult(true, "", "time", searchByRecentTime(safeLimit));
            case "hybrid" -> new MemorySearchResult(true, "", "hybrid", mixed(query, safeLimit, true));
            case "aggregate" -> new MemorySearchResult(true, "", "aggregate", mixed(query, safeLimit, false));
            default -> new MemorySearchResult(false, "不支持的检索模式：" + effectiveMode, effectiveMode, List.of());
        };
    }

    public synchronized String renderPromptBlock(String latestText, int limit) {
        MemorySearchResult result = search(latestText, "aggregate", limit);
        return result.toPromptText(limit);
    }

    public synchronized PersonProfileSnapshot getPersonProfile(String personId, int limit) {
        String pid = personId == null ? "" : personId.trim();
        PersonProfileSnapshot snapshot = new PersonProfileSnapshot();
        snapshot.personId = pid;
        if (pid.isBlank()) {
            snapshot.profileText = "暂无人物画像。";
            return snapshot;
        }

        List<MemoryRelation> relations = allRelations().stream()
                .filter(relation -> !relation.inactive)
                .filter(relation -> containsIgnoreCase(relation.subject, pid) || containsIgnoreCase(relation.object, pid))
                .sorted(Comparator.comparingDouble((MemoryRelation r) -> r.confidence).reversed())
                .limit(Math.max(1, limit))
                .toList();
        List<MemoryParagraph> paragraphs = allParagraphs().stream()
                .filter(paragraph -> !paragraph.deleted)
                .filter(paragraph -> containsIgnoreCase(paragraph.participants, pid) || containsIgnoreCase(paragraph.content, pid))
                .sorted(Comparator.comparingDouble((MemoryParagraph p) -> p.salience + score(p.content, pid)).reversed())
                .limit(Math.max(1, Math.min(4, limit)))
                .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("人物ID: ").append(pid).append('\n');
        if (!relations.isEmpty()) {
            builder.append("关系证据:\n");
            for (MemoryRelation relation : relations) {
                builder.append("- ").append(relation.readable())
                        .append(" (conf=")
                        .append(String.format(Locale.ROOT, "%.2f", relation.confidence))
                        .append(")\n");
            }
        }
        if (!paragraphs.isEmpty()) {
            builder.append("相关片段:\n");
            for (MemoryParagraph paragraph : paragraphs) {
                builder.append("- ").append(paragraph.content).append('\n');
            }
        }
        if (relations.isEmpty() && paragraphs.isEmpty()) {
            builder.append("暂无足够证据形成稳定画像。");
        }
        snapshot.profileText = builder.toString().trim();
        snapshot.evidenceIds = joinEvidence(relations, paragraphs);
        snapshot.updatedAt = MemoryParagraph.now();
        appendLine(profileSnapshotsPath(), snapshot.toJsonLine());
        return snapshot;
    }

    public synchronized MemoryMaintenanceReport maintainCycle() {
        List<MemoryParagraph> paragraphs = new ArrayList<>(allParagraphs());
        double now = MemoryParagraph.now();
        int deduplicated = 0;
        int decayed = 0;
        int solidified = 0;
        int correctionMarked = 0;
        int errorAffected = 0;
        int forgotten = 0;

        Map<String, MemoryParagraph> canonical = new LinkedHashMap<>();
        for (MemoryParagraph paragraph : paragraphs) {
            if (paragraph.deleted) {
                continue;
            }
            String key = canonicalKey(paragraph.content);
            MemoryParagraph keeper = canonical.get(key);
            if (keeper == null) {
                canonical.put(key, paragraph);
                continue;
            }
            MemoryParagraph winner = betterKeeper(keeper, paragraph);
            MemoryParagraph loser = winner == keeper ? paragraph : keeper;
            winner.salience = Math.max(winner.salience, loser.salience);
            winner.accessCount += Math.max(0, loser.accessCount);
            winner.tags = mergeCsv(winner.tags, loser.tags);
            loser.deleted = true;
            loser.updatedAt = now;
            canonical.put(key, winner);
            deduplicated++;
        }
        deduplicated += mergeStructurallySimilar(paragraphs, now);

        for (MemoryParagraph paragraph : paragraphs) {
            if (paragraph.deleted) {
                continue;
            }
            boolean protectedNow = paragraph.permanent || paragraph.protectedUntil > now;
            List<String> tagList = split(paragraph.tags);
            boolean important = tagList.contains("boundary")
                    || tagList.contains("promise")
                    || tagList.contains("relationship_event")
                    || tagList.contains("repair_debt")
                    || tagList.contains("self_memory")
                    || paragraph.salience >= 9;
            if (important && !paragraph.permanent) {
                paragraph.permanent = true;
                paragraph.protectedUntil = Math.max(paragraph.protectedUntil, now + 30 * 24 * 3600.0);
                paragraph.updatedAt = now;
                solidified++;
            }

            if (tagList.contains("correction")) {
                paragraph.tags = mergeCsv(paragraph.tags, "error_mark");
                paragraph.salience = Math.max(paragraph.salience, 8);
                paragraph.protectedUntil = Math.max(paragraph.protectedUntil, now + 14 * 24 * 3600.0);
                paragraph.updatedAt = now;
                correctionMarked++;
            }
            if (tagList.contains("error_mark")) {
                paragraph.salience = Math.max(paragraph.salience, 8);
                paragraph.protectedUntil = Math.max(paragraph.protectedUntil, now + 14 * 24 * 3600.0);
                paragraph.updatedAt = now;
            }

            double ageDays = Math.max(0, (now - paragraph.updatedAt) / 86400.0);
            if (!protectedNow && ageDays >= 7 && paragraph.salience > 1 && paragraph.accessCount == 0) {
                paragraph.salience--;
                paragraph.updatedAt = now;
                decayed++;
            }
            if (!protectedNow && ageDays >= 30 && paragraph.salience <= 2) {
                paragraph.deleted = true;
                paragraph.updatedAt = now;
                forgotten++;
            }
        }
        errorAffected = applyErrorMarks(paragraphs, now);

        writeAll(paragraphPath(), paragraphs.stream().map(MemoryParagraph::toJsonLine).toList());
        MemoryMaintenanceReport report = new MemoryMaintenanceReport(
                paragraphs.size(),
                deduplicated,
                decayed,
                solidified,
                correctionMarked,
                forgotten,
                "维护策略=exact_dedupe+structural_merge+solidify+explicit_correction_mark+error_mark_propagation+age_decay"
                        + "; errorAffected=" + errorAffected
        );
        appendLine(maintenanceLogPath(), report.toJsonLine());
        return report;
    }

    public synchronized String debugDump(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(30, limit));
        StringBuilder builder = new StringBuilder();
        builder.append("A-Memorix v2 记忆库\n")
                .append("root=").append(root).append("\n\n");

        builder.append("---- 检索结果 ----\n");
        MemorySearchResult result = search(query == null ? "" : query, "aggregate", safeLimit);
        builder.append(result.toPromptText(safeLimit)).append("\n\n");

        builder.append("---- 最近段落 ----\n");
        for (MemoryParagraph paragraph : allParagraphs().stream()
                .sorted(Comparator.comparingDouble((MemoryParagraph p) -> p.eventTimeEnd).reversed())
                .limit(safeLimit)
                .toList()) {
            builder.append(paragraph.deleted ? "[deleted] " : "")
                    .append("salience=").append(paragraph.salience)
                    .append(", permanent=").append(paragraph.permanent)
                    .append(", tags=").append(paragraph.tags)
                    .append("\n")
                    .append(paragraph.content)
                    .append("\n\n");
        }

        builder.append("---- 关系图谱 ----\n");
        for (MemoryRelation relation : allRelations().stream()
                .filter(relation -> !relation.inactive)
                .sorted(Comparator.comparingDouble((MemoryRelation r) -> r.confidence).reversed())
                .limit(safeLimit)
                .toList()) {
            builder.append("- ").append(relation.readable())
                    .append(" conf=").append(String.format(Locale.ROOT, "%.2f", relation.confidence))
                    .append("\n");
        }

        builder.append("\n---- 最近维护日志 ----\n");
        List<String> logs = readLines(maintenanceLogPath());
        logs.stream().skip(Math.max(0, logs.size() - safeLimit)).forEach(line -> builder.append(line).append('\n'));
        return builder.toString().trim();
    }

    public synchronized MemoryGraphSnapshot graphSnapshot(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(80, limit));
        String normalizedQuery = query == null ? "" : query.trim();
        List<MemoryParagraph> allParagraphs = allParagraphs();
        List<MemoryRelation> allRelations = allRelations();
        List<MemoryEntity> allEntities = allEntities();
        List<MemoryEpisode> allEpisodes = allEpisodes();

        Map<String, MemoryGraphSnapshot.Node> nodes = new LinkedHashMap<>();
        Map<String, MemoryGraphSnapshot.Edge> edges = new LinkedHashMap<>();
        Set<String> selectedParagraphs = new HashSet<>();

        List<MemoryParagraph> paragraphs = allParagraphs.stream()
                .filter(paragraph -> !paragraph.deleted)
                .filter(paragraph -> normalizedQuery.isBlank()
                        || containsIgnoreCase(paragraph.content, normalizedQuery)
                        || containsIgnoreCase(paragraph.tags, normalizedQuery)
                        || containsIgnoreCase(paragraph.participants, normalizedQuery)
                        || containsIgnoreCase(paragraph.sourceType, normalizedQuery))
                .sorted(Comparator.comparingDouble((MemoryParagraph p) -> p.salience).reversed()
                        .thenComparing(Comparator.comparingDouble((MemoryParagraph p) -> p.eventTimeEnd).reversed()))
                .limit(safeLimit)
                .toList();

        if (paragraphs.isEmpty() && !normalizedQuery.isBlank()) {
            paragraphs = allParagraphs.stream()
                    .filter(paragraph -> !paragraph.deleted)
                    .sorted(Comparator.comparingDouble((MemoryParagraph p) -> p.eventTimeEnd).reversed())
                    .limit(Math.min(12, safeLimit))
                    .toList();
        }

        for (MemoryParagraph paragraph : paragraphs) {
            String paragraphId = nodeId("paragraph", paragraph.hash);
            selectedParagraphs.add(paragraph.hash);
            addNode(nodes, paragraphNode(paragraph));
            for (String participant : split(paragraph.participants)) {
                String personId = nodeId("person", participant);
                addNode(nodes, new MemoryGraphSnapshot.Node(personId, "person", participant, "participant", 3));
                addEdge(edges, personId, paragraphId, "participates_in", paragraph.hash, 0.55);
            }
            for (String tag : split(paragraph.tags)) {
                String tagId = nodeId("tag", tag);
                addNode(nodes, new MemoryGraphSnapshot.Node(tagId, "tag", tag, "memory tag", 2));
                addEdge(edges, paragraphId, tagId, "tagged_as", paragraph.hash, Math.max(0.2, paragraph.salience / 10.0));
            }
            if (!paragraph.sourceType.isBlank()) {
                String sourceId = nodeId("source", paragraph.sourceType);
                addNode(nodes, new MemoryGraphSnapshot.Node(sourceId, "source", paragraph.sourceType, "source type", 1));
                addEdge(edges, sourceId, paragraphId, "emits", paragraph.hash, 0.45);
            }
        }

        for (MemoryRelation relation : allRelations.stream()
                .filter(relation -> !relation.inactive)
                .filter(relation -> selectedParagraphs.contains(relation.sourceParagraph)
                        || normalizedQuery.isBlank()
                        || containsIgnoreCase(relation.subject, normalizedQuery)
                        || containsIgnoreCase(relation.predicate, normalizedQuery)
                        || containsIgnoreCase(relation.object, normalizedQuery))
                .sorted(Comparator.comparingDouble((MemoryRelation r) -> r.confidence).reversed())
                .limit(safeLimit)
                .toList()) {
            String subjectId = nodeId("concept", relation.subject);
            String objectId = nodeId("concept", relation.object);
            addNode(nodes, new MemoryGraphSnapshot.Node(subjectId, "concept", relation.subject, "relation subject", relation.confidence * 5));
            addNode(nodes, new MemoryGraphSnapshot.Node(objectId, "concept", relation.object, "relation object", relation.confidence * 5));
            addEdge(edges, subjectId, objectId, relation.predicate, relation.sourceParagraph, relation.confidence);
            if (!relation.sourceParagraph.isBlank() && selectedParagraphs.contains(relation.sourceParagraph)) {
                addEdge(edges, nodeId("paragraph", relation.sourceParagraph), subjectId, "evidence_for", relation.sourceParagraph, relation.confidence);
            }
        }

        for (MemoryEntity entity : allEntities.stream()
                .filter(entity -> !entity.deleted)
                .filter(entity -> normalizedQuery.isBlank()
                        || containsIgnoreCase(entity.name, normalizedQuery)
                        || containsIgnoreCase(entity.kind, normalizedQuery))
                .sorted(Comparator.comparingInt((MemoryEntity e) -> e.appearanceCount).reversed())
                .limit(Math.max(8, safeLimit / 2))
                .toList()) {
            addNode(nodes, new MemoryGraphSnapshot.Node(
                    nodeId("entity", entity.name),
                    "entity",
                    entity.name,
                    entity.kind + " appearances=" + entity.appearanceCount,
                    entity.appearanceCount
            ));
        }

        for (MemoryEpisode episode : allEpisodes.stream()
                .filter(episode -> intersects(selectedParagraphs, split(episode.evidenceIds))
                        || normalizedQuery.isBlank()
                        || containsIgnoreCase(episode.summary, normalizedQuery)
                        || containsIgnoreCase(episode.keywords, normalizedQuery))
                .sorted(Comparator.comparingDouble((MemoryEpisode e) -> e.eventTimeEnd).reversed())
                .limit(Math.max(6, safeLimit / 3))
                .toList()) {
            String episodeId = nodeId("episode", episode.episodeId);
            addNode(nodes, new MemoryGraphSnapshot.Node(episodeId, "episode", episode.title, clip(episode.summary, 120), episode.confidence * 5));
            for (String evidence : split(episode.evidenceIds)) {
                if (selectedParagraphs.contains(evidence)) {
                    addEdge(edges, episodeId, nodeId("paragraph", evidence), "contains_evidence", evidence, episode.confidence);
                }
            }
        }

        return new MemoryGraphSnapshot(
                List.copyOf(nodes.values()),
                List.copyOf(edges.values()),
                allParagraphs.size(),
                allRelations.size(),
                allEntities.size(),
                allEpisodes.size(),
                normalizedQuery,
                Instant.now().toString()
        );
    }

    public synchronized String debugGraph(String query, int limit) {
        return graphSnapshot(query, limit).toHumanText(Math.max(20, limit * 6));
    }

    public synchronized String exportGraphJson(String query, int limit) {
        return graphSnapshot(query, limit).toJson();
    }

    public synchronized Path writeGraphJson(String query, int limit, Path outputPath) {
        Path target = outputPath == null ? root.resolve("graph_snapshot.json") : outputPath;
        try {
            Files.createDirectories(target.toAbsolutePath().normalize().getParent());
            Files.writeString(target, exportGraphJson(query, limit), StandardCharsets.UTF_8);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("写入记忆图谱 JSON 失败: " + target, e);
        }
    }

    public synchronized MemoryWriteResult maintain(String action, String target, double hours) {
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String key = target == null ? "" : target.trim();
        if (act.isBlank()) {
            return new MemoryWriteResult(false, List.of(), List.of(), "missing_action");
        }
        if ("recycle_bin".equals(act)) {
            return new MemoryWriteResult(true, List.of(), List.of(), "paragraphs=" + allParagraphs().stream().filter(p -> p.deleted).count());
        }

        List<MemoryParagraph> paragraphs = allParagraphs();
        int changed = 0;
        double now = MemoryParagraph.now();
        for (MemoryParagraph paragraph : paragraphs) {
            if (!key.isBlank() && !paragraph.hash.equals(key) && !containsIgnoreCase(paragraph.content, key)) {
                continue;
            }
            switch (act) {
                case "reinforce" -> {
                    paragraph.accessCount++;
                    paragraph.lastAccessed = now;
                    paragraph.salience = Math.min(10, paragraph.salience + 1);
                    changed++;
                }
                case "protect", "freeze" -> {
                    paragraph.protectedUntil = now + Math.max(1.0, hours) * 3600.0;
                    paragraph.permanent = "freeze".equals(act) || paragraph.permanent;
                    changed++;
                }
                case "forget" -> {
                    if (!paragraph.permanent && paragraph.protectedUntil < now) {
                        paragraph.deleted = true;
                        changed++;
                    }
                }
                case "restore" -> {
                    paragraph.deleted = false;
                    changed++;
                }
                default -> {
                    return new MemoryWriteResult(false, List.of(), List.of(), "unsupported_action:" + act);
                }
            }
        }
        writeAll(paragraphPath(), paragraphs.stream().map(MemoryParagraph::toJsonLine).toList());
        return new MemoryWriteResult(true, List.of(), List.of(), "changed=" + changed);
    }

    private List<MemorySearchHit> searchParagraphs(String query, int limit) {
        String q = normalize(query);
        return allParagraphs().stream()
                .filter(paragraph -> !paragraph.deleted)
                .map(paragraph -> new MemorySearchHit(
                        "paragraph",
                        paragraph.hash,
                        paragraph.content,
                        score(paragraph.content + " " + paragraph.tags + " " + paragraph.participants, q)
                                + paragraph.salience * 0.4
                                + paragraph.accessCount * 0.08,
                        paragraph.sourceType,
                        paragraph.tags
                ))
                .filter(hit -> q.isBlank() || hit.score() > 0.1)
                .sorted(hitComparator())
                .limit(limit)
                .toList();
    }

    private List<MemorySearchHit> searchEpisodes(String query, int limit) {
        String q = normalize(query);
        return allEpisodes().stream()
                .map(episode -> new MemorySearchHit(
                        "episode",
                        episode.episodeId,
                        episode.title + "\n" + episode.summary,
                        score(episode.title + " " + episode.summary + " " + episode.keywords + " " + episode.participants, q)
                                + episode.confidence,
                        episode.source,
                        episode.keywords
                ))
                .filter(hit -> q.isBlank() || hit.score() > 0.1)
                .sorted(hitComparator())
                .limit(limit)
                .toList();
    }

    private List<MemorySearchHit> searchByRecentTime(int limit) {
        return allParagraphs().stream()
                .filter(paragraph -> !paragraph.deleted)
                .sorted(Comparator.comparingDouble((MemoryParagraph p) -> p.eventTimeEnd).reversed())
                .limit(limit)
                .map(paragraph -> new MemorySearchHit(
                        "paragraph",
                        paragraph.hash,
                        paragraph.content,
                        paragraph.eventTimeEnd,
                        paragraph.sourceType,
                        paragraph.tags
                ))
                .toList();
    }

    private List<MemorySearchHit> mixed(String query, int limit, boolean recentBias) {
        Map<String, MemorySearchHit> bucket = new LinkedHashMap<>();
        addRrf(bucket, searchParagraphs(query, limit * 2), "search", 1.0);
        addRrf(bucket, searchEpisodes(query, limit * 2), "episode", 0.9);
        if (recentBias) {
            addRrf(bucket, searchByRecentTime(limit * 2), "time", 0.45);
        }
        return bucket.values().stream()
                .sorted(hitComparator())
                .limit(limit)
                .toList();
    }

    private static void addRrf(Map<String, MemorySearchHit> bucket, List<MemorySearchHit> hits, String branch, double weight) {
        double k = 60.0;
        int rank = 1;
        for (MemorySearchHit hit : hits) {
            double fusion = weight / (k + rank);
            MemorySearchHit old = bucket.get(hit.type() + ":" + hit.id());
            if (old == null) {
                bucket.put(hit.type() + ":" + hit.id(), new MemorySearchHit(
                        hit.type(),
                        hit.id(),
                        hit.content(),
                        hit.score() + fusion,
                        hit.source(),
                        branch
                ));
            } else {
                bucket.put(hit.type() + ":" + hit.id(), new MemorySearchHit(
                        old.type(),
                        old.id(),
                        old.content(),
                        old.score() + fusion,
                        old.source(),
                        old.metadata() + "," + branch
                ));
            }
            rank++;
        }
    }

    private void upsertEntities(MemoryParagraph paragraph) {
        Map<String, MemoryEntity> entities = new HashMap<>();
        for (MemoryEntity entity : allEntities()) {
            entities.put(entity.name, entity);
        }
        for (String name : candidateEntities(paragraph)) {
            if (name.isBlank()) {
                continue;
            }
            MemoryEntity entity = entities.get(name);
            if (entity == null) {
                entity = MemoryEntity.create(name, entityKind(name, paragraph));
                entities.put(name, entity);
            } else {
                entity.appearanceCount++;
            }
        }
        writeAll(entityPath(), entities.values().stream().map(MemoryEntity::toJsonLine).toList());
    }

    private void upsertRelations(MemoryParagraph paragraph) {
        Map<String, MemoryRelation> relations = new HashMap<>();
        for (MemoryRelation relation : allRelations()) {
            relations.put(relation.hash, relation);
        }
        String actor = paragraph.role == null || paragraph.role.isBlank() ? "unknown" : paragraph.role;
        MemoryRelation said = MemoryRelation.create(actor, "说过", clip(paragraph.content, 80), paragraph.hash, 0.65);
        relations.putIfAbsent(said.hash, said);
        for (String participant : split(paragraph.participants)) {
            MemoryRelation involved = MemoryRelation.create(participant, "参与了", paragraph.sourceType, paragraph.hash, 0.55);
            relations.putIfAbsent(involved.hash, involved);
        }
        List<String> tags = split(paragraph.tags);
        if (tags.contains("preference")) {
            MemoryRelation preference = MemoryRelation.create(actor, "表达偏好", clip(paragraph.content, 80), paragraph.hash, 0.75);
            relations.putIfAbsent(preference.hash, preference);
        }
        if (tags.contains("boundary")) {
            MemoryRelation boundary = MemoryRelation.create(actor, "表达边界", clip(paragraph.content, 80), paragraph.hash, 0.80);
            relations.putIfAbsent(boundary.hash, boundary);
        }
        writeAll(relationPath(), relations.values().stream().map(MemoryRelation::toJsonLine).toList());
    }

    private void upsertEpisode(MemoryEpisode episode) {
        Map<String, MemoryEpisode> episodes = new HashMap<>();
        for (MemoryEpisode item : allEpisodes()) {
            episodes.put(item.episodeId, item);
        }
        episodes.putIfAbsent(episode.episodeId, episode);
        writeAll(episodePath(), episodes.values().stream().map(MemoryEpisode::toJsonLine).toList());
    }

    private boolean externalRefExists(String externalId) {
        if (Files.notExists(externalRefsPath())) {
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(externalRefsPath(), StandardCharsets.UTF_8)) {
            String prefix = externalId + "\t";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("读取 external refs 失败: " + externalRefsPath(), e);
        }
    }

    private List<MemoryParagraph> allParagraphs() {
        return readLines(paragraphPath()).stream().map(MemoryParagraph::fromJsonLine).toList();
    }

    private List<MemoryEntity> allEntities() {
        return readLines(entityPath()).stream().map(MemoryEntity::fromJsonLine).toList();
    }

    private List<MemoryRelation> allRelations() {
        return readLines(relationPath()).stream().map(MemoryRelation::fromJsonLine).toList();
    }

    private List<MemoryEpisode> allEpisodes() {
        return readLines(episodePath()).stream().map(MemoryEpisode::fromJsonLine).toList();
    }

    private List<String> readLines(Path path) {
        if (Files.notExists(path)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.trim();
                if (!clean.isBlank()) {
                    lines.add(clean);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("读取记忆 v2 文件失败: " + path, e);
        }
    }

    private void appendLine(Path path, String line) {
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("写入记忆 v2 文件失败: " + path, e);
        }
    }

    private void writeAll(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("重写记忆 v2 文件失败: " + path, e);
        }
    }

    private List<String> candidateEntities(MemoryParagraph paragraph) {
        List<String> out = new ArrayList<>();
        out.addAll(split(paragraph.participants));
        out.addAll(split(paragraph.tags));
        if (!paragraph.role.isBlank()) {
            out.add(paragraph.role);
        }
        for (String token : paragraph.content.split("[\\s，。！？、,.!?：:；;（）()【】\\[\\]\"']+")) {
            String clean = token.trim();
            if (clean.length() >= 2 && clean.length() <= 16) {
                out.add(clean);
            }
        }
        return out.stream().distinct().limit(40).toList();
    }

    private static String entityKind(String name, MemoryParagraph paragraph) {
        if (split(paragraph.participants).contains(name) || name.equals(paragraph.role)) {
            return "person";
        }
        if (split(paragraph.tags).contains(name)) {
            return "tag";
        }
        return "concept";
    }

    private static double score(String text, String query) {
        String haystack = normalize(text);
        String q = normalize(query);
        if (q.isBlank()) {
            return 0.2;
        }
        double score = 0.0;
        if (haystack.contains(q)) {
            score += 8.0;
        }
        for (String token : q.split("\\s+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                score += 2.0;
            }
        }
        for (int i = 0; i + 1 < q.length(); i++) {
            if (haystack.contains(q.substring(i, i + 2))) {
                score += 0.35;
            }
        }
        return score;
    }

    private static Comparator<MemorySearchHit> hitComparator() {
        return Comparator.comparingDouble(MemorySearchHit::score).reversed()
                .thenComparing(MemorySearchHit::type)
                .thenComparing(MemorySearchHit::id);
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return normalize(text).contains(normalize(needle));
    }

    private static MemoryGraphSnapshot.Node paragraphNode(MemoryParagraph paragraph) {
        return new MemoryGraphSnapshot.Node(
                nodeId("paragraph", paragraph.hash),
                "paragraph",
                clip(paragraph.content, 80),
                "role=" + paragraph.role + " salience=" + paragraph.salience + " tags=" + paragraph.tags,
                paragraph.salience
        );
    }

    private static void addNode(Map<String, MemoryGraphSnapshot.Node> nodes, MemoryGraphSnapshot.Node node) {
        if (node.id().isBlank()) {
            return;
        }
        MemoryGraphSnapshot.Node old = nodes.get(node.id());
        if (old == null || node.weight() > old.weight()) {
            nodes.put(node.id(), node);
        }
    }

    private static void addEdge(
            Map<String, MemoryGraphSnapshot.Edge> edges,
            String from,
            String to,
            String label,
            String evidenceId,
            double weight
    ) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            return;
        }
        MemoryGraphSnapshot.Edge edge = new MemoryGraphSnapshot.Edge(from, to, label, evidenceId, weight);
        edges.putIfAbsent(MemoryHash.of(edge.from() + "\n" + edge.to() + "\n" + edge.label() + "\n" + edge.evidenceId()), edge);
    }

    private static boolean intersects(Set<String> selected, List<String> values) {
        if (selected.isEmpty() || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (selected.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String nodeId(String kind, String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            return "";
        }
        return kind + ":" + MemoryHash.of(value);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList());
    }

    private static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : text.split(",")) {
            String clean = item.trim();
            if (!clean.isBlank()) {
                out.add(clean);
            }
        }
        return out;
    }

    private static String clip(String text, int max) {
        String clean = text == null ? "" : text.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private static String joinEvidence(List<MemoryRelation> relations, List<MemoryParagraph> paragraphs) {
        List<String> ids = new ArrayList<>();
        ids.addAll(relations.stream().map(r -> r.hash).toList());
        ids.addAll(paragraphs.stream().map(p -> p.hash).toList());
        return String.join(",", ids);
    }

    private static MemoryParagraph betterKeeper(MemoryParagraph first, MemoryParagraph second) {
        if (first.permanent != second.permanent) {
            return first.permanent ? first : second;
        }
        if (first.salience != second.salience) {
            return first.salience > second.salience ? first : second;
        }
        return first.createdAt <= second.createdAt ? first : second;
    }

    private static int mergeStructurallySimilar(List<MemoryParagraph> paragraphs, double now) {
        int merged = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            MemoryParagraph left = paragraphs.get(i);
            if (left.deleted || !canStructuralMerge(left)) {
                continue;
            }
            for (int j = i + 1; j < paragraphs.size(); j++) {
                MemoryParagraph right = paragraphs.get(j);
                if (right.deleted || !canStructuralMerge(right)) {
                    continue;
                }
                if (!sameStructuralBucket(left, right)) {
                    continue;
                }
                double similarity = Math.max(
                        ngramJaccard(left.content, right.content, 2),
                        ngramJaccard(left.metadata, right.metadata, 2)
                );
                if (similarity < 0.56D) {
                    continue;
                }
                MemoryParagraph winner = betterKeeper(left, right);
                MemoryParagraph loser = winner == left ? right : left;
                winner.salience = Math.max(winner.salience, loser.salience);
                winner.accessCount += Math.max(0, loser.accessCount);
                winner.tags = mergeCsv(winner.tags, loser.tags);
                winner.metadata = mergeMetadata(winner.metadata, loser.metadata);
                winner.updatedAt = now;
                loser.deleted = true;
                loser.updatedAt = now;
                merged++;
                if (loser == left) {
                    break;
                }
            }
        }
        return merged;
    }

    private static int applyErrorMarks(List<MemoryParagraph> paragraphs, double now) {
        List<MemoryParagraph> marks = paragraphs.stream()
                .filter(paragraph -> !paragraph.deleted)
                .filter(MemoryV2Store::isErrorMark)
                .toList();
        if (marks.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (MemoryParagraph paragraph : paragraphs) {
            if (paragraph.deleted || isErrorMark(paragraph) || split(paragraph.tags).contains("error_affected")) {
                continue;
            }
            for (MemoryParagraph mark : marks) {
                if (!sameErrorTarget(paragraph, mark)) {
                    continue;
                }
                paragraph.tags = mergeCsv(paragraph.tags, "error_affected");
                paragraph.metadata = mergeMetadata(paragraph.metadata, "errorMark=" + mark.hash);
                paragraph.salience = Math.min(paragraph.salience, 2);
                paragraph.updatedAt = now;
                affected++;
                break;
            }
        }
        return affected;
    }

    private static boolean canStructuralMerge(MemoryParagraph paragraph) {
        List<String> tags = split(paragraph.tags);
        if (tags.contains("error_mark") || tags.contains("correction") || tags.contains("error_affected")) {
            return false;
        }
        return tags.contains("user_profile")
                || tags.contains("relationship_event")
                || tags.contains("world_fact")
                || tags.contains("promise")
                || tags.contains("memory_anchor")
                || tags.contains("repair_debt")
                || tags.contains("self_memory")
                || tags.contains("summary");
    }

    private static boolean sameStructuralBucket(MemoryParagraph left, MemoryParagraph right) {
        if (!normalize(left.sourceType).equals(normalize(right.sourceType))) {
            return false;
        }
        Set<String> leftTags = new HashSet<>(split(left.tags));
        Set<String> rightTags = new HashSet<>(split(right.tags));
        leftTags.retainAll(rightTags);
        return leftTags.stream().anyMatch(MemoryV2Store::isStructuralTag);
    }

    private static boolean isStructuralTag(String tag) {
        return switch (tag) {
            case "user_profile", "relationship_event", "world_fact", "promise", "memory_anchor",
                    "repair_debt", "self_memory", "summary" -> true;
            default -> false;
        };
    }

    private static boolean isErrorMark(MemoryParagraph paragraph) {
        List<String> tags = split(paragraph.tags);
        return tags.contains("error_mark") || tags.contains("correction");
    }

    private static boolean sameErrorTarget(MemoryParagraph paragraph, MemoryParagraph mark) {
        if (!sameStructuralBucketLoosely(paragraph, mark)) {
            return false;
        }
        double contentSimilarity = ngramJaccard(paragraph.content, mark.content, 2);
        double metadataSimilarity = ngramJaccard(paragraph.metadata, mark.metadata, 2);
        return Math.max(contentSimilarity, metadataSimilarity) >= 0.42D;
    }

    private static boolean sameStructuralBucketLoosely(MemoryParagraph left, MemoryParagraph right) {
        Set<String> leftTags = new HashSet<>(split(left.tags));
        Set<String> rightTags = new HashSet<>(split(right.tags));
        leftTags.retainAll(rightTags);
        if (leftTags.stream().anyMatch(MemoryV2Store::isStructuralTag)) {
            return true;
        }
        return !left.sourceType.isBlank() && normalize(left.sourceType).equals(normalize(right.sourceType));
    }

    private static double ngramJaccard(String left, String right, int n) {
        String a = normalize(left).replaceAll("\\s+", "");
        String b = normalize(right).replaceAll("\\s+", "");
        if (a.isBlank() || b.isBlank()) {
            return 0.0D;
        }
        if (a.equals(b)) {
            return 1.0D;
        }
        Set<String> leftGrams = ngrams(a, n);
        Set<String> rightGrams = ngrams(b, n);
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
            return 0.0D;
        }
        int intersection = 0;
        for (String gram : leftGrams) {
            if (rightGrams.contains(gram)) {
                intersection++;
            }
        }
        int union = leftGrams.size() + rightGrams.size() - intersection;
        return union <= 0 ? 0.0D : intersection / (double) union;
    }

    private static Set<String> ngrams(String text, int n) {
        Set<String> out = new HashSet<>();
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) {
            return out;
        }
        if (cps.length <= n) {
            out.add(text);
            return out;
        }
        for (int i = 0; i <= cps.length - n; i++) {
            out.add(new String(cps, i, n));
        }
        return out;
    }

    private static String canonicalKey(String content) {
        String clean = normalize(content)
                .replaceAll("[\\p{Punct}，。！？、；：“”‘’（）【】《》…\\s]+", "");
        if (clean.length() > 80) {
            clean = clean.substring(0, 80);
        }
        return clean;
    }

    private static String mergeCsv(String first, String second) {
        Set<String> values = new HashSet<>();
        values.addAll(split(first));
        values.addAll(split(second));
        return String.join(",", values.stream().filter(v -> v != null && !v.isBlank()).sorted().toList());
    }

    private static String mergeMetadata(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isBlank()) {
            return b;
        }
        if (b.isBlank() || a.contains(b)) {
            return a;
        }
        return a + ";" + b;
    }

    private Path paragraphPath() {
        return root.resolve("paragraphs.jsonl");
    }

    private Path entityPath() {
        return root.resolve("entities.jsonl");
    }

    private Path relationPath() {
        return root.resolve("relations.jsonl");
    }

    private Path episodePath() {
        return root.resolve("episodes.jsonl");
    }

    private Path externalRefsPath() {
        return root.resolve("external_refs.tsv");
    }

    private Path profileSnapshotsPath() {
        return root.resolve("person_profile_snapshots.jsonl");
    }

    private Path maintenanceLogPath() {
        return root.resolve("maintenance_log.jsonl");
    }
}
