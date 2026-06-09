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
            return new MaintenanceReport(0, 0, 0, 0, 0, 0, 0, 0, 0);
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

        MergeResult exactMerged = mergeDuplicates(normalized);
        MergeResult structuralMerged = mergeStructurallyEquivalent(exactMerged.episodes());
        int errorMarked = preservePlannerErrorMarks(structuralMerged.episodes());
        int errorAffected = applyErrorMarks(structuralMerged.episodes());
        int degraded = applyDecay(structuralMerged.episodes());
        int pinned = pinImportant(structuralMerged.episodes());
        store.replaceAllEpisodes(structuralMerged.episodes());
        return new MaintenanceReport(
                all.size(),
                exactMerged.duplicates(),
                exactMerged.merged(),
                structuralMerged.duplicates(),
                structuralMerged.merged(),
                degraded,
                pinned,
                errorMarked,
                errorAffected
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

    private static MergeResult mergeStructurallyEquivalent(List<LifeMemoryStore.MemoryEpisode> episodes) {
        Map<String, LifeMemoryStore.MemoryEpisode> unique = new LinkedHashMap<>();
        int duplicates = 0;
        int merged = 0;
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if (!canStructuralMerge(episode)) {
                unique.put("raw|" + unique.size(), episode);
                continue;
            }
            String key = structuralKeyOf(episode);
            LifeMemoryStore.MemoryEpisode previous = unique.get(key);
            if (previous == null) {
                unique.put(key, episode);
                continue;
            }
            if (!structurallyCompatible(previous, episode)) {
                unique.put(key + "|variant|" + unique.size(), episode);
                continue;
            }
            duplicates++;
            merged++;
            mergeInto(previous, episode);
        }
        return new MergeResult(new ArrayList<>(unique.values()), duplicates, merged);
    }

    private static boolean canStructuralMerge(LifeMemoryStore.MemoryEpisode episode) {
        String category = category(episode);
        if ("error_mark".equals(category) || episode.errorMarked || episode.contradicted) {
            return false;
        }
        return "owner_profile".equals(category)
                || "maid_self".equals(category)
                || "world_fact".equals(category)
                || "promise".equals(category)
                || "memory_anchor".equals(category)
                || "repair_record".equals(category);
    }

    private static boolean structurallyCompatible(
            LifeMemoryStore.MemoryEpisode previous,
            LifeMemoryStore.MemoryEpisode episode
    ) {
        double summary = ngramJaccard(previous.summary, episode.summary, 2);
        double evidence = ngramJaccard(previous.evidence, episode.evidence, 2);
        double vector = ngramJaccard(previous.vectorText, episode.vectorText, 2);
        return Math.max(summary, Math.max(evidence, vector)) >= 0.56D;
    }

    private static void mergeInto(LifeMemoryStore.MemoryEpisode previous, LifeMemoryStore.MemoryEpisode episode) {
        double previousConfidence = previous.confidence;
        int previousImportance = previous.importance;
        previous.importance = Math.max(previous.importance, episode.importance);
        previous.confidence = Math.max(previous.confidence, episode.confidence);
        previous.salience = Math.max(previous.salience, episode.salience);
        previous.pinned = previous.pinned || episode.pinned;
        previous.contradicted = previous.contradicted || episode.contradicted;
        previous.errorMarked = previous.errorMarked || episode.errorMarked;
        previous.mergeCount = Math.max(1, previous.mergeCount) + Math.max(1, episode.mergeCount);
        previous.time = newerTime(previous.time, episode.time);
        previous.evidence = mergeEvidence(previous.evidence, episode.evidence);
        if (episode.summary != null && !episode.summary.isBlank()
                && (previous.summary == null || previous.summary.isBlank()
                || episode.confidence > previousConfidence
                || episode.importance > previousImportance)) {
            previous.summary = episode.summary;
        }
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

    private static int applyErrorMarks(List<LifeMemoryStore.MemoryEpisode> episodes) {
        int affected = 0;
        List<LifeMemoryStore.MemoryEpisode> marks = episodes.stream()
                .filter(e -> "error_mark".equals(category(e)) || e.errorMarked)
                .toList();
        if (marks.isEmpty()) {
            return 0;
        }
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if ("error_mark".equals(category(episode))) {
                continue;
            }
            for (LifeMemoryStore.MemoryEpisode mark : marks) {
                if (sameMemoryTarget(episode, mark)) {
                    if (!episode.contradicted || !episode.errorMarked) {
                        affected++;
                    }
                    episode.contradicted = true;
                    episode.errorMarked = true;
                    episode.evidence = mergeEvidence(episode.evidence, "被错误标记影响：" + mark.evidence);
                    break;
                }
            }
        }
        return affected;
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

    private static String structuralKeyOf(LifeMemoryStore.MemoryEpisode episode) {
        return category(episode)
                + "|"
                + normalize(episode.subject)
                + "|"
                + normalize(episode.object)
                + "|"
                + normalize(episode.relationPredicate)
                + "|"
                + normalize(episode.eventType);
    }

    private static boolean sameMemoryTarget(LifeMemoryStore.MemoryEpisode episode, LifeMemoryStore.MemoryEpisode mark) {
        String markSubject = normalize(mark.subject);
        String markObject = normalize(mark.object);
        if (markSubject.isBlank() && markObject.isBlank()) {
            return false;
        }
        boolean subjectMatch = markSubject.isBlank() || markSubject.equals(normalize(episode.subject));
        boolean objectMatch = markObject.isBlank() || markObject.equals(normalize(episode.object));
        boolean predicateMatch = normalize(mark.relationPredicate).isBlank()
                || normalize(mark.relationPredicate).equals(normalize(episode.relationPredicate));
        return subjectMatch && objectMatch && predicateMatch;
    }

    private static String mergeEvidence(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isBlank()) {
            return b;
        }
        if (b.isBlank() || a.contains(b)) {
            return a;
        }
        return a + " | " + b;
    }

    private static String newerTime(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        try {
            return Instant.parse(right).isAfter(Instant.parse(left)) ? right : left;
        } catch (DateTimeParseException ignored) {
            return left;
        }
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

    private static double ngramJaccard(String left, String right, int n) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return 0.0D;
        }
        if (a.equals(b)) {
            return 1.0D;
        }
        java.util.Set<String> leftGrams = ngrams(a, n);
        java.util.Set<String> rightGrams = ngrams(b, n);
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

    private static java.util.Set<String> ngrams(String text, int n) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
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
            int structuralDuplicates,
            int structuralMerged,
            int degraded,
            int pinned,
            int errorMarked,
            int errorAffected
    ) {
    }

    private record MergeResult(List<LifeMemoryStore.MemoryEpisode> episodes, int duplicates, int merged) {
    }
}
