package com.yunchen.maidsoulcore.core.memory;

import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地记忆检索器。
 *
 * <p>它承担“没有 embedding 模型也能检索”的基础路径：基于结构化字段、关系边、
 * 字符 n-gram、通用 token 覆盖和重要度/时间/置顶信息做融合排序。这里不做“道歉、
 * 喜欢、辱骂”等语义分类，那些语义只能来自 planner 的结构化事件。</p>
 */
public final class MemoryLocalRetriever {
    public List<MemorySearchResult> search(List<LifeMemoryStore.MemoryEpisode> episodes, String query, int limit) {
        if (episodes == null || episodes.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        Set<String> queryTokens = tokenize(normalizedQuery);
        Set<String> queryNgrams = ngrams(normalizedQuery, 2);
        boolean emptyQuery = normalizedQuery.isBlank();
        List<EpisodeDocument> documents = new ArrayList<>();
        Map<LifeMemoryStore.MemoryEpisode, EpisodeDocument> documentByEpisode = new java.util.IdentityHashMap<>();
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if (episode == null || episode.summary == null || episode.summary.isBlank()) {
                continue;
            }
            EpisodeDocument doc = EpisodeDocument.from(episode);
            documents.add(doc);
            documentByEpisode.put(episode, doc);
        }
        Map<String, Integer> tokenDf = documentFrequency(documents, true);
        Map<String, Integer> ngramDf = documentFrequency(documents, false);

        List<MemorySearchResult> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (LifeMemoryStore.MemoryEpisode episode : episodes) {
            if (episode == null || episode.summary == null || episode.summary.isBlank()) {
                continue;
            }
            if (episode.errorMarked || episode.contradicted) {
                continue;
            }

            EpisodeDocument doc = documentByEpisode.get(episode);
            if (doc == null) {
                continue;
            }
            double lexical = emptyQuery ? 0.0D : lexicalScore(doc, queryTokens, queryNgrams, tokenDf, ngramDf);
            double graph = emptyQuery ? 0.0D : graphScore(doc, queryTokens);
            double metadata = emptyQuery ? 0.0D : metadataScore(doc, queryTokens);
            double priority = priorityScore(episode, now);
            if (!emptyQuery && lexical <= 0.0D && graph <= 0.0D && metadata <= 0.0D) {
                continue;
            }

            double score = emptyQuery
                    ? priority
                    : 0.58D * lexical + 0.20D * graph + 0.10D * metadata + 0.12D * priority;
            if (score <= 0.0D && !emptyQuery) {
                continue;
            }
            results.add(new MemorySearchResult(
                    episode,
                    score,
                    lexical,
                    graph,
                    metadata,
                    priority,
                    sourceOf(lexical, graph, metadata)
            ));
        }

        results.sort(Comparator
                .comparingDouble(MemorySearchResult::score).reversed()
                .thenComparing((MemorySearchResult r) -> r.episode().pinned ? 0 : 1)
                .thenComparing((MemorySearchResult r) -> -r.episode().importance));
        if (!emptyQuery) {
            results = pruneWeakTail(results);
        }
        int safeLimit = Math.max(1, limit);
        if (results.size() <= safeLimit) {
            return results;
        }
        return new ArrayList<>(results.subList(0, safeLimit));
    }

    private static List<MemorySearchResult> pruneWeakTail(List<MemorySearchResult> sorted) {
        if (sorted.size() <= 1) {
            return sorted;
        }
        double best = Math.max(0.0D, sorted.get(0).score());
        double floor = Math.max(0.08D, best * 0.45D);
        List<MemorySearchResult> kept = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            MemorySearchResult result = sorted.get(i);
            if (i == 0 || result.score() >= floor || result.graphScore() >= 0.35D || result.metadataScore() >= 0.35D) {
                kept.add(result);
            }
        }
        return kept.isEmpty() ? List.of(sorted.get(0)) : kept;
    }

    private static double lexicalScore(
            EpisodeDocument doc,
            Set<String> queryTokens,
            Set<String> queryNgrams,
            Map<String, Integer> tokenDf,
            Map<String, Integer> ngramDf
    ) {
        double token = weightedOverlapRatio(queryTokens, doc.tokens(), tokenDf);
        double gram = weightedOverlapRatio(queryNgrams, doc.ngrams(), ngramDf);
        return clamp01(Math.max(token, gram * 0.92D));
    }

    private static double graphScore(EpisodeDocument doc, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0D;
        }
        double subject = queryTokens.contains(doc.subject()) ? 0.35D : 0.0D;
        double object = queryTokens.contains(doc.object()) ? 0.35D : 0.0D;
        double predicate = queryTokens.contains(doc.predicate()) || queryTokens.contains(doc.eventType()) ? 0.20D : 0.0D;
        double pair = subject > 0.0D && object > 0.0D ? 0.20D : 0.0D;
        return clamp01(subject + object + predicate + pair);
    }

    private static double metadataScore(EpisodeDocument doc, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0D;
        }
        double score = 0.0D;
        if (queryTokens.contains(doc.category())) {
            score += 0.35D;
        }
        if (queryTokens.contains(doc.knowledgeType())) {
            score += 0.20D;
        }
        if (queryTokens.contains(doc.sourceKind())) {
            score += 0.15D;
        }
        return clamp01(score);
    }

    private static double priorityScore(LifeMemoryStore.MemoryEpisode episode, long now) {
        double importance = Math.max(1, Math.min(100, episode.importance)) / 100.0D;
        double confidence = episode.confidence <= 0.0D ? 0.70D : clamp01(episode.confidence);
        double salience = episode.salience <= 0.0D ? importance * confidence : clamp01(episode.salience);
        double recency = 1.0D / (1.0D + Math.max(0.0D, ageDays(episode.time, now)) / 14.0D);
        double pinned = episode.pinned ? 0.16D : 0.0D;
        double merged = Math.min(0.08D, Math.max(0, episode.mergeCount - 1) * 0.015D);
        return clamp01(0.46D * salience + 0.24D * importance + 0.14D * confidence + 0.10D * recency + pinned + merged);
    }

    private static double overlapRatio(Set<String> query, Set<String> document) {
        if (query.isEmpty() || document.isEmpty()) {
            return 0.0D;
        }
        int hit = 0;
        for (String token : query) {
            if (document.contains(token)) {
                hit++;
            }
        }
        return clamp01(hit / (double) query.size());
    }

    private static double weightedOverlapRatio(Set<String> query, Set<String> document, Map<String, Integer> df) {
        if (query.isEmpty() || document.isEmpty()) {
            return 0.0D;
        }
        double hit = 0.0D;
        double total = 0.0D;
        for (String token : query) {
            double weight = 1.0D / Math.sqrt(1.0D + Math.max(0, df.getOrDefault(token, 0)));
            total += weight;
            if (document.contains(token)) {
                hit += weight;
            }
        }
        if (total <= 0.0D) {
            return 0.0D;
        }
        return clamp01(hit / total);
    }

    private static Map<String, Integer> documentFrequency(List<EpisodeDocument> documents, boolean tokens) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (EpisodeDocument doc : documents) {
            Set<String> values = tokens ? doc.tokens() : doc.ngrams();
            for (String value : values) {
                out.put(value, out.getOrDefault(value, 0) + 1);
            }
        }
        return out;
    }

    private static String sourceOf(double lexical, double graph, double metadata) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("local_lexical", lexical);
        scores.put("local_graph", graph);
        scores.put("local_metadata", metadata);
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0.0D)
                .map(Map.Entry::getKey)
                .orElse("local_priority");
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

    private static Set<String> tokenize(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                current.appendCodePoint(cp);
            } else {
                flushToken(out, current);
            }
            i += Character.charCount(cp);
        }
        flushToken(out, current);
        return out;
    }

    private static void flushToken(Set<String> out, StringBuilder current) {
        if (current.length() <= 0) {
            return;
        }
        String token = current.toString();
        if (!token.isBlank()) {
            out.add(token);
        }
        current.setLength(0);
    }

    private static Set<String> ngrams(String text, int n) {
        Set<String> out = new HashSet<>();
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        if (compact.isBlank()) {
            return out;
        }
        int[] cps = compact.codePoints().toArray();
        if (cps.length <= n) {
            out.add(compact);
            return out;
        }
        for (int i = 0; i <= cps.length - n; i++) {
            out.add(new String(cps, i, n));
        }
        return out;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record EpisodeDocument(
            String text,
            Set<String> tokens,
            Set<String> ngrams,
            String category,
            String subject,
            String object,
            String predicate,
            String eventType,
            String knowledgeType,
            String sourceKind
    ) {
        static EpisodeDocument from(LifeMemoryStore.MemoryEpisode episode) {
            String subject = normalize(episode.subject);
            String object = normalize(episode.object);
            String predicate = normalize(episode.relationPredicate);
            String eventType = normalize(episode.eventType);
            String category = normalize(episode.category);
            String knowledgeType = normalize(episode.knowledgeType);
            String sourceKind = normalize(episode.sourceKind);
            String body = normalize(String.join("\n",
                    safe(episode.summary),
                    safe(episode.evidence),
                    safe(episode.vectorText),
                    subject,
                    object,
                    predicate,
                    eventType,
                    category,
                    knowledgeType,
                    sourceKind
            ));
            return new EpisodeDocument(
                    body,
                    tokenize(body),
                    MemoryLocalRetriever.ngrams(body, 2),
                    category,
                    subject,
                    object,
                    predicate,
                    eventType,
                    knowledgeType,
                    sourceKind
            );
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
