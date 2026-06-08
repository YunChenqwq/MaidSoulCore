package com.yunchen.maidsoulcore.core.memory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MemoryMaintenanceService {
    public MaintenanceReport maintain(LifeMemoryStore store) {
        if (store == null) {
            return new MaintenanceReport(0, 0, 0, 0, 0, 0);
        }
        List<LifeMemoryStore.MemoryEpisode> all = store.allEpisodes();
        List<LifeMemoryStore.MemoryEpisode> normalized = new ArrayList<>();
        for (LifeMemoryStore.MemoryEpisode episode : all) {
            if (episode == null || episode.summary == null || episode.summary.isBlank()) {
                continue;
            }
            normalizeEpisode(episode);
            normalized.add(episode);
        }

        MergeResult merged = mergeDuplicates(normalized);
        int degraded = applyDecay(merged.episodes());
        int pinned = pinImportant(merged.episodes());
        int errorMarked = preservePlannerErrorMarks(merged.episodes());
        store.replaceAllEpisodes(merged.episodes());
        return new MaintenanceReport(
                all.size(),
                merged.duplicates(),
                merged.merged(),
                degraded,
                pinned,
                errorMarked
        );
    }

    public MaintenanceReport compactExactDuplicates(LifeMemoryStore store) {
        return maintain(store);
    }

    private static MergeResult mergeDuplicates(List<LifeMemoryStore.MemoryEpisode> episodes) {
        Map<String, LifeMemoryStore.MemoryEpisode> unique = new LinkedHashMap<>();
        int duplicates = 0;
        int merged = 0;
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            String key = keyOf(episode);
            LifeMemoryStore.MemoryEpisode previous = unique.get(key);
            if (previous == null) {
                unique.put(key, episode);
                continue;
            }
            duplicates++;
            merged++;
            previous.importance = Math.max(previous.importance, episode.importance);
            previous.confidence = Math.max(previous.confidence, episode.confidence);
            previous.salience = Math.max(previous.salience, episode.salience);
            previous.pinned = previous.pinned || episode.pinned;
            previous.contradicted = previous.contradicted || episode.contradicted;
            previous.errorMarked = previous.errorMarked || episode.errorMarked;
            previous.mergeCount = Math.max(1, previous.mergeCount) + Math.max(1, episode.mergeCount);
            if (previous.evidence == null || previous.evidence.isBlank()) {
                previous.evidence = episode.evidence;
            } else if (episode.evidence != null && !episode.evidence.isBlank() && !previous.evidence.contains(episode.evidence)) {
                previous.evidence = previous.evidence + " | " + episode.evidence;
            }
        }
        return new MergeResult(new ArrayList<>(unique.values()), duplicates, merged);
    }

    private static int applyDecay(List<LifeMemoryStore.MemoryEpisode> episodes) {
        int degraded = 0;
        long now = System.currentTimeMillis();
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if (episode.pinned) {
                continue;
            }
            double before = episode.salience;
            double ageDays = ageDays(episode.time, now);
            double decayRate = switch (category(episode)) {
                case "short_context" -> 0.08D;
                case "owner_profile", "relation_event" -> 0.018D;
                case "repair_record" -> 0.012D;
                case "world_fact" -> 0.010D;
                default -> 0.020D;
            };
            episode.salience = clamp01(episode.importance / 100.0D * Math.max(0.25D, Math.exp(-decayRate * ageDays)) * confidenceOrDefault(episode));
            if (episode.salience + 0.001D < before) {
                degraded++;
            }
        }
        return degraded;
    }

    private static int pinImportant(List<LifeMemoryStore.MemoryEpisode> episodes) {
        int pinned = 0;
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            boolean shouldPin = episode.importance >= 88
                    || "promise".equals(category(episode))
                    || "memory_anchor".equals(category(episode));
            if (shouldPin && !episode.pinned) {
                episode.pinned = true;
                pinned++;
            }
        }
        return pinned;
    }

    private static int preservePlannerErrorMarks(List<LifeMemoryStore.MemoryEpisode> episodes) {
        int marked = 0;
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if ("error_mark".equals(category(episode)) && !episode.errorMarked) {
                episode.contradicted = true;
                episode.errorMarked = true;
                marked++;
            }
        }
        return marked;
    }

    private static void normalizeEpisode(LifeMemoryStore.MemoryEpisode episode) {
        if (episode.category == null) {
            episode.category = "";
        }
        if (episode.subject == null) {
            episode.subject = "";
        }
        if (episode.object == null) {
            episode.object = "";
        }
        if (episode.eventType == null) {
            episode.eventType = "";
        }
        if (episode.knowledgeType == null || episode.knowledgeType.isBlank()) {
            episode.knowledgeType = "structured";
        }
        if (episode.sourceKind == null || episode.sourceKind.isBlank()) {
            episode.sourceKind = "dialogue_event";
        }
        if (episode.relationPredicate == null || episode.relationPredicate.isBlank()) {
            episode.relationPredicate = episode.eventType;
        }
        if (episode.evidence == null) {
            episode.evidence = "";
        }
        if (episode.vectorText == null || episode.vectorText.isBlank()) {
            episode.vectorText = (episode.subject + " " + episode.relationPredicate + " " + episode.object + "\n" + episode.summary).trim();
        }
        if (episode.vectorState == null || episode.vectorState.isBlank()) {
            episode.vectorState = "none";
        }
        if (episode.writePolicy == null) {
            episode.writePolicy = "";
        }
        episode.importance = Math.max(1, Math.min(100, episode.importance));
        episode.confidence = confidenceOrDefault(episode);
        episode.salience = episode.salience <= 0.0D ? episode.importance / 100.0D * episode.confidence : clamp01(episode.salience);
        episode.mergeCount = Math.max(1, episode.mergeCount);
    }

    private static String keyOf(LifeMemoryStore.MemoryEpisode episode) {
        return category(episode)
                + "|"
                + normalize(episode.subject)
                + "|"
                + normalize(episode.object)
                + "|"
                + normalize(episode.summary);
    }

    private static double ageDays(String time, long nowMillis) {
        if (time == null || time.isBlank()) {
            return 0.0D;
        }
        try {
            return Math.max(0.0D, (nowMillis - Instant.parse(time).toEpochMilli()) / 86_400_000.0D);
        } catch (DateTimeParseException ignored) {
            return 0.0D;
        }
    }

    private static String category(LifeMemoryStore.MemoryEpisode episode) {
        return episode.category == null || episode.category.isBlank()
                ? MemoryCategory.RELATION_EVENT.id()
                : episode.category;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private static double confidenceOrDefault(LifeMemoryStore.MemoryEpisode episode) {
        return episode.confidence <= 0.0D ? 0.70D : clamp01(episode.confidence);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public record MaintenanceReport(
            int scanned,
            int exactDuplicates,
            int merged,
            int degraded,
            int pinned,
            int errorMarked
    ) {
    }

    private record MergeResult(List<LifeMemoryStore.MemoryEpisode> episodes, int duplicates, int merged) {
    }
}
