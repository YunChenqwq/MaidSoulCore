package com.yunchen.maidsoulcore.core.memory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MemoryTriggerScorer {
    private static final double DEFAULT_THRESHOLD = 0.12D;
    private static final double BOOST_FACTOR = 2.5D;

    public TriggerScore score(String context, Iterable<String> memories) {
        Set<String> contextTokens = tokens(context);
        if (contextTokens.isEmpty() || memories == null) {
            return new TriggerScore(0.0D, List.of());
        }

        List<ScoredMemory> scored = new ArrayList<>();
        for (String memory : memories) {
            if (memory == null || memory.isBlank()) {
                continue;
            }
            double similarity = jaccard(contextTokens, tokens(memory));
            if (similarity >= DEFAULT_THRESHOLD) {
                scored.add(new ScoredMemory(memory.trim(), similarity));
            }
        }
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        if (scored.isEmpty()) {
            return new TriggerScore(0.0D, List.of());
        }

        double weighted = scored.stream().limit(3).mapToDouble(ScoredMemory::score).average().orElse(0.0D);
        double triggerScore = Math.min(1.0D, weighted * BOOST_FACTOR);
        List<String> top = scored.stream().limit(2).map(ScoredMemory::text).toList();
        return new TriggerScore(triggerScore, top);
    }

    public TriggerScore score(String context, List<LifeMemoryStore.MemoryEpisode> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return new TriggerScore(0.0D, List.of());
        }
        Set<String> contextTokens = tokens(context);
        if (contextTokens.isEmpty()) {
            return new TriggerScore(0.0D, List.of());
        }
        long now = System.currentTimeMillis();
        List<ScoredMemory> scored = new ArrayList<>();
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if (episode == null || episode.summary == null || episode.summary.isBlank()) {
                continue;
            }
            double similarity = jaccard(contextTokens, tokens(episode.summary));
            double importanceWeight = 0.65D + Math.max(1, Math.min(100, episode.importance)) / 100.0D * 0.45D;
            double recencyWeight = recencyWeight(episode.time, now);
            double score = similarity * importanceWeight * recencyWeight;
            if (score >= DEFAULT_THRESHOLD) {
                scored.add(new ScoredMemory(episode.summary.trim(), score));
            }
        }
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        if (scored.isEmpty()) {
            return new TriggerScore(0.0D, List.of());
        }
        double weighted = scored.stream().limit(3).mapToDouble(ScoredMemory::score).average().orElse(0.0D);
        double triggerScore = Math.min(1.0D, weighted * BOOST_FACTOR);
        List<String> top = scored.stream().limit(2).map(ScoredMemory::text).toList();
        return new TriggerScore(triggerScore, top);
    }

    private static double recencyWeight(String time, long nowMillis) {
        if (time == null || time.isBlank()) {
            return 1.0D;
        }
        try {
            long ageMillis = Math.max(0L, nowMillis - Instant.parse(time).toEpochMilli());
            double ageDays = ageMillis / 86_400_000.0D;
            return 1.0D + 0.30D * Math.max(0.0D, 1.0D - ageDays / 30.0D);
        } catch (DateTimeParseException ignored) {
            return 1.0D;
        }
    }

    private static Set<String> tokens(String text) {
        String clean = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 2 <= clean.length(); i++) {
            result.add(clean.substring(i, i + 2));
        }
        for (int i = 0; i + 3 <= clean.length(); i++) {
            result.add(clean.substring(i, i + 3));
        }
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0D;
        }
        int intersection = 0;
        for (String item : left) {
            if (right.contains(item)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union <= 0 ? 0.0D : (double) intersection / union;
    }

    private record ScoredMemory(String text, double score) {
    }

    public record TriggerScore(double score, List<String> memories) {
    }
}
