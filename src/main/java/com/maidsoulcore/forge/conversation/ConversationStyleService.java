package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;

import java.util.List;
import java.util.UUID;

/**
 * Small expression style selector for ordinary chat.
 */
public final class ConversationStyleService {
    private ConversationStyleService() {
    }

    public static String chooseStyle(EntityMaid maid, String latestOwnerMessage) {
        List<? extends String> variants = MaidSoulCommonConfig.CONVERSATION_STYLE_VARIANTS.get();
        if (!MaidSoulCommonConfig.CONVERSATION_STYLE_ROTATION_ENABLED.get() || variants.isEmpty()) {
            return variants.isEmpty() ? "自然日常：像熟人聊天，不解释系统，不写报告。" : variants.get(0);
        }
        int seed = Math.abs((maid == null ? UUID.randomUUID() : maid.getUUID()).hashCode()
                ^ String.valueOf(latestOwnerMessage).hashCode()
                ^ (int) (System.currentTimeMillis() / 30_000L));
        return variants.get(seed % variants.size());
    }
}
