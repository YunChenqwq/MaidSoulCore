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
import java.util.Comparator;
import java.util.List;

public final class LifeMemoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final List<MemoryEpisode> episodes = new ArrayList<>();

    public LifeMemoryStore(Path path) {
        this.path = path;
        load();
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
        return query(query, limit).stream()
                .map(e -> "- " + e.summary + " (importance=" + e.importance + ")")
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
    }

    public synchronized List<MemoryEpisode> query(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        return episodes.stream()
                .sorted(Comparator.comparingInt((MemoryEpisode e) -> score(e, normalized)).reversed())
                .limit(Math.max(1, limit))
                .toList();
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

    private static int score(MemoryEpisode episode, String query) {
        int score = episode.importance;
        if (!query.isBlank() && episode.summary != null && episode.summary.contains(query)) {
            score += 100;
        }
        return score;
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
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save maid life memory: " + path, e);
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
