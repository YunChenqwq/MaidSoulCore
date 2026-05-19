package com.maidsoul.brain.memory;

import com.maidsoul.brain.affect.AffectEngine;
import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.affect.AffectProfile;
import com.maidsoul.brain.affect.AffectProfileStore;
import com.maidsoul.brain.affect.AffectSnapshot;
import com.maidsoul.brain.character.CharacterPackage;
import com.maidsoul.brain.config.MemoryConfig;

import java.nio.file.Path;
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
    private final LifeMemoryStore memoryStore;
    private final DailyMemoryConsolidator dailyConsolidator;
    private final AffectProfileStore affectStore;
    private final UserProfileStore profileStore;
    private final CharacterPackage characterPackage;
    private final AffectProfile affectProfile;
    private final UserProfile userProfile;

    public MemoryRuntime(MemoryConfig config) {
        this.config = config;
        this.memoryStore = new LifeMemoryStore(config);
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
            memoryStore.append(LifeMemory.of(
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
            ));
        }
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeWorldEvent(String eventType, String content) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.observeWorldEvent(affectProfile, eventType);
        memoryStore.append(LifeMemory.of(
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
        ));
        saveState();
        refreshDailySummary();
    }

    public synchronized void observeAffectEvent(AffectEvent event) {
        if (!config.enabled()) {
            return;
        }
        affectEngine.apply(affectProfile, event);
        saveState();
        refreshDailySummary();
    }

    public synchronized String renderPromptBlock(String latestText) {
        if (!config.enabled()) {
            return "";
        }
        String memories = memoryStore.renderPromptBlock(latestText, config.promptMemoryLimit());
        String profile = userProfile.renderForPrompt(config.promptProfileLimit());
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
                .append("\n\n[用户画像]\n")
                .append(profile == null || profile.isBlank() ? "none" : profile);
        return builder.toString();
    }

    public synchronized String queryMemory(String query, int limit) {
        if (!config.enabled()) {
            return "记忆系统未启用。";
        }
        List<LifeMemory> hits = memoryStore.search(query, limit <= 0 ? config.retrievalLimit() : limit);
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
        String text = memory.content;
        if (memory.tags.contains("conversation_style")) {
            userProfile.reinforcePreference(
                    "conversation_style",
                    "玩家重视自然、有连续性、不过度刷屏的聊天节奏。",
                    memory.id
            );
        }
        if (text.contains("不喜欢") || text.contains("讨厌")) {
            userProfile.reinforceBoundary("dislike_boundary", "玩家会明确指出不喜欢的表达方式，需要后续避免复读。", memory.id);
        }
        if (text.contains("烦") || text.contains("累") || text.contains("难受")) {
            userProfile.reinforceTrait("stress_response", "玩家情绪烦躁时需要先被接住，再慢慢展开话题。", memory.id);
        }
        if (text.contains("喜欢") || text.contains("希望") || text.contains("想要")) {
            userProfile.reinforcePreference("explicit_preference", "玩家表达过明确偏好，后续回复应优先尊重。", memory.id);
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
