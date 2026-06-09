package com.yunchen.maidsoulcore.core.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LifeMemoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final List<MemoryEpisode> episodes = new ArrayList<>();
    private final MemoryLocalRetriever localRetriever = new MemoryLocalRetriever();
    private final MemoryProjectionStore projectionStore;

    public LifeMemoryStore(Path path) {
        this.path = path;
        this.projectionStore = new MemoryProjectionStore(path);
        load();
        projectionStore.rebuild(episodes);
    }

    public synchronized void append(String summary, int importance) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        episodes.add(new MemoryEpisode(
                Instant.now().toString(),
                summary.trim(),
                Math.max(1, Math.min(100, importance))
        ));
        save();
    }

    public synchronized void appendRecord(EventMemoryRecord record) {
        if (record == null || record.summary == null || record.summary.isBlank()) {
            return;
        }
        record.normalize();
        episodes.add(MemoryEpisode.fromRecord(record));
        save();
    }

    public synchronized String searchText(String query, int limit) {
        List<MemorySearchResult> results = queryDetailed(query, limit);
        if (results.isEmpty()) {
            return "";
        }
        List<String> facts = new ArrayList<>();
        List<String> promises = new ArrayList<>();
        List<String> anchors = new ArrayList<>();
        List<String> repairs = new ArrayList<>();
        List<String> profile = new ArrayList<>();
        List<String> tone = new ArrayList<>();
        for (MemorySearchResult result : results) {
            MemoryEpisode episode = result.episode();
            String line = "- " + episode.summary
                    + evidenceSuffix(episode)
                    + " (score=" + String.format(java.util.Locale.ROOT, "%.3f", result.score())
                    + ", category=" + blankToDefault(episode.category, "unknown")
                    + ", confidence=" + String.format(java.util.Locale.ROOT, "%.2f", episode.confidence)
                    + ")";
            switch (blankToDefault(episode.category, "")) {
                case "promise" -> promises.add(line);
                case "memory_anchor" -> anchors.add(line);
                case "repair_record" -> repairs.add(line);
                case "owner_profile" -> profile.add(line);
                case "world_fact", "maid_self" -> facts.add(line);
                default -> {
                    if (episode.pinned || episode.importance >= 88) {
                        facts.add(line);
                    } else {
                        tone.add(line);
                    }
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[记忆使用边界]\n")
                .append("- 只能引用下方已列出的事实、承诺、画像和锚点。\n")
                .append("- 没列出的动作、地点、物品、天气、身体接触、额外台词都视为未知，不能补全。\n")
                .append("- 细节不足时，应说只记得这件事很重要。\n");
        appendSection(builder, "已确认事实", facts);
        appendSection(builder, "承诺和未来约定", promises);
        appendSection(builder, "关系锚点", anchors);
        appendSection(builder, "主人画像", profile);
        appendSection(builder, "修复记录", repairs);
        appendSection(builder, "关系氛围参考", tone);
        return builder.toString().trim();
    }

    public synchronized List<MemoryEpisode> query(String query, int limit) {
        return queryDetailed(query, limit).stream()
                .map(MemorySearchResult::episode)
                .toList();
    }

    public synchronized List<MemorySearchResult> queryDetailed(String query, int limit) {
        return localRetriever.search(episodes, query, limit);
    }

    public synchronized List<String> querySummaries(String query, int limit) {
        return query(query, limit).stream().map(e -> e.summary).toList();
    }

    public synchronized List<MemoryEpisode> allEpisodes() {
        return new ArrayList<>(episodes);
    }

    public synchronized void replaceAllEpisodes(List<MemoryEpisode> replacement) {
        episodes.clear();
        if (replacement != null) {
            episodes.addAll(replacement);
        }
        save();
    }

    private void load() {
        try {
            if (Files.notExists(path)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                MemoryEpisode[] loaded = GSON.fromJson(reader, MemoryEpisode[].class);
                if (loaded != null) {
                    episodes.addAll(List.of(loaded));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read maid life memory: " + path, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(episodes), StandardCharsets.UTF_8);
            projectionStore.rebuild(episodes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save maid life memory: " + path, e);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String evidenceSuffix(MemoryEpisode episode) {
        if (episode.evidence == null || episode.evidence.isBlank()) {
            return "";
        }
        return "；证据=" + episode.evidence;
    }

    private static void appendSection(StringBuilder builder, String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append("[").append(title).append("]\n");
        for (String line : lines) {
            builder.append(line).append("\n");
        }
    }

    public static final class MemoryEpisode {
        public String time;
        public String summary;
        public int importance;
        public String category;
        public String subject;
        public String object;
        public String eventType;
        public String knowledgeType;
        public String sourceKind;
        public String relationPredicate;
        public String evidence;
        public String vectorText;
        public String vectorState;
        public String writePolicy;
        public double confidence;
        public double salience;
        public boolean pinned;
        public boolean contradicted;
        public boolean errorMarked;
        public int mergeCount;

        public MemoryEpisode(String time, String summary, int importance) {
            this.time = time;
            this.summary = summary;
            this.importance = importance;
            this.category = "";
            this.subject = "";
            this.object = "";
            this.eventType = "";
            this.knowledgeType = "";
            this.sourceKind = "";
            this.relationPredicate = "";
            this.evidence = "";
            this.vectorText = "";
            this.vectorState = "";
            this.writePolicy = "";
            this.confidence = 0.0D;
            this.salience = importance / 100.0D;
            this.pinned = false;
            this.contradicted = false;
            this.errorMarked = false;
            this.mergeCount = 1;
        }

        public static MemoryEpisode fromRecord(EventMemoryRecord record) {
            MemoryEpisode episode = new MemoryEpisode(
                    record.timeIso(),
                    record.summary,
                    Math.max(1, Math.min(100, (int) Math.round(record.importance * 100.0D)))
            );
            episode.category = record.category;
            episode.subject = record.subject;
            episode.object = record.object;
            episode.eventType = record.eventType;
            episode.knowledgeType = record.knowledgeType;
            episode.sourceKind = record.sourceKind;
            episode.relationPredicate = record.relationPredicate;
            episode.evidence = record.evidence;
            episode.vectorText = record.vectorText;
            episode.vectorState = record.vectorState;
            episode.writePolicy = record.writePolicy;
            episode.confidence = record.confidence;
            episode.salience = record.salience;
            episode.pinned = record.pinned;
            episode.contradicted = record.contradicted;
            episode.errorMarked = record.errorMarked;
            episode.mergeCount = record.mergeCount;
            return episode;
        }
    }
}
