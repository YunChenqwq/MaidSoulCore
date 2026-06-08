package com.maidsoulcore.forge.memory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.service.MaidSoulEmotionService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidSoulLifeMemoryService {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson LINE_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ConcurrentMap<UUID, Integer> LAST_GAME_DAY = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> LAST_DRAFT_FINGERPRINT = new ConcurrentHashMap<>();

    private MaidSoulLifeMemoryService() {
    }

    /**
     * 统一 journal 的落盘入口。
     *
     * 这里不决定女仆要不要说话，只负责把“她经历过什么”写进草稿。
     * 真正可见发言仍然由主聊天或 MindLoop 决定，生命记忆只做背景和长期理解。
     */
    public static void recordJournalEntry(EntityMaid maid,
                                          String role,
                                          String source,
                                          String eventType,
                                          String content,
                                          boolean visible) {
        if (!MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.get()) {
            return;
        }
        if (maid == null || content == null || content.isBlank()) {
            return;
        }
        try {
            ensureProfile(maid);
            DraftRecord record = DraftRecord.from(maid, role, source, eventType, content, visible);
            String fingerprint = record.role + "|" + record.source + "|" + record.eventType + "|" + record.content;
            UUID maidId = maid.getUUID();
            if (fingerprint.equals(LAST_DRAFT_FINGERPRINT.get(maidId))) {
                return;
            }
            LAST_DRAFT_FINGERPRINT.put(maidId, fingerprint);
            appendJsonLine(draftPath(maid), record);
            trimDraftIfNeeded(maid);
        } catch (Exception ex) {
            MaidSoulStateRegistry.record(maid, "maidsoul.life_memory.error", ex.getClass().getSimpleName() + ": " + ex.getMessage(), com.maidsoulcore.event.EventPriority.P2);
        }
    }

    /**
     * 每只女仆按 Minecraft 日期做一次轻量整理。
     *
     * 当前版本先用本地规则合并上一天的 draft，避免每天都额外调用模型。
     * 后续如果要上 LLM 整理，也应该挂在这里，并且做成可配置的后台任务。
     */
    public static void onMaidTick(EntityMaid maid) {
        if (!MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.get()
                || !MaidSoulCommonConfig.LIFE_MEMORY_DAILY_CONSOLIDATION_ENABLED.get()) {
            return;
        }
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        int currentDay = gameDay(maid);
        UUID maidId = maid.getUUID();
        Integer previous = LAST_GAME_DAY.putIfAbsent(maidId, currentDay);
        if (previous == null) {
            ensureProfileQuietly(maid);
            return;
        }
        if (currentDay > previous) {
            LAST_GAME_DAY.put(maidId, currentDay);
            consolidateDay(maid, previous);
        }
    }

    /**
     * 聊天 prompt 里的长期记忆参考。
     *
     * 收敛规则：生命记忆永远不主动触发说话，只在主人发言后按相关性取几条。
     * 这样它能解决“刚说过的重要关系转头忘了”，但不会自己跑出来刷存在感。
     */
    public static String promptBlock(EntityMaid maid, String latestOwnerMessage) {
        if (!MaidSoulCommonConfig.LIFE_MEMORY_ENABLED.get()) {
            return "none";
        }
        if (maid == null) {
            return "none";
        }
        try {
            int maxEpisodes = Math.max(0, MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_EPISODES.get());
            int maxUnderstandings = Math.max(0, MaidSoulCommonConfig.LIFE_MEMORY_MAX_PROMPT_UNDERSTANDINGS.get());
            List<EpisodeRecord> episodes = rankEpisodes(maid, latestOwnerMessage, maxEpisodes);
            List<UnderstandingRecord> understandings = rankUnderstandings(maid, latestOwnerMessage, maxUnderstandings);
            if (episodes.isEmpty() && understandings.isEmpty()) {
                return "none";
            }
            StringBuilder builder = new StringBuilder();
            if (!understandings.isEmpty()) {
                builder.append("life_understandings:\n");
                for (UnderstandingRecord record : understandings) {
                    builder.append("- ")
                            .append(shortText(record.subject, 40))
                            .append(": ")
                            .append(shortText(record.content, 180))
                            .append("\n");
                }
            }
            if (!episodes.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append("life_episodes:\n");
                for (EpisodeRecord episode : episodes) {
                    builder.append("- day ")
                            .append(episode.game_day)
                            .append(" ")
                            .append(shortText(episode.title, 50))
                            .append(": ")
                            .append(shortText(episode.content, 220))
                            .append("\n");
                }
            }
            return builder.toString().trim();
        } catch (Exception ignored) {
            return "none";
        }
    }

    /**
     * 把某一天的草稿整理成一条 episode，并同步更新稳定 understanding。
     */
    public static void consolidateDay(EntityMaid maid, int targetGameDay) {
        if (maid == null || targetGameDay < 0) {
            return;
        }
        try {
            ensureProfile(maid);
            List<DraftRecord> all = readDrafts(maid);
            List<DraftRecord> target = all.stream()
                    .filter(record -> record.game_day == targetGameDay)
                    .toList();
            if (target.isEmpty()) {
                return;
            }
            if (episodeAlreadyExists(maid, targetGameDay)) {
                rewriteDraft(maid, all.stream().filter(record -> record.game_day != targetGameDay).toList());
                return;
            }
            EpisodeRecord episode = buildEpisode(maid, targetGameDay, target);
            appendJsonLine(memoryPath(maid), episode);
            appendJsonLine(dailySummaryPath(maid), episode);
            patchUnderstandings(maid, episode);
            rewriteDraft(maid, all.stream().filter(record -> record.game_day != targetGameDay).toList());
            MaidSoulStateRegistry.record(maid, "maidsoul.life_memory.consolidated", "day=" + targetGameDay + " title=" + episode.title, com.maidsoulcore.event.EventPriority.P2);
        } catch (Exception ex) {
            MaidSoulStateRegistry.record(maid, "maidsoul.life_memory.error", ex.getClass().getSimpleName() + ": " + ex.getMessage(), com.maidsoulcore.event.EventPriority.P2);
        }
    }

    private static EpisodeRecord buildEpisode(EntityMaid maid, int gameDay, List<DraftRecord> records) {
        DraftRecord first = records.get(0);
        DraftRecord last = records.get(records.size() - 1);
        Set<String> participants = new LinkedHashSet<>();
        Set<String> keywords = new LinkedHashSet<>();
        ArrayList<String> raw = new ArrayList<>();
        int attacks = 0;
        int ownerLines = 0;
        int assistantLines = 0;
        int events = 0;
        for (DraftRecord record : records) {
            if (record.owner_name != null && !record.owner_name.isBlank()) {
                participants.add(record.owner_name);
            }
            if (record.maid_name != null && !record.maid_name.isBlank()) {
                participants.add(record.maid_name);
            }
            if ("user".equals(record.role) && "owner".equals(record.source)) {
                ownerLines++;
            } else if ("assistant".equals(record.role)) {
                assistantLines++;
            } else if ("event".equals(record.source)) {
                events++;
            }
            if (record.eventType().contains("attacked")) {
                attacks++;
                keywords.add("hurt");
                keywords.add("safety");
            }
            addKeywordHints(record.content, keywords);
            raw.add(renderRaw(record));
        }
        String title = makeTitle(records, attacks, ownerLines, events);
        String content = makeContent(records, attacks, ownerLines, assistantLines, events);
        int importance = Math.min(5, Math.max(1, 2 + attacks + (ownerLines > 0 ? 1 : 0) + (containsRelationship(records) ? 2 : 0)));
        EpisodeRecord episode = new EpisodeRecord();
        episode.id = UUID.randomUUID().toString();
        episode.format_version = FORMAT_VERSION;
        episode.memory_owner = maid.getUUID().toString();
        episode.maid_uuid = maid.getUUID().toString();
        episode.maid_name = maid.getName().getString();
        episode.owner_uuid = ownerUuid(maid);
        episode.owner_name = ownerName(maid);
        episode.game_day = gameDay;
        episode.real_date = LocalDate.now(ZoneId.systemDefault()).toString();
        episode.time = first.game_time + "-" + last.game_time;
        episode.location = first.location;
        episode.participants = new ArrayList<>(participants);
        episode.keywords = new ArrayList<>(keywords);
        episode.importance = importance;
        episode.title = title;
        episode.content = content;
        episode.raw_dialogue = raw.stream()
                .limit(Math.max(1, MaidSoulCommonConfig.LIFE_MEMORY_RAW_LINES_PER_EPISODE.get()))
                .toList();
        episode.created_at = Instant.now().toString();
        episode.last_recalled_at = episode.created_at;
        return episode;
    }

    private static void patchUnderstandings(EntityMaid maid, EpisodeRecord episode) throws IOException {
        Map<String, UnderstandingRecord> records = new LinkedHashMap<>();
        for (UnderstandingRecord record : readUnderstandings(maid)) {
            records.put(record.subject.toLowerCase(Locale.ROOT), record);
        }
        upsertUnderstanding(records, "daily_life", "The maid has a continuing life with the owner across game days.", episode);
        if (episode.keywords.contains("relationship")) {
            upsertUnderstanding(records, "relationship_commitment", "The owner has spoken about a close bond or commitment; future replies should remember it as relationship context.", episode);
        }
        if (episode.keywords.contains("hurt")) {
            upsertUnderstanding(records, "boundary_and_hurt", "When the owner hurts the maid, it remains part of the relationship until repaired by apology, care, or changed behavior.", episode);
        }
        if (episode.keywords.contains("preference")) {
            upsertUnderstanding(records, "owner_preferences", "The owner has shared personal preferences or self-description that should be respected later.", episode);
        }
        writeUnderstandings(maid, new ArrayList<>(records.values()));
    }

    private static void upsertUnderstanding(Map<String, UnderstandingRecord> records,
                                            String subject,
                                            String content,
                                            EpisodeRecord episode) {
        UnderstandingRecord record = records.get(subject);
        if (record == null) {
            record = new UnderstandingRecord();
            record.id = UUID.randomUUID().toString();
            record.format_version = FORMAT_VERSION;
            record.memory_owner = episode.memory_owner;
            record.subject = subject;
            record.keywords = new ArrayList<>();
            record.linked_episodes = new ArrayList<>();
            record.history = new ArrayList<>();
            records.put(subject, record);
        }
        record.content = content;
        if (!record.linked_episodes.contains(episode.id)) {
            record.linked_episodes.add(episode.id);
        }
        for (String keyword : episode.keywords) {
            if (!record.keywords.contains(keyword)) {
                record.keywords.add(keyword);
            }
        }
        UnderstandingHistoryEntry entry = new UnderstandingHistoryEntry();
        entry.episode_id = episode.id;
        entry.game_day = episode.game_day;
        entry.title = episode.title;
        entry.content = content;
        record.history.add(entry);
        while (record.history.size() > 8) {
            record.history.remove(0);
        }
        record.updated_at = Instant.now().toString();
    }

    private static List<EpisodeRecord> rankEpisodes(EntityMaid maid, String query, int limit) throws IOException {
        if (limit <= 0) {
            return List.of();
        }
        String normalized = normalize(query);
        List<EpisodeRecord> episodes = readEpisodes(maid);
        int today = gameDay(maid);
        return episodes.stream()
                .sorted(Comparator.comparingDouble((EpisodeRecord episode) -> scoreEpisode(episode, normalized, today)).reversed())
                .limit(limit)
                .toList();
    }

    private static List<UnderstandingRecord> rankUnderstandings(EntityMaid maid, String query, int limit) throws IOException {
        if (limit <= 0) {
            return List.of();
        }
        String normalized = normalize(query);
        return readUnderstandings(maid).stream()
                .sorted(Comparator.comparingDouble((UnderstandingRecord record) -> scoreUnderstanding(record, normalized)).reversed())
                .limit(limit)
                .toList();
    }

    private static double scoreEpisode(EpisodeRecord episode, String query, int today) {
        double score = episode.importance * 1.4;
        int age = Math.max(0, today - episode.game_day);
        score += Math.max(0.0, 4.0 - Math.min(age, 20) * 0.15);
        score += overlapScore(query, episode.title) + overlapScore(query, episode.content);
        for (String keyword : episode.keywords) {
            if (!query.isBlank() && query.contains(normalize(keyword))) {
                score += 3.0;
            }
        }
        return score;
    }

    private static double scoreUnderstanding(UnderstandingRecord record, String query) {
        double score = 2.0 + record.linked_episodes.size() * 0.4;
        score += overlapScore(query, record.subject) + overlapScore(query, record.content);
        for (String keyword : record.keywords) {
            if (!query.isBlank() && query.contains(normalize(keyword))) {
                score += 2.0;
            }
        }
        return score;
    }

    private static double overlapScore(String query, String text) {
        String normalizedText = normalize(text);
        if (query.isBlank() || normalizedText.isBlank()) {
            return 0.0;
        }
        if (normalizedText.contains(query) || query.contains(normalizedText)) {
            return 4.0;
        }
        double score = 0.0;
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 && normalizedText.contains(token)) {
                score += 1.0;
            }
        }
        return score;
    }

    private static void ensureProfile(EntityMaid maid) throws IOException {
        Files.createDirectories(maidDir(maid));
        Path profile = profilePath(maid);
        ProfileRecord record = new ProfileRecord();
        record.format_version = FORMAT_VERSION;
        record.maid_uuid = maid.getUUID().toString();
        record.maid_name = maid.getName().getString();
        record.owner_uuid = ownerUuid(maid);
        record.owner_name = ownerName(maid);
        record.storage_created_or_refreshed_at = Instant.now().toString();
        Files.writeString(profile, GSON.toJson(record), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void ensureProfileQuietly(EntityMaid maid) {
        try {
            ensureProfile(maid);
        } catch (IOException ignored) {
        }
    }

    private static void trimDraftIfNeeded(EntityMaid maid) throws IOException {
        int max = Math.max(20, MaidSoulCommonConfig.LIFE_MEMORY_MAX_DRAFT_LINES_PER_DAY.get());
        List<DraftRecord> records = readDrafts(maid);
        if (records.size() <= max * 4) {
            return;
        }
        rewriteDraft(maid, records.subList(Math.max(0, records.size() - max * 4), records.size()));
    }

    private static boolean episodeAlreadyExists(EntityMaid maid, int gameDay) throws IOException {
        for (EpisodeRecord episode : readEpisodes(maid)) {
            if (episode.game_day == gameDay) {
                return true;
            }
        }
        return false;
    }

    private static List<DraftRecord> readDrafts(EntityMaid maid) throws IOException {
        return readJsonLines(draftPath(maid), DraftRecord.class);
    }

    private static List<EpisodeRecord> readEpisodes(EntityMaid maid) throws IOException {
        return readJsonLines(memoryPath(maid), EpisodeRecord.class);
    }

    private static List<UnderstandingRecord> readUnderstandings(EntityMaid maid) throws IOException {
        return readJsonLines(understandingPath(maid), UnderstandingRecord.class);
    }

    private static void rewriteDraft(EntityMaid maid, List<DraftRecord> records) throws IOException {
        rewriteJsonLines(draftPath(maid), records);
    }

    private static void writeUnderstandings(EntityMaid maid, List<UnderstandingRecord> records) throws IOException {
        rewriteJsonLines(understandingPath(maid), records);
    }

    private static <T> List<T> readJsonLines(Path path, Class<T> type) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        ArrayList<T> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.trim();
                if (!clean.isEmpty()) {
                    records.add(LINE_GSON.fromJson(clean, type));
                }
            }
        }
        return records;
    }

    private static void appendJsonLine(Path path, Object record) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(LINE_GSON.toJson(record));
            writer.newLine();
        }
    }

    private static void rewriteJsonLines(Path path, List<?> records) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Object record : records) {
                writer.write(LINE_GSON.toJson(record));
                writer.newLine();
            }
        }
    }

    private static Path maidDir(EntityMaid maid) {
        String name = sanitize(maid.getName().getString());
        return rootDir().resolve("maids").resolve(maid.getUUID() + "-" + name);
    }

    private static Path rootDir() {
        String configured = MaidSoulCommonConfig.LIFE_MEMORY_ROOT_DIR.get();
        Path path = Path.of(configured == null || configured.isBlank() ? "maidsoulcore/life_memory" : configured);
        if (!path.isAbsolute()) {
            path = FMLPaths.CONFIGDIR.get().resolve(path);
        }
        return path.normalize();
    }

    private static Path profilePath(EntityMaid maid) {
        return maidDir(maid).resolve("profile.json");
    }

    private static Path draftPath(EntityMaid maid) {
        return maidDir(maid).resolve("memory_draft.jsonl");
    }

    private static Path memoryPath(EntityMaid maid) {
        return maidDir(maid).resolve("memory.jsonl");
    }

    private static Path dailySummaryPath(EntityMaid maid) {
        return maidDir(maid).resolve("daily_summaries.jsonl");
    }

    private static Path understandingPath(EntityMaid maid) {
        return maidDir(maid).resolve("understanding.jsonl");
    }

    private static int gameDay(EntityMaid maid) {
        return (int) Math.max(0L, maid.level().getDayTime() / 24000L);
    }

    private static String gameTime(EntityMaid maid) {
        long time = maid.level().getDayTime() % 24000L;
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String location(EntityMaid maid) {
        BlockPos pos = maid.blockPosition();
        String dimension = maid.level().dimension().location().toString();
        return dimension + " x=" + pos.getX() + " y=" + pos.getY() + " z=" + pos.getZ();
    }

    private static String ownerUuid(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? "" : owner.getUUID().toString();
    }

    private static String ownerName(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? "" : owner.getName().getString();
    }

    private static String sanitize(String text) {
        String clean = text == null ? "maid" : text.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.isBlank() ? "maid" : clean;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String shortText(String text, int max) {
        String clean = clean(text);
        int safe = Math.max(20, max);
        return clean.length() <= safe ? clean : clean.substring(0, safe) + "...";
    }

    private static String normalize(String text) {
        return clean(text).toLowerCase(Locale.ROOT);
    }

    private static void addKeywordHints(String text, Set<String> keywords) {
        String normalized = normalize(text);
        if (containsAny(normalized, "marry", "wife", "lover", "partner", "relationship")
                || containsAny(normalized, "结婚", "婚礼", "老婆", "妻子", "恋人", "伴侣")) {
            keywords.add("relationship");
        }
        if (containsAny(normalized, "like", "love", "want", "prefer")
                || containsAny(normalized, "喜欢", "想要", "讨厌", "我是", "我叫")) {
            keywords.add("preference");
        }
        if (containsAny(normalized, "sorry", "apologize", "comfort")
                || containsAny(normalized, "对不起", "抱歉", "安慰", "没事")) {
            keywords.add("repair");
        }
        if (containsAny(normalized, "fight", "attack", "danger", "hostile")
                || containsAny(normalized, "战斗", "攻击", "危险", "怪物")) {
            keywords.add("safety");
        }
    }

    private static boolean containsRelationship(List<DraftRecord> records) {
        for (DraftRecord record : records) {
            String normalized = normalize(record.content);
            if (containsAny(normalized, "marry", "wife", "lover", "partner", "结婚", "婚礼", "老婆", "妻子", "恋人", "伴侣")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String makeTitle(List<DraftRecord> records, int attacks, int ownerLines, int events) {
        if (containsRelationship(records)) {
            return "Relationship commitment and shared talk";
        }
        if (attacks > 0) {
            return "Hurt and emotional repair";
        }
        if (ownerLines > 0) {
            return "A day of conversation with the owner";
        }
        if (events > 0) {
            return "Observed world events";
        }
        return "Quiet daily life";
    }

    private static String makeContent(List<DraftRecord> records, int attacks, int ownerLines, int assistantLines, int events) {
        DraftRecord firstOwner = records.stream()
                .filter(record -> "user".equals(record.role) && "owner".equals(record.source))
                .findFirst()
                .orElse(null);
        StringBuilder builder = new StringBuilder();
        builder.append("This day included ")
                .append(ownerLines)
                .append(" owner messages, ")
                .append(assistantLines)
                .append(" maid replies, and ")
                .append(events)
                .append(" observed events.");
        if (firstOwner != null) {
            builder.append(" The owner said: ").append(shortText(firstOwner.content, 120)).append(".");
        }
        if (attacks > 0) {
            builder.append(" There was hurt or danger, so later dialogue should preserve the emotional continuity until repaired.");
        }
        if (containsRelationship(records)) {
            builder.append(" A relationship commitment topic appeared and should remain available as stable context.");
        }
        return builder.toString();
    }

    private static String renderRaw(DraftRecord record) {
        String head = record.game_time + " " + record.role + "/" + record.source;
        if (record.eventType() != null && !record.eventType().isBlank()) {
            head += " " + record.eventType();
        }
        return head + ": " + shortText(record.content, 220);
    }

    private static final class ProfileRecord {
        int format_version;
        String maid_uuid;
        String maid_name;
        String owner_uuid;
        String owner_name;
        String storage_created_or_refreshed_at;
    }

    private static final class DraftRecord {
        String id;
        int format_version;
        String created_at;
        int game_day;
        String game_time;
        String location;
        String maid_uuid;
        String maid_name;
        String owner_uuid;
        String owner_name;
        String role;
        String source;
        String eventType;
        String content;
        boolean visible;
        String mood_snapshot;

        static DraftRecord from(EntityMaid maid, String role, String source, String eventType, String content, boolean visible) {
            DraftRecord record = new DraftRecord();
            record.id = UUID.randomUUID().toString();
            record.format_version = FORMAT_VERSION;
            record.created_at = Instant.now().toString();
            record.game_day = gameDay(maid);
            record.game_time = gameTime(maid);
            record.location = location(maid);
            record.maid_uuid = maid.getUUID().toString();
            record.maid_name = maid.getName().getString();
            record.owner_uuid = ownerUuid(maid);
            record.owner_name = ownerName(maid);
            record.role = clean(role);
            record.source = clean(source);
            record.eventType = clean(eventType);
            record.content = shortText(content, MaidSoulCommonConfig.CONVERSATION_MEMORY_LINE_MAX_CHARS.get());
            record.visible = visible;
            record.mood_snapshot = shortText(MaidSoulEmotionService.debugSummary(maid), 260);
            return record;
        }

        String eventType() {
            return eventType == null ? "" : eventType;
        }
    }

    private static final class EpisodeRecord {
        String id;
        int format_version;
        String memory_owner;
        String maid_uuid;
        String maid_name;
        String owner_uuid;
        String owner_name;
        int game_day;
        String real_date;
        String time;
        String location;
        List<String> participants = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        int importance;
        String title;
        String content;
        List<String> raw_dialogue = new ArrayList<>();
        String created_at;
        String last_recalled_at;
    }

    private static final class UnderstandingRecord {
        String id;
        int format_version;
        String memory_owner;
        String subject;
        List<String> keywords = new ArrayList<>();
        String content;
        List<String> linked_episodes = new ArrayList<>();
        List<UnderstandingHistoryEntry> history = new ArrayList<>();
        String updated_at;
    }

    private static final class UnderstandingHistoryEntry {
        String episode_id;
        int game_day;
        String title;
        String content;
    }
}
