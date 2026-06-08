package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MaidSoulCore ??????????
 * <p>
 * ?????? MaiBot ????????????????????
 * ??????????????????????????????
 */
public final class MaidSoulTopicCooldownService {
    /**
     * ?????????????????
     */
    private static final ConcurrentMap<UUID, ConcurrentMap<String, TopicRecord>> TOPIC_CACHE = new ConcurrentHashMap<>();

    private MaidSoulTopicCooldownService() {
    }

    /**
     * ????????????????????
     *
     * @param maid ????
     * @param eventType ????
     * @param detail ????
     * @return true ?????????????????
     */
    public static boolean shouldSuppress(EntityMaid maid, String eventType, String detail) {
        if (maid == null || !MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.get() || isDedupExempt(eventType)) {
            return false;
        }
        String topicKey = classifyTopicKey(eventType, detail);
        if (topicKey.isBlank()) {
            return false;
        }
        pruneExpired(maid.getUUID());
        TopicRecord record = TOPIC_CACHE.getOrDefault(maid.getUUID(), new ConcurrentHashMap<>()).get(topicKey);
        if (record == null) {
            return false;
        }
        long windowMillis = MaidSoulCommonConfig.TOPIC_DEDUP_WINDOW_SECONDS.get() * 1000L;
        return System.currentTimeMillis() - record.lastSpokenMillis() < windowMillis;
    }

    /**
     * ????????????????????????
     *
     * @param maid ????
     * @param eventType ????
     * @param detail ????
     * @param spokenText ???????
     */
    public static void markSpoken(EntityMaid maid, String eventType, String detail, String spokenText) {
        if (maid == null || !MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.get()) {
            return;
        }
        mark(maid, eventType, detail, spokenText);
    }

    /**
     * Mark a topic as already claimed before the slow proactive generation
     * finishes. This prevents short tick loops from entering the same topic
     * several times while a model call is still in flight.
     */
    public static void markPending(EntityMaid maid, String eventType, String detail) {
        if (maid == null || !MaidSoulCommonConfig.TOPIC_DEDUP_ENABLED.get() || isDedupExempt(eventType)) {
            return;
        }
        mark(maid, eventType, detail, "pending");
    }

    private static void mark(EntityMaid maid, String eventType, String detail, String spokenText) {
        String topicKey = classifyTopicKey(eventType, detail);
        if (topicKey.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        ConcurrentMap<String, TopicRecord> perMaid = TOPIC_CACHE.computeIfAbsent(maid.getUUID(), id -> new ConcurrentHashMap<>());
        TopicRecord previous = perMaid.get(topicKey);
        String summary = buildSampleSummary(topicKey, detail, spokenText);
        perMaid.put(topicKey, new TopicRecord(
                topicKey,
                previous == null ? now : previous.firstSeenMillis(),
                now,
                eventType,
                summary
        ));
        pruneExpired(maid.getUUID());
    }

    /**
     * ?????????????????????????????
     *
     * @param eventType ????
     * @param detail ????
     * @return ????? topicKey
     */
    public static String classifyTopicKey(String eventType, String detail) {
        if (eventType == null || eventType.isBlank()) {
            return "";
        }
        if (eventType.startsWith("world.weather.changed")) {
            return "topic.weather";
        }
        if (eventType.startsWith("world.time_phase.changed")) {
            return "topic.time_phase:" + normalizeToken(detail, "unknown");
        }
        if (eventType.startsWith("owner.view.cute_animal")) {
            return "topic.cute_animal";
        }
        if (eventType.startsWith("owner.view.player_nearby")) {
            return "topic.player_nearby";
        }
        if (eventType.startsWith("owner.view.other_maid")) {
            return "topic.other_maid";
        }
        if (eventType.startsWith("owner.view.risk_mob")) {
            return "topic.risk_mob";
        }
        if (eventType.startsWith("world.hostile_summary.changed")) {
            return "topic.hostile_summary";
        }
        if ("maid.idle.companion".equals(eventType)) {
            return "topic.idle_companion";
        }
        if (eventType.startsWith("maid.action.follow") || eventType.startsWith("maid.action.home")) {
            return "topic.maid_action:follow";
        }
        if (eventType.startsWith("maid.action.sit")) {
            return "topic.maid_action:sit";
        }
        if (eventType.startsWith("maid.action.schedule")) {
            return "topic.maid_action:schedule";
        }
        if (eventType.startsWith("maid.action.task") || eventType.startsWith("maid.action.executed")) {
            return "topic.maid_action:task";
        }
        if (eventType.startsWith("owner.view.")) {
            return "topic.owner_view:" + normalizeToken(eventType.substring("owner.view.".length()), "scene");
        }
        return "topic.event:" + normalizeToken(eventType, "generic");
    }

    /**
     * ??????????????? prompt ????????????
     *
     * @param maid ????
     * @param limit ??????
     * @return ?????????
     */
    public static List<String> tailRecentTopics(EntityMaid maid, int limit) {
        if (maid == null || limit <= 0) {
            return List.of();
        }
        pruneExpired(maid.getUUID());
        ConcurrentMap<String, TopicRecord> perMaid = TOPIC_CACHE.get(maid.getUUID());
        if (perMaid == null || perMaid.isEmpty()) {
            return List.of();
        }
        return perMaid.values().stream()
                .sorted(Comparator.comparingLong(TopicRecord::lastSpokenMillis).reversed())
                .limit(limit)
                .map(record -> record.topicKey() + " | " + record.sampleSummary())
                .toList();
    }

    /**
     * ?????????????????????
     *
     * @param eventType ????
     * @return true ????????????????
     */
    public static boolean isDedupExempt(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        if (eventType.startsWith("maid.attacked")
                || eventType.startsWith("maid.death")
                || eventType.startsWith("maid.action.")) {
            return true;
        }
        return eventType.contains("failed")
                || eventType.contains("missing")
                || eventType.contains("not_allowed")
                || eventType.contains("target_missing")
                || eventType.contains("owner.command");
    }

    /**
     * ????????????????????
     *
     * @param maidId ?? UUID
     */
    private static void pruneExpired(UUID maidId) {
        ConcurrentMap<String, TopicRecord> perMaid = TOPIC_CACHE.get(maidId);
        if (perMaid == null || perMaid.isEmpty()) {
            return;
        }
        long expireBefore = System.currentTimeMillis() - MaidSoulCommonConfig.TOPIC_DEDUP_WINDOW_SECONDS.get() * 1000L;
        perMaid.entrySet().removeIf(entry -> entry.getValue().lastSpokenMillis() < expireBefore);
        if (perMaid.isEmpty()) {
            TOPIC_CACHE.remove(maidId, perMaid);
        }
    }

    /**
     * ?????????????????? prompt ??????????????
     *
     * @param topicKey ???
     * @param detail ????
     * @param spokenText ????
     * @return ????
     */
    private static String buildSampleSummary(String topicKey, String detail, String spokenText) {
        String preferred = firstNonBlank(detail, spokenText, topicKey);
        preferred = preferred.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return preferred.length() <= 48 ? preferred : preferred.substring(0, 48) + "...";
    }

    /**
     * ???????????????????????? topicKey?
     *
     * @param raw ????
     * @param fallback ???
     * @return ?? topicKey ?? token
     */
    private static String normalizeToken(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    /**
     * ?????????????????
     */
    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return Objects.requireNonNullElse(fallback, "topic");
    }

    /**
     * ?????????
     */
    private record TopicRecord(
            String topicKey,
            long firstSeenMillis,
            long lastSpokenMillis,
            String sourceEventType,
            String sampleSummary
    ) {
    }
}
