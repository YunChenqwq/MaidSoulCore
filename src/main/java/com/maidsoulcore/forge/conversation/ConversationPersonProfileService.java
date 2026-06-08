package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.service.MaidSoulEmotionService;

/**
 * 当前人物画像注入。
 * <p>
 * 聊天源码里会根据当前说话人、@ 对象、引用对象注入人物画像。
 * Minecraft 私聊场景里最重要的人物就是主人，所以这里把主人画像整理成 reference：
 * 好感度来自车万女仆实体，本地偏好来自会话记忆，关系情绪来自我们的情绪系统。
 */
public final class ConversationPersonProfileService {
    private ConversationPersonProfileService() {
    }

    public static String ownerProfileReference(EntityMaid maid) {
        if (maid == null) {
            return "none";
        }
        String ownerName = maid.getOwner() == null ? "主人" : maid.getOwner().getName().getString();
        int favorability = safeFavorability(maid);
        int level = safeFavorabilityLevel(maid);
        String notes = ConversationMemoryService.notesForPrompt(maid);
        return """
                owner_name=%s
                maid_favorability=%d
                maid_favorability_level=%d
                owner_notes=%s
                relationship_state=%s
                rule=Use this as private background. Do not recite numbers unless the owner asks directly; let it influence trust, closeness, and how easy repair feels.
                """.formatted(
                ownerName,
                favorability,
                level,
                notes == null || notes.isBlank() ? "none" : notes,
                MaidSoulEmotionService.debugSummary(maid)
        );
    }

    private static int safeFavorability(EntityMaid maid) {
        try {
            return maid.getFavorability();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int safeFavorabilityLevel(EntityMaid maid) {
        try {
            return maid.getFavorabilityManager().getLevel();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
