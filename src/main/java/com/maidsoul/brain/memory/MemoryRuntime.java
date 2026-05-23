package com.maidsoul.brain.memory;

import com.maidsoul.brain.affect.AffectEngine;
import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.affect.AffectEventKind;
import com.maidsoul.brain.affect.AffectProfile;
import com.maidsoul.brain.affect.AffectProfileStore;
import com.maidsoul.brain.affect.AffectSnapshot;
import com.maidsoul.brain.character.CharacterPackage;
import com.maidsoul.brain.config.MemoryConfig;
import com.maidsoul.brain.memory.v2.MemorySearchResult;
import com.maidsoul.brain.memory.v2.MemoryV2Store;
import com.maidsoul.brain.memory.v2.MemoryMaintenanceReport;
import com.maidsoul.brain.memory.v2.MemoryWritePlan;
import com.maidsoul.brain.memory.v2.MemoryWriteResult;
import com.maidsoul.brain.memory.v2.MemoryWriteStrategy;
import com.maidsoul.brain.memory.v2.PersonProfileSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 情绪、人生记忆、用户画像的统一运行时。
 *
 * <p>它不直接让角色说话，只给主链路提供“她经历了什么、现在怎么感觉、她逐渐认识到什么”。</p>
 */
public final class MemoryRuntime {
    private final MemoryConfig config;
    private final AffectEngine affectEngine = new AffectEngine();
    private final MemoryCandidateExtractor extractor = new MemoryCandidateExtractor();
    private final MemoryWriteStrategy writeStrategy = new MemoryWriteStrategy();
    private final LifeMemoryStore memoryStore;
    private final MemoryV2Store memoryV2Store;
    private final DailyMemoryConsolidator dailyConsolidator;
    private final AffectProfileStore affectStore;
    private final UserProfileStore profileStore;
    private final CharacterPackage characterPackage;
    private final AffectProfile affectProfile;
    private final UserProfile userProfile;
    private int v2WritesSinceMaintenance;

    public MemoryRuntime(MemoryConfig config) {
        this.config = config;
        this.memoryStore = new LifeMemoryStore(config);
        this.memoryV2Store = new MemoryV2Store(config);
        this.dailyConsolidator = new DailyMemoryConsolidator(memoryStore.dailyDir());
        Path maidDir = memoryStore.maidDir();
        Path characterRoot = Path.of(config.characterRoot()).resolve(config.maidId());
        this.characterPackage = CharacterPackage.load(characterRoot, config.maidId());
        this.affectStore = new AffectProfileStore(characterPackage.affectPath(), maidDir.resolve("affect.json"));
        this.profileStore = new UserProfileStore(maidDir.resolve("user_profile.json"));
        this.affectProfile = affectStore.load();
        this.userProfile = profileStore.load(config.ownerId());
    }

    public synchronized void observeUserMessage(String text) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.observeOwnerMessage(affectProfile, text);
        MemoryCandidateExtractor.Candidate candidate = extractor.extractUserMessage(text);
        if (candidate.shouldRemember()) {
            LifeMemory memory = LifeMemory.of(
                    config.maidId(),
                    config.ownerId(),
                    config.worldId(),
                    candidate.type(),
                    "chat",
                    "user",
                    text,
                    candidate.importance(),
                    candidate.tags(),
                    AffectSnapshot.from(affectProfile)
            );
            memoryStore.append(memory);
            ingestV2(
                    "life:" + memory.id,
                    "chat",
                    "user",
                    text,
                    candidate.type(),
                    candidate.importance(),
                    candidate.tags()
            );
            updateProfileFrom(memory);
        }
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeAssistantMessage(String text) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.observeAssistantReply(affectProfile, text);
        MemoryCandidateExtractor.Candidate candidate = extractor.extractAssistantMessage(text);
        if (candidate.shouldRemember()) {
            LifeMemory memory = LifeMemory.of(
                    config.maidId(),
                    config.ownerId(),
                    config.worldId(),
                    candidate.type(),
                    "chat",
                    "assistant",
                    text,
                    candidate.importance(),
                    candidate.tags(),
                    AffectSnapshot.from(affectProfile)
            );
            memoryStore.append(memory);
            ingestV2(
                    "life:" + memory.id,
                    "chat",
                    "assistant",
                    text,
                    candidate.type(),
                    candidate.importance(),
                    candidate.tags()
            );
        }
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeWorldEvent(String eventType, String content) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.observeWorldEvent(affectProfile, eventType);
        LifeMemory memory = LifeMemory.of(
                config.maidId(),
                config.ownerId(),
                config.worldId(),
                MemoryType.WORLD,
                "world",
                "event",
                content == null || content.isBlank() ? eventType : content,
                4,
                List.of("world", eventType == null ? "event" : eventType),
                AffectSnapshot.from(affectProfile)
        );
        memoryStore.append(memory);
        ingestV2(
                "life:" + memory.id,
                "world",
                "event",
                content == null || content.isBlank() ? eventType : content,
                MemoryType.WORLD,
                4,
                List.of("world", eventType == null ? "event" : eventType)
        );
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeAffectEvent(AffectEvent event) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.apply(affectProfile, event);
        if (shouldPersistAffectEvent(event)) {
            ingestV2(
                    "affect:" + event.kind().name() + ":" + System.nanoTime(),
                    "affect",
                    "affect_event",
                    event.kind().name() + " intensity=" + event.intensity() + " note=" + event.note(),
                    MemoryType.EMOTION,
                    Math.max(3, event.intensity() / 12),
                    List.of("affect_event", "repair_debt", event.kind().name().toLowerCase())
            );
        }
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeStructuredMemory(StructuredMemoryEvent event) {
        if (!config.enabled() || event == null || event.content().isBlank()) {
            return;
        }
        List<String> tags = new ArrayList<>(event.tags());
        if (!event.layer().isBlank()) {
            tags.add(event.layer());
        }
        LifeMemory memory = LifeMemory.of(
                config.maidId(),
                config.ownerId(),
                config.worldId(),
                event.type(),
                event.source().isBlank() ? "structured" : event.source(),
                event.role().isBlank() ? "event" : event.role(),
                event.content(),
                event.importance(),
                tags,
                AffectSnapshot.from(affectProfile)
        );
        memoryStore.append(memory);
        ingestV2(
                "structured:" + memory.id,
                event.source().isBlank() ? "structured" : event.source(),
                event.role().isBlank() ? "event" : event.role(),
                event.content(),
                event.type(),
                event.importance(),
                tags
        );
        updateProfileFrom(memory);
        saveState();
        refreshDailySummary();
    }

    public synchronized String renderPromptBlock(String latestText) {
        if (!config.enabled()) {
            return "";
        }
        String memories = memoryStore.renderPromptBlock(latestText, config.promptMemoryLimit());
        String v2Memories = memoryV2Store.renderPromptBlock(latestText, config.promptMemoryLimit());
        String profile = userProfile.renderForPrompt(config.promptProfileLimit());
        PersonProfileSnapshot ownerProfile = memoryV2Store.getPersonProfile(config.ownerId(), config.promptProfileLimit());
        StringBuilder builder = new StringBuilder();
        builder.append(characterPackage.renderPromptBlock(affectProfile, latestText, config.promptMemoryLimit()))
                .append("\n\n[当前情绪关系]\n")
                .append(affectProfile.brief())
                .append("\n状态解释：")
                .append(affectProfile.stateHint())
                .append("\n主动欲望：")
                .append(affectProfile.proactiveHint())
                .append("\n\n[相关人生记忆]\n")
                .append(memories == null || memories.isBlank() ? "none" : memories)
                .append("\n\n[A-Memorix风格长期记忆]\n")
                .append(v2Memories == null || v2Memories.isBlank() ? "none" : v2Memories)
                .append("\n\n[A-Memorix人物画像]\n")
                .append(ownerProfile.profileText == null || ownerProfile.profileText.isBlank() ? "none" : ownerProfile.profileText)
                .append("\n\n[用户画像]\n")
                .append(profile == null || profile.isBlank() ? "none" : profile);
        return builder.toString();
    }

    public synchronized String queryMemory(String query, int limit) {
        if (!config.enabled()) {
            return "记忆系统未启用。";
        }
        int safeLimit = limit <= 0 ? config.retrievalLimit() : limit;
        MemorySearchResult v2Result = memoryV2Store.search(query, "aggregate", safeLimit);
        if (v2Result.success() && v2Result.hits() != null && !v2Result.hits().isEmpty()) {
            return v2Result.toPromptText(safeLimit);
        }
        List<LifeMemory> hits = memoryStore.search(query, safeLimit);
        if (hits.isEmpty()) {
            return "未找到相关记忆。";
        }
        StringBuilder builder = new StringBuilder("长期记忆检索结果：\n");
        for (LifeMemory hit : hits) {
            builder.append("- ")
                    .append(hit.content)
                    .append("（type=")
                    .append(hit.type.name().toLowerCase())
                    .append(", importance=")
                    .append(hit.importance)
                    .append(", affect=")
                    .append("心情")
                    .append(hit.mood)
                    .append("/受伤")
                    .append(hit.hurt)
                    .append("/愤怒")
                    .append(hit.anger)
                    .append("）\n");
        }
        return builder.toString().trim();
    }

    public synchronized MemoryMaintenanceReport maintainV2() {
        return memoryV2Store.maintainCycle();
    }

    public synchronized String debugMemoryV2(String query, int limit) {
        return memoryV2Store.debugDump(query, limit);
    }

    public synchronized String affectSummary() {
        return affectProfile.brief();
    }

    public synchronized String proactiveAffectHint() {
        return affectProfile.proactiveHint();
    }

    public synchronized int activeCuriosity() {
        return affectProfile.effectiveCuriosity();
    }

    private void updateProfileFrom(LifeMemory memory) {
        if (memory.tags.contains("conversation_style")) {
            userProfile.reinforcePreference(
                    "conversation_style",
                    "玩家重视自然、有连续性、不过度刷屏的聊天节奏。",
                    memory.id
            );
        }
        if (memory.tags.contains("boundary")) {
            userProfile.reinforceBoundary("explicit_boundary", "玩家表达过明确边界，后续回应需要优先尊重。", memory.id);
        }
        if (memory.tags.contains("stress_response")) {
            userProfile.reinforceTrait("stress_response", "玩家情绪烦躁时需要先被接住，再慢慢展开话题。", memory.id);
        }
        if (memory.tags.contains("preference")) {
            userProfile.reinforcePreference("explicit_preference", "玩家表达过明确偏好，后续回复应优先尊重。", memory.id);
        }
    }

    private static boolean shouldPersistAffectEvent(AffectEvent event) {
        return event.intensity() >= 45
                || event.kind() == AffectEventKind.OWNER_APOLOGY
                || event.kind() == AffectEventKind.MAID_HURT_BY_OWNER
                || event.kind() == AffectEventKind.MAID_HURT_BY_WORLD;
    }

    private void ingestV2(
            String externalId,
            String sourceType,
            String role,
            String text,
            MemoryType type,
            int importance,
            List<String> tags
    ) {
        MemoryWritePlan plan = writeStrategy.plan(role, text, type, importance, tags);
        if (!plan.shouldStore()) {
            return;
        }
        List<String> participants = role == null || role.isBlank()
                ? List.of(config.ownerId())
                : List.of(config.ownerId(), role);
        MemoryWriteResult ignored = memoryV2Store.ingestText(
                externalId,
                plan.sourceType(),
                config.worldId(),
                role,
                text,
                participants,
                plan.tags(),
                "maidId=" + config.maidId() + ";ownerId=" + config.ownerId() + ";" + plan.metadataSuffix(),
                plan.salience()
        );
        if (ignored.success() && !ignored.storedIds().isEmpty()) {
            v2WritesSinceMaintenance++;
            if (v2WritesSinceMaintenance >= 12) {
                memoryV2Store.maintainCycle();
                v2WritesSinceMaintenance = 0;
            }
        }
    }

    private void saveState() {
        affectStore.save(affectProfile);
        profileStore.save(userProfile);
    }

    private void refreshDailySummary() {
        dailyConsolidator.refreshToday(memoryStore.all(), affectProfile, userProfile);
    }
}
